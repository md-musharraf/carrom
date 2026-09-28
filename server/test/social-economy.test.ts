import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { OFFLINE_WINS_PER_DAY, SPIN_PRIZES } from "../src/modules/economy/economy.service.js";
import { UserModel } from "../src/modules/users/user.model.js";
import { bearer, bootServer, guest, nextEvent, socketFor, type TestServer } from "./helpers.js";

let t: TestServer;

beforeAll(async () => {
  t = await bootServer();
});
afterAll(async () => {
  await t.close();
});

describe("friends", () => {
  it("sends, lists and accepts requests, with a live notification", async () => {
    const asha = await guest(t.api);
    const ravi = await guest(t.api);
    const raviSocket = await socketFor(t.server.url, ravi);
    const notified = nextEvent<{ player: { playerId: string } }>(raviSocket, "friends:request");

    const sent = await t.api.post("/v1/friends/requests").set(bearer(asha)).send({ playerId: ravi.user.playerId }).expect(201);
    expect(sent.body.status).toBe("pending");
    expect((await notified).player.playerId).toBe(asha.user.playerId);

    await t.api.post("/v1/friends/requests").set(bearer(asha)).send({ playerId: ravi.user.playerId }).expect(409);
    const incoming = await t.api.get("/v1/friends/requests").set(bearer(ravi)).expect(200);
    expect(incoming.body.requests).toHaveLength(1);
    expect(incoming.body.requests[0].direction).toBe("incoming");

    await t.api.post(`/v1/friends/requests/${incoming.body.requests[0].id}/accept`).set(bearer(ravi)).expect(204);
    const list = await t.api.get("/v1/friends").set(bearer(asha)).expect(200);
    expect(list.body.friends).toHaveLength(1);
    expect(list.body.friends[0]).toMatchObject({ playerId: ravi.user.playerId, online: true });
    raviSocket.disconnect();
  });

  it("auto-accepts when both players ask each other", async () => {
    const a = await guest(t.api);
    const b = await guest(t.api);
    await t.api.post("/v1/friends/requests").set(bearer(a)).send({ playerId: b.user.playerId }).expect(201);
    const back = await t.api.post("/v1/friends/requests").set(bearer(b)).send({ playerId: a.user.playerId }).expect(201);
    expect(back.body.status).toBe("accepted");
  });

  it("rejects befriending yourself or unknown IDs, and removes friends", async () => {
    const a = await guest(t.api);
    const b = await guest(t.api);
    await t.api.post("/v1/friends/requests").set(bearer(a)).send({ playerId: a.user.playerId }).expect(400);
    await t.api.post("/v1/friends/requests").set(bearer(a)).send({ playerId: "ZZZZZZZZ" }).expect(404);
    await t.api.post("/v1/friends/requests").set(bearer(a)).send({ playerId: b.user.playerId }).expect(201);
    await t.api.post("/v1/friends/requests").set(bearer(b)).send({ playerId: a.user.playerId }).expect(201);
    await t.api.delete(`/v1/friends/${b.user.playerId}`).set(bearer(a)).expect(204);
    expect((await t.api.get("/v1/friends").set(bearer(a))).body.friends).toHaveLength(0);
  });
});

describe("leaderboards", () => {
  it("ranks players by rating and includes the caller's own rank", async () => {
    const players = await Promise.all([guest(t.api), guest(t.api), guest(t.api)]);
    const ratings = [1720, 1510, 1655];
    for (const [i, p] of players.entries()) {
      const user = await UserModel.findByIdAndUpdate(p.user.id, { $set: { "rating.r": ratings[i], country: "IN" } }, { returnDocument: "after" });
      await t.server.container.leaderboards.recordRating(user!);
    }
    const res = await t.api.get("/v1/leaderboards/rating?limit=10").set(bearer(players[1]!)).expect(200);
    expect(res.body.entries.map((e: { score: number }) => e.score)).toEqual([1720, 1655, 1510]);
    expect(res.body.me).toMatchObject({ rank: 3, score: 1510, isMe: true });

    const country = await t.api.get("/v1/leaderboards/country").set(bearer(players[0]!)).expect(200);
    expect(country.body.entries[0].country).toBe("IN");
    await t.api.get("/v1/leaderboards/everyone").set(bearer(players[0]!)).expect(400);
  });
});

