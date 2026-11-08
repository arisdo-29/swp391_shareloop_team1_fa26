// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { RequestResponse } from '../../services/requestApi';
import { SelectRequestDialog } from './SelectRequestDialog';

const baseRequest: RequestResponse = {
  id: 101,
  itemId: 45,
  receiverId: 12,
  type: 'GIVE',
  status: 'PENDING',
  createdAt: '2026-11-08T08:00:00Z',
  receiverName: 'Nguyễn Văn A',
  itemTitle: 'Xe đạp cũ',
};

function renderDialog(request: RequestResponse, needsTopUp = false) {
  render(
    <MemoryRouter>
      <SelectRequestDialog
        conversation={{ request }}
        participantName="Nguyễn Văn A"
        submitting={false}
        error=""
        needsTopUp={needsTopUp}
        onCancel={vi.fn()}
        onConfirm={vi.fn()}
      />
    </MemoryRouter>,
  );
  return screen.getByRole('dialog', { name: 'Xác nhận chọn người giao dịch' });
}

afterEach(cleanup);

describe('SelectRequestDialog khi chọn người làm đổi hình thức bài', () => {
  it('yêu cầu kèm món ở bài Cho cảnh báo chuyển sang Trao đổi với số Credit backend trả', () => {
    // given
    const crossRequest = {
      ...baseRequest,
      offeredItemId: 60,
      offeredItemTitle: 'Ghế xoay',
      itemOfferType: 'GIVE' as const,
      conversionFee: 7,
    };

    // when
    const dialog = renderDialog(crossRequest);

    // then
    expect(
      within(dialog).getByText(
        'Bài đang là Cho tặng. Chọn người này thì bài chuyển sang Trao đổi và bạn trả 7 Credit phí đăng Trao đổi. Các yêu cầu khác sẽ bị từ chối.',
      ),
    ).toBeTruthy();
    expect(within(dialog).getByRole('article', { name: 'Món đề xuất' })).toBeTruthy();
  });

  it('yêu cầu không kèm món ở bài Trao đổi cảnh báo chuyển sang Cho tặng, không hoàn phí', () => {
    // given
    const receiveOnlyRequest = {
      ...baseRequest,
      type: 'SWAP' as const,
      itemOfferType: 'SWAP' as const,
      conversionFee: 0,
    };

    // when
    const dialog = renderDialog(receiveOnlyRequest);

    // then
    expect(
      within(dialog).getByText(
        'Bài đang là Trao đổi. Chọn người này thì bài chuyển sang Cho tặng; phí đăng đã trả không được hoàn.',
      ),
    ).toBeTruthy();
  });

  it('cùng loại với bài thì không hiện cảnh báo đổi hình thức', () => {
    // when
    const dialog = renderDialog({ ...baseRequest, itemOfferType: 'GIVE', conversionFee: 7 });

    // then
    expect(within(dialog).queryByRole('note')).toBeNull();
  });

  it('thiếu Credit thì báo số cần và có nút Nạp Credit dẫn tới trang nạp', () => {
    // given
    const crossRequest = {
      ...baseRequest,
      offeredItemId: 60,
      itemOfferType: 'GIVE' as const,
      conversionFee: 7,
    };

    // when
    const dialog = renderDialog(crossRequest, true);

    // then
    expect(within(dialog).getByText('Cần 7 Credit để chuyển bài sang Trao đổi.')).toBeTruthy();
    expect(within(dialog).getByRole('link', { name: 'Nạp Credit' }).getAttribute('href')).toBe(
      '/credit',
    );
  });
});
