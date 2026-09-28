import { Schema, Types, model } from "mongoose";
import { badRequest, conflict, isDuplicateKeyError, notFound } from "../../lib/errors.js";
import type { Notifier } from "../../lib/notifier.js";
import type { PresenceService } from "../realtime/presence.js";
import type { PublicProfile, UsersService } from "../users/users.service.js";

interface Friendship {
  _id: Types.ObjectId;
  /** The pair, stored in sorted order so (a, b) is unique regardless of who asked. */
  a: Types.ObjectId;
  b: Types.ObjectId;
  requester: Types.ObjectId;
  status: "pending" | "accepted";
  acceptedAt?: Date;
  createdAt: Date;
}

const friendshipSchema = new Schema<Friendship>(
  {
    a: { type: Schema.Types.ObjectId, required: true },
    b: { type: Schema.Types.ObjectId, required: true },
    requester: { type: Schema.Types.ObjectId, required: true },
    status: { type: String, enum: ["pending", "accepted"], required: true },
    acceptedAt: Date,
  },
  { timestamps: { createdAt: true, updatedAt: false } },
);
friendshipSchema.index({ a: 1, b: 1 }, { unique: true });
friendshipSchema.index({ b: 1, status: 1 });
friendshipSchema.index({ a: 1, status: 1 });

export const FriendshipModel = model<Friendship>("Friendship", friendshipSchema);

export const MAX_FRIENDS = 200;

export interface FriendView extends PublicProfile {
  online: boolean;
  since: string;
}

export interface FriendRequestView {
  id: string;
  direction: "incoming" | "outgoing";
  player: PublicProfile;
  createdAt: string;
}

const pair = (x: string, y: string) => (x < y ? { a: x, b: y } : { a: y, b: x });

export class FriendsService {
  constructor(
    private readonly users: UsersService,
    private readonly presence: PresenceService,
    private readonly notifier: Notifier,
  ) {}

  async list(userId: string): Promise<FriendView[]> {
    const friendships = await FriendshipModel.find({ status: "accepted", $or: [{ a: userId }, { b: userId }] });
    const otherIds = friendships.map((f) => (f.a.toString() === userId ? f.b : f.a).toString());
    const [users, online] = await Promise.all([this.users.findManyByIds(otherIds), this.presence.onlineMany(otherIds)]);
    return friendships
      .flatMap((f) => {
        const otherId = (f.a.toString() === userId ? f.b : f.a).toString();
        const user = users.get(otherId);
        if (!user) return [];
        return [{ ...this.users.toPublic(user), online: online.has(otherId), since: (f.acceptedAt ?? f.createdAt).toISOString() }];
      })
      .sort((x, y) => Number(y.online) - Number(x.online) || x.displayName.localeCompare(y.displayName));
  }

  async friendIds(userId: string): Promise<string[]> {
    const friendships = await FriendshipModel.find({ status: "accepted", $or: [{ a: userId }, { b: userId }] }, { a: 1, b: 1 });
    return friendships.map((f) => (f.a.toString() === userId ? f.b : f.a).toString());
  }

  async areFriends(x: string, y: string): Promise<boolean> {
    return (await FriendshipModel.countDocuments({ ...pair(x, y), status: "accepted" })) > 0;
  }

  async requests(userId: string): Promise<FriendRequestView[]> {
    const pending = await FriendshipModel.find({ status: "pending", $or: [{ a: userId }, { b: userId }] }).sort({ createdAt: -1 });
    const otherIds = pending.map((f) => (f.a.toString() === userId ? f.b : f.a).toString());
    const users = await this.users.findManyByIds(otherIds);
    return pending.flatMap((f) => {
      const other = users.get((f.a.toString() === userId ? f.b : f.a).toString());
      if (!other) return [];
      return [
        {
          id: f.id as string,
          direction: f.requester.toString() === userId ? ("outgoing" as const) : ("incoming" as const),
          player: this.users.toPublic(other),
          createdAt: f.createdAt.toISOString(),
        },
      ];
    });
  }

  /** Sends a request to the player with [targetPlayerId]; accepts it instead if they already asked us. */
  async request(userId: string, targetPlayerId: string): Promise<{ status: "pending" | "accepted" }> {
    const target = await this.users.findByPlayerId(targetPlayerId);
    if (!target) throw notFound("player_not_found", "No player has that ID");
    const targetId = target.id as string;
    if (targetId === userId) throw badRequest("cannot_friend_self", "That's your own player ID");

    const existing = await FriendshipModel.findOne(pair(userId, targetId));
    if (existing?.status === "accepted") throw conflict("already_friends", "You're already friends");
    if (existing && existing.requester.toString() === userId) throw conflict("request_pending", "Request already sent");
    if (existing) {
      await this.accept(userId, existing.id as string);
      return { status: "accepted" };
    }

    if ((await this.friendIds(userId)).length >= MAX_FRIENDS) throw conflict("friend_limit", `You can have up to ${MAX_FRIENDS} friends`);
    try {
      const created = await FriendshipModel.create({ ...pair(userId, targetId), requester: userId, status: "pending" });
      const me = await this.users.get(userId);
      this.notifier.toUser(targetId, "friends:request", { id: created.id, player: this.users.toPublic(me) });
    } catch (error) {
      if (isDuplicateKeyError(error)) throw conflict("request_pending", "Request already sent");
      throw error;
    }
    return { status: "pending" };
  }

  async accept(userId: string, requestId: string): Promise<void> {
    const request = await this.incoming(userId, requestId);
    request.status = "accepted";
    request.acceptedAt = new Date();
    await request.save();
    const me = await this.users.get(userId);
    this.notifier.toUser(request.requester.toString(), "friends:accepted", { player: this.users.toPublic(me) });
  }

  async decline(userId: string, requestId: string): Promise<void> {
    const request = await FriendshipModel.findOne({ _id: toId(requestId), status: "pending", $or: [{ a: userId }, { b: userId }] });
    if (!request) throw notFound("request_not_found", "Friend request not found");
    await request.deleteOne();
  }

  async remove(userId: string, friendPlayerId: string): Promise<void> {
    const friend = await this.users.findByPlayerId(friendPlayerId);
    if (!friend) throw notFound("player_not_found", "No player has that ID");
    await FriendshipModel.deleteOne({ ...pair(userId, friend.id as string) });
  }

  async removeAll(userId: string): Promise<void> {
    await FriendshipModel.deleteMany({ $or: [{ a: userId }, { b: userId }] });
  }

  private async incoming(userId: string, requestId: string) {
    const request = await FriendshipModel.findOne({
      _id: toId(requestId),
      status: "pending",
      requester: { $ne: userId },
      $or: [{ a: userId }, { b: userId }],
    });
    if (!request) throw notFound("request_not_found", "Friend request not found");
    return request;
  }
}

function toId(id: string): Types.ObjectId {
  if (!Types.ObjectId.isValid(id)) throw notFound("request_not_found", "Friend request not found");
  return new Types.ObjectId(id);
}
