/**
 * Pushes an event to every connected device of a user. Implemented by the realtime layer; services
 * depend only on this interface so they stay testable without sockets.
 */
export interface Notifier {
  toUser(userId: string, event: string, payload: unknown): void;
}

export const silentNotifier: Notifier = { toUser: () => undefined };

/** Late-bound notifier: services are built before the Socket.IO server exists. */
export class DeferredNotifier implements Notifier {
  private target: Notifier = silentNotifier;

  bind(target: Notifier): void {
    this.target = target;
  }

  toUser(userId: string, event: string, payload: unknown): void {
    this.target.toUser(userId, event, payload);
  }
}
