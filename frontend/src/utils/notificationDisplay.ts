import type { IconName } from '../components/ui';
import type { NotificationResponse } from '../services/notificationApi';

/** Link trong thông báo chỉ được là đường dẫn nội bộ, không cho mở trang ngoài. */
export function isInternalLink(link: string | null | undefined): link is string {
  return Boolean(link?.startsWith('/') && !link.startsWith('//') && !link.includes('\\'));
}

/**
 * Nơi mở ra khi bấm một thông báo; null nếu thông báo không có link nội bộ.
 *
 * B24: BOOST_ENDING_SOON mới có link /activities?boost={id}&until=... (trang Hoạt động mở sẵn hộp Đẩy bài). Thông báo
 * gửi trước bản này có link /items/{id}, nên đổi sang cùng đích để bấm vào vẫn đẩy tiếp được.
 */
export function notificationTarget(
  notification: Pick<NotificationResponse, 'type' | 'link'>,
): string | null {
  const { link, type } = notification;
  if (!isInternalLink(link)) return null;
  if (type === 'BOOST_ENDING_SOON') {
    const legacy = /^\/items\/(\d+)$/.exec(link);
    if (legacy) return `/activities?boost=${legacy[1]}`;
  }
  return link;
}

/** Chữ trên link của thông báo: loại nhắc thao tác thì ghi thẳng việc cần làm. */
export function notificationActionLabel(type: string): string {
  if (type === 'BOOST_ENDING_SOON') return 'Đẩy tiếp bài';
  if (isExpiringReminder(type)) return 'Gia hạn bài';
  return 'Xem chi tiết';
}

/** Icon riêng cho loại thông báo nhắc việc về đợt đẩy và hạn hiển thị; loại khác không có icon riêng. */
export function notificationIcon(type: string): IconName | null {
  if (type.startsWith('BOOST_')) return 'boost';
  if (isExpiringReminder(type)) return 'expiring';
  return null;
}

// B19: nhắc gia hạn 48 giờ và 24 giờ trước khi bài hết hạn; link /activities?renew={id}&until=... mở hộp Gia hạn.
function isExpiringReminder(type: string) {
  return type === 'ITEM_EXPIRING_48H' || type === 'ITEM_EXPIRING_24H';
}
