import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    globalSetup: ["./test/global-setup.ts"],
    include: ["test/**/*.test.ts"],
    // Files share one MongoDB and one Redis; each file uses its own database but Redis keys are
    // global, so files run one at a time.
    fileParallelism: false,
    testTimeout: 20_000,
    hookTimeout: 60_000,
  },
});
