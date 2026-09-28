import { createServer } from "node:http";
import type { AddressInfo } from "node:net";
import { createApp } from "./app.js";
import type { AppConfig } from "./config/env.js";
import { createContainer, type Container, type Overrides } from "./container.js";
import { connectMongo, disconnectMongo } from "./infra/mongo.js";
import { createRedis } from "./infra/redis.js";
import { createLogger } from "./lib/logger.js";
import { attachRealtime, type Realtime } from "./modules/realtime/socket.js";

export interface RunningServer {
  url: string;
  container: Container;
  realtime: Realtime;
  close(): Promise<void>;
}

/** Boots MongoDB, Redis, the REST API and the realtime server on one HTTP port. */
export async function startServer(config: AppConfig, overrides: Overrides = {}): Promise<RunningServer> {
  const logger = createLogger(config);
  await connectMongo(config.mongoUrl, logger);
  const redis = createRedis(config.redisUrl, logger, "app");
  const adapterRedis = createRedis(config.redisUrl, logger, "socket-adapter");

  const container = createContainer(config, logger, redis, overrides);
  const httpServer = createServer(createApp(container));
  const realtime = attachRealtime(httpServer, { ...container, adapterRedis });

  await new Promise<void>((resolve) => httpServer.listen(config.port, resolve));
  const { port } = httpServer.address() as AddressInfo;
  logger.info({ port }, "Royal Carrom server listening");

  let closing: Promise<void> | null = null;
  const close = () =>
    (closing ??= (async () => {
      await realtime.close(); // Also closes the HTTP server.
      await Promise.allSettled([redis.quit(), adapterRedis.quit()]);
      await disconnectMongo();
      logger.info("server stopped");
    })());

  return { url: `http://127.0.0.1:${port}`, container, realtime, close };
}
