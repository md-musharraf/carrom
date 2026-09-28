import type { NextFunction, Request, Response } from "express";
import { unauthorized } from "../../lib/errors.js";
import type { AccessClaims, AccessTokens } from "./tokens.js";

declare module "express-serve-static-core" {
  interface Request {
    auth?: AccessClaims;
  }
}

export function bearerToken(header: string | undefined): string | null {
  if (!header?.startsWith("Bearer ")) return null;
  const token = header.slice(7).trim();
  return token.length > 0 ? token : null;
}

/** Rejects the request unless it carries a valid access token; exposes the caller as `req.auth`. */
export function requireAuth(tokens: AccessTokens) {
  return async (req: Request, _res: Response, next: NextFunction) => {
    const token = bearerToken(req.get("authorization"));
    if (!token) throw unauthorized();
    req.auth = await tokens.verify(token);
    next();
  };
}

/** Like [requireAuth] but lets anonymous requests through. */
export function optionalAuth(tokens: AccessTokens) {
  return async (req: Request, _res: Response, next: NextFunction) => {
    const token = bearerToken(req.get("authorization"));
    if (token) req.auth = await tokens.verify(token);
    next();
  };
}

/** The authenticated caller; only valid behind [requireAuth]. */
export function caller(req: Request): AccessClaims {
  if (!req.auth) throw unauthorized();
  return req.auth;
}
