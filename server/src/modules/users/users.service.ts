import { Types } from "mongoose";
import { isDuplicateKeyError, notFound } from "../../lib/errors.js";
import { formatPlayerId, generatePlayerId, normalizePlayerId } from "../../lib/ids.js";
import { UserModel, type User, type UserDocument } from "./user.model.js";

export type IdentityProvider = "google" | "facebook" | "phone";

export interface NewUser {
  displayName?: string;
  avatarUrl?: string;
  country?: string;
  isGuest: boolean;
  identities?: User["identities"];
}

export interface PublicProfile {
  playerId: string;
  displayId: string;
  displayName: string;
  avatarUrl?: string;
  country?: string;
  level: number;
  rating: number;
  stats: { played: number; won: number; winRate: number; pockets: number; queenCovers: number };
}

export interface PrivateProfile extends PublicProfile {
  id: string;
  isGuest: boolean;
  linked: { google: boolean; facebook: boolean; phone: string | null };
  wallet: User["wallet"];
  progress: User["progress"];
  inventory: User["inventory"];
  equipped: User["equipped"];
  daily: { streak: number; lastClaimDay: string | null; lastSpinDay: string | null };
}

const MAX_ID_ATTEMPTS = 6;

export class UsersService {
  /** Creates an account with a unique player ID, retrying on the (astronomically rare) collision. */
  async create(input: NewUser): Promise<UserDocument> {
    for (let attempt = 1; ; attempt++) {
      const playerId = generatePlayerId();
      try {
        return await UserModel.create({
          playerId,
          displayName: input.displayName?.trim() || `Player ${playerId.slice(0, 4)}`,
          avatarUrl: input.avatarUrl,
          country: input.country,
          isGuest: input.isGuest,
          identities: input.identities ?? {},
        });
      } catch (error) {
        const playerIdCollision = isDuplicateKeyError(error) && JSON.stringify((error as { keyPattern?: unknown }).keyPattern ?? {}).includes("playerId");
        if (!playerIdCollision || attempt >= MAX_ID_ATTEMPTS) throw error;
      }
    }
  }

  async get(userId: string): Promise<UserDocument> {
    const user = Types.ObjectId.isValid(userId) ? await UserModel.findById(userId) : null;
    if (!user) throw notFound("user_not_found", "Account not found");
    return user;
  }

  async findByPlayerId(input: string): Promise<UserDocument | null> {
    const playerId = normalizePlayerId(input);
    return playerId ? UserModel.findOne({ playerId }) : null;
  }

  async findManyByIds(ids: string[]): Promise<Map<string, UserDocument>> {
    const valid = ids.filter((id) => Types.ObjectId.isValid(id));
    const users = await UserModel.find({ _id: { $in: valid } });
    return new Map(users.map((user) => [user.id as string, user]));
  }

  async findByIdentity(provider: IdentityProvider, key: string): Promise<UserDocument | null> {
    const path = provider === "google" ? "identities.google.sub" : provider === "facebook" ? "identities.facebook.id" : "identities.phone.e164";
    return UserModel.findOne({ [path]: key });
  }

  async updateProfile(userId: string, changes: { displayName?: string; avatarUrl?: string | null; country?: string }): Promise<UserDocument> {
    const set: Record<string, unknown> = {};
    const unset: Record<string, 1> = {};
    if (changes.displayName !== undefined) set.displayName = changes.displayName.trim();
    if (changes.country !== undefined) set.country = changes.country.toUpperCase();
    if (changes.avatarUrl === null) unset.avatarUrl = 1;
    else if (changes.avatarUrl !== undefined) set.avatarUrl = changes.avatarUrl;
    const user = await UserModel.findByIdAndUpdate(userId, { $set: set, $unset: unset }, { returnDocument: "after", runValidators: true });
    if (!user) throw notFound("user_not_found", "Account not found");
    return user;
  }

  async delete(userId: string): Promise<void> {
    await UserModel.deleteOne({ _id: userId });
  }

  toPublic(user: User): PublicProfile {
    const { played, won, pockets, queenCovers } = user.stats;
    return {
      playerId: user.playerId,
      displayId: formatPlayerId(user.playerId),
      displayName: user.displayName,
      avatarUrl: user.avatarUrl,
      country: user.country,
      level: user.progress.level,
      rating: Math.round(user.rating.r),
      stats: { played, won, winRate: played > 0 ? Math.round((won / played) * 100) : 0, pockets, queenCovers },
    };
  }

  toPrivate(user: User): PrivateProfile {
    const phone = user.identities.phone?.e164;
    return {
      ...this.toPublic(user),
      id: user._id.toString(),
      isGuest: user.isGuest,
      linked: {
        google: Boolean(user.identities.google?.sub),
        facebook: Boolean(user.identities.facebook?.id),
        // Only the last digits are ever sent back.
        phone: phone ? `•••• ${phone.slice(-4)}` : null,
      },
      wallet: { coins: user.wallet.coins, gems: user.wallet.gems },
      progress: { level: user.progress.level, xp: user.progress.xp, xpToNext: user.progress.xpToNext },
      inventory: { strikers: [...user.inventory.strikers], boards: [...user.inventory.boards] },
      equipped: { striker: user.equipped.striker, board: user.equipped.board },
      daily: { streak: user.daily.streak, lastClaimDay: user.daily.lastClaimDay ?? null, lastSpinDay: user.daily.lastSpinDay ?? null },
    };
  }
}
