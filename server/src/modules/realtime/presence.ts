import type { Redis } from "../../infra/redis.js";
import type { Clock } from "../../lib/time.js";

/** A socket counts as live if its node refreshed it within this window. */
const STALE_MS = 70_000;

/**
 * Who is online, stored per user as a sorted set of socket ids scored by last heartbeat. Unlike a
 * counter, entries left behind by a crashed node simply age out.
 */
export class PresenceService {
  constructor(
    private readonly redis: Redis,
    private readonly clock: Clock,
  ) {}

  async connected(userId: string, socketId: string): Promise<boolean> {
    const wasOnline = await this.isOnline(userId);
    await this.redis.multi().zadd(this.key(userId), this.clock.now(), socketId).expire(this.key(userId), 86_400).exec();
    return !wasOnline;
  }

  /** Returns true when this was the user's last live connection. */
  async disconnected(userId: string, socketId: string): Promise<boolean> {
    await this.redis.zrem(this.key(userId), socketId);
    return !(await this.isOnline(userId));
  }

  async heartbeat(sockets: ReadonlyArray<{ userId: string; socketId: string }>): Promise<void> {
    if (sockets.length === 0) return;
    const pipeline = this.redis.pipeline();
    const now = this.clock.now();
    for (const { userId, socketId } of sockets) pipeline.zadd(this.key(userId), now, socketId);
    await pipeline.exec();
  }

  async isOnline(userId: string): Promise<boolean> {
    return (await this.redis.zcount(this.key(userId), this.clock.now() - STALE_MS, "+inf")) > 0;
  }

  async onlineMany(userIds: string[]): Promise<Set<string>> {
    if (userIds.length === 0) return new Set();
    const pipeline = this.redis.pipeline();
    const since = this.clock.now() - STALE_MS;
    for (const id of userIds) pipeline.zcount(this.key(id), since, "+inf");
    const results = (await pipeline.exec()) ?? [];
    return new Set(userIds.filter((_, i) => Number(results[i]?.[1] ?? 0) > 0));
  }

  private key(userId: string) {
    return `presence:${userId}`;
  }
}
