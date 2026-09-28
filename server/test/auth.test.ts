import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { bearer, bootServer, guest, socialToken, type TestServer } from "./helpers.js";

let t: TestServer;

beforeAll(async () => {
  t = await bootServer();
});
afterAll(async () => {
  await t.close();
});

describe("guest accounts and player IDs", () => {
  it("creates a guest with a unique, shareable player ID and starting wallet", async () => {
    const a = await guest(t.api);
    const b = await guest(t.api);
    expect(a.user.playerId).toMatch(/^[0-9A-HJKMNP-TV-Z]{8}$/);
    expect(a.user.playerId).not.toBe(b.user.playerId);
    expect(a.user.displayId).toBe(`${a.user.playerId.slice(0, 4)}-${a.user.playerId.slice(4)}`);
    expect(a.user.isGuest).toBe(true);
    expect(a.user.wallet).toEqual({ coins: 1200, gems: 25 });
    expect(a.refreshToken).toMatch(/^rt_/);
  });

  it("serves the caller's profile only with a valid access token", async () => {
    const session = await guest(t.api);
    await t.api.get("/v1/me").expect(401);
    await t.api.get("/v1/me").set({ Authorization: "Bearer not-a-token" }).expect(401);
    const me = await t.api.get("/v1/me").set(bearer(session)).expect(200);
    expect(me.body.playerId).toBe(session.user.playerId);
  });

  it("finds players by ID regardless of dashes, case and look-alike letters", async () => {
    const me = await guest(t.api);
    const other = await guest(t.api);
    const typed = `${other.user.playerId.slice(0, 4).toLowerCase()}-${other.user.playerId.slice(4)}`.replace(/0/g, "o").replace(/1/g, "l");
    const res = await t.api.get(`/v1/players/${typed}`).set(bearer(me)).expect(200);
    expect(res.body.playerId).toBe(other.user.playerId);
    expect(res.body).not.toHaveProperty("wallet");
    await t.api.get("/v1/players/ZZZZ-ZZZZ").set(bearer(me)).expect(404);
  });

  it("validates profile edits", async () => {
    const session = await guest(t.api);
    await t.api.patch("/v1/me").set(bearer(session)).send({ displayName: "x" }).expect(400);
    const res = await t.api.patch("/v1/me").set(bearer(session)).send({ displayName: "Carrom King", country: "in" }).expect(200);
    expect(res.body.displayName).toBe("Carrom King");
    expect(res.body.country).toBe("IN");
  });
});

describe("refresh token rotation", () => {
  it("rotates on every refresh and revokes the family when an old token is replayed", async () => {
    const session = await guest(t.api);
    const first = await t.api.post("/v1/auth/refresh").send({ refreshToken: session.refreshToken }).expect(200);
    expect(first.body.refreshToken).not.toBe(session.refreshToken);

    // Replaying the rotated token looks like theft: it fails and kills the newer token too.
    const replay = await t.api.post("/v1/auth/refresh").send({ refreshToken: session.refreshToken }).expect(401);
    expect(replay.body.error.code).toBe("refresh_token_reused");
    await t.api.post("/v1/auth/refresh").send({ refreshToken: first.body.refreshToken }).expect(401);
  });

  it("logs out by revoking the session", async () => {
    const session = await guest(t.api);
    await t.api.post("/v1/auth/logout").send({ refreshToken: session.refreshToken }).expect(204);
    await t.api.post("/v1/auth/refresh").send({ refreshToken: session.refreshToken }).expect(401);
  });
});

