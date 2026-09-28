import { Router } from "express";
import { z } from "zod";
import type { Limiter } from "../../lib/rate-limit.js";
import { parse } from "../../lib/validate.js";
import { caller } from "../auth/auth.middleware.js";
import type { FriendsService } from "./friends.service.js";

const requestBody = z.object({ playerId: z.string().min(8).max(12) });

/** /v1/friends. Mounted behind requireAuth. */
export function friendsRoutes(friends: FriendsService, limiter: Limiter): Router {
  const router = Router();

  router.get("/", async (req, res) => {
    res.json({ friends: await friends.list(caller(req).userId) });
  });

  router.get("/requests", async (req, res) => {
    res.json({ requests: await friends.requests(caller(req).userId) });
  });

  router.post("/requests", async (req, res) => {
    const { userId } = caller(req);
    await limiter.consume(userId);
    const body = parse(requestBody, req.body);
    res.status(201).json(await friends.request(userId, body.playerId));
  });

  router.post("/requests/:id/accept", async (req, res) => {
    await friends.accept(caller(req).userId, req.params.id ?? "");
    res.status(204).end();
  });

  router.post("/requests/:id/decline", async (req, res) => {
    await friends.decline(caller(req).userId, req.params.id ?? "");
    res.status(204).end();
  });

  router.delete("/:playerId", async (req, res) => {
    await friends.remove(caller(req).userId, req.params.playerId ?? "");
    res.status(204).end();
  });

  return router;
}
