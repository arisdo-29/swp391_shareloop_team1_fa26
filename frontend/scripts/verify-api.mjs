import assert from 'node:assert/strict';
import { test } from 'node:test';
import { build } from 'esbuild';

const memory = new Map();
globalThis.localStorage = {
  getItem: (key) => memory.get(key) ?? null,
  setItem: (key, value) => memory.set(key, String(value)),
  removeItem: (key) => memory.delete(key),
};

const bundle = await build({
  stdin: {
    contents: `export { http, tokenStorage } from './src/services/http.ts';
      export { aiApi } from './src/services/aiApi.ts';
      export { chatApi } from './src/services/chatApi.ts';
      export { catalogApi } from './src/services/catalogApi.ts';
      export { adminAuthenticityApi, authenticityErrorMessage, beginAuthenticitySubmission, validateAuthenticityReason } from './src/services/adminAuthenticityApi.ts';
      export { adminModerationApi } from './src/services/adminModerationApi.ts';
      export { reportApi } from './src/services/reportApi.ts';
      export { adminFinanceApi } from './src/services/adminFinanceApi.ts';
      export { paymentApi } from './src/services/paymentApi.ts';
      export { startPayment, confirmDemoPayment } from './src/services/paymentFlow.ts';
      export { loadPendingTopUp, savePendingTopUp } from './src/services/topUpSession.ts';
      export { adminAuditApi } from './src/services/adminAuditApi.ts';
      export { requestApi, toFeItemRequest } from './src/services/requestApi.ts';
      export { canChat, canSchedule, canConfirmHandover, requestStatusLabel } from './src/utils/request.ts';
      export { loadPersistedState } from './src/utils/storage.ts';`,
    resolveDir: process.cwd(),
    loader: 'ts',
    sourcefile: 'api-verification.ts',
  },
  bundle: true,
  platform: 'browser',
  format: 'esm',
  write: false,
  define: { 'import.meta.env': '{}' },
});
const api = await import(
  `data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].contents).toString('base64')}`
);

globalThis.sessionStorage = globalThis.localStorage;
const navigations = [];
globalThis.window = {
  location: { origin: 'https://shareloop.test', assign: (url) => navigations.push(url) },
};

let requests = [];
let nextResponse = {};
api.http.defaults.adapter = async (config) => {
  requests.push({
    method: config.method,
    url: config.url,
    params: config.params,
    body: config.data ? JSON.parse(config.data) : undefined,
  });
  return { data: nextResponse, status: 200, statusText: 'OK', headers: {}, config };
};

await test('MANUAL tạo đơn, đọc lại và mark-paid qua API; không gọi mock-confirm khi bắt đầu', async () => {
  requests = [];
  nextResponse = {
    orderCode: 'manual-code',
    provider: 'MANUAL',
    status: 'PENDING',
    credits: 20,
    amountVnd: 20000,
    redirectUrl: '/wallet/topup/manual-checkout?orderCode=manual-code',
  };
  const order = await api.paymentApi.createTopUpOrder({ amountVnd: 20000 });
  assert.deepEqual(requests[0].body, { amountVnd: 20000 });
  await assert.rejects(api.confirmDemoPayment(order));
  assert.equal(await api.startPayment(order), undefined);
  assert.deepEqual(navigations, ['/wallet/topup/manual-checkout?orderCode=manual-code']);
  assert.equal(requests.length, 1);
  assert.deepEqual(await api.paymentApi.getOrder(order.orderCode), nextResponse);
  assert.equal(requests[1].url, '/api/v1/wallet/topup-orders/manual-code');
  assert.equal(requests[1].method, 'get');
  nextResponse = { ...nextResponse, submittedAt: '2026-10-20T07:00:00Z' };
  assert.equal((await api.paymentApi.markPaid(order.orderCode)).status, 'PENDING');
  assert.equal(requests[2].url, '/api/v1/wallet/topup-orders/manual-code/mark-paid');
  assert.equal(requests[2].body, undefined);
});