describe("Google and Facebook sign-in", () => {
  it("creates an account on first sign-in and returns the same account afterwards", async () => {
    const first = await t.api.post("/v1/auth/google").send({ idToken: socialToken("google", "g-100", "Asha") }).expect(200);
    expect(first.body.isNewUser).toBe(true);
    expect(first.body.user.displayName).toBe("Asha");
    expect(first.body.user.linked.google).toBe(true);
    const again = await t.api.post("/v1/auth/google").send({ idToken: socialToken("google", "g-100", "Asha") }).expect(200);
    expect(again.body.isNewUser).toBe(false);
    expect(again.body.user.playerId).toBe(first.body.user.playerId);
  });

  it("rejects tokens that fail verification", async () => {
    const res = await t.api.post("/v1/auth/facebook").send({ accessToken: "definitely-not-a-valid-facebook-token" }).expect(401);
    expect(res.body.error.code).toBe("invalid_facebook_token");
  });

  it("links a provider to a guest, keeping progress, and refuses identities owned by someone else", async () => {
    const session = await guest(t.api);
    const linked = await t.api
      .post("/v1/auth/link/facebook")
      .set(bearer(session))
      .send({ accessToken: socialToken("facebook", "fb-7", "Ravi") })
      .expect(200);
    expect(linked.body.user.playerId).toBe(session.user.playerId);
    expect(linked.body.user.isGuest).toBe(false);
    expect(linked.body.user.displayName).toBe("Ravi");

    const someoneElse = await guest(t.api);
    const clash = await t.api
      .post("/v1/auth/link/facebook")
      .set(bearer(someoneElse))
      .send({ accessToken: socialToken("facebook", "fb-7", "Ravi") })
      .expect(409);
    expect(clash.body.error.code).toBe("identity_in_use");

    // Signing in with Facebook now lands on the upgraded guest account.
    const signIn = await t.api.post("/v1/auth/facebook").send({ accessToken: socialToken("facebook", "fb-7") }).expect(200);
    expect(signIn.body.user.playerId).toBe(session.user.playerId);
  });
});

describe("phone number sign-in", () => {
  const phone = "+919876543210";

  it("sends a code, rejects wrong codes and signs in with the right one", async () => {
    const start = await t.api.post("/v1/auth/phone/start").send({ phone }).expect(202);
    expect(start.body).toMatchObject({ phone, expiresInSeconds: 300, codeLength: 6 });
    const code = t.sms.lastCode(phone)!;
    expect(code).toMatch(/^\d{6}$/);

    const wrong = code === "000000" ? "111111" : "000000";
    const bad = await t.api.post("/v1/auth/phone/verify").send({ phone, code: wrong }).expect(401);
    expect(bad.body.error.code).toBe("invalid_code");

    const ok = await t.api.post("/v1/auth/phone/verify").send({ phone, code }).expect(200);
    expect(ok.body.isNewUser).toBe(true);
    expect(ok.body.user.linked.phone).toBe("•••• 3210");
    expect(ok.body.user.country).toBe("IN");

    // Codes are single-use.
    await t.api.post("/v1/auth/phone/verify").send({ phone, code }).expect(401);
  });

  it("accepts local formats with a country hint and rejects invalid numbers", async () => {
    await t.api.post("/v1/auth/phone/start").send({ phone: "12345" }).expect(400);
    const res = await t.api.post("/v1/auth/phone/start").send({ phone: "0412 345 678", country: "AU" }).expect(202);
    expect(res.body.phone).toBe("+61412345678");
  });

  it("locks the code after five wrong guesses", async () => {
    const target = "+14155550123";
    await t.api.post("/v1/auth/phone/start").send({ phone: target }).expect(202);
    const code = t.sms.lastCode(target)!;
    const wrong = code === "123456" ? "654321" : "123456";
    for (let i = 0; i < 5; i++) await t.api.post("/v1/auth/phone/verify").send({ phone: target, code: wrong }).expect(401);
    await t.api.post("/v1/auth/phone/verify").send({ phone: target, code }).expect(429);
  });

  it("rate-limits code requests per number", async () => {
    const target = "+447911123456";
    for (let i = 0; i < 3; i++) await t.api.post("/v1/auth/phone/start").send({ phone: target }).expect(202);
    const limited = await t.api.post("/v1/auth/phone/start").send({ phone: target }).expect(429);
    expect(limited.headers["retry-after"]).toBeDefined();
  });
});

describe("account deletion", () => {
  it("deletes the account and invalidates its sessions", async () => {
    const session = await guest(t.api);
    await t.api.delete("/v1/me").set(bearer(session)).expect(204);
    await t.api.post("/v1/auth/refresh").send({ refreshToken: session.refreshToken }).expect(401);
    await t.api.get("/v1/me").set(bearer(session)).expect(404);
  });
});
