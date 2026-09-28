import type { Server as HttpServer } from "node:http";
import { createAdapter } from "@socket.io/redis-streams-adapter";
import { Server, type Socket } from "socket.io";
import { z } from "zod";
import type { AppConfig } from "../../config/env.js";
import type { Redis } from "../../infra/redis.js";
import { AppError, tooManyRequests } from "../../lib/errors.js";
import type { Logger } from "../../lib/logger.js";
import type { DeferredNotifier } from "../../lib/notifier.js";
import { parse } from "../../lib/validate.js";
import type { AccessClaims, AccessTokens } from "../auth/tokens.js";
import type { FriendsService } from "../friends/friends.service.js";
import type { UsersService } from "../users/users.service.js";
import type { DeadlineScheduler } from "./deadlines.js";
import type { MatchBroadcaster, MatchService } from "./match.service.js";
import type { Matchmaker, RoomService } from "./matchmaking.js";
import type { PresenceService } from "./presence.js";

export const EMOTES = ["nice_shot", "well_played", "oops", "thanks", "good_luck", "wow", "hurry_up", "gg"] as const;

const mode = z.enum(["classic", "freestyle"]);
const matchId = z.uuid();
const shotFields = {
  offset: z.number().min(0).max(1),
  angle: z.number().min(-10).max(10),
  power: z.number().min(0).max(100),
};
const schemas = {
  queueJoin: z.object({ mode, arena: z.string().max(32) }),
  roomCreate: z.object({ mode }),
  roomJoin: z.object({ code: z.string().min(4).max(10) }),
  roomInvite: z.object({ playerId: z.string().min(8).max(12) }),
  // Clients may emit without a payload, which arrives as null.
  resume: z.object({ matchId: matchId.optional() }).nullish(),
  aim: z.object({ matchId, ...shotFields }),
  shoot: z.object({ matchId, turn: z.number().int().min(0), ...shotFields }),
  emote: z.object({ matchId, emote: z.enum(EMOTES) }),
  resign: z.object({ matchId }),
};

type Ack = (response: { ok: true; data?: unknown } | { ok: false; error: { code: string; message: string } }) => void;

/** Late-bound broadcaster so MatchService can be built before the Socket.IO server exists. */
export class DeferredBroadcaster implements MatchBroadcaster {
  private io: Server | null = null;

  bind(io: Server): void {
    this.io = io;
  }

  toUser(userId: string, event: string, payload: unknown): void {
    this.io?.to(`user:${userId}`).emit(event, payload);
  }

  toMatch(matchId: string, event: string, payload: unknown): void {
    this.io?.to(`match:${matchId}`).emit(event, payload);
  }

  toMatchExcept(matchId: string, exceptUserId: string, event: string, payload: unknown): void {
    this.io?.to(`match:${matchId}`).except(`user:${exceptUserId}`).emit(event, payload);
  }

  joinMatch(userId: string, matchId: string): void {
    this.io?.in(`user:${userId}`).socketsJoin(`match:${matchId}`);
  }

  leaveMatch(userId: string, matchId: string): void {
    this.io?.in(`user:${userId}`).socketsLeave(`match:${matchId}`);
  }
}

export interface RealtimeDeps {
  config: AppConfig;
  logger: Logger;
  accessTokens: AccessTokens;
  users: UsersService;
  friends: FriendsService;
  presence: PresenceService;
  matches: MatchService;
  matchmaker: Matchmaker;
  rooms: RoomService;
  deadlines: DeadlineScheduler;
  notifier: DeferredNotifier;
  broadcaster: DeferredBroadcaster;
  /** Dedicated connection for the Redis Streams adapter (it issues blocking reads). */
  adapterRedis?: Redis;
}

export interface Realtime {
  io: Server;
  close(): Promise<void>;
}

const HEARTBEAT_MS = 25_000;
const AIM_INTERVAL_MS = 50;
const EMOTE_INTERVAL_MS = 1_500;

