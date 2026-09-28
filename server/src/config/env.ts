import { z } from "zod";

const csv = z
  .string()
  .default("")
  .transform((value) =>
    value
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean),
  );

const schema = z
  .object({
    NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
    PORT: z.coerce.number().int().min(0).max(65535).default(8080),
    LOG_LEVEL: z.enum(["fatal", "error", "warn", "info", "debug", "trace", "silent"]).default("info"),
    MONGO_URL: z.string().startsWith("mongodb"),
    REDIS_URL: z.string().startsWith("redis").default("redis://127.0.0.1:6379"),
    CORS_ORIGINS: csv,

    JWT_SECRET: z.string().min(32, "JWT_SECRET must be at least 32 characters"),
    JWT_ISSUER: z.string().default("royal-carrom"),
    ACCESS_TOKEN_TTL_SECONDS: z.coerce.number().int().positive().default(15 * 60),
    REFRESH_TOKEN_TTL_DAYS: z.coerce.number().int().positive().default(30),

    GOOGLE_CLIENT_IDS: csv,
    FACEBOOK_APP_ID: z.string().optional(),
    FACEBOOK_APP_SECRET: z.string().optional(),
    FACEBOOK_GRAPH_VERSION: z.string().default("v21.0"),

    OTP_PROVIDER: z.enum(["console", "twilio"]).default("console"),
    OTP_PEPPER: z.string().min(16).default("dev-only-otp-pepper-change-me"),
    TWILIO_ACCOUNT_SID: z.string().optional(),
    TWILIO_AUTH_TOKEN: z.string().optional(),
    TWILIO_VERIFY_SERVICE_SID: z.string().optional(),

    TURN_SECONDS: z.coerce.number().positive().default(20),
    MATCHMAKING_TICK_MS: z.coerce.number().int().positive().default(1000),
  })
  .superRefine((env, ctx) => {
    if (env.NODE_ENV === "production" && env.OTP_PROVIDER === "console") {
      ctx.addIssue({ code: "custom", path: ["OTP_PROVIDER"], message: "console OTP delivery is not allowed in production" });
    }
    if (env.OTP_PROVIDER === "twilio" && !(env.TWILIO_ACCOUNT_SID && env.TWILIO_AUTH_TOKEN && env.TWILIO_VERIFY_SERVICE_SID)) {
      ctx.addIssue({ code: "custom", path: ["OTP_PROVIDER"], message: "Twilio credentials are required for the twilio OTP provider" });
    }
    if (Boolean(env.FACEBOOK_APP_ID) !== Boolean(env.FACEBOOK_APP_SECRET)) {
      ctx.addIssue({ code: "custom", path: ["FACEBOOK_APP_SECRET"], message: "set both FACEBOOK_APP_ID and FACEBOOK_APP_SECRET" });
    }
  });

export type Env = z.infer<typeof schema>;

export interface AppConfig {
  env: Env["NODE_ENV"];
  port: number;
  logLevel: Env["LOG_LEVEL"];
  mongoUrl: string;
  redisUrl: string;
  corsOrigins: string[];
  jwt: { secret: string; issuer: string; accessTtlSeconds: number; refreshTtlDays: number };
  google: { clientIds: string[] };
  facebook: { appId?: string; appSecret?: string; graphVersion: string };
  otp: {
    provider: Env["OTP_PROVIDER"];
    pepper: string;
    twilio?: { accountSid: string; authToken: string; serviceSid: string };
  };
  game: { turnSeconds: number; matchmakingTickMs: number };
}

/** Parses and validates configuration from the environment; throws with every problem listed. */
export function loadConfig(source: NodeJS.ProcessEnv = process.env): AppConfig {
  const parsed = schema.safeParse(source);
  if (!parsed.success) {
    const problems = parsed.error.issues.map((issue) => `  - ${issue.path.join(".")}: ${issue.message}`).join("\n");
    throw new Error(`Invalid configuration:\n${problems}`);
  }
  const env = parsed.data;
  return {
    env: env.NODE_ENV,
    port: env.PORT,
    logLevel: env.LOG_LEVEL,
    mongoUrl: env.MONGO_URL,
    redisUrl: env.REDIS_URL,
    corsOrigins: env.CORS_ORIGINS,
    jwt: {
      secret: env.JWT_SECRET,
      issuer: env.JWT_ISSUER,
      accessTtlSeconds: env.ACCESS_TOKEN_TTL_SECONDS,
      refreshTtlDays: env.REFRESH_TOKEN_TTL_DAYS,
    },
    google: { clientIds: env.GOOGLE_CLIENT_IDS },
    facebook: { appId: env.FACEBOOK_APP_ID, appSecret: env.FACEBOOK_APP_SECRET, graphVersion: env.FACEBOOK_GRAPH_VERSION },
    otp: {
      provider: env.OTP_PROVIDER,
      pepper: env.OTP_PEPPER,
      twilio:
        env.TWILIO_ACCOUNT_SID && env.TWILIO_AUTH_TOKEN && env.TWILIO_VERIFY_SERVICE_SID
          ? { accountSid: env.TWILIO_ACCOUNT_SID, authToken: env.TWILIO_AUTH_TOKEN, serviceSid: env.TWILIO_VERIFY_SERVICE_SID }
          : undefined,
    },
    game: { turnSeconds: env.TURN_SECONDS, matchmakingTickMs: env.MATCHMAKING_TICK_MS },
  };
}
