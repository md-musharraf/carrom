import { Router } from "express";
import { z } from "zod";
import { parse } from "../../lib/validate.js";
import { caller } from "../auth/auth.middleware.js";
import type { FriendsService } from "../friends/friends.service.js";
import type { UsersService } from "../users/users.service.js";
import type { LeaderboardService } from "./leaderboard.service.js";

const params = z.object({ board: z.enum(["rating", "weekly", "country", "friends"]) });
const query = z.object({ limit: z.coerce.number().int().min(1).max(100).default(50) });

/** /v1/leaderboards/:board. Mounted behind requireAuth. */
export function leaderboardRoutes(deps: { leaderboards: LeaderboardService; users: UsersService; friends: FriendsService }): Router {
  const router = Router();
  router.get("/:board", async (req, res) => {
    const { board } = parse(params, req.params);
    const { limit } = parse(query, req.query);
    const { userId } = caller(req);
    const me = await deps.users.get(userId);
    const friendIds = board === "friends" ? await deps.friends.friendIds(userId) : undefined;
    res.json(await deps.leaderboards.get(board, me, { limit, friendIds }));
  });
  return router;
}
