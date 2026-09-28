import { conflict, isDuplicateKeyError, unauthorized, unavailable } from "../../lib/errors.js";
import type { RateLimits } from "../../lib/rate-limit.js";
import type { UserDocument } from "../users/user.model.js";
import type { IdentityProvider, PrivateProfile, UsersService } from "../users/users.service.js";
import { normalizePhone, OTP_TTL_SECONDS, type OtpChannel } from "./providers/phone.js";
import type { ExternalProfile, IdentityVerifier } from "./providers/social.js";
import type { AccessTokens, RefreshSessions, SessionMeta } from "./tokens.js";

export interface AuthResult {
  accessToken: string;
  accessTokenExpiresAt: number;
  refreshToken: string;
  refreshTokenExpiresAt: number;
  isNewUser: boolean;
  user: PrivateProfile;
}

export interface RequestMeta extends SessionMeta {
  ip: string;
}

interface VerifiedIdentity {
  provider: IdentityProvider;
  key: string;
  profile: ExternalProfile;
  country?: string;
}

export interface AuthProviders {
  google: IdentityVerifier | null;
  facebook: IdentityVerifier | null;
  otp: OtpChannel;
}

export class AuthService {
  constructor(
    private readonly users: UsersService,
    private readonly accessTokens: AccessTokens,
    private readonly sessions: RefreshSessions,
    private readonly providers: AuthProviders,
    private readonly limits: RateLimits,
  ) {}

  /** A brand-new guest account. Its refresh token is its only credential until it links a provider. */
  async guest(meta: RequestMeta, country?: string): Promise<AuthResult> {
    await this.limits.accountCreatePerIp.consume(meta.ip);
    const user = await this.users.create({ isGuest: true, country });
    return this.issue(user, meta, true);
  }

  async google(idToken: string, meta: RequestMeta, linkToUserId?: string): Promise<AuthResult> {
    const verifier = this.providers.google ?? this.notConfigured("Google");
    const profile = await verifier.verify(idToken);
    return this.signIn({ provider: "google", key: profile.id, profile }, meta, linkToUserId);
  }

  async facebook(accessToken: string, meta: RequestMeta, linkToUserId?: string): Promise<AuthResult> {
    const verifier = this.providers.facebook ?? this.notConfigured("Facebook");
    const profile = await verifier.verify(accessToken);
    return this.signIn({ provider: "facebook", key: profile.id, profile }, meta, linkToUserId);
  }

  async startPhone(rawPhone: string, meta: RequestMeta, defaultCountry?: string) {
    const phone = normalizePhone(rawPhone, defaultCountry);
    await this.limits.otpSendPerIp.consume(meta.ip);
    await this.limits.otpSendPerPhone.consume(phone.e164);
    await this.providers.otp.send(phone.e164);
    return { phone: phone.e164, expiresInSeconds: OTP_TTL_SECONDS, codeLength: 6 };
  }

  async verifyPhone(rawPhone: string, code: string, meta: RequestMeta, linkToUserId?: string, defaultCountry?: string): Promise<AuthResult> {
    const phone = normalizePhone(rawPhone, defaultCountry);
    await this.limits.otpVerifyPerPhone.consume(phone.e164);
    if (!(await this.providers.otp.check(phone.e164, code))) {
      throw unauthorized("invalid_code", "That code is incorrect or has expired");
    }
    return this.signIn({ provider: "phone", key: phone.e164, profile: { id: phone.e164 }, country: phone.country }, meta, linkToUserId);
  }

  async refresh(refreshToken: string, meta: SessionMeta): Promise<AuthResult> {
    const rotated = await this.sessions.rotate(refreshToken, meta);
    const user = await this.users.get(rotated.userId);
    const access = await this.accessTokens.sign({ userId: rotated.userId, playerId: user.playerId });
    return {
      accessToken: access.token,
      accessTokenExpiresAt: access.expiresAt,
      refreshToken: rotated.refreshToken,
      refreshTokenExpiresAt: rotated.expiresAt,
      isNewUser: false,
      user: this.users.toPrivate(user),
    };
  }

  logout(refreshToken: string): Promise<void> {
    return this.sessions.revoke(refreshToken);
  }

  revokeAll(userId: string): Promise<void> {
    return this.sessions.revokeAllForUser(userId);
  }

  /**
   * Signs in with a verified identity, or — when [linkToUserId] is given — attaches it to that
   * account (typically upgrading a guest). An identity can only ever belong to one account.
   */
  private async signIn(identity: VerifiedIdentity, meta: RequestMeta, linkToUserId?: string): Promise<AuthResult> {
    const existing = await this.users.findByIdentity(identity.provider, identity.key);

    if (linkToUserId) {
      if (existing && existing.id !== linkToUserId) {
        throw conflict("identity_in_use", `This ${identity.provider} account is already linked to another player`);
      }
      const user = existing ?? (await this.attach(await this.users.get(linkToUserId), identity));
      return this.issue(user, meta, false);
    }

    if (existing) return this.issue(existing, meta, false);

    await this.limits.accountCreatePerIp.consume(meta.ip);
    const user = await this.users.create({
      isGuest: false,
      displayName: identity.profile.name,
      avatarUrl: identity.profile.avatarUrl,
      country: identity.country,
      identities: identityFields(identity),
    });
    return this.issue(user, meta, true);
  }

  private async attach(user: UserDocument, identity: VerifiedIdentity): Promise<UserDocument> {
    const fields = identityFields(identity);
    if (fields.google) user.identities.google = fields.google;
    if (fields.facebook) user.identities.facebook = fields.facebook;
    if (fields.phone) user.identities.phone = fields.phone;
    if (user.isGuest) {
      // A guest adopting a real identity keeps its progress and takes the provider's name/photo.
      if (identity.profile.name) user.displayName = identity.profile.name.slice(0, 24);
      if (identity.profile.avatarUrl) user.avatarUrl = identity.profile.avatarUrl;
      user.isGuest = false;
    }
    if (!user.country && identity.country) user.country = identity.country;
    try {
      return await user.save();
    } catch (error) {
      if (isDuplicateKeyError(error)) throw conflict("identity_in_use", `This ${identity.provider} account is already linked to another player`);
      throw error;
    }
  }

  private async issue(user: UserDocument, meta: SessionMeta, isNewUser: boolean): Promise<AuthResult> {
    const access = await this.accessTokens.sign({ userId: user.id as string, playerId: user.playerId });
    const refresh = await this.sessions.issue(user.id as string, meta);
    return {
      accessToken: access.token,
      accessTokenExpiresAt: access.expiresAt,
      refreshToken: refresh.refreshToken,
      refreshTokenExpiresAt: refresh.expiresAt,
      isNewUser,
      user: this.users.toPrivate(user),
    };
  }

  private notConfigured(name: string): never {
    throw unavailable("provider_not_configured", `${name} sign-in is not configured on this server`);
  }
}

function identityFields(identity: VerifiedIdentity) {
  switch (identity.provider) {
    case "google":
      return { google: { sub: identity.key, email: identity.profile.email } };
    case "facebook":
      return { facebook: { id: identity.key } };
    case "phone":
      return { phone: { e164: identity.key } };
  }
}
