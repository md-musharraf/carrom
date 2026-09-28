import { Schema, model, type HydratedDocument, type Types } from "mongoose";

export interface GlickoRating {
  /** Glicko-2 rating on the familiar 1500-centred scale. */
  r: number;
  /** Rating deviation (uncertainty). */
  rd: number;
  /** Volatility. */
  vol: number;
}

export interface User {
  _id: Types.ObjectId;
  playerId: string;
  displayName: string;
  avatarUrl?: string;
  country?: string;
  isGuest: boolean;
  identities: {
    google?: { sub: string; email?: string };
    facebook?: { id: string };
    phone?: { e164: string };
  };
  wallet: { coins: number; gems: number };
  progress: { level: number; xp: number; xpToNext: number };
  rating: GlickoRating;
  stats: { played: number; won: number; pockets: number; queenCovers: number };
  inventory: { strikers: string[]; boards: string[] };
  equipped: { striker: string; board: string };
  daily: {
    streak: number;
    lastClaimDay?: string;
    lastSpinDay?: string;
    offlineRewardDay?: string;
    offlineRewardsToday: number;
  };
  createdAt: Date;
  updatedAt: Date;
}

export type UserDocument = HydratedDocument<User>;

export const STARTING_COINS = 1200;
export const STARTING_GEMS = 25;
export const DEFAULT_STRIKER = "classic_ivory";
export const DEFAULT_BOARD = "classic_teak";

const userSchema = new Schema<User>(
  {
    playerId: { type: String, required: true, unique: true },
    displayName: { type: String, required: true, trim: true, maxlength: 24 },
    avatarUrl: { type: String },
    country: { type: String, uppercase: true, minlength: 2, maxlength: 2 },
    isGuest: { type: Boolean, required: true, default: true },
    identities: {
      google: { sub: String, email: String },
      facebook: { id: String },
      phone: { e164: String },
    },
    wallet: {
      coins: { type: Number, required: true, default: STARTING_COINS, min: 0 },
      gems: { type: Number, required: true, default: STARTING_GEMS, min: 0 },
    },
    progress: {
      level: { type: Number, default: 1 },
      xp: { type: Number, default: 0 },
      xpToNext: { type: Number, default: 100 },
    },
    rating: {
      r: { type: Number, default: 1500 },
      rd: { type: Number, default: 350 },
      vol: { type: Number, default: 0.06 },
    },
    stats: {
      played: { type: Number, default: 0 },
      won: { type: Number, default: 0 },
      pockets: { type: Number, default: 0 },
      queenCovers: { type: Number, default: 0 },
    },
    inventory: {
      strikers: { type: [String], default: [DEFAULT_STRIKER] },
      boards: { type: [String], default: [DEFAULT_BOARD] },
    },
    equipped: {
      striker: { type: String, default: DEFAULT_STRIKER },
      board: { type: String, default: DEFAULT_BOARD },
    },
    daily: {
      streak: { type: Number, default: 0 },
      lastClaimDay: String,
      lastSpinDay: String,
      offlineRewardDay: String,
      offlineRewardsToday: { type: Number, default: 0 },
    },
  },
  { timestamps: true },
);

// One account per external identity. Partial indexes ignore accounts without that identity.
userSchema.index({ "identities.google.sub": 1 }, { unique: true, partialFilterExpression: { "identities.google.sub": { $type: "string" } } });
userSchema.index({ "identities.facebook.id": 1 }, { unique: true, partialFilterExpression: { "identities.facebook.id": { $type: "string" } } });
userSchema.index({ "identities.phone.e164": 1 }, { unique: true, partialFilterExpression: { "identities.phone.e164": { $type: "string" } } });

export const UserModel = model<User>("User", userSchema);
