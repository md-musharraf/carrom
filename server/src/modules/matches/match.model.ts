import { Schema, model, type Types } from "mongoose";

export type EndReason = "completed" | "resigned" | "timeout" | "abandoned";

export interface MatchRecord {
  _id: Types.ObjectId;
  matchId: string;
  mode: "classic" | "freestyle";
  arena: string | null;
  ranked: boolean;
  entryFee: number;
  players: Array<{
    userId: Types.ObjectId;
    playerId: string;
    name: string;
    seat: number;
    score: number;
    fouls: number;
    pockets: number;
    ratingBefore: number;
    ratingAfter: number;
    coinsWon: number;
  }>;
  winnerSeat: number;
  endReason: EndReason;
  shots: number;
  startedAt: Date;
  endedAt: Date;
}

const matchSchema = new Schema<MatchRecord>({
  matchId: { type: String, required: true, unique: true },
  mode: { type: String, required: true },
  arena: { type: String, default: null },
  ranked: { type: Boolean, required: true },
  entryFee: { type: Number, default: 0 },
  players: [
    {
      _id: false,
      userId: { type: Schema.Types.ObjectId, required: true },
      playerId: String,
      name: String,
      seat: Number,
      score: Number,
      fouls: Number,
      pockets: Number,
      ratingBefore: Number,
      ratingAfter: Number,
      coinsWon: Number,
    },
  ],
  winnerSeat: { type: Number, required: true },
  endReason: { type: String, required: true },
  shots: Number,
  startedAt: Date,
  endedAt: { type: Date, index: true },
});
matchSchema.index({ "players.userId": 1, endedAt: -1 });

export const MatchModel = model<MatchRecord>("Match", matchSchema);

export interface MatchSummary {
  matchId: string;
  mode: string;
  arena: string | null;
  ranked: boolean;
  result: "win" | "loss";
  endReason: EndReason;
  myScore: number;
  opponent: { playerId: string; name: string; score: number };
  ratingChange: number;
  coinsWon: number;
  endedAt: string;
}

export async function matchHistory(userId: string, limit: number): Promise<MatchSummary[]> {
  const matches = await MatchModel.find({ "players.userId": userId }).sort({ endedAt: -1 }).limit(limit).lean();
  return matches.flatMap((m) => {
    const me = m.players.find((p) => p.userId.toString() === userId);
    const opponent = m.players.find((p) => p.userId.toString() !== userId);
    if (!me || !opponent) return [];
    return [
      {
        matchId: m.matchId,
        mode: m.mode,
        arena: m.arena,
        ranked: m.ranked,
        result: me.seat === m.winnerSeat ? ("win" as const) : ("loss" as const),
        endReason: m.endReason,
        myScore: me.score,
        opponent: { playerId: opponent.playerId, name: opponent.name, score: opponent.score },
        ratingChange: Math.round(me.ratingAfter - me.ratingBefore),
        coinsWon: me.coinsWon,
        endedAt: m.endedAt.toISOString(),
      },
    ];
  });
}
