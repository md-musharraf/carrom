import type { PieceType } from "./physics.js";

/** Server port of the client's CarromRules (points race with queen and cover). */

export type Seat = 0 | 1;
export type OnlineMode = "classic" | "freestyle";

export interface QueenStatus {
  pottedBy: Seat | null;
  awaitingCover: boolean;
  covered: boolean;
}

export interface ShotOutcome {
  scoreDelta: number;
  isFoul: boolean;
  queen: QueenStatus;
  returnQueenToCenter: boolean;
  queenCoveredNow: boolean;
  keepsTurn: boolean;
  announcement: string | null;
}

export const FOUL_PENALTY = 5;
export const QUEEN_COVER_BONUS = 25;
export const FREESTYLE_TARGET = 160;
export const POINTS: Record<Exclude<PieceType, "STRIKER" | "QUEEN">, number> = { WHITE: 10, BLACK: 5 };

export const noQueen = (): QueenStatus => ({ pottedBy: null, awaitingCover: false, covered: false });

export function evaluateShot(shooter: Seat, pocketed: readonly PieceType[], strikerPocketed: boolean, queen: QueenStatus): ShotOutcome {
  const queenPocketed = pocketed.includes("QUEEN");
  const coverDiscs = pocketed.filter((t) => t === "WHITE" || t === "BLACK").length;
  const pieceScore = pocketed.reduce((sum, t) => sum + (t === "WHITE" || t === "BLACK" ? POINTS[t] : 0), 0);
  const awaitingOwnCover = queen.awaitingCover && queen.pottedBy === shooter;

  if (strikerPocketed) {
    const forfeitQueen = queenPocketed || awaitingOwnCover;
    return {
      scoreDelta: pieceScore - FOUL_PENALTY,
      isFoul: true,
      queen: forfeitQueen ? noQueen() : queen,
      returnQueenToCenter: forfeitQueen,
      queenCoveredNow: false,
      keepsTurn: false,
      announcement: forfeitQueen ? `Foul! Striker pocketed (−${FOUL_PENALTY}) · Queen returned` : `Foul! Striker pocketed (−${FOUL_PENALTY})`,
    };
  }
  if (queenPocketed && coverDiscs > 0) {
    return {
      scoreDelta: pieceScore + QUEEN_COVER_BONUS,
      isFoul: false,
      queen: { pottedBy: shooter, awaitingCover: false, covered: true },
      returnQueenToCenter: false,
      queenCoveredNow: true,
      keepsTurn: true,
      announcement: `Queen pocketed and covered! +${QUEEN_COVER_BONUS}`,
    };
  }
  if (queenPocketed) {
    return {
      scoreDelta: pieceScore,
      isFoul: false,
      queen: { pottedBy: shooter, awaitingCover: true, covered: false },
      returnQueenToCenter: false,
      queenCoveredNow: false,
      keepsTurn: true,
      announcement: "Queen pocketed — cover it with your next shot",
    };
  }
  if (awaitingOwnCover && coverDiscs > 0) {
    return {
      scoreDelta: pieceScore + QUEEN_COVER_BONUS,
      isFoul: false,
      queen: { ...queen, awaitingCover: false, covered: true },
      returnQueenToCenter: false,
      queenCoveredNow: true,
      keepsTurn: true,
      announcement: `Queen covered! +${QUEEN_COVER_BONUS}`,
    };
  }
  if (awaitingOwnCover) {
    return {
      scoreDelta: pieceScore,
      isFoul: false,
      queen: noQueen(),
      returnQueenToCenter: true,
      queenCoveredNow: false,
      keepsTurn: false,
      announcement: "Queen not covered — returned to the centre",
    };
  }
  return { scoreDelta: pieceScore, isFoul: false, queen, returnQueenToCenter: false, queenCoveredNow: false, keepsTurn: pocketed.length > 0, announcement: null };
}

/** The winner once decided, or null while play continues. Ties go to seat 0. */
export function winnerOrNull(mode: OnlineMode, discsLeft: number, scores: readonly [number, number]): Seat | null {
  const decided = discsLeft === 0 || (mode === "freestyle" && (scores[0] >= FREESTYLE_TARGET || scores[1] >= FREESTYLE_TARGET));
  if (!decided) return null;
  return scores[0] >= scores[1] ? 0 : 1;
}
