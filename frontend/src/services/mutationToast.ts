import type { ApiError } from './http';
import type { ToastTone } from '../components/toast';

export interface MutationToastDefinition {
  success?: string;
  failure: string;
  tone?: Exclude<ToastTone, 'error'>;
}

function matches(path: string, pattern: RegExp) {
  return pattern.test(path);
}

export function mutationToastDefinition(
  url: string | undefined,
  method: string | undefined,
): MutationToastDefinition | null {
  const verb = method?.toLowerCase();
  if (!url || !verb || !['post', 'put', 'patch', 'delete'].includes(verb)) return null;
  const path = new URL(url, 'https://shareloop.local').pathname.replace(/\/+$/, '');

  if (matches(path, /\/api\/v1\/notifications\/\d+\/read$/)) return null;
  if (matches(path, /\/api\/v1\/ai\/(describe|search)$/)) return null;
  if (matches(path, /\/api\/v1\/admin\/lexicon\/test$/)) return null;

  const exact: Record<string, MutationToastDefinition> = {
    '/api/v1/auth/login': {
      failure: 'Không đăng nhập được. Vui lòng kiểm tra thông tin và thử lại.',
    },
    '/api/v1/auth/register': {
      success: 'Đăng ký thành công. Vui lòng xác thực tài khoản.',
      failure: 'Không đăng ký được tài khoản.',
    },
    '/api/v1/auth/verify-otp': {
      success: 'Xác thực tài khoản thành công.',
      failure: 'Không xác thực được tài khoản.',
    },
    '/api/v1/auth/resend-otp': {
      success: 'Đã gửi lại mã xác thực.',
      failure: 'Không gửi lại được mã xác thực.',
    },
    '/api/v1/auth/logout': {
      failure: 'Không thể kết nối máy chủ khi đăng xuất.',
    },
    '/api/v1/auth/change-password': {
      success: 'Đổi mật khẩu thành công.',
      failure: 'Không đổi được mật khẩu.',
    },
    '/api/v1/auth/forgot-password': {
      success: 'Đã gửi mã đặt lại mật khẩu.',
      failure: 'Không gửi được mã đặt lại mật khẩu.',
    },
    '/api/v1/auth/reset-password': {
      success: 'Đặt lại mật khẩu thành công.',
      failure: 'Không đặt lại được mật khẩu.',
    },
    '/api/v1/items': {
      success: verb === 'post' ? 'Đăng bài thành công.' : 'Cập nhật bài đăng thành công.',
      failure: verb === 'post' ? 'Không đăng được bài.' : 'Không cập nhật được bài đăng.',
    },
    '/api/v1/media': {
      failure: 'Không tải được ảnh lên.',
    },
    '/api/v1/reports': {
      success: 'Gửi báo cáo thành công.',
      failure: 'Không gửi được báo cáo.',
    },
    '/api/v1/me': {
      success: 'Cập nhật hồ sơ thành công.',
      failure: 'Không cập nhật được hồ sơ.',
    },
    '/api/v1/me/phone/otp': {
      success: 'Đã gửi mã xác thực số điện thoại.',
      failure: 'Không gửi được mã xác thực số điện thoại.',
    },
    '/api/v1/me/phone': {
      success: 'Đổi số điện thoại thành công.',
      failure: 'Không đổi được số điện thoại.',
    },
  };
  if (exact[path]) return exact[path];

  if (matches(path, /\/api\/v1\/items\/\d+\/requests$/)) {
    return { success: 'Gửi yêu cầu thành công.', failure: 'Không gửi được yêu cầu.' };
  }
  if (matches(path, /\/api\/v1\/items\/\d+\/renew$/)) {
    return { success: 'Gia hạn bài đăng thành công.', failure: 'Không gia hạn được bài đăng.' };
  }
  // Đẩy bài: "Bài của tôi" tự báo kèm hạn đẩy và số dư, lỗi thì hiện popup lý do; không thêm toast chung trùng lặp.
  if (matches(path, /\/api\/v1\/items\/\d+\/boost$/)) return null;
  if (matches(path, /\/api\/v1\/items\/\d+$/)) {
    if (verb === 'delete') {
      return { success: 'Đã gỡ bài đăng.', failure: 'Không gỡ được bài đăng.', tone: 'warning' };
    }
    return { success: 'Cập nhật bài đăng thành công.', failure: 'Không cập nhật được bài đăng.' };
  }
  if (matches(path, /\/api\/v1\/requests\/\d+\/messages$/)) {
    return { success: 'Gửi tin nhắn thành công.', failure: 'Không gửi được tin nhắn.' };
  }
  if (matches(path, /\/api\/v1\/requests\/\d+\/cancel$/)) {
    return { success: 'Đã hủy yêu cầu.', failure: 'Không hủy được yêu cầu.', tone: 'warning' };
  }
  if (matches(path, /\/api\/v1\/requests\/\d+\/select$/)) {
    return {
      success: 'Chọn người giao dịch thành công.',
      failure: 'Không chọn được người giao dịch.',
    };
  }
  if (matches(path, /\/api\/v1\/requests\/\d+\/schedule$/)) {
    return { success: 'Gửi lịch giao nhận thành công.', failure: 'Không gửi được lịch giao nhận.' };
  }
  if (matches(path, /\/api\/v1\/requests\/\d+\/confirm-logistics$/)) {
    return {
      success: 'Xác nhận lịch giao nhận thành công.',
      failure: 'Không xác nhận được lịch giao nhận.',
    };
  }
  if (matches(path, /\/api\/v1\/requests\/\d+\/cancel-transaction$/)) {
    return { success: 'Đã hủy giao dịch.', failure: 'Không hủy được giao dịch.', tone: 'warning' };
  }
  if (matches(path, /\/api\/v1\/requests\/\d+\/confirm-handover$/)) {
    return {
      success: 'Xác nhận hoàn tất giao nhận thành công.',
      failure: 'Không xác nhận được giao nhận.',
    };
  }
  if (matches(path, /\/api\/v1\/requests\/\d+\/handover-issue$/)) {
    return {
      success: 'Đã gửi báo cáo sự cố giao nhận.',
      failure: 'Không gửi được báo cáo sự cố.',
      tone: 'warning',
    };
  }
  if (matches(path, /\/api\/v1\/requests\/\d+\/media$/)) {
    return { success: 'Đính kèm ảnh thành công.', failure: 'Không đính kèm được ảnh.' };
  }
  if (matches(path, /\/api\/v1\/requests\/\d+\/disputes$/)) {
    return {
      success: 'Gửi khiếu nại thành công.',
      failure: 'Không gửi được khiếu nại.',
      tone: 'warning',
    };
  }
  if (matches(path, /\/api\/v1\/(disputes|reports)\/\d+\/response$/)) {
    return { success: 'Gửi giải trình thành công.', failure: 'Không gửi được giải trình.' };
  }
  // Các trang Admin này tự hiển thị phản hồi cụ thể ngay tại nơi thao tác.
  // Không phát Toast từ interceptor để tránh lặp cả thông báo thành công lẫn lỗi.
  if (
    matches(path, /\/api\/v1\/admin\/reviews\/\d+\/(approve|reject)$/) ||
    matches(path, /\/api\/v1\/admin\/users\/\d+\/(lock|unlock)$/) ||
    matches(path, /\/api\/v1\/admin\/requests\/\d+\/cancel$/) ||
    matches(path, /\/api\/v1\/admin\/reports\/\d+\/(request-response|conclude)$/) ||
    matches(path, /\/api\/v1\/admin\/checklist(?:\/.*)?$/) ||
    matches(path, /\/api\/v1\/admin\/(configs|categories|areas|lexicon)(?:\/.*)?$/)
  ) {
    return null;
  }
  if (matches(path, /\/api\/v1\/payments\/topup/)) {
    return {
      success: 'Cập nhật giao dịch nạp Credit thành công.',
      failure: 'Không cập nhật được giao dịch nạp Credit.',
    };
  }
  if (path.startsWith('/api/v1/admin/')) {
    return {
      failure: 'Không thực hiện được thao tác quản trị.',
    };
  }
  return { success: 'Thao tác thành công.', failure: 'Không thực hiện được thao tác.' };
}

export function mutationErrorMessage(error: ApiError, fallback: string) {
  if (error.status === 0) return 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';
  if (error.status === 401) return '';
  if (error.status === 403) return 'Bạn không có quyền thực hiện thao tác này.';
  if (error.status >= 500) return 'Máy chủ đang gặp sự cố. Vui lòng thử lại sau.';
  return error.message?.trim() || fallback;
}
