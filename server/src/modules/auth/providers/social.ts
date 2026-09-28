import { createHmac } from "node:crypto";
import { OAuth2Client } from "google-auth-library";
import { unauthorized } from "../../../lib/errors.js";

/** The subset of an external account we keep. */
export interface ExternalProfile {
  id: string;
  name?: string;
  email?: string;
  avatarUrl?: string;
}

export interface IdentityVerifier {
  verify(token: string): Promise<ExternalProfile>;
}

/**
 * Verifies Google ID tokens (from Android Credential Manager / Sign in with Google): signature,
 * issuer, expiry and that the audience is one of our OAuth client IDs.
 */
export class GoogleIdTokenVerifier implements IdentityVerifier {
  private readonly client = new OAuth2Client();

  constructor(private readonly clientIds: string[]) {}

  async verify(idToken: string): Promise<ExternalProfile> {
    try {
      const ticket = await this.client.verifyIdToken({ idToken, audience: this.clientIds });
      const payload = ticket.getPayload();
      if (!payload?.sub) throw new Error("no subject");
      return {
        id: payload.sub,
        name: payload.name,
        email: payload.email_verified ? payload.email : undefined,
        avatarUrl: payload.picture,
      };
    } catch {
      throw unauthorized("invalid_google_token", "Google sign-in could not be verified");
    }
  }
}

type FetchLike = (input: string, init?: RequestInit) => Promise<Response>;

/**
 * Verifies Facebook user access tokens server-side: `debug_token` (with our app token) proves the
 * token is valid and was issued to *our* app, then `/me` (with appsecret_proof) reads the profile.
 */
export class FacebookGraphVerifier implements IdentityVerifier {
  constructor(
    private readonly appId: string,
    private readonly appSecret: string,
    private readonly graphVersion: string,
    private readonly fetchImpl: FetchLike = fetch,
  ) {}

  async verify(accessToken: string): Promise<ExternalProfile> {
    const base = `https://graph.facebook.com/${this.graphVersion}`;
    const appToken = `${this.appId}|${this.appSecret}`;
    const debug = await this.getJson(
      `${base}/debug_token?input_token=${encodeURIComponent(accessToken)}&access_token=${encodeURIComponent(appToken)}`,
    );
    const data = (debug as { data?: { is_valid?: boolean; app_id?: string; user_id?: string } }).data;
    if (!data?.is_valid || data.app_id !== this.appId || !data.user_id) {
      throw unauthorized("invalid_facebook_token", "Facebook sign-in could not be verified");
    }

    const proof = createHmac("sha256", this.appSecret).update(accessToken).digest("hex");
    const me = (await this.getJson(
      `${base}/me?fields=id,name,picture.type(large)&access_token=${encodeURIComponent(accessToken)}&appsecret_proof=${proof}`,
    )) as { id?: string; name?: string; picture?: { data?: { url?: string; is_silhouette?: boolean } } };
    if (me.id !== data.user_id) throw unauthorized("invalid_facebook_token", "Facebook sign-in could not be verified");

    const picture = me.picture?.data;
    return { id: me.id, name: me.name, avatarUrl: picture && !picture.is_silhouette ? picture.url : undefined };
  }

  private async getJson(url: string): Promise<unknown> {
    const response = await this.fetchImpl(url, { signal: AbortSignal.timeout(5_000) });
    if (!response.ok) throw unauthorized("invalid_facebook_token", "Facebook sign-in could not be verified");
    return response.json();
  }
}
