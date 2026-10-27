// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ItemResponse } from '../../services/itemApi';
import type { MessageResponse } from '../../services/chatApi';
import type { RequestResponse } from '../../services/requestApi';
import { canChat, canSelectPendingRequest, requestStatusLabel } from '../../utils/request';
import {
  mergeChatMessages,
  messageOfferStatusLabel,
  requestConversationPath,
  shouldRefreshConversationDetail,
} from '../../utils/chatMessages';
import { SelectRequestDialog, type ApiConversation } from './Messages';

const request: RequestResponse = {
  id: 101,
  itemId: 45,
  receiverId: 12,
  offeredItemId: 78,
  type: 'SWAP',
  status: 'PENDING',
  createdAt: '2026-10-22T08:00:00Z',
  receiverName: 'Nguyễn Văn A',
};

const item: ItemResponse = {
  id: 45,
  donorId: 9,
  categoryId: 1,
  areaId: 1,
  offerType: 'SWAP',
  title: 'Máy pha cà phê',
  description: 'Máy còn hoạt động tốt.',
  desiredItem: 'Máy xay cà phê',
  condition: 'GOOD',
  defectNote: null,
  brand: null,
  images: [],
  defectImages: [],
  operatingStatus: 'NORMAL',
  status: 'APPROVED',
  createdAt: '2026-10-20T08:00:00Z',
  updatedAt: '2026-10-20T08:00:00Z',
  expireAt: null,
  boostedUntil: null,
};

const offeredItem: ItemResponse = {
  ...item,
  id: 78,
  donorId: 12,
  title: 'Máy xay cà phê',
  description: 'Máy xay ít sử dụng.',
};

const conversation: ApiConversation = { request, item, offeredItem };

afterEach(cleanup);

describe('chọn người giao dịch', () => {
  it('chỉ cho chủ bài chọn yêu cầu đang chờ khi chưa có người được chọn', () => {
    expect(canSelectPendingRequest('PENDING', 9, 9, ['PENDING', 'REJECTED'])).toBe(true);
    expect(canSelectPendingRequest('PENDING', 9, 12, ['PENDING'])).toBe(false);
    expect(canSelectPendingRequest('REJECTED', 9, 9, ['REJECTED'])).toBe(false);
    expect(canSelectPendingRequest('PENDING', 9, 9, ['PENDING', 'RESERVED'])).toBe(false);
  });

  it.each(['RESERVED', 'AWAITING_LOGISTICS', 'LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER', 'DISPUTED'] as const)(
    'không cho chọn thêm người khi cùng bài đã có yêu cầu ở trạng thái %s',
    (selectedStatus) => {
      expect(canSelectPendingRequest('PENDING', 9, 9, ['PENDING', selectedStatus])).toBe(false);
    },
  );

  it('phân biệt trạng thái chờ, đã chọn, đã hủy, hoàn thành và quyền mở chat', () => {
    expect(requestStatusLabel.PENDING).toBe('Chờ phản hồi');
    expect(requestStatusLabel.RESERVED).toBe('Đã được chọn');
    expect(requestStatusLabel.CANCELLED).toBe('Đã hủy');
    expect(requestStatusLabel.COMPLETED).toBe('Hoàn tất');
    expect(canChat('PENDING')).toBe(false);
    expect(canChat('CANCELLED')).toBe(false);
    expect(canChat('RESERVED')).toBe(true);
    expect(canChat('COMPLETED')).toBe(true);
  });

  it('không xác nhận khi chỉ mở hoặc hủy modal', async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    const onConfirm = vi.fn();
    render(
      <SelectRequestDialog
        conversation={conversation}
        participantName="Nguyễn Văn A"
        submitting={false}
        error=""
        onCancel={onCancel}
        onConfirm={onConfirm}
      />,
    );

    expect(screen.getByText('Máy xay cà phê')).toBeTruthy();
    expect(onConfirm).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Hủy' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('hiển thị đúng chi tiết GIVE khi chuyển sang yêu cầu khác', () => {
    const giveConversation: ApiConversation = {
      request: {
        ...request,
        id: 102,
        type: 'GIVE',
        offeredItemId: undefined,
        receiverName: 'Trần Thị B',
      },
      item: { ...item, id: 46, offerType: 'GIVE', title: 'Bộ sách giáo khoa' },
    };
    const { rerender } = render(
      <SelectRequestDialog
        conversation={conversation}
        participantName="Nguyễn Văn A"
        submitting={false}
        error=""
        onCancel={() => undefined}
        onConfirm={() => undefined}
      />,
    );
    expect(screen.getByText('Máy xay cà phê')).toBeTruthy();

    rerender(
      <SelectRequestDialog
        conversation={giveConversation}
        participantName="Trần Thị B"
        submitting={false}
        error=""
        onCancel={() => undefined}
        onConfirm={() => undefined}
      />,
    );
    expect(screen.getByText('Trần Thị B')).toBeTruthy();
    expect(screen.getByText(/Bộ sách giáo khoa/)).toBeTruthy();
    expect(screen.queryByText('Máy xay cà phê')).toBeNull();
  });

  it('chỉ xác nhận một lần và khóa thao tác khi đang gửi', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    const { rerender } = render(
      <SelectRequestDialog
        conversation={conversation}
        participantName="Nguyễn Văn A"
        submitting={false}
        error=""
        onCancel={() => undefined}
        onConfirm={onConfirm}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Xác nhận chọn' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);

    rerender(
      <SelectRequestDialog
        conversation={conversation}
        participantName="Nguyễn Văn A"
        submitting
        error=""
        onCancel={() => undefined}
        onConfirm={onConfirm}
      />,
    );
    expect((screen.getByRole('button', { name: 'Đang chọn...' }) as HTMLButtonElement).disabled).toBe(true);
  });
});

describe('tích hợp tin nhắn API', () => {
  const message = (id: number, content: string): MessageResponse => ({
    id,
    requestId: 101,
    senderId: 9,
    kind: 'TEXT',
    content,
    mediaId: null,
    refItemId: null,
    itemCard: null,
    offerStatus: null,
    moderation: 'PASS',
    blockedReason: null,
    createdAt: `2026-10-22T08:00:0${id}Z`,
  });

  it('gộp polling theo ID, không nhân đôi và luôn giữ đúng thứ tự', () => {
    expect(mergeChatMessages([message(2, 'cũ')], [message(1, 'đầu'), message(2, 'mới')]))
      .toEqual([message(1, 'đầu'), message(2, 'mới')]);
  });

  it('chỉ tải lại chi tiết cuộc trò chuyện mỗi sáu lần polling', () => {
    expect(shouldRefreshConversationDetail(0)).toBe(true);
    expect(shouldRefreshConversationDetail(1)).toBe(false);
    expect(shouldRefreshConversationDetail(5)).toBe(false);
    expect(shouldRefreshConversationDetail(6)).toBe(true);
  });

  it('tạo URL ổn định khi đổi cuộc trò chuyện và hiển thị trạng thái đề xuất', () => {
    expect(requestConversationPath(101)).toBe('/requests/101');
    expect(messageOfferStatusLabel('PENDING')).toBe('Đề xuất đang chờ');
    expect(messageOfferStatusLabel('ACCEPTED')).toBe('Đề xuất đã được chấp nhận');
    expect(messageOfferStatusLabel('DECLINED')).toBe('Đề xuất đã bị từ chối');
    expect(messageOfferStatusLabel(null)).toBeNull();
  });
});
