/** Injectable clock so daily resets and timers are testable. */
export interface Clock {
  now(): number;
}

export const systemClock: Clock = { now: () => Date.now() };

/** UTC calendar day, e.g. "2026-09-28". Daily rewards reset at 00:00 UTC. */
export function utcDay(epochMs: number): string {
  return new Date(epochMs).toISOString().slice(0, 10);
}

/** The UTC day before [day]. */
export function previousUtcDay(day: string): string {
  const date = new Date(`${day}T00:00:00.000Z`);
  date.setUTCDate(date.getUTCDate() - 1);
  return date.toISOString().slice(0, 10);
}

/** ISO-8601 week key, e.g. "2026-W39", for weekly leaderboards. */
export function isoWeek(epochMs: number): string {
  const date = new Date(epochMs);
  const target = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  const dayNumber = target.getUTCDay() || 7;
  target.setUTCDate(target.getUTCDate() + 4 - dayNumber);
  const yearStart = new Date(Date.UTC(target.getUTCFullYear(), 0, 1));
  const week = Math.ceil(((target.getTime() - yearStart.getTime()) / 86_400_000 + 1) / 7);
  return `${target.getUTCFullYear()}-W${String(week).padStart(2, "0")}`;
}
