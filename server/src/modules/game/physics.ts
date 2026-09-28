/**
 * Server port of the Android client's CarromPhysicsEngine (same constants, same sub-stepping).
 * The server simulates every online shot to produce the authoritative end state; clients animate
 * the same input locally and settle onto this result.
 */

export type PieceType = "STRIKER" | "WHITE" | "BLACK" | "QUEEN";

export interface Piece {
  id: string;
  type: PieceType;
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  mass: number;
  pocketed: boolean;
  pocketId: number;
}

export const BOARD_SIZE = 800;
export const PLAYABLE_MIN = 50;
export const PLAYABLE_MAX = 750;
export const CENTER = 400;
export const STRIKER_RADIUS = 22;
export const PUCK_RADIUS = 15.5;
export const POCKET_RADIUS = 32;
export const POCKET_SUCTION_RADIUS = 40;
export const STRIKER_MASS = 3;
export const PUCK_MASS = 1;

export const BASELINE_WIDTH = 420;
export const BASELINE_START_X = (BOARD_SIZE - BASELINE_WIDTH) / 2;
export const BASELINE_END_X = BASELINE_START_X + BASELINE_WIDTH;
export const BASELINE_BOTTOM_Y = BOARD_SIZE - 140;
export const BASELINE_TOP_Y = 140;
export const BASELINE_CIRCLE_RADIUS = 16;
export const MIN_BASELINE_FRACTION = 0.06;
export const MAX_BASELINE_FRACTION = 0.94;
export const MIN_POWER = 20;
export const MAX_POWER = 100;

export const POCKETS = [
  { id: 0, x: PLAYABLE_MIN + 18, y: PLAYABLE_MIN + 18 },
  { id: 1, x: PLAYABLE_MAX - 18, y: PLAYABLE_MIN + 18 },
  { id: 2, x: PLAYABLE_MAX - 18, y: PLAYABLE_MAX - 18 },
  { id: 3, x: PLAYABLE_MIN + 18, y: PLAYABLE_MAX - 18 },
] as const;

const SUB_STEPS = 16;
const MAX_SUB_STEPS = 64;
const SUB_STEP_EPSILON = 1e-3;
export const MAX_STRIKE_SPEED = 34;
const RESTITUTION_PUCK_PUCK = 0.95;
const RESTITUTION_STRIKER_PUCK = 0.93;
const RESTITUTION_WALL = 0.89;
const WALL_TANGENTIAL_FRICTION = 0.97;
const CONTACT_TANGENTIAL_FRICTION = 0.05;
const FRICTION_BASE = 0.9935;
const LINEAR_DRAG = 0.026;
const VELOCITY_EPSILON = 0.06;
const POCKET_PULL = 0.65;
const STRIKER_DROP_MARGIN = 4.5;
const PUCK_DROP_MARGIN = 1.5;

function piece(id: string, type: PieceType, x: number, y: number): Piece {
  const striker = type === "STRIKER";
  return {
    id,
    type,
    x,
    y,
    vx: 0,
    vy: 0,
    radius: striker ? STRIKER_RADIUS : PUCK_RADIUS,
    mass: striker ? STRIKER_MASS : PUCK_MASS,
    pocketed: false,
    pocketId: -1,
  };
}

/** The standard 19-disc break: queen in the centre, rings of alternating whites and blacks. */
export function classicCluster(): Piece[] {
  const pieces: Piece[] = [piece("queen", "QUEEN", CENTER, CENTER)];
  const d1 = 2 * PUCK_RADIUS + 0.35;
  const step = (Math.PI * 2) / 6;
  for (let i = 0; i < 6; i++) {
    const type: PieceType = i % 2 === 0 ? "WHITE" : "BLACK";
    pieces.push(piece(`inner_${i}_${type.toLowerCase()}`, type, CENTER + Math.cos(i * step) * d1, CENTER + Math.sin(i * step) * d1));
  }
  const dCorner = 2 * d1;
  const dEdge = Math.sqrt(3) * d1;
  for (let i = 0; i < 6; i++) {
    const cornerAngle = i * step;
    const cornerType: PieceType = i % 2 === 1 ? "WHITE" : "BLACK";
    pieces.push(
      piece(`outer_c_${i}_${cornerType.toLowerCase()}`, cornerType, CENTER + Math.cos(cornerAngle) * dCorner, CENTER + Math.sin(cornerAngle) * dCorner),
    );
    const edgeAngle = cornerAngle + step / 2;
    const edgeType: PieceType = i % 2 === 0 ? "WHITE" : "BLACK";
    pieces.push(
      piece(`outer_e_${i}_${edgeType.toLowerCase()}`, edgeType, CENTER + Math.cos(edgeAngle) * dEdge, CENTER + Math.sin(edgeAngle) * dEdge),
    );
  }
  return pieces;
}

