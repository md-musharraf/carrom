import type { Redis } from "../../infra/redis.js";
import { AppError, badRequest, conflict, forbidden, notFound } from "../../lib/errors.js";
import { generateRoomCode } from "../../lib/ids.js";
import type { Logger } from "../../lib/logger.js";
import type { Notifier } from "../../lib/notifier.js";
import type { Clock } from "../../lib/time.js";
import { arena as findArena } from "../economy/catalog.js";
import type { FriendsService } from "../friends/friends.service.js";
import type { OnlineMode } from "../game/rules.js";
import { UserModel } from "../users/user.model.js";
import type { UsersService } from "../users/users.service.js";
import type { MatchService } from "./match.service.js";

const QUEUES = "mm:queues";
const queueKey = (mode: OnlineMode, arenaId: string) => `mm:${mode}:${arenaId}`;
const ticketKey = (userId: string) => `mm:ticket:${userId}`;

/** Rating window: starts tight and widens the longer a player waits. */
export function ratingWindow(waitSeconds: number): number {
  return Math.min(800, 100 + 50 * waitSeconds);
}

interface Waiting {
  userId: string;
  rating: number;
  waitSeconds: number;
}

/** Greedy pairing of rating-sorted players whose windows overlap. Exported for unit tests. */
export function pairPlayers(sortedByRating: Waiting[]): Array<[Waiting, Waiting]> {
  const pairs: Array<[Waiting, Waiting]> = [];
  for (let i = 0; i + 1 < sortedByRating.length; ) {
    const a = sortedByRating[i]!;
    const b = sortedByRating[i + 1]!;
    const window = Math.max(ratingWindow(a.waitSeconds), ratingWindow(b.waitSeconds));
    if (Math.abs(a.rating - b.rating) <= window) {
      pairs.push([a, b]);
      i += 2;
    } else {
      i += 1;
    }
  }
  return pairs;
}

/**
 * Ranked quick match. Queues are Redis sorted sets scored by rating, one per mode × arena. Every
 * node ticks, but a per-queue lock means only one node pairs a given queue at a time.
 */
export class Matchmaker {
  private timer: NodeJS.Timeout | null = null;

  constructor(
    private readonly redis: Redis,
    private readonly matches: MatchService,
    private readonly clock: Clock,
    private readonly logger: Logger,
    private readonly notifier: Notifier,
  ) {}

  async join(userId: string, mode: OnlineMode, arenaId: string) {
    const arena = findArena(arenaId);
    if (!arena) throw notFound("arena_not_found", "Unknown arena");
    if (await this.matches.activeMatchId(userId)) throw conflict("already_in_match", "Finish your current match first");
    const user = await UserModel.findById(userId);
    if (!user) throw notFound("user_not_found", "Account not found");
    if (user.progress.level < arena.minLevel) throw forbidden("arena_locked", `${arena.name} unlocks at level ${arena.minLevel}`);
    if (user.wallet.coins < arena.entryFee) throw conflict("insufficient_funds", `You need ${arena.entryFee} coins to enter ${arena.name}`);

    await this.leave(userId);
    const queue = queueKey(mode, arena.id);
    await this.redis
      .multi()
      .zadd(queue, Math.round(user.rating.r), userId)
      .hset(ticketKey(userId), { queue, joinedAt: String(this.clock.now()), mode, arena: arena.id })
      .expire(ticketKey(userId), 900)
      .sadd(QUEUES, queue)
      .exec();
    return { mode, arena: arena.id, entryFee: arena.entryFee, rating: Math.round(user.rating.r) };
  }

  async leave(userId: string): Promise<boolean> {
    const queue = await this.redis.hget(ticketKey(userId), "queue");
    if (!queue) return false;
    await this.redis.multi().zrem(queue, userId).del(ticketKey(userId)).exec();
    return true;
  }

  async tick(): Promise<number> {
    let started = 0;
    for (const queue of await this.redis.smembers(QUEUES)) {
      const lock = await this.redis.set(`mm:lock:${queue}`, "1", "PX", 5_000, "NX");
      if (lock !== "OK") continue;
      try {
        started += await this.pairQueue(queue);
      } finally {
        await this.redis.del(`mm:lock:${queue}`);
      }
    }
    return started;
  }

  start(intervalMs: number): void {
    this.stop();
    this.timer = setInterval(() => {
      void this.tick().catch((error: unknown) => this.logger.error({ err: error }, "matchmaking tick failed"));
    }, intervalMs);
    this.timer.unref();
  }

