import type { z } from "zod";
import { badRequest } from "./errors.js";

/** Parses untrusted input with [schema], turning validation failures into a 400 with field details. */
export function parse<T extends z.ZodType>(schema: T, input: unknown): z.infer<T> {
  const result = schema.safeParse(input);
  if (!result.success) {
    const details = result.error.issues.map((issue) => ({ path: issue.path.join("."), message: issue.message }));
    throw badRequest("invalid_request", "Request validation failed", details);
  }
  return result.data;
}