export function baselinePosition(fraction: number, bottom: boolean): { x: number; y: number } {
  const clamped = Math.min(1, Math.max(0, fraction));
  return { x: BASELINE_START_X + clamped * BASELINE_WIDTH, y: bottom ? BASELINE_BOTTOM_Y : BASELINE_TOP_Y };
}

export function isOverBaselineCircle(x: number, y: number, bottom: boolean): boolean {
  const baselineY = bottom ? BASELINE_BOTTOM_Y : BASELINE_TOP_Y;
  const reach = BASELINE_CIRCLE_RADIUS + 4;
  return Math.hypot(x - BASELINE_START_X, y - baselineY) <= reach || Math.hypot(x - BASELINE_END_X, y - baselineY) <= reach;
}

export function createStriker(fraction: number, bottom: boolean): Piece {
  const pos = baselinePosition(fraction, bottom);
  return piece("striker", "STRIKER", pos.x, pos.y);
}

/** Advances by [dtSeconds]; returns true while anything moves. Mirrors the Kotlin engine step for step. */
export function stepPhysics(pieces: Piece[], striker: Piece | null, dtSeconds: number, onPocket: (p: Piece) => void): boolean {
  const frames = Math.max(0, dtSeconds) * 60;
  const subSteps = Math.min(MAX_SUB_STEPS, Math.max(1, Math.ceil(frames * SUB_STEPS - SUB_STEP_EPSILON)));
  const subDt = frames / subSteps;
  const frictionMult = FRICTION_BASE ** subDt;
  const linearDecel = LINEAR_DRAG * subDt;

  const active: Piece[] = [];
  if (striker && !striker.pocketed) active.push(striker);
  for (const p of pieces) if (!p.pocketed) active.push(p);

  for (let s = 0; s < subSteps; s++) {
    integrate(active, subDt, frictionMult, linearDecel, onPocket);
    resolveCushions(active);
    resolveContacts(active);
  }
  return active.some((p) => !p.pocketed && (p.vx !== 0 || p.vy !== 0));
}

function integrate(active: Piece[], subDt: number, frictionMult: number, linearDecel: number, onPocket: (p: Piece) => void) {
  for (const p of active) {
    if (p.pocketed) continue;
    p.x += p.vx * subDt;
    p.y += p.vy * subDt;

    const speed = Math.hypot(p.vx, p.vy);
    const moving = speed > VELOCITY_EPSILON;
    if (moving) {
      const scale = frictionMult - Math.min(speed, linearDecel) / speed;
      p.vx *= scale;
      p.vy *= scale;
    } else {
      p.vx = 0;
      p.vy = 0;
    }

    const dropThreshold = POCKET_RADIUS - (p.type === "STRIKER" ? STRIKER_DROP_MARGIN : PUCK_DROP_MARGIN);
    for (const pocket of POCKETS) {
      const dx = pocket.x - p.x;
      const dy = pocket.y - p.y;
      const dist = Math.hypot(dx, dy);
      if (dist >= POCKET_SUCTION_RADIUS) continue;
      if (dist < dropThreshold) {
        p.pocketed = true;
        p.pocketId = pocket.id;
        p.vx = 0;
        p.vy = 0;
        onPocket(p);
        break;
      }
      if (moving && dist > 0.001) {
        const ratio = 1 - dist / POCKET_SUCTION_RADIUS;
        const pull = (POCKET_PULL * ratio * ratio * subDt) / dist;
        p.vx += dx * pull;
        p.vy += dy * pull;
      }
    }
  }
}

function resolveCushions(active: Piece[]) {
  for (const p of active) {
    if (p.pocketed) continue;
    const min = PLAYABLE_MIN + p.radius;
    const max = PLAYABLE_MAX - p.radius;
    if (p.x < min) {
      p.x = min;
      p.vx = -p.vx * RESTITUTION_WALL;
      p.vy *= WALL_TANGENTIAL_FRICTION;
    } else if (p.x > max) {
      p.x = max;
      p.vx = -p.vx * RESTITUTION_WALL;
      p.vy *= WALL_TANGENTIAL_FRICTION;
    }
    if (p.y < min) {
      p.y = min;
      p.vy = -p.vy * RESTITUTION_WALL;
      p.vx *= WALL_TANGENTIAL_FRICTION;
    } else if (p.y > max) {
      p.y = max;
      p.vy = -p.vy * RESTITUTION_WALL;
      p.vx *= WALL_TANGENTIAL_FRICTION;
    }
  }
}

