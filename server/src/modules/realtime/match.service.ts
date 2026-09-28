import { randomInt, randomUUID } from "node:crypto";
import { withLock, type Redis } from "../../infra/redis.js";
import { badRequest, conflict, forbidden, notFound } from "../../lib/errors.js";
import type { Logger } from "../../lib/logger.js";
import type { Clock } from "../../lib/time.js";
import { strikerPower } from "../economy/catalog.js";
import type { EconomyService } from "../economy/economy.service.js";
import { rateGame } from "../game/glicko.js";
import {
  classicCluster,
  findFreeSpot,
  simulateShot,
  type Piece,
  type PieceType,
  type ShotInput,
} from "../game/physics.js";
import { evaluateShot, noQueen, winnerOrNull, type OnlineMode, type QueenStatus, type Seat } from "../game/rules.js";
import type { LeaderboardService } from "../leaderboard/leaderboard.service.js";
import { MatchModel, type EndReason } from "../matches/match.model.js";
import { UserModel, type UserDocument } from "../users/user.model.js";
import type { DeadlineScheduler } from "./deadlines.js";

export interface SeatState {
  userId: string;
  playerId: string;
  name: string;
  avatarUrl?: string;
  level: number;
  rating: number;
  strikerId: string;
  score: number;
  fouls: number;
  pockets: number;
  queenCovers: number;
  timeouts: number;
  connected: boolean;
}

export interface MatchState {
  id: string;
  mode: OnlineMode;
  arena: string | null;
  ranked: boolean;
  entryFee: number;
  seats: [SeatState, SeatState];
  pieces: Piece[];
  queen: QueenStatus;
  current: Seat;
  turn: number;
  deadline: number;
  phase: "playing" | "over";
  winner: Seat | null;
  endReason: EndReason | null;
  startedAt: number;
}

/** What clients see: no internal user ids, pieces without velocities. */
export interface MatchSnapshot {
  id: string;
  mode: OnlineMode;
  arena: string | null;
  ranked: boolean;
  entryFee: number;
  seats: Array<Omit<SeatState, "userId" | "timeouts" | "pockets" | "queenCovers">>;
  pieces: Array<{ id: string; type: PieceType; x: number; y: number; pocketed: boolean }>;
  queen: QueenStatus;
  current: Seat;
  turn: number;
  deadline: number;
  /** Server clock when the snapshot was taken, so clients can correct for device clock skew. */
  serverTime: number;
  turnSeconds: number;
  phase: MatchState["phase"];
  winner: Seat | null;
  endReason: EndReason | null;
}

export interface MatchBroadcaster {
  toUser(userId: string, event: string, payload: unknown): void;
  toMatch(matchId: string, event: string, payload: unknown): void;
  /** Sends to everyone in the match except [exceptUserId]'s devices. */
  toMatchExcept(matchId: string, exceptUserId: string, event: string, payload: unknown): void;
  joinMatch(userId: string, matchId: string): void;
  leaveMatch(userId: string, matchId: string): void;
}

export interface StartOptions {
  mode: OnlineMode;
  arena: string | null;
  ranked: boolean;
  entryFee: number;
}

export interface AimUpdate {
  offset: number;
  angle: number;
  power: number;
}

export const MAX_TIMEOUTS = 3;
const WIN_XP = 100;
const LOSS_XP = 25;
const STATE_TTL_SECONDS = 2 * 3600;
const FINISHED_TTL_SECONDS = 10 * 60;

const stateKey = (matchId: string) => `match:${matchId}`;
const activeKey = (userId: string) => `user:${userId}:match`;
const lockKey = (matchId: string) => `lock:match:${matchId}`;

/**
 * Authoritative online matches. State lives in Redis (so any node can serve any event) and every
 * mutation runs under a per-match lock. Shots are simulated here; clients only send inputs.
 */
export class MatchService {
  constructor(
    private readonly redis: Redis,
    private readonly broadcaster: MatchBroadcaster,
    private readonly deadlines: DeadlineScheduler,
    private readonly economy: EconomyService,
    private readonly leaderboards: LeaderboardService,
    private readonly clock: Clock,
    private readonly logger: Logger,
    private readonly turnSeconds: number,
  ) {}

  async activeMatchId(userId: string): Promise<string | null> {
    return this.redis.get(activeKey(userId));
  }

