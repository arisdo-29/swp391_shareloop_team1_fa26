import { describe, expect, it } from 'vitest';
import { mutationErrorMessage, mutationToastDefinition } from './mutationToast';

describe('mutationToastDefinition', () => {
  it.each([
    ['/api/v1/items', 'post', 'Đăng bài thành công.'],
    ['/api/v1/items/12', 'delete', 'Đã gỡ bài đăng.'],
    ['/api/v1/items/12/renew', 'post', 'Gia hạn bài đăng thành công.'],
    ['/api/v1/items/12/requests', 'post', 'Gửi yêu cầu thành công.'],
    ['/api/v1/requests/7/select', 'post', 'Chọn người giao dịch thành công.'],
    ['/api/v1/requests/7/schedule', 'post', 'Gửi lịch giao nhận thành công.'],
    ['/api/v1/requests/7/confirm-logistics', 'post', 'Xác nhận lịch giao nhận thành công.'],
    ['/api/v1/requests/7/confirm-handover', 'post', 'Xác nhận hoàn tất giao nhận thành công.'],
    ['/api/v1/requests/7/messages', 'post', 'Gửi tin nhắn thành công.'],
    ['/api/v1/requests/7/disputes', 'post', 'Gửi khiếu nại thành công.'],
    ['/api/v1/reports', 'post', 'Gửi báo cáo thành công.'],
  ])('ánh xạ %s thành thông báo cụ thể', (url, method, message) => {
    expect(mutationToastDefinition(url, method)?.success).toBe(message);
  });

  it('không tạo thông báo thành công khi đăng nhập hoặc đăng xuất nhưng vẫn giữ thông báo lỗi', () => {
    const login = mutationToastDefinition('/api/v1/auth/login', 'post');
    const logout = mutationToastDefinition('/api/v1/auth/logout', 'post');

    expect(login?.success).toBeUndefined();
    expect(login?.failure).toBe('Không đăng nhập được. Vui lòng kiểm tra thông tin và thử lại.');
    expect(logout?.success).toBeUndefined();
    expect(logout?.failure).toBe('Không thể kết nối máy chủ khi đăng xuất.');
  });

  it('không tạo thông báo thành công khi tải ảnh nhưng vẫn giữ thông báo lỗi', () => {
    const mediaUpload = mutationToastDefinition('/api/v1/media', 'post');

    expect(mediaUpload?.success).toBeUndefined();
    expect(mediaUpload?.failure).toBe('Không tải được ảnh lên.');
  });

  it.each([
    ['/api/v1/admin/configs/post_fee', 'put'],
    ['/api/v1/admin/categories/1', 'put'],
    ['/api/v1/admin/areas/1/active', 'patch'],
    ['/api/v1/admin/lexicon/1', 'delete'],
    ['/api/v1/admin/checklist/4', 'put'],
    ['/api/v1/admin/reviews/8/approve', 'post'],
    ['/api/v1/admin/reviews/8/reject', 'post'],
    ['/api/v1/admin/users/9/lock', 'post'],
    ['/api/v1/admin/users/9/unlock', 'post'],
    ['/api/v1/admin/requests/7/cancel', 'post'],
    ['/api/v1/admin/reports/6/request-response', 'post'],
    ['/api/v1/admin/reports/6/conclude', 'post'],
  ])('không tạo Toast trùng với phản hồi tại trang cho %s', (url, method) => {
    expect(mutationToastDefinition(url, method)).toBeNull();
  });

  it('không có success fallback cho mutation Admin chưa được ánh xạ', () => {
    const fallback = mutationToastDefinition('/api/v1/admin/future-action', 'post');

    expect(fallback?.success).toBeUndefined();
    expect(fallback?.failure).toBe('Không thực hiện được thao tác quản trị.');
  });

  it('không tạo Toast cho request đọc dữ liệu hoặc thao tác nền', () => {
    expect(mutationToastDefinition('/api/v1/items', 'get')).toBeNull();
    expect(mutationToastDefinition('/api/v1/notifications/3/read', 'post')).toBeNull();
    expect(mutationToastDefinition('/api/v1/ai/search', 'post')).toBeNull();
    // Đẩy bài tự báo kèm hạn đẩy và số dư ở "Bài của tôi"
    expect(mutationToastDefinition('/api/v1/items/12/boost', 'post')).toBeNull();
  });
});

describe('mutationErrorMessage', () => {
  it('ưu tiên thông báo dễ hiểu cho lỗi mạng, phân quyền và máy chủ', () => {
    const base = { code: 'UNKNOWN', message: '', status: 0 };
    expect(mutationErrorMessage(base, 'Lỗi')).toContain('Không kết nối được máy chủ');
    expect(mutationErrorMessage({ ...base, status: 403 }, 'Lỗi')).toContain('không có quyền');
    expect(mutationErrorMessage({ ...base, status: 500 }, 'Lỗi')).toContain(
      'Máy chủ đang gặp sự cố',
    );
  });
});
