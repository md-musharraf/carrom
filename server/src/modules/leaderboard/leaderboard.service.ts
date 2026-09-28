import type { Redis } from "../../infra/redis.js";
import { isoWeek, type Clock } from "../../lib/time.js";
import type { UserDocument } from "../users/user.model.js";
import type { UsersService } from "../users/users.service.js";

export type Board = "rating" | "weekly" | "country" | "friends";

export interface LeaderboardEntry {
  rank: number;
  playerId: string;
  displayName: string;
  avatarUrl?: string;
  country?: string;
  level: number;
  score: number;
  isMe: boolean;
}

export interface Leaderboard {
  board: Board;
  period?: string;
  entries: LeaderboardEntry[];
  me: LeaderboardEntry | null;
}

const WEEKLY_TTL_SECONDS = 21 * 86_400;

/** Global, weekly and per-country boards in Redis sorted sets; the friends board is computed. */
export class LeaderboardService {
  constructor(
    private readonly redis: Redis,
    private readonly users: UsersService,
    private readonly clock: Clock,
  ) {}

  async recordRating(user: UserDocument): Promise<void> {
    const rating = Math.round(user.rating.r);
    const pipeline = this.redis.pipeline().zadd("lb:rating", rating, user.id as string);
    if (user.country) pipeline.zadd(`lb:rating:${user.country}`, rating, user.id as string);
    await pipeline.exec();
  }

  async recordWin(userId: string): Promise<void> {
    const key = this.weeklyKey();
    await this.redis.multi().zincrby(key, 1, userId).expire(key, WEEKLY_TTL_SECONDS).exec();
  }

  async remove(userId: string, country?: string): Promise<void> {
    const pipeline = this.redis.pipeline().zrem("lb:rating", userId).zrem(this.weeklyKey(), userId);
    if (country) pipeline.zrem(`lb:rating:${country}`, userId);
    await pipeline.exec();
  }

  async get(board: Board, me: UserDocument, options: { limit: number; friendIds?: string[] }): Promise<Leaderboard> {
    if (board === "friends") return this.friends(me, options.friendIds ?? [], options.limit);
    const key = board === "rating" ? "lb:rating" : board === "weekly" ? this.weeklyKey() : `lb:rating:${me.country ?? "ZZ"}`;

    const raw = await this.redis.zrevrange(key, 0, options.limit - 1, "WITHSCORES");
    const ranked: Array<{ id: string; score: number }> = [];
    for (let i = 0; i < raw.length; i += 2) ranked.push({ id: raw[i]!, score: Number(raw[i + 1]) });
    const users = await this.users.findManyByIds(ranked.map((r) => r.id));
    const myId = me.id as string;
    const entries = ranked.flatMap((r, i) => {
      const user = users.get(r.id);
      return user ? [this.entry(user, i + 1, r.score, myId)] : [];
    });

    const [myRank, myScore] = await Promise.all([this.redis.zrevrank(key, myId), this.redis.zscore(key, myId)]);
    const mine = myRank !== null && myScore !== null ? this.entry(me, myRank + 1, Number(myScore), myId) : null;
    return { board, period: board === "weekly" ? isoWeek(this.clock.now()) : undefined, entries, me: mine };
  }

  private async friends(me: UserDocument, friendIds: string[], limit: number): Promise<Leaderboard> {
    const users = [...(await this.users.findManyByIds(friendIds)).values(), me];
    const myId = me.id as string;
    const entries = users
      .sort((x, y) => y.rating.r - x.rating.r)
      .slice(0, limit)
      .map((user, i) => this.entry(user, i + 1, Math.round(user.rating.r), myId));
    return { board: "friends", entries, me: entries.find((e) => e.isMe) ?? null };
  }

  private entry(user: UserDocument, rank: number, score: number, myId: string): LeaderboardEntry {
    return {
      rank,
      playerId: user.playerId,
      displayName: user.displayName,
      avatarUrl: user.avatarUrl,
      country: user.country,
      level: user.progress.level,
      score,
      isMe: (user.id as string) === myId,
    };
  }

  private weeklyKey() {
    return `lb:wins:${isoWeek(this.clock.now())}`;
  }
}
