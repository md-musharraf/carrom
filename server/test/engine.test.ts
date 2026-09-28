import { describe, expect, it } from "vitest";
import { loadConfig } from "../src/config/env.js";
import { formatPlayerId, generateRoomCode, normalizePlayerId } from "../src/lib/ids.js";
import { isoWeek, previousUtcDay } from "../src/lib/time.js";
import { TwilioVerifyChannel } from "../src/modules/auth/providers/phone.js";
import { FacebookGraphVerifier } from "../src/modules/auth/providers/social.js";
import { drawWeighted } from "../src/modules/economy/economy.service.js";
import { rateGame, updateRating } from "../src/modules/game/glicko.js";
import { classicCluster, simulateShot, stepPhysics, type Piece } from "../src/modules/game/physics.js";
import { evaluateShot, noQueen, winnerOrNull } from "../src/modules/game/rules.js";
import { pairPlayers, ratingWindow } from "../src/modules/realtime/matchmaking.js";

describe("physics port", () => {
  it("racks the same 19 discs with the same ids as the Android client", () => {
    const pieces = classicCluster();
    expect(pieces).toHaveLength(19);
    expect(pieces.filter((p) => p.type === "WHITE")).toHaveLength(9);
    expect(pieces.filter((p) => p.type === "BLACK")).toHaveLength(9);
    expect(pieces[0]).toMatchObject({ id: "queen", x: 400, y: 400 });
    expect(pieces.map((p) => p.id)).toContain("outer_e_3_black");
  });

  it("is identical at 60 and 120 Hz", () => {
    const run = (hz: number) => {
      const board = classicCluster();
      const striker: Piece = { id: "striker", type: "STRIKER", x: 400, y: 660, vx: 0, vy: -34, radius: 22, mass: 3, pocketed: false, pocketId: -1 };
      for (let i = 0; i < (hz * 3) / 2; i++) stepPhysics(board, striker, 1 / hz, () => undefined);
      return board;
    };
    const a = run(60);
    const b = run(120);
    a.forEach((p, i) => {
      expect(p.x).toBeCloseTo(b[i]!.x, 6);
      expect(p.y).toBeCloseTo(b[i]!.y, 6);
    });
  });

  it("simulates whole shots deterministically without mutating the input", () => {
    const board = classicCluster();
    const before = JSON.stringify(board);
    const first = simulateShot(board, { offset: 0.5, angle: -Math.PI / 2, power: 100 }, true);
    const second = simulateShot(board, { offset: 0.5, angle: -Math.PI / 2, power: 100 }, true);
    expect(JSON.stringify(board)).toBe(before);
    expect(first.pieces).toEqual(second.pieces);
    expect(first.pieces.every((p) => p.vx === 0 && p.vy === 0)).toBe(true);
  });

  it("drops a disc that is struck straight into a pocket", () => {
    const target: Piece = { id: "w", type: "WHITE", x: 640, y: 660 - 110, vx: 0, vy: 0, radius: 15.5, mass: 1, pocketed: false, pocketId: -1 };
    // Aim the ghost-ball point behind the disc on the disc → top-right pocket line.
    const pocket = { x: 732, y: 68 };
    const len = Math.hypot(pocket.x - target.x, pocket.y - target.y);
    const gx = target.x - ((pocket.x - target.x) / len) * 37.5;
    const gy = target.y - ((pocket.y - target.y) / len) * 37.5;
    const strikerX = 400;
    const angle = Math.atan2(gy - 660, gx - strikerX);
    const result = simulateShot([target], { offset: 0.5, angle, power: 90 }, true);
    expect(result.pocketed.map((p) => p.id)).toEqual(["w"]);
    expect(result.pieces[0]).toMatchObject({ pocketed: true, pocketId: 1 });
  });
});

describe("rules port", () => {
  it("matches the client's queen, cover and foul rules", () => {
    expect(evaluateShot(0, ["QUEEN", "WHITE"], false, noQueen())).toMatchObject({ scoreDelta: 35, queenCoveredNow: true, keepsTurn: true });
    expect(evaluateShot(0, [], false, { pottedBy: 0, awaitingCover: true, covered: false })).toMatchObject({ returnQueenToCenter: true, keepsTurn: false });
    expect(evaluateShot(1, ["BLACK"], true, noQueen())).toMatchObject({ scoreDelta: 0, isFoul: true, keepsTurn: false });
    expect(winnerOrNull("freestyle", 4, [100, 160])).toBe(1);
    expect(winnerOrNull("classic", 4, [100, 160])).toBeNull();
    expect(winnerOrNull("classic", 0, [70, 70])).toBe(0);
  });
});

describe("Glicko-2", () => {
  it("reproduces the worked example from Glickman's paper", () => {
    const updated = updateRating({ r: 1500, rd: 200, vol: 0.06 }, [
      { opponent: { r: 1400, rd: 30, vol: 0.06 }, score: 1 },
      { opponent: { r: 1550, rd: 100, vol: 0.06 }, score: 0 },
      { opponent: { r: 1700, rd: 300, vol: 0.06 }, score: 0 },
    ]);
    expect(updated.r).toBeCloseTo(1464.06, 1);
    expect(updated.rd).toBeCloseTo(151.52, 1);
    expect(updated.vol).toBeCloseTo(0.05999, 4);
  });

  it("moves new players a lot and settled players a little", () => {
    const fresh = rateGame({ r: 1500, rd: 350, vol: 0.06 }, { r: 1500, rd: 350, vol: 0.06 });
    const settled = rateGame({ r: 1500, rd: 50, vol: 0.06 }, { r: 1500, rd: 50, vol: 0.06 });
    expect(fresh.winner.r - 1500).toBeGreaterThan(100);
    expect(settled.winner.r - 1500).toBeLessThan(20);
    expect(fresh.winner.rd).toBeLessThan(350);
  });
});