await test('session chỉ giữ mã tham chiếu; không tin số tiền và trạng thái trong storage', () => {
  api.savePendingTopUp({
    orderCode: 'manual-code',
    amountVnd: 20000,
    credits: 20,
    status: 'PENDING',
    provider: 'MANUAL',
  });
  assert.deepEqual(JSON.parse(sessionStorage.getItem('shareloop:pendingTopUp')), {
    orderCode: 'manual-code',
  });
  sessionStorage.setItem(
    'shareloop:pendingTopUp',
    JSON.stringify({ orderCode: 'other-code', status: 'SUCCESS', credits: 999999 }),
  );
  assert.deepEqual(api.loadPendingTopUp(), { orderCode: 'other-code' });
  sessionStorage.setItem('shareloop:pendingTopUp', '{broken');
  assert.equal(api.loadPendingTopUp(), null);
});

await test('admin duyệt/từ chối chỉ gửi ghi chú và lý do, giữ phân trang', async () => {
  requests = [];
  const params = { page: 2, size: 10, status: 'PENDING', submittedOnly: true };
  await api.adminFinanceApi.topUpOrders(params);
  assert.equal(requests[0].url, '/api/v1/admin/finance/topup-orders');
  assert.deepEqual(requests[0].params, params);
  await api.adminFinanceApi.approveTopUp('code', 'checked');
  assert.equal(requests[1].url, '/api/v1/admin/finance/topup-orders/code/approve');
  assert.deepEqual(requests[1].body, { note: 'checked' });
  await api.adminFinanceApi.rejectTopUp('code', 'invalid');
  assert.equal(requests[2].url, '/api/v1/admin/finance/topup-orders/code/reject');
  assert.deepEqual(requests[2].body, { reason: 'invalid' });
});

await test('quota không tìm kiếm hoặc tiêu lượt; search giữ quota và kết quả dự phòng', async () => {
  requests = [];
  nextResponse = { freeUsesRemaining: 1, feeCredits: 2, hasToppedUp: false };
  assert.deepEqual(await api.aiApi.quota(), nextResponse);
  assert.equal(requests[0].method, 'get');
  assert.equal(requests[0].url, '/api/v1/ai/quota');
  nextResponse = {
    filters: {},
    results: { items: [], page: 2, size: 6, totalElements: 20 },
    quota: nextResponse,
    fallback: true,
    chargedCredits: 0,
  };
  assert.deepEqual(await api.aiApi.search('bàn học', 2, 6), nextResponse);
  assert.deepEqual(requests[1].body, { query: 'bàn học' });
  assert.deepEqual(requests[1].params, { page: 2, size: 6 });
});

await test('polling truyền afterId, pinned items dùng path canonical và tin nhắn gửi refItemId', async () => {
  requests = [];
  nextResponse = [];
  await api.chatApi.list(101, 25);
  assert.deepEqual(requests[0].params, { afterId: 25 });
  await api.chatApi.pinnedItems(101);
  assert.equal(requests[1].url, '/api/v1/requests/101/pinned-items');
  assert.equal(requests[1].params, undefined);
  await api.chatApi.send(101, { content: '', refItemId: 42 });
  assert.equal(requests[2].url, '/api/v1/requests/101/messages');
  assert.deepEqual(requests[2].body, { content: '', refItemId: 42 });
});

await test('thuộc tính danh mục gọi đúng path và không tự thêm query', async () => {
  requests = [];
  nextResponse = [
    {
      id: 3,
      categoryId: 7,
      name: 'Trạng thái hoạt động',
      dataType: 'SELECT',
      options: ['NORMAL'],
      required: true,
      sortOrder: 1,
    },
  ];
  assert.deepEqual(await api.catalogApi.categoryAttributes(7), nextResponse);
  assert.equal(requests[0].url, '/api/v1/categories/7/attributes');
  assert.equal(requests[0].method, 'get');
  assert.equal(requests[0].params, undefined);
});

