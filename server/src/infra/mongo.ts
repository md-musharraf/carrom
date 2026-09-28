import mongoose from "mongoose";
import type { Logger } from "../lib/logger.js";

export async function connectMongo(url: string, logger: Logger): Promise<typeof mongoose> {
  mongoose.set("strictQuery", true);
  mongoose.connection.on("disconnected", () => logger.warn("MongoDB disconnected"));
  mongoose.connection.on("reconnected", () => logger.info("MongoDB reconnected"));
  await mongoose.connect(url, { serverSelectionTimeoutMS: 10_000, autoIndex: true });
  // Build unique indexes up front so the very first requests are already protected by them.
  await Promise.all(Object.values(mongoose.models).map((model) => model.syncIndexes()));
  logger.info("MongoDB connected");
  return mongoose;
}

export async function disconnectMongo(): Promise<void> {
  await mongoose.disconnect();
}