describe("matchmaking", () => {
  it("widens the rating window with waiting time", () => {
    expect(ratingWindow(0)).toBe(100);
    expect(ratingWindow(10)).toBe(600);
    expect(ratingWindow(600)).toBe(800);
  });

  it("pairs neighbours whose windows overlap and leaves outliers waiting", () => {
    const pairs = pairPlayers([
      { userId: "a", rating: 1400, waitSeconds: 0 },
      { userId: "b", rating: 1450, waitSeconds: 0 },
      { userId: "c", rating: 1900, waitSeconds: 0 },
      { userId: "d", rating: 2400, waitSeconds: 1 },
    ]);
    expect(pairs.map(([x, y]) => `${x.userId}${y.userId}`)).toEqual(["ab"]);
    const patient = pairPlayers([
      { userId: "c", rating: 1900, waitSeconds: 9 },
      { userId: "d", rating: 2400, waitSeconds: 0 },
    ]);
    expect(patient).toHaveLength(1);
  });
});

describe("identifiers and time", () => {
  it("normalises player IDs as people type them", () => {
    expect(normalizePlayerId("7k3p-9qxa")).toBe("7K3P9QXA");
    expect(normalizePlayerId(" 1O2L 3I4U ")).toBeNull(); // 'U' is excluded from Crockford.
    expect(normalizePlayerId("AB0L-1234")).toBe("AB011234");
    expect(normalizePlayerId("short")).toBeNull();
    expect(formatPlayerId("7K3P9QXA")).toBe("7K3P-9QXA");
    expect(generateRoomCode()).toMatch(/^[2-9A-HJ-NP-Z]{6}$/);
  });

  it("computes UTC days and ISO weeks", () => {
    expect(previousUtcDay("2026-03-01")).toBe("2026-02-28");
    expect(isoWeek(Date.UTC(2026, 0, 1))).toBe("2026-W01");
    expect(isoWeek(Date.UTC(2027, 0, 1))).toBe("2026-W53");
  });

  it("draws weighted prizes in proportion", () => {
    const counts = [0, 0];
    for (let i = 0; i < 4000; i++) counts[drawWeighted([3, 1])]! += 1;
    expect(counts[0]! / 4000).toBeGreaterThan(0.7);
    expect(counts[0]! / 4000).toBeLessThan(0.8);
  });
});

describe("external identity providers", () => {
  const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

  it("verifies Facebook tokens with debug_token, app id and appsecret_proof", async () => {
    const urls: string[] = [];
    const verifier = new FacebookGraphVerifier("app-1", "secret", "v21.0", async (url) => {
      urls.push(url);
      if (url.includes("/debug_token")) return json(200, { data: { is_valid: true, app_id: "app-1", user_id: "42" } });
      return json(200, { id: "42", name: "Meera", picture: { data: { url: "https://pic", is_silhouette: false } } });
    });
    await expect(verifier.verify("user-token")).resolves.toEqual({ id: "42", name: "Meera", avatarUrl: "https://pic" });
    expect(urls[0]).toContain("access_token=app-1%7Csecret");
    expect(urls[1]).toMatch(/appsecret_proof=[0-9a-f]{64}/);
  });

  it("rejects Facebook tokens issued to another app", async () => {
    const verifier = new FacebookGraphVerifier("app-1", "secret", "v21.0", async () =>
      json(200, { data: { is_valid: true, app_id: "someone-else", user_id: "42" } }),
    );
    await expect(verifier.verify("user-token")).rejects.toMatchObject({ code: "invalid_facebook_token" });
  });

  it("delegates OTPs to Twilio Verify", async () => {
    const calls: Array<{ url: string; body: string }> = [];
    const channel = new TwilioVerifyChannel("AC1", "token", "VA1", async (url, init) => {
      calls.push({ url, body: String(init?.body) });
      if (url.endsWith("/Verifications")) return json(201, { status: "pending" });
      return String(init?.body).includes("Code=123456") ? json(200, { status: "approved" }) : json(200, { status: "pending" });
    });
    await channel.send("+919876543210");
    expect(calls[0]!.url).toBe("https://verify.twilio.com/v2/Services/VA1/Verifications");
    expect(calls[0]!.body).toContain("Channel=sms");
    await expect(channel.check("+919876543210", "123456")).resolves.toBe(true);
    await expect(channel.check("+919876543210", "000000")).resolves.toBe(false);
  });
});

describe("configuration", () => {
  const base = { MONGO_URL: "mongodb://localhost/x", JWT_SECRET: "x".repeat(40) };

  it("refuses unsafe production settings", () => {
    expect(() => loadConfig({ ...base, NODE_ENV: "production" })).toThrow(/console OTP delivery/);
    expect(() => loadConfig({ ...base, JWT_SECRET: "short" })).toThrow(/JWT_SECRET/);
    expect(() => loadConfig({ ...base, FACEBOOK_APP_ID: "1" })).toThrow(/FACEBOOK_APP_SECRET/);
  });

  it("parses lists and defaults", () => {
    const config = loadConfig({ ...base, GOOGLE_CLIENT_IDS: "a.apps, b.apps", CORS_ORIGINS: "" });
    expect(config.google.clientIds).toEqual(["a.apps", "b.apps"]);
    expect(config.corsOrigins).toEqual([]);
    expect(config.game.turnSeconds).toBe(20);
  });
});
