import { randomInt } from "node:crypto";
import { Schema, model, type Types } from "mongoose";
import { badRequest, conflict, notFound } from "../../lib/errors.js";
import { previousUtcDay, utcDay, type Clock } from "../../lib/time.js";
import { UserModel, type UserDocument } from "../users/user.model.js";
import { catalogItem, CATALOG } from "./catalog.js";

export type LedgerKind =
  | "daily_reward"
  | "spin"
  | "purchase"
  | "entry_fee"
  | "match_payout"
  | "refund"
  | "offline_win";

interface LedgerEntry {
  userId: Types.ObjectId;
  kind: LedgerKind;
  coins: number;
  gems: number;
  balance: { coins: number; gems: number };
  ref?: string;
  createdAt: Date;
}

const ledgerSchema = new Schema<LedgerEntry>(
  {
    userId: { type: Schema.Types.ObjectId, required: true, index: true },
    kind: { type: String, required: true },
    coins: { type: Number, default: 0 },
    gems: { type: Number, default: 0 },
    balance: { coins: Number, gems: Number },
    ref: String,
  },
  { timestamps: { createdAt: true, updatedAt: false } },
);

/** Append-only audit trail of every balance change. */
export const LedgerModel = model<LedgerEntry>("LedgerEntry", ledgerSchema);

export interface Balance {
  coins: number;
  gems: number;
}

/** 7-day login streak; day 7 also pays gems, then the cycle repeats. */
export const DAILY_REWARDS: ReadonlyArray<Balance> = [
  { coins: 100, gems: 0 },
  { coins: 150, gems: 0 },
  { coins: 200, gems: 0 },
  { coins: 300, gems: 0 },
  { coins: 400, gems: 1 },
  { coins: 500, gems: 0 },
  { coins: 1000, gems: 5 },
];

/** Wheel segments in the same order as the client's wheel, with draw weights favouring small prizes. */
export const SPIN_PRIZES: ReadonlyArray<{ coins: number; weight: number }> = [
  { coins: 100, weight: 26 },
  { coins: 250, weight: 18 },
  { coins: 500, weight: 10 },
  { coins: 1000, weight: 4 },
  { coins: 150, weight: 22 },
  { coins: 300, weight: 14 },
  { coins: 750, weight: 5 },
  { coins: 2000, weight: 1 },
];

/** findOneAndUpdate option: return the document as it is after the update. */
const AFTER = { returnDocument: "after" } as const;

export const OFFLINE_WIN_COINS = 50;
export const OFFLINE_WIN_XP = 20;
export const OFFLINE_WINS_PER_DAY = 10;

/**
 * Coins, gems, XP and every way to earn or spend them. All balance changes are single conditional
 * MongoDB updates, so concurrent requests can never double-claim or overspend.
 */
export class EconomyService {
  constructor(private readonly clock: Clock) {}

  async credit(userId: string, amount: Balance, kind: LedgerKind, ref?: string): Promise<Balance> {
    const user = await UserModel.findByIdAndUpdate(
      userId,
      { $inc: { "wallet.coins": amount.coins, "wallet.gems": amount.gems } },
      AFTER,
    );
    if (!user) throw notFound("user_not_found", "Account not found");
    return this.record(user, amount, kind, ref);
  }

  /** Takes [amount] only if the balance covers it. */
  async debit(userId: string, amount: Balance, kind: LedgerKind, ref?: string): Promise<Balance> {
    const user = await UserModel.findOneAndUpdate(
      { _id: userId, "wallet.coins": { $gte: amount.coins }, "wallet.gems": { $gte: amount.gems } },
      { $inc: { "wallet.coins": -amount.coins, "wallet.gems": -amount.gems } },
      AFTER,
    );
    if (!user) throw conflict("insufficient_funds", "Not enough coins");
    return this.record(user, { coins: -amount.coins, gems: -amount.gems }, kind, ref);
  }

  dailyStatus(user: UserDocument) {
    const today = utcDay(this.clock.now());
    const claimedToday = user.daily.lastClaimDay === today;
    const continuing = user.daily.lastClaimDay === previousUtcDay(today) || claimedToday;
    const streak = continuing ? user.daily.streak : 0;
    const nextDay = claimedToday ? streak : streak + 1;
    return {
      today,
      streak,
      claimedToday,
      nextReward: DAILY_REWARDS[(nextDay - 1) % DAILY_REWARDS.length]!,
      rewards: DAILY_REWARDS,
      spinAvailable: user.daily.lastSpinDay !== today,
    };
  }

  async claimDaily(userId: string) {
    const today = utcDay(this.clock.now());
    const user = await UserModel.findById(userId);
    if (!user) throw notFound("user_not_found", "Account not found");
    const status = this.dailyStatus(user);
    if (status.claimedToday) throw conflict("already_claimed", "Today's reward is already claimed");

    const streak = status.streak + 1;
    const reward = DAILY_REWARDS[(streak - 1) % DAILY_REWARDS.length]!;
    const updated = await UserModel.findOneAndUpdate(
      { _id: userId, "daily.lastClaimDay": { $ne: today } },
      {
        $set: { "daily.lastClaimDay": today, "daily.streak": streak },
        $inc: { "wallet.coins": reward.coins, "wallet.gems": reward.gems },
      },
      AFTER,
    );
    if (!updated) throw conflict("already_claimed", "Today's reward is already claimed");
    const balance = await this.record(updated, reward, "daily_reward", today);
    return { streak, reward, balance };
  }