await test('authenticity grant không gửi body; reject và revoke chỉ gửi reason', async () => {
  requests = [];
  nextResponse = undefined;
  await api.adminAuthenticityApi.grant(42);
  assert.equal(requests[0].url, '/api/v1/admin/authenticity/42/grant');
  assert.equal(requests[0].method, 'post');
  assert.equal(requests[0].body, undefined);
  await api.adminAuthenticityApi.reject(42, 'Chứng từ không khớp');
  assert.equal(requests[1].url, '/api/v1/admin/authenticity/42/reject');
  assert.equal(requests[1].method, 'post');
  assert.deepEqual(requests[1].body, { reason: 'Chứng từ không khớp' });
  await api.adminAuthenticityApi.revoke(42, 'Chứng từ giả');
  assert.equal(requests[2].url, '/api/v1/admin/authenticity/42/revoke');
  assert.equal(requests[2].method, 'post');
  assert.deepEqual(requests[2].body, { reason: 'Chứng từ giả' });
});

await test('duyệt gửi ID điều kiện cùng đáp án, không ghép lại theo vị trí', async () => {
  requests = [];
  const answers = [
    { id: 12, passed: true },
    { id: 11, passed: true },
  ];
  await api.adminModerationApi.approve(42, { answers });
  assert.equal(requests[0].url, '/api/v1/admin/reviews/42/approve');
  assert.deepEqual(requests[0].body.answers, answers);
});

await test('khiếu nại truyền bộ lọc, phân trang và đọc hồ sơ theo report ID', async () => {
  requests = [];
  nextResponse = {
    items: [{ id: 71, targetType: 'REQUEST', requestId: 88 }],
    page: 2,
    size: 20,
    totalElements: 41,
    totalPages: 3,
  };
  const controller = new AbortController();
  assert.deepEqual(
    await api.reportApi.list(
      { page: 2, size: 20, status: 'OPEN', targetType: 'REQUEST' },
      controller.signal,
    ),
    nextResponse,
  );
  assert.equal(requests[0].url, '/api/v1/admin/reports');
  assert.deepEqual(requests[0].params, {
    page: 2,
    size: 20,
    status: 'OPEN',
    targetType: 'REQUEST',
  });
  nextResponse = {
    report: { id: 71, requestId: 88 },
    response: 'Giải trình',
    resolution: null,
    evidence: [{ id: 12, url: '/api/v1/admin/reports/71/evidence/12', available: true }],
    responseEvidence: [{ id: 15, url: null, available: false }],
    responseDueAt: '2026-10-23T03:00:00Z',
  };
  assert.deepEqual(await api.reportApi.detail(71), nextResponse);
  assert.equal(requests[1].url, '/api/v1/admin/reports/71');
  nextResponse = new Blob(['image'], { type: 'image/png' });
  assert.equal(await api.reportApi.evidence(71, 12, controller.signal), nextResponse);
  assert.equal(requests[2].url, '/api/v1/admin/reports/71/evidence/12');
});

await test('kết luận gửi UPHOLD hoặc DISMISS cùng ghi chú, giữ lỗi 409 từ backend', async () => {
  requests = [];
  for (const decision of ['UPHOLD', 'DISMISS']) {
    nextResponse = { id: 71, status: decision === 'UPHOLD' ? 'RESOLVED' : 'DISMISSED' };
    assert.deepEqual(
      await api.reportApi.conclude(71, { decision, note: 'Đã xác minh' }),
      nextResponse,
    );
    assert.deepEqual(requests.at(-1).body, { decision, note: 'Đã xác minh' });
    assert.equal(requests.at(-1).url, '/api/v1/admin/reports/71/conclude');
  }
  const adapter = api.http.defaults.adapter;
  api.http.defaults.adapter = async (config) => {
    throw {
      isAxiosError: true,
      config,
      response: { status: 409, data: { code: 'REPORT_ALREADY_CONCLUDED', message: 'Đã xử lý' } },
    };
  };
  try {
    await assert.rejects(
      api.reportApi.conclude(71, { decision: 'UPHOLD' }),
      (error) => error.status === 409 && error.code === 'REPORT_ALREADY_CONCLUDED',
    );
  } finally {
    api.http.defaults.adapter = adapter;
  }
});

