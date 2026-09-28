import { RateLimiterRedis, RateLimiterRes } from "rate-limiter-flexible";
import type { Redis } from "../infra/redis.js";
import { tooManyRequests } from "./errors.js";

export interface Limiter {
  consume(key: string): Promise<void>;
}

/** Redis-backed fixed-window limiter shared by every server node. */
export function redisLimiter(redis: Redis, name: string, points: number, durationSeconds: number): Limiter {
  const limiter = new RateLimiterRedis({ storeClient: redis, keyPrefix: `rl:${name}`, points, duration: durationSeconds });
  return {
    async consume(key: string) {
      try {
        await limiter.consume(key);
      } catch (error) {
        if (error instanceof RateLimiterRes) throw tooManyRequests(Math.ceil(error.msBeforeNext / 1000));
        throw error;
      }
    },
  };
}

export interface RateLimits {
  otpSendPerPhone: Limiter;
  otpSendPerIp: Limiter;
  otpVerifyPerPhone: Limiter;
  accountCreatePerIp: Limiter;
  socialPerUser: Limiter;
}

export function createRateLimits(redis: Redis): RateLimits {
  return {
    otpSendPerPhone: redisLimiter(redis, "otp-send-phone", 3, 15 * 60),
    otpSendPerIp: redisLimiter(redis, "otp-send-ip", 10, 60 * 60),
    otpVerifyPerPhone: redisLimiter(redis, "otp-verify-phone", 10, 15 * 60),
    accountCreatePerIp: redisLimiter(redis, "account-ip", 20, 60 * 60),
    socialPerUser: redisLimiter(redis, "social-user", 60, 60),
  };
}