  /** Seats both players, escrows entry fees and deals the break. */
  async start(userIds: [string, string], options: StartOptions): Promise<MatchSnapshot> {
    const users = await Promise.all(userIds.map((id) => UserModel.findById(id)));
    if (!users[0] || !users[1]) throw notFound("user_not_found", "Player not found");
    const id = randomUUID();

    if (options.entryFee > 0) {
      await this.economy.debit(userIds[0], { coins: options.entryFee, gems: 0 }, "entry_fee", id);
      try {
        await this.economy.debit(userIds[1], { coins: options.entryFee, gems: 0 }, "entry_fee", id);
      } catch (error) {
        await this.economy.credit(userIds[0], { coins: options.entryFee, gems: 0 }, "refund", id);
        throw error;
      }
    }

    // A coin toss decides who breaks.
    const order: [UserDocument, UserDocument] = randomInt(2) === 0 ? [users[0], users[1]] : [users[1], users[0]];
    const state: MatchState = {
      id,
      ...options,
      seats: [seatFor(order[0]), seatFor(order[1])],
      pieces: classicCluster(),
      queen: noQueen(),
      current: 0,
      turn: 0,
      deadline: this.nextDeadline(),
      phase: "playing",
      winner: null,
      endReason: null,
      startedAt: this.clock.now(),
    };
    await this.save(state);
    await Promise.all(state.seats.map((seat) => this.redis.set(activeKey(seat.userId), id, "EX", STATE_TTL_SECONDS)));
    await this.deadlines.schedule(id, state.turn, state.deadline);

    const snapshot = this.snapshot(state);
    for (const seat of state.seats) {
      this.broadcaster.joinMatch(seat.userId, id);
      // Sent to each player's own room: joining the match room may still be in flight on other nodes.
      this.broadcaster.toUser(seat.userId, "match:start", snapshot);
    }
    this.logger.info({ matchId: id, mode: options.mode, arena: options.arena }, "match started");
    return snapshot;
  }

  async snapshotFor(userId: string, matchId: string): Promise<MatchSnapshot> {
    const state = await this.load(matchId);
    this.seatOf(state, userId);
    this.broadcaster.joinMatch(userId, matchId);
    return this.snapshot(state);
  }

  /** Relays the shooter's live aim to the opponent (not stored). */
  async aim(userId: string, matchId: string, aim: AimUpdate): Promise<void> {
    const state = await this.load(matchId);
    const seat = this.seatOf(state, userId);
    if (state.phase !== "playing" || state.current !== seat) return;
    this.broadcaster.toMatchExcept(matchId, userId, "match:aim", { seat, ...aim });
  }

  /** Plays [input] for the caller. [turn] guards against replays and double taps. */
  async shoot(userId: string, matchId: string, input: ShotInput, turn: number): Promise<void> {
    await withLock(this.redis, lockKey(matchId), async () => {
      const state = await this.load(matchId);
      const seat = this.seatOf(state, userId);
      if (state.phase !== "playing") throw conflict("match_over", "This match has finished");
      if (state.current !== seat) throw forbidden("not_your_turn", "Wait for your turn");
      if (turn !== state.turn) throw conflict("stale_turn", "That turn has already been played");
      if (![input.offset, input.angle, input.power].every(Number.isFinite)) throw badRequest("invalid_shot", "Invalid shot");

      await this.deadlines.cancel(matchId, state.turn);
      const shooter = state.seats[seat];
      const sim = simulateShot(state.pieces, input, seat === 0, strikerPower(shooter.strikerId));
      const outcome = evaluateShot(
        seat,
        sim.pocketed.map((p) => p.type),
        sim.strikerPocketed,
        state.queen,
      );

      state.pieces = sim.pieces;
      if (outcome.returnQueenToCenter) {
        const queen = state.pieces.find((p) => p.type === "QUEEN");
        if (queen) {
          Object.assign(queen, findFreeSpot(state.pieces, queen.radius, queen), { pocketed: false, pocketId: -1 });
        }
      }
      shooter.score = Math.max(0, shooter.score + outcome.scoreDelta);
      shooter.fouls += outcome.isFoul ? 1 : 0;
      shooter.pockets += sim.pocketed.filter((p) => p.type !== "QUEEN").length;
      shooter.queenCovers += outcome.queenCoveredNow ? 1 : 0;
      shooter.timeouts = 0;
      state.queen = outcome.queen;

      const playedTurn = state.turn;
      const winner = winnerOrNull(
        state.mode,
        state.pieces.filter((p) => !p.pocketed).length,
        [state.seats[0].score, state.seats[1].score],
      );
      state.turn += 1;
      if (winner === null) {
        state.current = outcome.keepsTurn ? seat : other(seat);
        state.deadline = this.nextDeadline(sim.seconds);
        await this.save(state);
        await this.deadlines.schedule(matchId, state.turn, state.deadline);
      }

      this.broadcaster.toMatch(matchId, "match:shot", {
        matchId,
        turn: playedTurn,
        seat,
        input,
        strikerPower: strikerPower(shooter.strikerId),
        result: {
          pocketed: sim.pocketed.map((p) => p.id),
          strikerPocketed: sim.strikerPocketed,
          scoreDelta: outcome.scoreDelta,
          foul: outcome.isFoul,
          announcement: outcome.announcement,
          seconds: sim.seconds,
        },
        snapshot: this.snapshot(state),
      });

      if (winner !== null) await this.finish(state, winner, "completed");
    });
  }