  /** The free daily spin. The server draws the prize; the client just animates to [index]. */
  async spin(userId: string) {
    const today = utcDay(this.clock.now());
    const index = drawWeighted(SPIN_PRIZES.map((p) => p.weight));
    const prize = SPIN_PRIZES[index]!;
    const updated = await UserModel.findOneAndUpdate(
      { _id: userId, "daily.lastSpinDay": { $ne: today } },
      { $set: { "daily.lastSpinDay": today }, $inc: { "wallet.coins": prize.coins } },
      AFTER,
    );
    if (!updated) throw conflict("already_spun", "Come back tomorrow for another free spin");
    const balance = await this.record(updated, { coins: prize.coins, gems: 0 }, "spin", today);
    return { index, coins: prize.coins, balance };
  }

  /** Small, capped reward for offline wins, so solo play still progresses a signed-in account. */
  async rewardOfflineWin(userId: string) {
    const today = utcDay(this.clock.now());
    const reward = { coins: OFFLINE_WIN_COINS, gems: 0 };
    const sameDay = await UserModel.findOneAndUpdate(
      { _id: userId, "daily.offlineRewardDay": today, "daily.offlineRewardsToday": { $lt: OFFLINE_WINS_PER_DAY } },
      { $inc: { "daily.offlineRewardsToday": 1, "wallet.coins": reward.coins } },
      AFTER,
    );
    const updated =
      sameDay ??
      (await UserModel.findOneAndUpdate(
        { _id: userId, "daily.offlineRewardDay": { $ne: today } },
        { $set: { "daily.offlineRewardDay": today, "daily.offlineRewardsToday": 1 }, $inc: { "wallet.coins": reward.coins } },
        AFTER,
      ));
    if (!updated) throw conflict("daily_cap_reached", "Offline rewards are capped for today — try an online match");
    const balance = await this.record(updated, reward, "offline_win", today);
    const progress = await this.addXp(userId, OFFLINE_WIN_XP);
    return { coins: reward.coins, xp: OFFLINE_WIN_XP, balance, progress, remainingToday: OFFLINE_WINS_PER_DAY - updated.daily.offlineRewardsToday };
  }

  catalog() {
    return CATALOG;
  }

  async purchase(userId: string, itemId: string) {
    const item = catalogItem(itemId);
    if (!item) throw notFound("item_not_found", "No such item");
    const field = item.kind === "striker" ? "inventory.strikers" : "inventory.boards";
    const updated = await UserModel.findOneAndUpdate(
      { _id: userId, "wallet.coins": { $gte: item.price }, [field]: { $ne: item.id } },
      { $inc: { "wallet.coins": -item.price }, $addToSet: { [field]: item.id } },
      AFTER,
    );
    if (!updated) {
      const user = await UserModel.findById(userId);
      const owned = user && (item.kind === "striker" ? user.inventory.strikers : user.inventory.boards).includes(item.id);
      throw owned ? conflict("already_owned", "You already own this") : conflict("insufficient_funds", "Not enough coins");
    }
    const balance = await this.record(updated, { coins: -item.price, gems: 0 }, "purchase", item.id);
    return { item, balance, inventory: updated.inventory };
  }

  async equip(userId: string, itemId: string) {
    const item = catalogItem(itemId);
    if (!item) throw notFound("item_not_found", "No such item");
    const field = item.kind === "striker" ? "inventory.strikers" : "inventory.boards";
    const slot = item.kind === "striker" ? "equipped.striker" : "equipped.board";
    const updated = await UserModel.findOneAndUpdate({ _id: userId, [field]: item.id }, { $set: { [slot]: item.id } }, AFTER);
    if (!updated) throw badRequest("not_owned", "Buy this item before equipping it");
    return { equipped: updated.equipped };
  }

  /** Adds XP and rolls levels over with the same curve as the client (next level needs 30% more). */
  async addXp(userId: string, xp: number) {
    for (let attempt = 0; attempt < 5; attempt++) {
      const user = await UserModel.findById(userId, { progress: 1 });
      if (!user) throw notFound("user_not_found", "Account not found");
      const next = { ...user.progress };
      next.xp += xp;
      while (next.xp >= next.xpToNext) {
        next.xp -= next.xpToNext;
        next.level += 1;
        next.xpToNext = Math.floor(next.xpToNext * 1.3);
      }
      const result = await UserModel.updateOne(
        { _id: userId, "progress.xp": user.progress.xp, "progress.level": user.progress.level },
        { $set: { progress: next } },
      );
      if (result.modifiedCount === 1) return { ...next, leveledUp: next.level > user.progress.level };
    }
    throw conflict("busy", "Please retry");
  }

  async history(userId: string, limit = 50) {
    return LedgerModel.find({ userId }).sort({ createdAt: -1 }).limit(limit).lean();
  }

  private async record(user: UserDocument, delta: Balance, kind: LedgerKind, ref?: string): Promise<Balance> {
    const balance = { coins: user.wallet.coins, gems: user.wallet.gems };
    await LedgerModel.create({ userId: user._id, kind, coins: delta.coins, gems: delta.gems, balance, ref });
    return balance;
  }
}

/** Index drawn with probability proportional to its weight, using a CSPRNG. */
export function drawWeighted(weights: number[]): number {
  const total = weights.reduce((sum, w) => sum + w, 0);
  let roll = randomInt(total);
  for (let i = 0; i < weights.length; i++) {
    roll -= weights[i]!;
    if (roll < 0) return i;
  }
  return weights.length - 1;
}