await test('giữ API mở khiếu nại giao dịch và giải trình của màn người dùng', async () => {
  requests = [];
  const input = { reason: 'NOT_AS_DESCRIBED', note: 'Đồ khác mô tả', evidenceMediaIds: [12] };
  nextResponse = { id: 71, responseDueAt: '2026-10-23T03:00:00Z' };
  assert.deepEqual(await api.reportApi.openDispute(88, input), nextResponse);
  assert.equal(requests[0].url, '/api/v1/requests/88/disputes');
  assert.deepEqual(requests[0].body, input);
  const response = { note: 'Giải trình', evidenceMediaIds: [15] };
  assert.equal(await api.reportApi.respondDispute(71, response), undefined);
  assert.equal(requests[1].url, '/api/v1/disputes/71/response');
  assert.deepEqual(requests[1].body, response);
});

await test('nhật ký đọc được cả metadata page mới và response Spring cũ', async () => {
  nextResponse = {
    content: [{ id: 1, actorName: 'Người duyệt', typeLabel: 'Duyệt bài' }],
    page: { number: 1, size: 20, totalElements: 45, totalPages: 3 },
  };
  const page = await api.adminAuditApi.list({ page: 1 });
  assert.equal(page.totalElements, 45);
  assert.equal(page.totalPages, 3);
  assert.equal(page.content[0].actorName, 'Người duyệt');
  assert.equal(page.last, false);
  nextResponse = { content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 };
  assert.equal((await api.adminAuditApi.list()).totalElements, 0);
});

await test('báo cáo dùng ngày lịch; tra cứu lịch sử có phân trang; khóa gửi lý do', async () => {
  requests = [];
  await api.adminFinanceApi.report({ from: '2026-10-01', to: '2026-10-19' });
  assert.deepEqual(requests[0].params, { from: '2026-10-01', to: '2026-10-19' });
  await api.adminFinanceApi.userHistory(9, { page: 2, size: 20 });
  assert.equal(requests[1].url, '/api/v1/admin/finance/users/9/history');
  assert.deepEqual(requests[1].params, { page: 2, size: 20 });
  await api.adminFinanceApi.setLocked(9, true, 'Đã xác minh vi phạm');
  assert.equal(requests[2].url, '/api/v1/admin/users/9/lock');
  assert.deepEqual(requests[2].body, { reason: 'Đã xác minh vi phạm' });
  await api.adminFinanceApi.setLocked(9, false, 'Đã giải trình');
  assert.equal(requests[3].url, '/api/v1/admin/users/9/unlock');
});

await test('AWAITING_LOGISTICS vẫn cho sửa lịch, các trạng thái sau chốt vẫn cho chat', () => {
  assert.equal(api.canSchedule('RESERVED'), true);
  assert.equal(api.canSchedule('AWAITING_LOGISTICS'), true);
  for (const status of [
    'PENDING',
    'LOGISTICS_CONFIRMED',
    'AWAITING_HANDOVER',
    'COMPLETED',
    'DISPUTED',
    'REJECTED',
    'CANCELLED',
  ])
    assert.equal(api.canSchedule(status), false);
  for (const status of [
    'RESERVED',
    'AWAITING_LOGISTICS',
    'LOGISTICS_CONFIRMED',
    'AWAITING_HANDOVER',
    'COMPLETED',
    'DISPUTED',
  ])
    assert.equal(api.canChat(status), true);
  for (const status of ['PENDING', 'REJECTED', 'CANCELLED'])
    assert.equal(api.canChat(status), false);
});

await test('chỉ mở xác nhận giao nhận ở LOGISTICS_CONFIRMED hoặc AWAITING_HANDOVER', () => {
  for (const status of Object.keys(api.requestStatusLabel)) {
    assert.equal(
      api.canConfirmHandover(status),
      ['LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER'].includes(status),
    );
  }
  assert.equal(
    api.toFeItemRequest({ id: 9, receiverId: 2, itemId: 3, type: 'GIVE', status: 'COMPLETED' })
      .status,
    'COMPLETED',
  );
});

