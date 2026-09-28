import { Router } from "express";
import { z } from "zod";
import { parse } from "../../lib/validate.js";
import { caller } from "../auth/auth.middleware.js";
import type { UsersService } from "../users/users.service.js";
import { ARENAS } from "./catalog.js";
import type { EconomyService } from "./economy.service.js";

const itemBody = z.object({ itemId: z.string().min(1).max(40) });

/** /v1/rewards, /v1/shop, /v1/arenas and /v1/wallet. Mounted behind requireAuth. */
export function economyRoutes(economy: EconomyService, users: UsersService): Router {
  const router = Router();

  router.get("/rewards/daily", async (req, res) => {
    res.json(economy.dailyStatus(await users.get(caller(req).userId)));
  });
  router.post("/rewards/daily/claim", async (req, res) => {
    res.json(await economy.claimDaily(caller(req).userId));
  });
  router.post("/rewards/spin", async (req, res) => {
    res.json(await economy.spin(caller(req).userId));
  });
  router.post("/rewards/offline-win", async (req, res) => {
    res.json(await economy.rewardOfflineWin(caller(req).userId));
  });

  router.get("/shop/catalog", (_req, res) => {
    res.json({ items: economy.catalog() });
  });
  router.post("/shop/purchase", async (req, res) => {
    const { itemId } = parse(itemBody, req.body);
    res.json(await economy.purchase(caller(req).userId, itemId));
  });
  router.post("/shop/equip", async (req, res) => {
    const { itemId } = parse(itemBody, req.body);
    res.json(await economy.equip(caller(req).userId, itemId));
  });

  router.get("/arenas", async (req, res) => {
    const me = await users.get(caller(req).userId);
    res.json({ arenas: ARENAS.map((a) => ({ ...a, unlocked: me.progress.level >= a.minLevel, affordable: me.wallet.coins >= a.entryFee })) });
  });

  router.get("/wallet/history", async (req, res) => {
    res.json({ entries: await economy.history(caller(req).userId) });
  });

  return router;
}
