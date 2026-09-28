import { spawn, type ChildProcess } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { createServer } from "node:net";
import { tmpdir } from "node:os";
import { join } from "node:path";
import type { TestProject } from "vitest/node";

declare module "vitest" {
  export interface ProvidedContext {
    mongoUrl: string;
    redisUrl: string;
  }
}

const children: ChildProcess[] = [];
const tempDirs: string[] = [];

/**
 * Integration tests run against real MongoDB and Redis. If MONGO_URL / REDIS_URL are set (CI
 * service containers) they are used; otherwise local `mongod` / `redis-server` binaries are
 * started on free ports (override the paths with MONGOD_BIN / REDIS_SERVER_BIN).
 */
export async function setup(project: TestProject) {
  const mongoUrl = process.env.MONGO_URL ?? (await startMongo());
  const redisUrl = process.env.REDIS_URL ?? (await startRedis());
  project.provide("mongoUrl", mongoUrl);
  project.provide("redisUrl", redisUrl);
}

export async function teardown() {
  for (const child of children) child.kill("SIGTERM");
  await new Promise((resolve) => setTimeout(resolve, 300));
  for (const dir of tempDirs) rmSync(dir, { recursive: true, force: true });
}

async function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      server.close(() => (typeof address === "object" && address ? resolve(address.port) : reject(new Error("no port"))));
    });
  });
}

async function startMongo(): Promise<string> {
  const port = await freePort();
  const dbPath = mkdtempSync(join(tmpdir(), "carrom-mongo-"));
  tempDirs.push(dbPath);
  const child = spawn(process.env.MONGOD_BIN ?? "mongod", ["--port", String(port), "--dbpath", dbPath, "--bind_ip", "127.0.0.1", "--quiet"], {
    stdio: ["ignore", "pipe", "pipe"],
  });
  children.push(child);
  await waitForOutput(child, /Waiting for connections/i, "mongod");
  return `mongodb://127.0.0.1:${port}`;
}

async function startRedis(): Promise<string> {
  const port = await freePort();
  const child = spawn(process.env.REDIS_SERVER_BIN ?? "redis-server", ["--port", String(port), "--save", "", "--appendonly", "no"], {
    stdio: ["ignore", "pipe", "pipe"],
  });
  children.push(child);
  await waitForOutput(child, /Ready to accept connections/i, "redis-server");
  return `redis://127.0.0.1:${port}`;
}

function waitForOutput(child: ChildProcess, pattern: RegExp, name: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${name} did not start in time`)), 30_000);
    const onData = (chunk: Buffer) => {
      if (pattern.test(chunk.toString())) {
        clearTimeout(timer);
        resolve();
      }
    };
    child.stdout?.on("data", onData);
    child.stderr?.on("data", onData);
    child.once("exit", (code) => reject(new Error(`${name} exited with code ${code}`)));
    child.once("error", reject);
  });
}
