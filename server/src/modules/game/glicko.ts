/**
 * Glicko-2 (Glickman, 2012). Ratings use the familiar 1500-centred scale externally and the
 * Glicko-2 scale internally. Each ranked game is its own rating period.
 */

export interface Rating {
  r: number;
  rd: number;
  vol: number;
}

export interface GameResult {
  opponent: Rating;
  /** 1 win, 0.5 draw, 0 loss. */
  score: number;
}

const SCALE = 173.7178;
const TAU = 0.5;
const EPSILON = 0.000001;
const MIN_RD = 30;
const MAX_RD = 350;

export const INITIAL_RATING: Rating = { r: 1500, rd: 350, vol: 0.06 };

const g = (phi: number) => 1 / Math.sqrt(1 + (3 * phi * phi) / (Math.PI * Math.PI));
const expected = (mu: number, muJ: number, phiJ: number) => 1 / (1 + Math.exp(-g(phiJ) * (mu - muJ)));

/** Updates [player] after [results]; with no results only the uncertainty grows. */
export function updateRating(player: Rating, results: readonly GameResult[]): Rating {
  const mu = (player.r - 1500) / SCALE;
  const phi = player.rd / SCALE;
  const sigma = player.vol;

  if (results.length === 0) {
    const grown = Math.sqrt(phi * phi + sigma * sigma) * SCALE;
    return { r: player.r, rd: Math.min(MAX_RD, grown), vol: sigma };
  }

  let vInv = 0;
  let deltaSum = 0;
  for (const { opponent, score } of results) {
    const muJ = (opponent.r - 1500) / SCALE;
    const phiJ = opponent.rd / SCALE;
    const gj = g(phiJ);
    const e = expected(mu, muJ, phiJ);
    vInv += gj * gj * e * (1 - e);
    deltaSum += gj * (score - e);
  }
  const v = 1 / vInv;
  const delta = v * deltaSum;

  // Volatility by the Illinois algorithm (step 5 of the paper).
  const a = Math.log(sigma * sigma);
  const f = (x: number) => {
    const ex = Math.exp(x);
    return (ex * (delta * delta - phi * phi - v - ex)) / (2 * (phi * phi + v + ex) ** 2) - (x - a) / (TAU * TAU);
  };
  let A = a;
  let B: number;
  if (delta * delta > phi * phi + v) {
    B = Math.log(delta * delta - phi * phi - v);
  } else {
    let k = 1;
    while (f(a - k * TAU) < 0) k++;
    B = a - k * TAU;
  }
  let fA = f(A);
  let fB = f(B);
  while (Math.abs(B - A) > EPSILON) {
    const C = A + ((A - B) * fA) / (fB - fA);
    const fC = f(C);
    if (fC * fB <= 0) {
      A = B;
      fA = fB;
    } else {
      fA /= 2;
    }
    B = C;
    fB = fC;
  }
  const newSigma = Math.exp(A / 2);

  const phiStar = Math.sqrt(phi * phi + newSigma * newSigma);
  const newPhi = 1 / Math.sqrt(1 / (phiStar * phiStar) + 1 / v);
  const newMu = mu + newPhi * newPhi * deltaSum;
  return {
    r: newMu * SCALE + 1500,
    rd: Math.min(MAX_RD, Math.max(MIN_RD, newPhi * SCALE)),
    vol: newSigma,
  };
}

/** Rates a single decisive game between [winner] and [loser]. */
export function rateGame(winner: Rating, loser: Rating): { winner: Rating; loser: Rating } {
  return {
    winner: updateRating(winner, [{ opponent: loser, score: 1 }]),
    loser: updateRating(loser, [{ opponent: winner, score: 0 }]),
  };
}
