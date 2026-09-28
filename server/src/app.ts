import cors from "cors";
import express, { Router, type ErrorRequestHandler, type Express } from "express";
import helmet from "helmet";
import mongoose from "mongoose";
import { pinoHttp } from "pino-http";
import type { Container } from "./container.js";
import { AppError } from "./lib/errors.js";
import type { Logger } from "./lib/logger.js";
import { requireAuth } from "./modules/auth/auth.middleware.js";
import { authRoutes } from "./modules/auth/auth.routes.js";
import { economyRoutes } from "./modules/economy/economy.routes.js";
import { friendsRoutes } from "./modules/friends/friends.routes.js";
import { leaderboardRoutes } from "./modules/leaderboard/leaderboard.routes.js";
import { matchesRoutes } from "./modules/matches/matches.routes.js";
import { usersRoutes } from "./modules/users/users.routes.js";

export function createApp(c: Container): Express {
  const app = express();
  app.set("trust proxy", 1);
  app.disable("x-powered-by");
  app.use(helmet());
  app.use(cors({ origin: c.config.corsOrigins.length > 0 ? c.config.corsOrigins : "*" }));
  app.use(express.json({ limit: "32kb" }));
  app.use(
    pinoHttp({
      logger: c.logger,
      autoLogging: { ignore: (req) => req.url === "/health" || req.url === "/ready" },
    }),
  );

  app.get("/health", (_req, res) => {
    res.json({ status: "ok" });
  });
  app.get("/ready", async (_req, res) => {
    const [mongo, redis] = await Promise.allSettled([mongoose.connection.db?.admin().ping(), c.redis.ping()]);
    const ok = mongo.status === "fulfilled" && mongoose.connection.readyState === 1 && redis.status === "fulfilled";
    res.status(ok ? 200 : 503).json({ status: ok ? "ready" : "degraded", mongo: mongo.status, redis: redis.status });
  });

  const secured = Router();
  secured.use(requireAuth(c.accessTokens));
  secured.use(usersRoutes(c));
  secured.use("/friends", friendsRoutes(c.friends, c.limits.socialPerUser));
  secured.use("/leaderboards", leaderboardRoutes(c));
  secured.use("/matches", matchesRoutes());
  secured.use(economyRoutes(c.economy, c.users));

  const v1 = Router();
  v1.use("/auth", authRoutes(c.auth, c.accessTokens));
  v1.use(secured);
  app.use("/v1", v1);

  app.use((_req, res) => {
    res.status(404).json({ error: { code: "not_found", message: "Route not found" } });
  });
  app.use(errorHandler(c.logger));
  return app;
}

/** Every error becomes `{ error: { code, message, details? } }` with the right status. */
export function errorHandler(logger: Logger): ErrorRequestHandler {
  return (error: unknown, _req, res, _next) => {
    if (error instanceof AppError) {
      const retryAfter = (error.details as { retryAfterSeconds?: number } | undefined)?.retryAfterSeconds;
      if (error.status === 429 && retryAfter) res.setHeader("Retry-After", String(retryAfter));
      res.status(error.status).json({ error: { code: error.code, message: error.message, details: error.details } });
      return;
    }
    const type = (error as { type?: string }).type;
    if (type === "entity.parse.failed") {
      res.status(400).json({ error: { code: "invalid_json", message: "Malformed JSON body" } });
      return;
    }
    if (type === "entity.too.large") {
      res.status(413).json({ error: { code: "payload_too_large", message: "Request body is too large" } });
      return;
    }
    logger.error({ err: error }, "unhandled error");
    res.status(500).json({ error: { code: "internal", message: "Something went wrong" } });
  };
}
