/**
 * An error that maps cleanly onto an HTTP response (and a Socket.IO acknowledgement).
 * `code` is a stable, machine-readable identifier clients can switch on.
 */
export class AppError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details?: unknown,
  ) {
    super(message);
    this.name = "AppError";
  }
}

export const badRequest = (code: string, message: string, details?: unknown) => new AppError(400, code, message, details);
export const unauthorized = (code = "unauthorized", message = "Authentication required") => new AppError(401, code, message);
export const forbidden = (code: string, message: string) => new AppError(403, code, message);
export const notFound = (code: string, message: string) => new AppError(404, code, message);
export const conflict = (code: string, message: string) => new AppError(409, code, message);
export const tooManyRequests = (retryAfterSeconds: number) =>
  new AppError(429, "rate_limited", "Too many requests, please slow down", { retryAfterSeconds });
export const unavailable = (code: string, message: string) => new AppError(503, code, message);

/** True for MongoDB duplicate-key errors (unique index violations). */
export function isDuplicateKeyError(error: unknown): boolean {
  return typeof error === "object" && error !== null && (error as { code?: number }).code === 11000;
}
