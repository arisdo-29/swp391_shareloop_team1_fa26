import { usePrivateMediaUrl } from '../utils/usePrivateMediaUrl';

/**
 * Ảnh riêng tư của giao dịch (ảnh chat, ảnh giao nhận): tải bằng token rồi hiện. Đang tải hiện khung chờ, lỗi thì hiện
 * câu ngắn thay vì ảnh vỡ.
 */
export function PrivateImage({
  path,
  alt,
  className = '',
}: {
  path: string | null | undefined;
  alt: string;
  className?: string;
}) {
  const { url, loading, failed } = usePrivateMediaUrl(path);
  if (url) return <img src={url} alt={alt} className={className} />;
  if (failed) {
    return (
      <span role="img" aria-label={alt} className={`grid place-items-center bg-surface-low p-2 text-center text-2xs text-text-muted ${className}`}>
        Không tải được ảnh
      </span>
    );
  }
  return (
    <span
      role="img"
      aria-label={loading ? 'Đang tải ảnh' : alt}
      className={`block animate-pulse bg-surface-low ${className}`}
    />
  );
}
