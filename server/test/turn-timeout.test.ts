import { afterAll, beforeAll, expect, it } from "vitest";
import { MAX_TIMEOUTS, type MatchSnapshot } from "../src/modules/realtime/match.service.js";
import { bootServer, call, guest, nextEvent, socketFor, type TestServer } from "./helpers.js";

let t: TestServer;

beforeAll(async () => {
  t = await bootServer({ TURN_SECONDS: "0.4" });
});
afterAll(async () => {
  await t.close();
});

it("passes the turn when the clock runs out and forfeits after repeated timeouts", async () => {
  const [a, b] = await Promise.all([guest(t.api), guest(t.api)]);
  const [sa, sb] = await Promise.all([socketFor(t.server.url, a), socketFor(t.server.url, b)]);
  const started = nextEvent<MatchSnapshot>(sa, "match:start");
  const room = await call<{ code: string }>(sa, "room:create", { mode: "classic" });
  await call(sb, "room:join", { code: room.code });
  const snapshot = await started;

  const firstTimeout = await nextEvent<{ reason: string; timedOutSeat: number; snapshot: MatchSnapshot }>(sa, "match:turn");
  expect(firstTimeout).toMatchObject({ reason: "timeout", timedOutSeat: 0 });
  expect(firstTimeout.snapshot).toMatchObject({ current: 1, turn: 1 });

  // Seats alternate timing out; seat 0 reaches the limit first and forfeits.
  const end = await nextEvent<{ winner: number; reason: string }>(sb, "match:end", 15_000);
  expect(end).toEqual(expect.objectContaining({ winner: 1, reason: "timeout" }));
  expect(MAX_TIMEOUTS).toBe(3);
  expect(snapshot.turnSeconds).toBe(0.4);
  sa.disconnect();
  sb.disconnect();
});
