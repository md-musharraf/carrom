import { Router } from "express";
import { z } from "zod";
import { parse } from "../../lib/validate.js";
import { caller } from "../auth/auth.middleware.js";
import { matchHistory } from "./match.model.js";

const query = z.object({ limit: z.coerce.number().int().min(1).max(50).default(20) });

/** /v1/matches — the caller's finished games, newest first. Mounted behind requireAuth. */
export function matchesRoutes(): Router {
  const router = Router();
  router.get("/", async (req, res) => {
    const { limit } = parse(query, req.query);
    res.json({ matches: await matchHistory(caller(req).userId, limit) });
  });
  return router;
}
