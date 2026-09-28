import { Router } from "express";
import { z } from "zod";
import { notFound } from "../../lib/errors.js";
import { parse } from "../../lib/validate.js";
import { caller } from "../auth/auth.middleware.js";
import type { AuthService } from "../auth/auth.service.js";
import type { FriendsService } from "../friends/friends.service.js";
import type { LeaderboardService } from "../leaderboard/leaderboard.service.js";
import type { PresenceService } from "../realtime/presence.js";
import type { UsersService } from "./users.service.js";

const displayName = z
  .string()
  .trim()
  .min(3)
  .max(20)
  .regex(/^[\p{L}\p{N} _.-]+$/u, "Use letters, numbers, spaces, dots, dashes or underscores");

const patchBody = z
  .object({
    displayName: displayName.optional(),
    avatarUrl: z.url().max(500).nullable().optional(),
    country: z.string().length(2).regex(/^[A-Za-z]{2}$/).optional(),
  })
  .refine((body) => Object.keys(body).length > 0, "Nothing to update");

/** /v1/me (the caller's account) and /v1/players/:playerId (public profiles). Mounted behind requireAuth. */
export function usersRoutes(deps: {
  users: UsersService;
  auth: AuthService;
  friends: FriendsService;
  leaderboards: LeaderboardService;
  presence: PresenceService;
}): Router {
  const { users, auth, friends, leaderboards, presence } = deps;
  const router = Router();

  router.get("/me", async (req, res) => {
    res.json(users.toPrivate(await users.get(caller(req).userId)));
  });

  router.patch("/me", async (req, res) => {
    const body = parse(patchBody, req.body);
    res.json(users.toPrivate(await users.updateProfile(caller(req).userId, body)));
  });

  /** Permanent account deletion (Play Store requirement): sessions, friendships and rankings go too. */
  router.delete("/me", async (req, res) => {
    const { userId } = caller(req);
    const user = await users.get(userId);
    await auth.revokeAll(userId);
    await friends.removeAll(userId);
    await leaderboards.remove(userId, user.country);
    await users.delete(userId);
    res.status(204).end();
  });

  router.get("/players/:playerId", async (req, res) => {
    const player = await users.findByPlayerId(req.params.playerId ?? "");
    if (!player) throw notFound("player_not_found", "No player has that ID");
    const me = caller(req).userId;
    const [online, isFriend] = await Promise.all([presence.isOnline(player.id as string), friends.areFriends(me, player.id as string)]);
    res.json({ ...users.toPublic(player), online, isFriend, isMe: player.id === me });
  });

  return router;
}
