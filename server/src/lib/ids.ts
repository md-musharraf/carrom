import { randomInt } from "node:crypto";

/**
 * Crockford base32: no I, L, O or U, so IDs survive being read aloud or typed from a screenshot.
 */
const CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
const ROOM_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

export const PLAYER_ID_LENGTH = 8;

/** A random 8-character player ID (32^8 ≈ 1.1 trillion combinations). */
export function generatePlayerId(): string {
  let id = "";
  for (let i = 0; i < PLAYER_ID_LENGTH; i++) id += CROCKFORD[randomInt(CROCKFORD.length)];
  return id;
}

/**
 * Normalises user-entered player IDs: strips separators and whitespace, upper-cases, and maps the
 * characters Crockford treats as look-alikes (I/L → 1, O → 0). Returns null if it can't be an ID.
 */
export function normalizePlayerId(input: string): string | null {
  const cleaned = input
    .toUpperCase()
    .replace(/[\s-]/g, "")
    .replace(/[IL]/g, "1")
    .replace(/O/g, "0");
  if (cleaned.length !== PLAYER_ID_LENGTH) return null;
  for (const char of cleaned) if (!CROCKFORD.includes(char)) return null;
  return cleaned;
}

/** Human-friendly presentation: "7K3P9QXA" → "7K3P-9QXA". */
export function formatPlayerId(id: string): string {
  return `${id.slice(0, 4)}-${id.slice(4)}`;
}

/** Short private-room code, avoiding 0/O and 1/I/L confusion. */
export function generateRoomCode(length = 6): string {
  let code = "";
  for (let i = 0; i < length; i++) code += ROOM_ALPHABET[randomInt(ROOM_ALPHABET.length)];
  return code;
}