  /** Called when a turn deadline passes: the turn moves on; three in a row forfeits. */
  async handleTimeout(matchId: string, turn: number): Promise<void> {
    await withLock(this.redis, lockKey(matchId), async () => {
      const state = await this.loadOrNull(matchId);
      if (!state || state.phase !== "playing" || state.turn !== turn) return;
      const seat = state.current;
      state.seats[seat].timeouts += 1;
      if (state.seats[seat].timeouts >= MAX_TIMEOUTS) {
        await this.finish(state, other(seat), "timeout");
        return;
      }
      state.current = other(seat);
      state.turn += 1;
      state.deadline = this.nextDeadline();
      await this.save(state);
      await this.deadlines.schedule(matchId, state.turn, state.deadline);
      this.broadcaster.toMatch(matchId, "match:turn", { reason: "timeout", timedOutSeat: seat, snapshot: this.snapshot(state) });
    });
  }

  async resign(userId: string, matchId: string): Promise<void> {
    await withLock(this.redis, lockKey(matchId), async () => {
      const state = await this.load(matchId);
      const seat = this.seatOf(state, userId);
      if (state.phase !== "playing") return;
      await this.deadlines.cancel(matchId, state.turn);
      await this.finish(state, other(seat), "resigned");
    });
  }

  async setConnected(userId: string, connected: boolean): Promise<void> {
    const matchId = await this.activeMatchId(userId);
    if (!matchId) return;
    await withLock(this.redis, lockKey(matchId), async () => {
      const state = await this.loadOrNull(matchId);
      if (!state || state.phase !== "playing") return;
      const seat = state.seats.findIndex((s) => s.userId === userId);
      if (seat < 0 || state.seats[seat]!.connected === connected) return;
      state.seats[seat]!.connected = connected;
      await this.save(state);
      this.broadcaster.toMatch(matchId, "match:presence", { seat, connected });
    });
  }

  /** Relays a quick-chat emote to both players. */
  async emote(userId: string, matchId: string, emote: string): Promise<void> {
    const state = await this.load(matchId);
    const seat = this.seatOf(state, userId);
    this.broadcaster.toMatch(matchId, "match:emote", { seat, emote });
  }

  async load(matchId: string): Promise<MatchState> {
    const state = await this.loadOrNull(matchId);
    if (!state) throw notFound("match_not_found", "Match not found or already finished");
    return state;
  }

