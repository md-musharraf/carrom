import type { AppConfig } from "./config/env.js";
import type { Redis } from "./infra/redis.js";
import type { Logger } from "./lib/logger.js";
import { DeferredNotifier } from "./lib/notifier.js";
import { createRateLimits } from "./lib/rate-limit.js";
import { systemClock, type Clock } from "./lib/time.js";
import { AuthService } from "./modules/auth/auth.service.js";
import { ConsoleSmsSender, LocalOtpChannel, TwilioVerifyChannel, type OtpChannel, type SmsSender } from "./modules/auth/providers/phone.js";
import { FacebookGraphVerifier, GoogleIdTokenVerifier, type IdentityVerifier } from "./modules/auth/providers/social.js";
import { AccessTokens, RefreshSessions } from "./modules/auth/tokens.js";
import { EconomyService } from "./modules/economy/economy.service.js";
import { FriendsService } from "./modules/friends/friends.service.js";
import { LeaderboardService } from "./modules/leaderboard/leaderboard.service.js";
import { DeadlineScheduler } from "./modules/realtime/deadlines.js";
import { MatchService } from "./modules/realtime/match.service.js";
import { Matchmaker, RoomService } from "./modules/realtime/matchmaking.js";
import { PresenceService } from "./modules/realtime/presence.js";
import { DeferredBroadcaster } from "./modules/realtime/socket.js";
import { UsersService } from "./modules/users/users.service.js";

/** Test seams: swap external providers and the clock without touching production wiring. */
export interface Overrides {
  clock?: Clock;
  google?: IdentityVerifier | null;
  facebook?: IdentityVerifier | null;
  otp?: OtpChannel;
  sms?: SmsSender;
}

/** Composition root: builds every service once, with explicit dependencies. */
export function createContainer(config: AppConfig, logger: Logger, redis: Redis, overrides: Overrides = {}) {
  const clock = overrides.clock ?? systemClock;
  const notifier = new DeferredNotifier();
  const broadcaster = new DeferredBroadcaster();

  const google =
    overrides.google !== undefined ? overrides.google : config.google.clientIds.length > 0 ? new GoogleIdTokenVerifier(config.google.clientIds) : null;
  const facebook =
    overrides.facebook !== undefined
      ? overrides.facebook
      : config.facebook.appId && config.facebook.appSecret
        ? new FacebookGraphVerifier(config.facebook.appId, config.facebook.appSecret, config.facebook.graphVersion)
        : null;
  const otp =
    overrides.otp ??
    (config.otp.provider === "twilio" && config.otp.twilio
      ? new TwilioVerifyChannel(config.otp.twilio.accountSid, config.otp.twilio.authToken, config.otp.twilio.serviceSid)
      : new LocalOtpChannel(redis, overrides.sms ?? new ConsoleSmsSender(logger), config.otp.pepper));

  const users = new UsersService();
  const accessTokens = new AccessTokens(config.jwt, clock);
  const sessions = new RefreshSessions(config.jwt, clock);
  const limits = createRateLimits(redis);
  const auth = new AuthService(users, accessTokens, sessions, { google, facebook, otp }, limits);
  const presence = new PresenceService(redis, clock);
  const friends = new FriendsService(users, presence, notifier);
  const leaderboards = new LeaderboardService(redis, users, clock);
  const economy = new EconomyService(clock);
  const deadlines = new DeadlineScheduler(redis, clock);
  const matches = new MatchService(redis, broadcaster, deadlines, economy, leaderboards, clock, logger, config.game.turnSeconds);
  const matchmaker = new Matchmaker(redis, matches, clock, logger, notifier);
  const rooms = new RoomService(redis, matches, friends, users, notifier);

  return {
    config,
    logger,
    clock,
    redis,
    notifier,
    broadcaster,
    users,
    accessTokens,
    sessions,
    limits,
    auth,
    presence,
    friends,
    leaderboards,
    economy,
    deadlines,
    matches,
    matchmaker,
    rooms,
  };
}

export type Container = ReturnType<typeof createContainer>;