function resolveContacts(active: Piece[]) {
  for (let i = 0; i < active.length; i++) {
    const p1 = active[i]!;
    if (p1.pocketed) continue;
    for (let j = i + 1; j < active.length; j++) {
      const p2 = active[j]!;
      if (p2.pocketed) continue;
      const dx = p2.x - p1.x;
      const dy = p2.y - p1.y;
      const minDist = p1.radius + p2.radius;
      if (Math.abs(dx) >= minDist || Math.abs(dy) >= minDist) continue;
      const dist = Math.hypot(dx, dy);
      if (dist >= minDist || dist <= 0.0001) continue;

      const nx = dx / dist;
      const ny = dy / dist;
      const invM1 = 1 / p1.mass;
      const invM2 = 1 / p2.mass;
      const invMassSum = 1 / (invM1 + invM2);

      const overlap = (minDist - dist) * 0.95;
      p1.x -= nx * overlap * invM1 * invMassSum;
      p1.y -= ny * overlap * invM1 * invMassSum;
      p2.x += nx * overlap * invM2 * invMassSum;
      p2.y += ny * overlap * invM2 * invMassSum;

      const rvx = p2.vx - p1.vx;
      const rvy = p2.vy - p1.vy;
      const velAlongNormal = rvx * nx + rvy * ny;
      if (velAlongNormal >= 0) continue;

      const restitution = p1.type === "STRIKER" || p2.type === "STRIKER" ? RESTITUTION_STRIKER_PUCK : RESTITUTION_PUCK_PUCK;
      const impulse = -(1 + restitution) * velAlongNormal * invMassSum;
      p1.vx -= impulse * invM1 * nx;
      p1.vy -= impulse * invM1 * ny;
      p2.vx += impulse * invM2 * nx;
      p2.vy += impulse * invM2 * ny;

      const tx = -ny;
      const ty = nx;
      const tangentImpulse = -(rvx * tx + rvy * ty) * CONTACT_TANGENTIAL_FRICTION * invMassSum;
      p1.vx -= tangentImpulse * invM1 * tx;
      p1.vy -= tangentImpulse * invM1 * ty;
      p2.vx += tangentImpulse * invM2 * tx;
      p2.vy += tangentImpulse * invM2 * ty;
    }
  }
}

export interface ShotInput {
  /** Placement along the shooter's baseline, 0..1. */
  offset: number;
  /** Direction in radians (board space, y down). */
  angle: number;
  /** Percent, 20..100. */
  power: number;
}

export interface ShotSimulation {
  pieces: Piece[];
  striker: Piece;
  /** Discs (not the striker) in the order they dropped. */
  pocketed: Piece[];
  strikerPocketed: boolean;
  /** Seconds of play simulated until everything came to rest. */
  seconds: number;
}

const SIMULATION_DT = 1 / 60;
const MAX_SIMULATION_SECONDS = 15;

/** Plays a whole shot to rest from [input]; [pieces] are copied, never mutated. */
export function simulateShot(pieces: readonly Piece[], input: ShotInput, bottom: boolean, powerMultiplier = 1): ShotSimulation {
  const board = pieces.map((p) => ({ ...p }));
  const offset = clampOffset(input.offset);
  const striker = createStriker(offset, bottom);
  if (isOverBaselineCircle(striker.x, striker.y, bottom)) {
    const safe = baselinePosition(striker.x < CENTER ? 0.12 : 0.88, bottom);
    striker.x = safe.x;
    striker.y = safe.y;
  }
  const speed = (clampPower(input.power) / 100) * MAX_STRIKE_SPEED * powerMultiplier;
  striker.vx = Math.cos(input.angle) * speed;
  striker.vy = Math.sin(input.angle) * speed;

  const pocketed: Piece[] = [];
  let seconds = 0;
  let moving = true;
  while (moving && seconds < MAX_SIMULATION_SECONDS) {
    moving = stepPhysics(board, striker, SIMULATION_DT, (p) => {
      if (p.type !== "STRIKER") pocketed.push(p);
    });
    seconds += SIMULATION_DT;
  }
  for (const p of board) {
    p.vx = 0;
    p.vy = 0;
  }
  return { pieces: board, striker, pocketed, strikerPocketed: striker.pocketed, seconds };
}

export const clampOffset = (offset: number) => Math.min(MAX_BASELINE_FRACTION, Math.max(MIN_BASELINE_FRACTION, offset));
export const clampPower = (power: number) => Math.min(MAX_POWER, Math.max(MIN_POWER, power));

/** Nearest spot to the centre where a disc overlaps nothing (re-spotting the queen). */
export function findFreeSpot(pieces: readonly Piece[], radius: number, ignore?: Piece): { x: number; y: number } {
  const free = (x: number, y: number) => pieces.every((p) => p === ignore || p.pocketed || Math.hypot(p.x - x, p.y - y) >= radius + p.radius + 0.5);
  if (free(CENTER, CENTER)) return { x: CENTER, y: CENTER };
  const min = PLAYABLE_MIN + radius;
  const max = PLAYABLE_MAX - radius;
  for (let ring = 1; ring <= 16; ring++) {
    const samples = 8 * ring;
    for (let i = 0; i < samples; i++) {
      const angle = (i * Math.PI * 2) / samples;
      const x = CENTER + Math.cos(angle) * ring * radius;
      const y = CENTER + Math.sin(angle) * ring * radius;
      if (x >= min && x <= max && y >= min && y <= max && free(x, y)) return { x, y };
    }
  }
  return { x: CENTER, y: CENTER };
}