export function attachRealtime(httpServer: HttpServer, deps: RealtimeDeps): Realtime {
  const { config, logger, accessTokens: tokens, presence, matches, matchmaker, rooms, friends, users } = deps;
  const io = new Server(httpServer, {
    cors: { origin: config.corsOrigins.length > 0 ? config.corsOrigins : "*" },
    adapter: deps.adapterRedis ? createAdapter(deps.adapterRedis) : undefined,
    // Brief drops (tunnels, network hand-over) resume with rooms and missed events intact.
    connectionStateRecovery: { maxDisconnectionDuration: 2 * 60_000, skipMiddlewares: true },
  });
  deps.broadcaster.bind(io);
  deps.notifier.bind({ toUser: (userId, event, payload) => io.to(`user:${userId}`).emit(event, payload) });

  io.use(async (socket, next) => {
    try {
      const token = (socket.handshake.auth as { token?: unknown } | undefined)?.token;
      if (typeof token !== "string") throw new AppError(401, "unauthorized", "Authentication required");
      socket.data.auth = await tokens.verify(token);
      next();
    } catch {
      next(new Error("unauthorized"));
    }
  });

  const notifyFriends = async (userId: string, online: boolean) => {
    const me = await users.get(userId).catch(() => null);
    if (!me) return;
    for (const friendId of await friends.friendIds(userId)) {
      io.to(`user:${friendId}`).emit("friends:presence", { playerId: me.playerId, online });
    }
  };

  io.on("connection", (socket: Socket) => {
    const auth = socket.data.auth as AccessClaims;
    const { userId } = auth;
    void socket.join(`user:${userId}`);
    let lastAim = 0;
    let lastEmote = 0;

    void (async () => {
      if (await presence.connected(userId, socket.id)) await notifyFriends(userId, true);
      const active = await matches.activeMatchId(userId);
      if (active) {
        await socket.join(`match:${active}`);
        await matches.setConnected(userId, true);
      }
    })().catch((error: unknown) => logger.error({ err: error }, "connect bookkeeping failed"));

    const on = <S extends z.ZodType>(event: string, schema: S, handler: (data: z.infer<S>) => Promise<unknown>) => {
      socket.on(event, async (payload: unknown, ack?: Ack) => {
        try {
          const data = await handler(parse(schema, payload));
          ack?.({ ok: true, data });
        } catch (error) {
          if (!(error instanceof AppError)) logger.error({ err: error, event }, "socket handler failed");
          const body = error instanceof AppError ? { code: error.code, message: error.message } : { code: "internal", message: "Something went wrong" };
          ack?.({ ok: false, error: body });
        }
      });
    };

    on("queue:join", schemas.queueJoin, (d) => matchmaker.join(userId, d.mode, d.arena));
    on("queue:leave", z.unknown(), () => matchmaker.leave(userId));
    on("room:create", schemas.roomCreate, (d) => rooms.create(userId, d.mode));
    on("room:join", schemas.roomJoin, (d) => rooms.join(userId, d.code));
    on("room:cancel", z.unknown(), () => rooms.cancel(userId));
    on("room:invite", schemas.roomInvite, (d) => rooms.invite(userId, d.playerId));
    on("match:resume", schemas.resume, async (d) => {
      const id = d?.matchId ?? (await matches.activeMatchId(userId));
      if (!id) return null;
      await socket.join(`match:${id}`);
      return matches.snapshotFor(userId, id);
    });
    on("match:aim", schemas.aim, async (d) => {
      const now = Date.now();
      if (now - lastAim < AIM_INTERVAL_MS) return; // Aim is cosmetic; drop floods silently.
      lastAim = now;
      await matches.aim(userId, d.matchId, d);
    });
    on("match:shoot", schemas.shoot, (d) => matches.shoot(userId, d.matchId, { offset: d.offset, angle: d.angle, power: d.power }, d.turn));
    on("match:emote", schemas.emote, async (d) => {
      const now = Date.now();
      if (now - lastEmote < EMOTE_INTERVAL_MS) throw tooManyRequests(Math.ceil((EMOTE_INTERVAL_MS - (now - lastEmote)) / 1000));
      lastEmote = now;
      await matches.emote(userId, d.matchId, d.emote);
    });
    on("match:resign", schemas.resign, (d) => matches.resign(userId, d.matchId));

    socket.on("disconnect", () => {
      void (async () => {
        if (!(await presence.disconnected(userId, socket.id))) return;
        await matchmaker.leave(userId);
        await matches.setConnected(userId, false);
        await notifyFriends(userId, false);
      })().catch((error: unknown) => logger.error({ err: error }, "disconnect bookkeeping failed"));
    });
  });

  const heartbeat = setInterval(() => {
    void io.local
      .fetchSockets()
      .then((sockets) => presence.heartbeat(sockets.map((s) => ({ userId: (s.data.auth as AccessClaims).userId, socketId: s.id }))))
      .catch((error: unknown) => logger.error({ err: error }, "presence heartbeat failed"));
  }, HEARTBEAT_MS);
  heartbeat.unref();

  matchmaker.start(config.game.matchmakingTickMs);
  deps.deadlines.start(
    250,
    (id, turn) => matches.handleTimeout(id, turn),
    (error) => logger.error({ err: error }, "turn timeout failed"),
  );

  return {
    io,
    async close() {
      clearInterval(heartbeat);
      matchmaker.stop();
      deps.deadlines.stop();
      await new Promise<void>((resolve) => {
        io.close(() => resolve());
      });
    },
  };
}
