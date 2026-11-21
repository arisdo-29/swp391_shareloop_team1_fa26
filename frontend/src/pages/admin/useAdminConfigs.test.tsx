// @vitest-environment jsdom

import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminConfigApi, type ConfigPreviewResponse } from '../../services/adminConfigApi';
import { useAdminConfigs } from './useAdminConfigs';

vi.mock('../../services/adminConfigApi', () => ({
  adminConfigApi: { list: vi.fn(), preview: vi.fn(), updateBatch: vi.fn() },
}));

const emptyPreview: ConfigPreviewResponse = { changes: [], errors: [], warnings: [] };

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

beforeEach(() => {
  vi.mocked(adminConfigApi.list).mockResolvedValue([]);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('useAdminConfigs', () => {
  it('chỉ nhận kết quả xem trước của lần gọi mới nhất khi kết quả về lệch thứ tự', async () => {
    // given: lần gọi đầu chậm, lần gọi sau nhanh
    const first = deferred<ConfigPreviewResponse>();
    const second = deferred<ConfigPreviewResponse>();
    vi.mocked(adminConfigApi.preview)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const { result } = renderHook(() => useAdminConfigs());
    await waitFor(() => expect(result.current.loading).toBe(false));

    // when: gọi hai lần liên tiếp rồi trả lời lần sau trước, lần đầu sau
    let firstOutcome: Awaited<ReturnType<typeof result.current.preview>> | undefined;
    let secondOutcome: Awaited<ReturnType<typeof result.current.preview>> | undefined;
    await act(async () => {
      const firstCall = result.current.preview('boost_days', '4').then((outcome) => {
        firstOutcome = outcome;
      });
      const secondCall = result.current.preview('boost_days', '5').then((outcome) => {
        secondOutcome = outcome;
      });
      second.resolve({ ...emptyPreview, warnings: [{ message: 'Của lần mới', keys: [], errorCode: null }] });
      first.resolve({ ...emptyPreview, warnings: [{ message: 'Của lần cũ', keys: [], errorCode: null }] });
      await Promise.all([firstCall, secondCall]);
    });

    // then: kết quả lần cũ bị bỏ qua
    expect(firstOutcome).toEqual({ status: 'stale' });
    expect(secondOutcome).toMatchObject({ status: 'ok', preview: { warnings: [{ message: 'Của lần mới' }] } });
  });

  it('huỷ lần gọi xem trước cũ bằng AbortSignal khi có lần gọi mới', async () => {
    // given
    vi.mocked(adminConfigApi.preview).mockResolvedValue(emptyPreview);
    const { result } = renderHook(() => useAdminConfigs());
    await waitFor(() => expect(result.current.loading).toBe(false));

    // when
    await act(async () => {
      await result.current.preview('boost_days', '4');
      await result.current.preview('boost_days', '5');
    });

    // then
    const firstSignal = vi.mocked(adminConfigApi.preview).mock.calls[0][2]!;
    expect(firstSignal.aborted).toBe(true);
  });

  it('bỏ qua kết quả xem trước sau khi huỷ (ví dụ đóng hộp thoại)', async () => {
    // given
    const pending = deferred<ConfigPreviewResponse>();
    vi.mocked(adminConfigApi.preview).mockReturnValueOnce(pending.promise);
    const { result } = renderHook(() => useAdminConfigs());
    await waitFor(() => expect(result.current.loading).toBe(false));

    // when
    let outcome: Awaited<ReturnType<typeof result.current.preview>> | undefined;
    await act(async () => {
      const call = result.current.preview('boost_days', '4').then((value) => {
        outcome = value;
      });
      result.current.cancelPreview();
      pending.resolve(emptyPreview);
      await call;
    });

    // then
    expect(outcome).toEqual({ status: 'stale' });
  });

  it('sau khi lưu theo lô gộp mọi cấu hình trả về vào danh sách, kể cả cấu hình đồng bộ', async () => {
    // given
    const base = { defaultValue: '3', description: '', group: 'POSTING' as const, groupLabel: 'Đăng bài' };
    vi.mocked(adminConfigApi.list).mockResolvedValue([
      { ...base, key: 'boost_days', value: '3', label: 'Số ngày mỗi gói đẩy bài' },
      { ...base, key: 'boost_max_days', value: '6', label: 'Số ngày đẩy tối đa' },
    ]);
    vi.mocked(adminConfigApi.updateBatch).mockResolvedValue({
      configs: [
        { ...base, key: 'boost_days', value: '4', label: 'Số ngày mỗi gói đẩy bài' },
        { ...base, key: 'boost_max_days', value: '8', label: 'Số ngày đẩy tối đa' },
      ],
    });
    const { result } = renderHook(() => useAdminConfigs());
    await waitFor(() => expect(result.current.configs).toHaveLength(2));

    // when
    await act(async () => {
      await result.current.updateBatch({
        changes: [{ key: 'boost_days', value: '4' }],
        reason: 'Đổi gói',
        acknowledgedWarnings: false,
      });
    });

    // then
    expect(result.current.configs.map((entry) => entry.value)).toEqual(['4', '8']);
  });

  it('lưu lỗi 409 thì trả bản xem trước và không đặt câu báo chung', async () => {
    // given
    vi.mocked(adminConfigApi.updateBatch).mockRejectedValue({
      status: 409,
      code: 'SETTING_CONFIG_WARNING_NOT_ACKNOWLEDGED',
      message: 'x',
      details: { preview: { changes: [], errors: [], warnings: [{ message: 'Cảnh báo', keys: [], errorCode: null }] } },
    });
    const { result } = renderHook(() => useAdminConfigs());
    await waitFor(() => expect(result.current.loading).toBe(false));

    // when
    let saved: Awaited<ReturnType<typeof result.current.updateBatch>> | undefined;
    await act(async () => {
      saved = await result.current.updateBatch({ changes: [], reason: 'r', acknowledgedWarnings: false });
    });

    // then
    expect(saved).toMatchObject({ ok: false, preview: { warnings: [{ message: 'Cảnh báo' }] } });
    expect(result.current.saveError).toBe('');
  });
});