  /** Settles a finished match: pot, XP, stats, ratings, leaderboards and the permanent record. */
  private async finish(state: MatchState, winner: Seat, reason: EndReason): Promise<void> {
    state.phase = "over";
    state.winner = winner;
    state.endReason = reason;
    const loser = other(winner);
    const pot = state.entryFee * 2;

    const [winnerUser, loserUser] = await Promise.all([UserModel.findById(state.seats[winner].userId), UserModel.findById(state.seats[loser].userId)]);
    const ratingBefore: [number, number] = [state.seats[0].rating, state.seats[1].rating];
    const ratingAfter: [number, number] = [...ratingBefore];

    if (state.ranked && winnerUser && loserUser) {
      const rated = rateGame(winnerUser.rating, loserUser.rating);
      winnerUser.rating = rated.winner;
      loserUser.rating = rated.loser;
      ratingAfter[winner] = rated.winner.r;
      ratingAfter[loser] = rated.loser.r;
    }
    for (const [user, seat] of [
      [winnerUser, winner],
      [loserUser, loser],
    ] as const) {
      if (!user) continue;
      const s = state.seats[seat];
      user.stats.played += 1;
      user.stats.won += seat === winner ? 1 : 0;
      user.stats.pockets += s.pockets;
      user.stats.queenCovers += s.queenCovers;
      await user.save();
    }

    if (pot > 0) await this.economy.credit(state.seats[winner].userId, { coins: pot, gems: 0 }, "match_payout", state.id);
    const [winnerXp, loserXp] = await Promise.all([
      this.economy.addXp(state.seats[winner].userId, WIN_XP),
      this.economy.addXp(state.seats[loser].userId, LOSS_XP),
    ]);

    if (winnerUser) {
      await this.leaderboards.recordWin(winnerUser.id as string);
      if (state.ranked) await this.leaderboards.recordRating(winnerUser);
    }
    if (loserUser && state.ranked) await this.leaderboards.recordRating(loserUser);

    const endedAt = this.clock.now();
    await MatchModel.create({
      matchId: state.id,
      mode: state.mode,
      arena: state.arena,
      ranked: state.ranked,
      entryFee: state.entryFee,
      players: state.seats.map((s, seat) => ({
        userId: s.userId,
        playerId: s.playerId,
        name: s.name,
        seat,
        score: s.score,
        fouls: s.fouls,
        pockets: s.pockets,
        ratingBefore: ratingBefore[seat],
        ratingAfter: ratingAfter[seat],
        coinsWon: seat === winner ? pot : 0,
      })),
      winnerSeat: winner,
      endReason: reason,
      shots: state.turn,
      startedAt: new Date(state.startedAt),
      endedAt: new Date(endedAt),
    });

    await this.save(state, FINISHED_TTL_SECONDS);
    await Promise.all(state.seats.map((s) => this.redis.del(activeKey(s.userId))));

    const rewards = ([0, 1] as const).map((seat) => ({
      coins: seat === winner ? pot : 0,
      xp: seat === winner ? WIN_XP : LOSS_XP,
      ratingBefore: Math.round(ratingBefore[seat]),
      ratingAfter: Math.round(ratingAfter[seat]),
      leveledUp: seat === winner ? winnerXp.leveledUp : loserXp.leveledUp,
    }));
    this.broadcaster.toMatch(state.id, "match:end", { snapshot: this.snapshot(state), winner, reason, rewards });
    for (const seat of state.seats) this.broadcaster.leaveMatch(seat.userId, state.id);
    this.logger.info({ matchId: state.id, winner, reason }, "match finished");
  }

  snapshot(state: MatchState): MatchSnapshot {
    return {
      id: state.id,
      mode: state.mode,
      arena: state.arena,
      ranked: state.ranked,
      entryFee: state.entryFee,
      seats: state.seats.map(({ userId: _u, timeouts: _t, pockets: _p, queenCovers: _q, ...visible }) => visible),
      pieces: state.pieces.map((p) => ({ id: p.id, type: p.type, x: round(p.x), y: round(p.y), pocketed: p.pocketed })),
      queen: state.queen,
      current: state.current,
      turn: state.turn,
      deadline: state.deadline,
      serverTime: this.clock.now(),
      turnSeconds: this.turnSeconds,
      phase: state.phase,
      winner: state.winner,
      endReason: state.endReason,
    };
  }

  private seatOf(state: MatchState, userId: string): Seat {
    const seat = state.seats.findIndex((s) => s.userId === userId);
    if (seat < 0) throw forbidden("not_in_match", "You are not playing in this match");
    return seat as Seat;
  }

  private nextDeadline(animationSeconds = 0): number {
    // Give clients time to watch the shot play out before the next turn's clock starts.
    // Whole milliseconds: clients read timestamps as integers.
    return this.clock.now() + Math.round((animationSeconds + this.turnSeconds) * 1000);
  }

  private async loadOrNull(matchId: string): Promise<MatchState | null> {
    const raw = await this.redis.get(stateKey(matchId));
    return raw ? (JSON.parse(raw) as MatchState) : null;
  }

  private async save(state: MatchState, ttlSeconds = STATE_TTL_SECONDS): Promise<void> {
    await this.redis.set(stateKey(state.id), JSON.stringify(state), "EX", ttlSeconds);
  }
}

const other = (seat: Seat): Seat => (seat === 0 ? 1 : 0);
const round = (value: number) => Math.round(value * 100) / 100;

function seatFor(user: UserDocument): SeatState {
  return {
    userId: user.id as string,
    playerId: user.playerId,
    name: user.displayName,
    avatarUrl: user.avatarUrl,
    level: user.progress.level,
    rating: Math.round(user.rating.r),
    strikerId: user.equipped.striker,
    score: 0,
    fouls: 0,
    pockets: 0,
    queenCovers: 0,
    timeouts: 0,
    connected: true,
  };
}
