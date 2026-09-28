import { createHmac, randomInt, timingSafeEqual } from "node:crypto";
import { parsePhoneNumberFromString, type CountryCode } from "libphonenumber-js";
import { badRequest, tooManyRequests, unavailable } from "../../../lib/errors.js";
import type { Logger } from "../../../lib/logger.js";
import type { Redis } from "../../../infra/redis.js";

export const OTP_LENGTH = 6;
export const OTP_TTL_SECONDS = 5 * 60;
export const OTP_MAX_ATTEMPTS = 5;

export interface NormalizedPhone {
  e164: string;
  country?: string;
}

/** Validates and normalises a phone number to E.164, using [defaultCountry] for local formats. */
export function normalizePhone(raw: string, defaultCountry?: string): NormalizedPhone {
  const parsed = parsePhoneNumberFromString(raw, defaultCountry?.toUpperCase() as CountryCode | undefined);
  if (!parsed?.isValid()) throw badRequest("invalid_phone", "Enter a valid mobile number including the country code");
  return { e164: parsed.number, country: parsed.country };
}

/** Delivers and checks one-time codes for a phone number. */
export interface OtpChannel {
  send(e164: string): Promise<void>;
  /** True when [code] is correct; false when wrong or expired. */
  check(e164: string, code: string): Promise<boolean>;
}

export interface SmsSender {
  send(to: string, message: string): Promise<void>;
}

/** Development sender: writes the code to the server log instead of sending an SMS. */
export class ConsoleSmsSender implements SmsSender {
  constructor(private readonly logger: Logger) {}

  async send(to: string, message: string): Promise<void> {
    this.logger.warn({ to }, `[DEV SMS] ${message}`);
  }
}

/** Test sender that remembers the last message per number. */
export class MemorySmsSender implements SmsSender {
  readonly messages = new Map<string, string>();

  async send(to: string, message: string): Promise<void> {
    this.messages.set(to, message);
  }

  lastCode(to: string): string | undefined {
    return this.messages.get(to)?.match(/\d{6}/)?.[0];
  }
}

/**
 * Self-hosted OTPs: codes come from a CSPRNG, only an HMAC of the code is stored (in Redis, with
 * a 5 minute TTL) and a number is locked after five wrong guesses.
 */
export class LocalOtpChannel implements OtpChannel {
  constructor(
    private readonly redis: Redis,
    private readonly sender: SmsSender,
    private readonly pepper: string,
  ) {}

  async send(e164: string): Promise<void> {
    const code = String(randomInt(0, 10 ** OTP_LENGTH)).padStart(OTP_LENGTH, "0");
    const key = this.key(e164);
    await this.redis.multi().del(key).hset(key, { hash: this.digest(e164, code), attempts: 0 }).expire(key, OTP_TTL_SECONDS).exec();
    await this.sender.send(e164, `${code} is your Royal Carrom code. It expires in 5 minutes. Never share it.`);
  }

  async check(e164: string, code: string): Promise<boolean> {
    const key = this.key(e164);
    const stored = await this.redis.hgetall(key);
    if (!stored.hash) return false;
    if (Number(stored.attempts ?? 0) >= OTP_MAX_ATTEMPTS) throw tooManyRequests(await this.redis.ttl(key));

    const expected = Buffer.from(stored.hash, "hex");
    const actual = Buffer.from(this.digest(e164, code), "hex");
    if (expected.length === actual.length && timingSafeEqual(expected, actual)) {
      await this.redis.del(key);
      return true;
    }
    await this.redis.hincrby(key, "attempts", 1);
    return false;
  }

  private key(e164: string) {
    return `otp:${e164}`;
  }

  private digest(e164: string, code: string) {
    return createHmac("sha256", this.pepper).update(`${e164}:${code}`).digest("hex");
  }
}

type FetchLike = (input: string, init?: RequestInit) => Promise<Response>;

/** Twilio Verify: Twilio generates, delivers and rate-limits the codes. */
export class TwilioVerifyChannel implements OtpChannel {
  constructor(
    private readonly accountSid: string,
    private readonly authToken: string,
    private readonly serviceSid: string,
    private readonly fetchImpl: FetchLike = fetch,
  ) {}

  async send(e164: string): Promise<void> {
    const response = await this.post("Verifications", { To: e164, Channel: "sms" });
    if (!response.ok) throw unavailable("sms_unavailable", "We couldn't send a code right now, please try again");
  }

  async check(e164: string, code: string): Promise<boolean> {
    const response = await this.post("VerificationCheck", { To: e164, Code: code });
    if (response.status === 404) return false; // Expired or already used.
    if (response.status === 429) throw tooManyRequests(600);
    if (!response.ok) throw unavailable("sms_unavailable", "We couldn't check the code right now, please try again");
    const body = (await response.json()) as { status?: string };
    return body.status === "approved";
  }

  private post(path: string, form: Record<string, string>) {
    return this.fetchImpl(`https://verify.twilio.com/v2/Services/${this.serviceSid}/${path}`, {
      method: "POST",
      headers: {
        Authorization: `Basic ${Buffer.from(`${this.accountSid}:${this.authToken}`).toString("base64")}`,
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: new URLSearchParams(form),
      signal: AbortSignal.timeout(8_000),
    });
  }
}
