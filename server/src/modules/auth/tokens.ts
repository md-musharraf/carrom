import { createHash, randomBytes } from "node:crypto";
import { SignJWT, jwtVerify } from "jose";
import { Schema, model, type Types } from "mongoose";
import type { AppConfig } from "../../config/env.js";
import { unauthorized } from "../../lib/errors.js";
import type { Clock } from "../../lib/time.js";

export interface AccessClaims {
  userId: string;
  playerId: string;
}

/** Short-lived, stateless access tokens (HS256 JWT). */
export class AccessTokens {
  private readonly key: Uint8Array;

  constructor(
    private readonly config: AppConfig["jwt"],
    private readonly clock: Clock,
  ) {
    this.key = new TextEncoder().encode(config.secret);
  }

  async sign(claims: AccessClaims): Promise<{ token: string; expiresAt: number }> {
    const issuedAt = Math.floor(this.clock.now() / 1000);
    const expiresAt = issuedAt + this.config.accessTtlSeconds;
    const token = await new SignJWT({ pid: claims.playerId })
      .setProtectedHeader({ alg: "HS256", typ: "JWT" })
      .setSubject(claims.userId)
      .setIssuer(this.config.issuer)
      .setIssuedAt(issuedAt)
      .setExpirationTime(expiresAt)
      .sign(this.key);
    return { token, expiresAt: expiresAt * 1000 };
  }

  async verify(token: string): Promise<AccessClaims> {
    try {
      const { payload } = await jwtVerify(token, this.key, {
        issuer: this.config.issuer,
        algorithms: ["HS256"],
        currentDate: new Date(this.clock.now()),
      });
      if (typeof payload.sub !== "string" || typeof payload.pid !== "string") throw new Error("missing claims");
      return { userId: payload.sub, playerId: payload.pid };
    } catch {
      throw unauthorized("invalid_token", "Access token is invalid or expired");
    }
  }
}

interface Session {
  userId: Types.ObjectId;
  familyId: string;
  tokenHash: string;
  expiresAt: Date;
  rotatedAt?: Date;
  revokedAt?: Date;
  userAgent?: string;
  createdAt: Date;
}

const sessionSchema = new Schema<Session>(
  {
    userId: { type: Schema.Types.ObjectId, required: true, index: true },
    familyId: { type: String, required: true, index: true },
    tokenHash: { type: String, required: true, unique: true },
    expiresAt: { type: Date, required: true, expires: 0 },
    rotatedAt: Date,
    revokedAt: Date,
    userAgent: String,
  },
  { timestamps: { createdAt: true, updatedAt: false } },
);

export const SessionModel = model<Session>("Session", sessionSchema);

export interface SessionMeta {
  userAgent?: string;
}

const hash = (token: string) => createHash("sha256").update(token).digest("hex");

/**
 * Opaque refresh tokens stored only as SHA-256 hashes. Every refresh rotates the token; presenting
 * an already-rotated token is treated as theft and revokes the whole session family.
 */
export class RefreshSessions {
  constructor(
    private readonly config: AppConfig["jwt"],
    private readonly clock: Clock,
  ) {}

  async issue(userId: string, meta: SessionMeta, familyId: string = randomBytes(12).toString("hex")) {
    const refreshToken = `rt_${randomBytes(32).toString("base64url")}`;
    const expiresAt = new Date(this.clock.now() + this.config.refreshTtlDays * 86_400_000);
    await SessionModel.create({ userId, familyId, tokenHash: hash(refreshToken), expiresAt, userAgent: meta.userAgent });
    return { refreshToken, expiresAt: expiresAt.getTime() };
  }

  /** Exchanges [refreshToken] for a new one; returns the owning user id. */
  async rotate(refreshToken: string, meta: SessionMeta) {
    const now = new Date(this.clock.now());
    const tokenHash = hash(refreshToken);
    const current = await SessionModel.findOneAndUpdate(
      { tokenHash, rotatedAt: { $exists: false }, revokedAt: { $exists: false }, expiresAt: { $gt: now } },
      { $set: { rotatedAt: now } },
    );
    if (!current) {
      const known = await SessionModel.findOne({ tokenHash });
      if (known?.rotatedAt && !known.revokedAt) {
        await this.revokeFamily(known.familyId);
        throw unauthorized("refresh_token_reused", "Session was used elsewhere; please sign in again");
      }
      throw unauthorized("invalid_refresh_token", "Session expired; please sign in again");
    }
    const next = await this.issue(current.userId.toString(), meta, current.familyId);
    return { userId: current.userId.toString(), ...next };
  }

  async revoke(refreshToken: string): Promise<void> {
    const session = await SessionModel.findOne({ tokenHash: hash(refreshToken) });
    if (session) await this.revokeFamily(session.familyId);
  }

  async revokeAllForUser(userId: string): Promise<void> {
    await SessionModel.updateMany({ userId, revokedAt: { $exists: false } }, { $set: { revokedAt: new Date(this.clock.now()) } });
  }

  private async revokeFamily(familyId: string): Promise<void> {
    await SessionModel.updateMany({ familyId, revokedAt: { $exists: false } }, { $set: { revokedAt: new Date(this.clock.now()) } });
  }
}
