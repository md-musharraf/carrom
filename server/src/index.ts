import { loadConfig } from "./config/env.js";
import { startServer } from "./server.js";

const config = loadConfig();
const server = await startServer(config);

for (const signal of ["SIGINT", "SIGTERM"] as const) {
  process.once(signal, () => {
    void server.close().then(() => process.exit(0));
    // Don't hang forever on a stuck connection.
    setTimeout(() => process.exit(1), 10_000).unref();
  });
}
