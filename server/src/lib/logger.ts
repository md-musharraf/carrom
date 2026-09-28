import { pino, type Logger } from "pino";
import type { AppConfig } from "../config/env.js";

export type { Logger };

export function createLogger(config: Pick<AppConfig, "env" | "logLevel">): Logger {
  return pino({
    level: config.env === "test" ? "silent" : config.logLevel,
    redact: {
      paths: ["req.headers.authorization", "*.refreshToken", "*.accessToken", "*.idToken", "*.code"],
      censor: "[redacted]",
    },
    transport: config.env === "development" ? { target: "pino-pretty", options: { colorize: true } } : undefined,
  });
}
