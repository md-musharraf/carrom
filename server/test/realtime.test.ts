import type { Socket } from "socket.io-client";
import { io as connect } from "socket.io-client";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { MatchModel } from "../src/modules/matches/match.model.js";
import type { MatchSnapshot } from "../src/modules/realtime/match.service.js";
import { bearer, bootServer, call, guest, nextEvent, socketFor, type Session, type TestServer } from "./helpers.js";

let t: TestServer;
const sockets: Socket[] = [];

beforeAll(async () => {
  t = await bootServer();
});
afterEach(() => {
  for (const s of sockets.splice(0)) s.disconnect();
});
afterAll(async () => {
  await t.close();
});

interface Player {
  session: Session;
  socket: Socket;
}

async function player(): Promise<Player> {
  const session = await guest(t.api);
  const socket = await socketFor(t.server.url, session);
  sockets.push(socket);
  return { session, socket };
}

/** Queues two fresh players into the same arena and waits until both see the match start. */
async function quickMatch(): Promise<{ players: [Player, Player]; snapshot: MatchSnapshot; bySeat: (seat: number) => Player }> {
  const a = await player();
  const b = await player();
  const startedA = nextEvent<MatchSnapshot>(a.socket, "match:start");
  const startedB = nextEvent<MatchSnapshot>(b.socket, "match:start");
  await call(a.socket, "queue:join", { mode: "classic", arena: "bronze" });
  await call(b.socket, "queue:join", { mode: "classic", arena: "bronze" });
  const [snapshot, snapshotB] = await Promise.all([startedA, startedB]);
  expect(snapshotB.id).toBe(snapshot.id);
  const bySeat = (seat: number) => (snapshot.seats[seat]!.playerId === a.session.user.playerId ? a : b);
  return { players: [a, b], snapshot, bySeat };
}

describe("quick match", () => {
  it("pairs two queued players, escrows the entry fee and deals the break", async () => {
    const { players, snapshot } = await quickMatch();
    expect(snapshot).toMatchObject({ mode: "classic", arena: "bronze", ranked: true, entryFee: 100, turn: 0, current: 0, phase: "playing" });
    expect(snapshot.pieces).toHaveLength(19);
    expect(snapshot.seats.map((s) => s.playerId).sort()).toEqual(players.map((p) => p.session.user.playerId).sort());
    expect(snapshot.seats[0]).not.toHaveProperty("userId");
    for (const p of players) {
      const me = await t.api.get("/v1/me").set(bearer(p.session)).expect(200);
      expect(me.body.wallet.coins).toBe(1100);
    }
  });

  it("only lets the current player shoot, once per turn", async () => {
    const { snapshot, bySeat } = await quickMatch();
    const waiting = bySeat(1);
    await expect(call(waiting.socket, "match:shoot", { matchId: snapshot.id, turn: 0, offset: 0.5, angle: 1.57, power: 80 })).rejects.toMatchObject({
      code: "not_your_turn",
    });
    await expect(call(bySeat(0).socket, "match:shoot", { matchId: snapshot.id, turn: 5, offset: 0.5, angle: -1.57, power: 80 })).rejects.toMatchObject({
      code: "stale_turn",
    });
  });

  it("simulates shots on the server and sends both players the same authoritative result", async () => {
    const { snapshot, bySeat } = await quickMatch();
    const shooter = bySeat(0);
    const watcher = bySeat(1);
    type ShotEvent = { turn: number; seat: number; input: { angle: number }; snapshot: MatchSnapshot; result: { pocketed: string[]; seconds: number } };
    const seenByShooter = nextEvent<ShotEvent>(shooter.socket, "match:shot");
    const seenByWatcher = nextEvent<ShotEvent>(watcher.socket, "match:shot");

    // A break lasting 3.8333… s: the next deadline would be a fractional millisecond unless rounded.
    await call(shooter.socket, "match:shoot", { matchId: snapshot.id, turn: 0, offset: 0.5, angle: -Math.PI / 2 + 0.01, power: 100 });
    const [a, b] = await Promise.all([seenByShooter, seenByWatcher]);
    expect(a).toEqual(b);
    expect(a).toMatchObject({ turn: 0, seat: 0 });
    expect(a.snapshot.turn).toBe(1);
    expect(a.result.seconds).toBeGreaterThan(0.5);
    // Timestamps are whole milliseconds (clients parse them as integers), even after a fractional-second shot.
    expect(Number.isInteger(a.snapshot.deadline)).toBe(true);
    expect(Number.isInteger(a.snapshot.serverTime)).toBe(true);
    // The break scattered the cluster.
    const moved = a.snapshot.pieces.filter((p) => {
      const before = snapshot.pieces.find((q) => q.id === p.id)!;
      return Math.hypot(p.x - before.x, p.y - before.y) > 1;
    });
    expect(moved.length).toBeGreaterThan(10);
    // Replaying the same turn is refused.
    await expect(call(shooter.socket, "match:shoot", { matchId: snapshot.id, turn: 0, offset: 0.5, angle: -1.57, power: 100 })).rejects.toBeDefined();
  });

  it("relays the shooter's live aim and quick-chat emotes", async () => {
    const { snapshot, bySeat } = await quickMatch();
    const aimed = nextEvent<{ seat: number; angle: number }>(bySeat(1).socket, "match:aim");
    await call(bySeat(0).socket, "match:aim", { matchId: snapshot.id, offset: 0.3, angle: -1.2, power: 60 });
    expect(await aimed).toMatchObject({ seat: 0, offset: 0.3, angle: -1.2, power: 60 });

    const emoteSeen = nextEvent<{ seat: number; emote: string }>(bySeat(0).socket, "match:emote");
    await call(bySeat(1).socket, "match:emote", { matchId: snapshot.id, emote: "good_luck" });
    expect(await emoteSeen).toEqual({ seat: 1, emote: "good_luck" });
    await expect(call(bySeat(1).socket, "match:emote", { matchId: snapshot.id, emote: "wow" })).rejects.toMatchObject({ code: "rate_limited" });
    await expect(call(bySeat(1).socket, "match:emote", { matchId: snapshot.id, emote: "<script>" })).rejects.toMatchObject({ code: "invalid_request" });
  });

  it("settles a resignation: pot, ratings, history and weekly leaderboard", async () => {
    const { snapshot, bySeat } = await quickMatch();
    const quitter = bySeat(0);
    const winner = bySeat(1);
    type EndEvent = { winner: number; reason: string; rewards: Array<{ coins: number; ratingBefore: number; ratingAfter: number }> };
    const ended = nextEvent<EndEvent>(winner.socket, "match:end");
    await call(quitter.socket, "match:resign", { matchId: snapshot.id });
    const end = await ended;

    expect(end).toMatchObject({ winner: 1, reason: "resigned" });
    expect(end.rewards[1]!.coins).toBe(200);
    expect(end.rewards[1]!.ratingAfter).toBeGreaterThan(end.rewards[1]!.ratingBefore);
    expect(end.rewards[0]!.ratingAfter).toBeLessThan(end.rewards[0]!.ratingBefore);

    const winnerMe = await t.api.get("/v1/me").set(bearer(winner.session)).expect(200);
    expect(winnerMe.body.wallet.coins).toBe(1300);
    expect(winnerMe.body.stats).toMatchObject({ played: 1, won: 1 });
    expect(await MatchModel.countDocuments({ matchId: snapshot.id })).toBe(1);

    const history = await t.api.get("/v1/matches").set(bearer(quitter.session)).expect(200);
    expect(history.body.matches[0]).toMatchObject({ matchId: snapshot.id, result: "loss", endReason: "resigned", coinsWon: 0 });
    const weekly = await t.api.get("/v1/leaderboards/weekly").set(bearer(winner.session)).expect(200);
    expect(weekly.body.me).toMatchObject({ score: 1, isMe: true });

    // After the match both players are free to queue again.
    await call(quitter.socket, "queue:join", { mode: "classic", arena: "bronze" });
    await call(quitter.socket, "queue:leave");
  });

  it("refuses arenas the player hasn't unlocked or can't afford", async () => {
    const p = await player();
    await expect(call(p.socket, "queue:join", { mode: "classic", arena: "royal" })).rejects.toMatchObject({ code: "arena_locked" });
    await expect(call(p.socket, "queue:join", { mode: "classic", arena: "atlantis" })).rejects.toMatchObject({ code: "arena_not_found" });
  });
});