describe("economy", () => {
  it("pays the daily reward once per day", async () => {
    const s = await guest(t.api);
    const status = await t.api.get("/v1/rewards/daily").set(bearer(s)).expect(200);
    expect(status.body).toMatchObject({ claimedToday: false, streak: 0, nextReward: { coins: 100 } });
    const claim = await t.api.post("/v1/rewards/daily/claim").set(bearer(s)).expect(200);
    expect(claim.body).toMatchObject({ streak: 1, reward: { coins: 100 }, balance: { coins: 1300 } });
    const again = await t.api.post("/v1/rewards/daily/claim").set(bearer(s)).expect(409);
    expect(again.body.error.code).toBe("already_claimed");
  });

  it("continues the streak from yesterday and survives concurrent claims", async () => {
    const s = await guest(t.api);
    const yesterday = new Date(Date.now() - 86_400_000).toISOString().slice(0, 10);
    await UserModel.updateOne({ _id: s.user.id }, { $set: { "daily.lastClaimDay": yesterday, "daily.streak": 6 } });
    const results = await Promise.all([1, 2, 3].map(() => t.api.post("/v1/rewards/daily/claim").set(bearer(s))));
    const ok = results.filter((r) => r.status === 200);
    expect(ok).toHaveLength(1);
    expect(ok[0]!.body).toMatchObject({ streak: 7, reward: { coins: 1000, gems: 5 } });
  });

  it("lets the server draw the daily spin and allows one per day", async () => {
    const s = await guest(t.api);
    const spin = await t.api.post("/v1/rewards/spin").set(bearer(s)).expect(200);
    expect(SPIN_PRIZES[spin.body.index]!.coins).toBe(spin.body.coins);
    expect(spin.body.balance.coins).toBe(1200 + spin.body.coins);
    await t.api.post("/v1/rewards/spin").set(bearer(s)).expect(409);
  });

  it("caps offline-win rewards per day", async () => {
    const s = await guest(t.api);
    for (let i = 0; i < OFFLINE_WINS_PER_DAY; i++) await t.api.post("/v1/rewards/offline-win").set(bearer(s)).expect(200);
    const capped = await t.api.post("/v1/rewards/offline-win").set(bearer(s)).expect(409);
    expect(capped.body.error.code).toBe("daily_cap_reached");
    const me = await t.api.get("/v1/me").set(bearer(s)).expect(200);
    expect(me.body.progress.level).toBeGreaterThan(1);
  });

  it("sells, refuses double-buys and overspending, and equips owned items only", async () => {
    const s = await guest(t.api);
    await t.api.post("/v1/shop/equip").set(bearer(s)).send({ itemId: "ruby_emperor" }).expect(400);
    const bought = await t.api.post("/v1/shop/purchase").set(bearer(s)).send({ itemId: "ruby_emperor" }).expect(200);
    expect(bought.body.balance.coins).toBe(600);
    expect(bought.body.inventory.strikers).toContain("ruby_emperor");
    expect((await t.api.post("/v1/shop/purchase").set(bearer(s)).send({ itemId: "ruby_emperor" }).expect(409)).body.error.code).toBe("already_owned");
    expect((await t.api.post("/v1/shop/purchase").set(bearer(s)).send({ itemId: "solar_gold" }).expect(409)).body.error.code).toBe("insufficient_funds");
    const equipped = await t.api.post("/v1/shop/equip").set(bearer(s)).send({ itemId: "ruby_emperor" }).expect(200);
    expect(equipped.body.equipped.striker).toBe("ruby_emperor");

    const ledger = await t.api.get("/v1/wallet/history").set(bearer(s)).expect(200);
    expect(ledger.body.entries[0]).toMatchObject({ kind: "purchase", coins: -600, ref: "ruby_emperor" });
  });

  it("lists arenas with lock and affordability for the caller", async () => {
    const s = await guest(t.api);
    const res = await t.api.get("/v1/arenas").set(bearer(s)).expect(200);
    expect(res.body.arenas[0]).toMatchObject({ id: "bronze", unlocked: true, affordable: true });
    expect(res.body.arenas.find((a: { id: string }) => a.id === "royal")).toMatchObject({ unlocked: false, affordable: false });
  });
});

describe("hardening", () => {
  it("answers unknown routes and malformed JSON with structured errors", async () => {
    const s = await guest(t.api);
    expect((await t.api.get("/v1/nope").set(bearer(s)).expect(404)).body.error.code).toBe("not_found");
    const bad = await t.api.post("/v1/auth/refresh").set("Content-Type", "application/json").send("{not json").expect(400);
    expect(bad.body.error.code).toBe("invalid_json");
  });

  it("reports readiness of MongoDB and Redis", async () => {
    const res = await t.api.get("/ready").expect(200);
    expect(res.body).toMatchObject({ status: "ready", mongo: "fulfilled", redis: "fulfilled" });
  });
});
