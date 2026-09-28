import type { Redis } from "../../infra/redis.js";
import type { Clock } from "../../lib/time.js";

const KEY = "match:deadlines";

/**
 * Turn deadlines in one Redis sorted set. Every node sweeps it; `ZREM` decides which node claims
 * an expired deadline, so each timeout is handled exactly once even across a cluster.
 */
export class DeadlineScheduler {
  private timer: NodeJS.Timeout | null = null;

  constructor(
    private readonly redis: Redis,
    private readonly clock: Clock,
  ) {}

  async schedule(matchId: string, turn: number, at: number): Promise<void> {
    await this.redis.zadd(KEY, at, `${matchId}:${turn}`);
  }

  async cancel(matchId: string, turn: number): Promise<void> {
    await this.redis.zrem(KEY, `${matchId}:${turn}`);
  }

  /** Claims and returns expired deadlines. */
  async claimDue(limit = 50): Promise<Array<{ matchId: string; turn: number }>> {
    const due = await this.redis.zrangebyscore(KEY, "-inf", this.clock.now(), "LIMIT", 0, limit);
    const claimed: Array<{ matchId: string; turn: number }> = [];
    for (const member of due) {
      if ((await this.redis.zrem(KEY, member)) === 1) {
        const split = member.lastIndexOf(":");
        claimed.push({ matchId: member.slice(0, split), turn: Number(member.slice(split + 1)) });
      }
    }
    return claimed;
  }

  start(intervalMs: number, onDue: (matchId: string, turn: number) => Promise<void>, onError: (error: unknown) => void): void {
    this.stop();
    this.timer = setInterval(() => {
      void this.claimDue()
        .then((items) => Promise.all(items.map((item) => onDue(item.matchId, item.turn).catch(onError))))
        .catch(onError);
    }, intervalMs);
    this.timer.unref();
  }

  stop(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }
}