describe("private rooms", () => {
  it("starts an unranked, free game when a friend enters the code", async () => {
    const host = await player();
    const guestPlayer = await player();
    const room = await call<{ code: string }>(host.socket, "room:create", { mode: "freestyle" });
    expect(room.code).toMatch(/^[2-9A-HJ-NP-Z]{6}$/);
    await expect(call(host.socket, "room:join", { code: room.code })).rejects.toMatchObject({ code: "own_room" });

    const started = nextEvent<MatchSnapshot>(host.socket, "match:start");
    await call(guestPlayer.socket, "room:join", { code: room.code.toLowerCase() });
    const snapshot = await started;
    expect(snapshot).toMatchObject({ mode: "freestyle", ranked: false, entryFee: 0, arena: null });
    await expect(call(guestPlayer.socket, "room:join", { code: room.code })).rejects.toMatchObject({ code: "room_not_found" });

    // Reconnecting players get the live match back.
    const resumed = await call<MatchSnapshot>(guestPlayer.socket, "match:resume");
    expect(resumed.id).toBe(snapshot.id);
  });

  it("delivers invites to friends only", async () => {
    const host = await player();
    const friend = await player();
    const stranger = await player();
    await t.api.post("/v1/friends/requests").set(bearer(host.session)).send({ playerId: friend.session.user.playerId }).expect(201);
    await t.api.post("/v1/friends/requests").set(bearer(friend.session)).send({ playerId: host.session.user.playerId }).expect(201);

    const room = await call<{ code: string }>(host.socket, "room:create", { mode: "classic" });
    const invited = nextEvent<{ code: string; from: { playerId: string } }>(friend.socket, "invite:received");
    await call(host.socket, "room:invite", { playerId: friend.session.user.playerId });
    expect(await invited).toMatchObject({ code: room.code, from: { playerId: host.session.user.playerId } });
    await expect(call(host.socket, "room:invite", { playerId: stranger.session.user.playerId })).rejects.toMatchObject({ code: "not_friends" });
    await call(host.socket, "room:cancel");
  });
});

describe("socket authentication", () => {
  it("rejects connections without a valid access token", async () => {
    const socket = connect(t.server.url, { auth: { token: "nope" }, transports: ["websocket"], forceNew: true });
    const error = await new Promise<Error>((resolve) => socket.once("connect_error", resolve));
    expect(error.message).toBe("unauthorized");
    socket.disconnect();
  });
});
