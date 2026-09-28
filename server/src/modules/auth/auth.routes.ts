import { Router, type Request } from "express";
import { z } from "zod";
import { parse } from "../../lib/validate.js";
import { caller, requireAuth } from "./auth.middleware.js";
import type { AuthService, RequestMeta } from "./auth.service.js";
import type { AccessTokens } from "./tokens.js";

const country = z.string().length(2).optional();
const guestBody = z.object({ country });
const googleBody = z.object({ idToken: z.string().min(20).max(4096) });
const facebookBody = z.object({ accessToken: z.string().min(20).max(4096) });
const phoneStartBody = z.object({ phone: z.string().min(6).max(24), country });
const phoneVerifyBody = z.object({ phone: z.string().min(6).max(24), code: z.string().regex(/^\d{6}$/), country });
const refreshBody = z.object({ refreshToken: z.string().startsWith("rt_").max(200) });

const meta = (req: Request): RequestMeta => ({ ip: req.ip ?? "unknown", userAgent: req.get("user-agent")?.slice(0, 200) });

/**
 * /v1/auth — sign-in with guest, Google, Facebook or phone OTP; the same endpoints under
 * /v1/auth/link attach a provider to the signed-in account instead.
 */
export function authRoutes(auth: AuthService, tokens: AccessTokens): Router {
  const router = Router();

  router.post("/guest", async (req, res) => {
    const body = parse(guestBody, req.body ?? {});
    res.status(201).json(await auth.guest(meta(req), body.country));
  });

  router.post("/google", async (req, res) => {
    const body = parse(googleBody, req.body);
    res.json(await auth.google(body.idToken, meta(req)));
  });

  router.post("/facebook", async (req, res) => {
    const body = parse(facebookBody, req.body);
    res.json(await auth.facebook(body.accessToken, meta(req)));
  });

  router.post("/phone/start", async (req, res) => {
    const body = parse(phoneStartBody, req.body);
    res.status(202).json(await auth.startPhone(body.phone, meta(req), body.country));
  });

  router.post("/phone/verify", async (req, res) => {
    const body = parse(phoneVerifyBody, req.body);
    res.json(await auth.verifyPhone(body.phone, body.code, meta(req), undefined, body.country));
  });

  router.post("/refresh", async (req, res) => {
    const body = parse(refreshBody, req.body);
    res.json(await auth.refresh(body.refreshToken, meta(req)));
  });

  router.post("/logout", async (req, res) => {
    const body = parse(refreshBody, req.body);
    await auth.logout(body.refreshToken);
    res.status(204).end();
  });

  const link = Router();
  link.use(requireAuth(tokens));
  link.post("/google", async (req, res) => {
    const body = parse(googleBody, req.body);
    res.json(await auth.google(body.idToken, meta(req), caller(req).userId));
  });
  link.post("/facebook", async (req, res) => {
    const body = parse(facebookBody, req.body);
    res.json(await auth.facebook(body.accessToken, meta(req), caller(req).userId));
  });
  link.post("/phone/verify", async (req, res) => {
    const body = parse(phoneVerifyBody, req.body);
    res.json(await auth.verifyPhone(body.phone, body.code, meta(req), caller(req).userId, body.country));
  });
  router.use("/link", link);

  return router;
}