  stop(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  private async pairQueue(queue: string): Promise<number> {
    const raw = await this.redis.zrange(queue, 0, "-1", "WITHSCORES");
    if (raw.length < 4) return 0;
    const now = this.clock.now();
    const waiting: Waiting[] = [];
    for (let i = 0; i < raw.length; i += 2) {
      const userId = raw[i]!;
      const joinedAt = Number(await this.redis.hget(ticketKey(userId), "joinedAt"));
      if (!joinedAt) {
        await this.redis.zrem(queue, userId); // Ticket expired.
        continue;
      }
      waiting.push({ userId, rating: Number(raw[i + 1]), waitSeconds: (now - joinedAt) / 1000 });
    }

    const [, mode, arenaId] = queue.split(":") as [string, OnlineMode, string];
    const entryFee = findArena(arenaId)?.entryFee ?? 0;
    let started = 0;
    for (const [a, b] of pairPlayers(waiting)) {
      // Both must still be queued; ZREM is our atomic claim.
      if ((await this.redis.zrem(queue, a.userId, b.userId)) !== 2) continue;
      await this.redis.del(ticketKey(a.userId), ticketKey(b.userId));
      try {
        await this.matches.start([a.userId, b.userId], { mode, arena: arenaId, ranked: true, entryFee });
        started++;
      } catch (error) {
        this.logger.warn({ err: error, queue }, "could not start matched game");
        const reason = error instanceof AppError ? error.message : "Matchmaking failed";
        for (const id of [a.userId, b.userId]) this.notifier.toUser(id, "queue:cancelled", { reason });
      }
    }
    return started;
  }
}

const roomKey = (code: string) => `room:${code}`;
const hostRoomKey = (userId: string) => `user:${userId}:room`;
const ROOM_TTL_SECONDS = 10 * 60;

/** Private, unranked games between friends via a short invite code. */
export class RoomService {
  constructor(
    private readonly redis: Redis,
    private readonly matches: MatchService,
    private readonly friends: FriendsService,
    private readonly users: UsersService,
    private readonly notifier: Notifier,
  ) {}

  async create(userId: string, mode: OnlineMode) {
    if (await this.matches.activeMatchId(userId)) throw conflict("already_in_match", "Finish your current match first");
    await this.cancel(userId);
    for (let attempt = 0; attempt < 5; attempt++) {
      const code = generateRoomCode();
      const created = await this.redis.set(roomKey(code), JSON.stringify({ hostId: userId, mode }), "EX", ROOM_TTL_SECONDS, "NX");
      if (created === "OK") {
        await this.redis.set(hostRoomKey(userId), code, "EX", ROOM_TTL_SECONDS);
        return { code, mode, expiresInSeconds: ROOM_TTL_SECONDS };
      }
    }
    throw conflict("busy", "Please try again");
  }

  async join(userId: string, rawCode: string) {
    const code = rawCode.trim().toUpperCase();
    const raw = await this.redis.getdel(roomKey(code));
    if (!raw) throw notFound("room_not_found", "That room code has expired or doesn't exist");
    const room = JSON.parse(raw) as { hostId: string; mode: OnlineMode };
    if (room.hostId === userId) {
      await this.redis.set(roomKey(code), raw, "EX", ROOM_TTL_SECONDS);
      throw badRequest("own_room", "Share this code with a friend to start");
    }
    await this.redis.del(hostRoomKey(room.hostId));
    return this.matches.start([room.hostId, userId], { mode: room.mode, arena: null, ranked: false, entryFee: 0 });
  }

  async cancel(userId: string): Promise<void> {
    const code = await this.redis.getdel(hostRoomKey(userId));
    if (code) await this.redis.del(roomKey(code));
  }

  /** Sends the caller's open room code to a friend. */
  async invite(userId: string, friendPlayerId: string) {
    const code = await this.redis.get(hostRoomKey(userId));
    if (!code) throw badRequest("no_room", "Create a room first");
    const friend = await this.users.findByPlayerId(friendPlayerId);
    if (!friend) throw notFound("player_not_found", "No player has that ID");
    if (!(await this.friends.areFriends(userId, friend.id as string))) throw forbidden("not_friends", "You can only invite friends");
    const raw = await this.redis.get(roomKey(code));
    const mode = raw ? (JSON.parse(raw) as { mode: OnlineMode }).mode : "classic";
    const me = await this.users.get(userId);
    this.notifier.toUser(friend.id as string, "invite:received", { code, mode, from: this.users.toPublic(me) });
    return { sent: true };
  }
}
