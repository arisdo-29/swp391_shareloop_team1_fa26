import type { MessageResponse } from '../services/chatApi';

const offerStatusLabels: Record<string, string> = {
  PENDING: 'Đề xuất đang chờ',
  ACCEPTED: 'Đề xuất đã được chấp nhận',
  DECLINED: 'Đề xuất đã bị từ chối',
};

export const CHAT_DETAIL_REFRESH_EVERY = 6;
export const CHAT_POLL_INTERVAL_MS = 5_000;

export function mergeChatMessages(
  current: MessageResponse[],
  incoming: MessageResponse[],
): MessageResponse[] {
  if (!incoming.length) return current;
  return [...new Map([...current, ...incoming].map((entry) => [entry.id, entry])).values()]
    .sort((a, b) => a.id - b.id);
}

export function messageOfferStatusLabel(status: string | null): string | null {
  if (!status) return null;
  return offerStatusLabels[status.toUpperCase()] ?? status;
}

export function shouldRefreshConversationDetail(pollCount: number): boolean {
  return pollCount % CHAT_DETAIL_REFRESH_EVERY === 0;
}

export function requestConversationPath(requestId: number): string {
  return `/requests/${requestId}`;
}