await test('bên nhận giữ nguyên lựa chọn không đúng mô tả và lý do khi xác nhận', async () => {
  requests = [];
  await api.requestApi.confirmHandover(101, 7, {
    asDescribed: false,
    notAsDescribedReason: 'Màn hình bị vỡ',
  });
  assert.equal(requests[0].url, '/api/v1/requests/101/confirm-handover');
  assert.deepEqual(requests[0].body, {
    asDescribed: false,
    notAsDescribedReason: 'Màn hình bị vỡ',
  });
});

await test('khởi tạo bỏ qua dữ liệu nghiệp vụ cũ và số dư lưu trên trình duyệt', () => {
  memory.set(
    'shareloop:v1:state',
    JSON.stringify({ currentUserId: 'user_001', users: [{ availableCredit: 999999 }] }),
  );
  const state = api.loadPersistedState();
  assert.equal(state.currentUserId, null);
  for (const key of ['users', 'items', 'transactions', 'creditHistory', 'topups', 'complaints'])
    assert.deepEqual(state[key], []);
});

await test('lỗi 403 và checklist cũ giữ code để giao diện xử lý', async () => {
  const originalAdapter = api.http.defaults.adapter;
  try {
    api.http.defaults.adapter = async (config) =>
      Promise.reject({
        response: { status: 403, data: { code: 'FORBIDDEN', message: 'Không có quyền' } },
        config,
      });
    await assert.rejects(
      api.adminFinanceApi.report({}),
      (error) => error.status === 403 && error.code === 'FORBIDDEN',
    );
    api.http.defaults.adapter = async (config) =>
      Promise.reject({
        response: {
          status: 409,
          data: { code: 'REVIEW_CHECKLIST_OUTDATED', message: 'Checklist đã thay đổi' },
        },
        config,
      });
    await assert.rejects(
      api.adminModerationApi.approve(1, { answers: [{ id: 11, passed: true }] }),
      (error) => error.code === 'REVIEW_CHECKLIST_OUTDATED',
    );
  } finally {
    api.http.defaults.adapter = originalAdapter;
  }
});

await test('authenticity xử lý 204 không body và validate lý do reject/revoke', async () => {
  requests = [];
  nextResponse = undefined;
  assert.equal(await api.adminAuthenticityApi.grant(7), undefined);
  assert.equal(api.validateAuthenticityReason('grant', ''), null);
  assert.equal(api.validateAuthenticityReason('reject', '   '), 'Lý do là bắt buộc.');
  assert.equal(api.validateAuthenticityReason('revoke', ''), 'Lý do là bắt buộc.');
  assert.equal(api.validateAuthenticityReason('reject', 'Chứng từ không khớp'), null);
});

await test('authenticity không submit trước xác nhận và chặn double-submit khi đang loading', () => {
  const lock = { current: false };
  let requestCount = 0;
  if (api.beginAuthenticitySubmission(lock, false)) requestCount += 1;
  assert.equal(requestCount, 0);
  assert.equal(lock.current, false);
  assert.equal(api.beginAuthenticitySubmission(lock, true), true);
  requestCount += 1;
  assert.equal(api.beginAuthenticitySubmission(lock, true), false);
  assert.equal(requestCount, 1);
});

await test('authenticity ánh xạ lỗi HTTP 400/401/403/404/409 và 500', () => {
  const expected = new Map([
    [400, 'Dữ liệu hoặc lý do không hợp lệ.'],
    [401, 'Bạn chưa đăng nhập hoặc phiên đăng nhập đã hết hạn.'],
    [403, 'Tài khoản hiện tại không có quyền Admin.'],
    [404, 'Không tìm thấy bài đăng.'],
    [409, 'Trạng thái bài đăng không cho phép thực hiện thao tác này.'],
    [500, 'Hệ thống đang gặp lỗi. Vui lòng thử lại.'],
  ]);
  for (const [status, message] of expected) {
    assert.equal(api.authenticityErrorMessage({ status }), message);
  }
});
