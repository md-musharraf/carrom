import { randomUUID } from "node:crypto";
import { io as connect, type Socket } from "socket.io-client";
import supertest from "supertest";
import { inject } from "vitest";
import { loadConfig } from "../src/config/env.js";
import type { Overrides } from "../src/container.js";
import { unauthorized } from "../src/lib/errors.js";
import { MemorySmsSender } from "../src/modules/auth/providers/phone.js";
import type { IdentityVerifier } from "../src/modules/auth/providers/social.js";
import { startServer, type RunningServer } from "../src/server.js";

/** Test double for Google/Facebook: a token "<provider>-test-token:<id>:<name>" verifies as that account. */
export function fakeVerifier(provider: string): IdentityVerifier {
  return {
    async verify(token: string) {
      const [prefix, id, name] = token.split(":");
      if (prefix !== `${provider}-test-token` || !id) throw unauthorized(`invalid_${provider}_token`, "Invalid token");
      return { id, name, email: `${id}@example.com`, avatarUrl: `https://img.example.com/${id}.png` };
    },
  };
}

export const socialToken = (provider: "google" | "facebook", id: string, name = "Test Player") => `${provider}-test-token:${id}:${name}`;

export interface TestServer {
  server: RunningServer;
  api: ReturnType<typeof supertest>;
  sms: MemorySmsSender;
  close(): Promise<void>;
}

/** Boots the whole server (REST + realtime) against the shared test MongoDB/Redis. */
export async function bootServer(env: Record<string, string> = {}, overrides: Overrides = {}): Promise<TestServer> {
  const sms = new MemorySmsSender();
  const config = loadConfig({
    NODE_ENV: "test",
    PORT: "0",
    MONGO_URL: `${inject("mongoUrl")}/carrom_test_${randomUUID().slice(0, 8)}`,
    REDIS_URL: inject("redisUrl"),
    JWT_SECRET: "test-secret-that-is-long-enough-for-hs256-signing",
    MATCHMAKING_TICK_MS: "100",
    ...env,
  });
  const server = await startServer(config, { google: fakeVerifier("google"), facebook: fakeVerifier("facebook"), sms, ...overrides });
  await server.container.redis.flushdb();
  return {
    server,
    api: supertest(server.url),
    sms,
    async close() {
      const { default: mongoose } = await import("mongoose");
      await mongoose.connection.db?.dropDatabase();
      await server.close();
    },
  };
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  user: { id: string; playerId: string; displayName: string; wallet: { coins: number; gems: number } } & Record<string, unknown>;
}

export async function guest(api: TestServer["api"]): Promise<Session> {
  const res = await api.post("/v1/auth/guest").send({});
  if (res.status !== 201) throw new Error(`guest sign-in failed: ${res.status} ${JSON.stringify(res.body)}`);
  return res.body as Session;
}

export const bearer = (session: Session) => ({ Authorization: `Bearer ${session.accessToken}` });

/** A connected Socket.IO client authenticated as [session]. */
export async function socketFor(url: string, session: Session): Promise<Socket> {
  const socket = connect(url, { auth: { token: session.accessToken }, transports: ["websocket"], forceNew: true });
  await new Promise<void>((resolve, reject) => {
    socket.once("connect", () => resolve());
    socket.once("connect_error", reject);
  });
  return socket;
}

/** Emits with an acknowledgement and unwraps `{ ok, data | error }`. */
export async function call<T = unknown>(socket: Socket, event: string, payload?: unknown): Promise<T> {
  const response = (await socket.timeout(10_000).emitWithAck(event, payload)) as { ok: boolean; data?: T; error?: { code: string } };
  if (!response.ok) throw Object.assign(new Error(response.error?.code ?? "error"), { code: response.error?.code });
  return response.data as T;
}

export function nextEvent<T = unknown>(socket: Socket, event: string, timeoutMs = 10_000): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`timed out waiting for ${event}`)), timeoutMs);
    socket.once(event, (payload: T) => {
      clearTimeout(timer);
      resolve(payload);
    });
  });
}
