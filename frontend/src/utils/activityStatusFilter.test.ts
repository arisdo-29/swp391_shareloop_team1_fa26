import { describe, expect, it } from 'vitest';
import type { Item, ItemRequest, ItemRequestStatus, ItemStatus } from '../types/domain';
import {
  itemActivityStatusOptions,
  matchesItemActivityStatus,
  matchesSentRequestActivityStatus,
  sentRequestActivityStatusOptions,
} from './activityStatusFilter';

const itemWithStatus = (status: ItemStatus) => ({ status }) as Item;
const requestWithStatus = (status: ItemRequestStatus) => ({ status }) as ItemRequest;

describe('activity status filters', () => {
  it('phân biệt trạng thái bài với trạng thái giao dịch liên quan', () => {
    const hiddenItem = { ...itemWithStatus('APPROVED'), hidden: true } as Item;
    const hiddenPendingItem = { ...itemWithStatus('PENDING_REVIEW'), hidden: true } as Item;

    expect(matchesItemActivityStatus(itemWithStatus('PENDING_REVIEW'), 'pending_review')).toBe(
      true,
    );
    expect(matchesItemActivityStatus(itemWithStatus('APPROVED'), 'waiting_for_choice')).toBe(true);
    expect(matchesItemActivityStatus(hiddenItem, 'temporarily_hidden')).toBe(true);
    expect(matchesItemActivityStatus(hiddenItem, 'waiting_for_choice')).toBe(false);
    expect(matchesItemActivityStatus(hiddenPendingItem, 'pending_review')).toBe(false);
    expect(
      matchesItemActivityStatus(hiddenItem, 'cancelled', [requestWithStatus('CANCELLED')]),
    ).toBe(false);
    expect(matchesItemActivityStatus(itemWithStatus('RESERVED'), 'in_transaction')).toBe(true);
    expect(matchesItemActivityStatus(itemWithStatus('TRADED'), 'completed')).toBe(true);
    expect(
      matchesItemActivityStatus(itemWithStatus('APPROVED'), 'cancelled', [
        requestWithStatus('CANCELLED'),
      ]),
    ).toBe(true);
    expect(matchesItemActivityStatus(itemWithStatus('REJECTED'), 'cancelled')).toBe(false);
    expect(matchesItemActivityStatus(itemWithStatus('REJECTED'), 'rejected')).toBe(true);
    expect(matchesItemActivityStatus(itemWithStatus('EXPIRED'), 'expired')).toBe(true);
    expect(itemActivityStatusOptions.map((option) => option.value)).toEqual([
      'all',
      'pending_review',
      'waiting_for_choice',
      'temporarily_hidden',
      'in_transaction',
      'completed',
      'cancelled',
      'rejected',
      'expired',
    ]);
  });

  it('nhóm yêu cầu đang chạy, hoàn thành và đã hủy mà không gộp REJECTED vào Đã hủy', () => {
    expect(sentRequestActivityStatusOptions.map((option) => option.value)).toEqual([
      'all',
      'waiting_for_choice',
      'in_transaction',
      'completed',
      'cancelled',
    ]);
    for (const status of [
      'RESERVED',
      'AWAITING_LOGISTICS',
      'LOGISTICS_CONFIRMED',
      'AWAITING_HANDOVER',
      'DISPUTED',
    ] as const) {
      expect(matchesSentRequestActivityStatus(requestWithStatus(status), 'in_transaction')).toBe(
        true,
      );
    }

    expect(
      matchesSentRequestActivityStatus(requestWithStatus('PENDING'), 'waiting_for_choice'),
    ).toBe(true);
    expect(matchesSentRequestActivityStatus(requestWithStatus('COMPLETED'), 'completed')).toBe(
      true,
    );
    expect(matchesSentRequestActivityStatus(requestWithStatus('CANCELLED'), 'cancelled')).toBe(
      true,
    );
    expect(matchesSentRequestActivityStatus(requestWithStatus('REJECTED'), 'cancelled')).toBe(
      false,
    );
  });
});
