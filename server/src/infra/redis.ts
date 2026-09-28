import { randomUUID } from "node:crypto";
import { Redis } from "ioredis";
import type { Logger } from "../lib/logger.js";

export type { Redis };

export function createRedis(url: string, logger: Logger, name: string): Redis {
  const client = new Redis(url, { maxRetriesPerRequest: 3, enableReadyCheck: true, connectionName: name });
  client.on("error", (error: Error) => logger.error({ err: error, client: name }, "Redis error"));
  return client;
}

const RELEASE_LOCK = `
if redis.call("get", KEYS[1]) == ARGV[1] then
  return redis.call("del", KEYS[1])
end
return 0`;

/**
 * Runs [fn] while holding a short Redis mutex on [key], so concurrent events for the same match
 * (possibly on different server nodes) are processed one at a time.
 */
export async function withLock<T>(redis: Redis, key: string, fn: () => Promise<T>, ttlMs = 5_000): Promise<T> {
  const token = randomUUID();
  const deadline = Date.now() + ttlMs;
  while ((await redis.set(key, token, "PX", ttlMs, "NX")) !== "OK") {
    if (Date.now() > deadline) throw new Error(`Timed out acquiring lock ${key}`);
    await new Promise((resolve) => setTimeout(resolve, 15));
  }
  try {
    return await fn();
  } finally {
    await redis.eval(RELEASE_LOCK, 1, key, token);
  }
}
