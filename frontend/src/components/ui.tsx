import type {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react';
import { useEffect, useId, useState } from 'react';
import type { IconWeight } from '@phosphor-icons/react';
import { X } from '@phosphor-icons/react/dist/csr/X';
import { List } from '@phosphor-icons/react/dist/csr/List';
import { Eye } from '@phosphor-icons/react/dist/csr/Eye';
import { EyeSlash } from '@phosphor-icons/react/dist/csr/EyeSlash';
import { CaretDown } from '@phosphor-icons/react/dist/csr/CaretDown';
import { Bell } from '@phosphor-icons/react/dist/csr/Bell';
import { UserCircle } from '@phosphor-icons/react/dist/csr/UserCircle';
import { Image } from '@phosphor-icons/react/dist/csr/Image';
import { Link } from 'react-router-dom';
import type { Item, ItemCondition, ItemType, TransactionStatus, User } from '../types/domain';
import { conditionLabel, txLabel } from '../utils/formatting';
import { resolveMediaUrl } from '../services/mediaUrl';

// Chỉ giữ icon có chức năng (đóng, mở menu, hiện mật khẩu, mở danh sách, thông báo, đính kèm ảnh).
// Icon trang trí như mũi tên, kính lúp, dấu cộng đã bỏ: nút và nhãn dùng chữ.
const iconMap = {
  close: X,
  menu: List,
  eye: Eye,
  eyeSlash: EyeSlash,
  chevronDown: CaretDown,
  bell: Bell,
  user: UserCircle,
  image: Image,
} as const;

export type IconName = keyof typeof iconMap;

export function Icon({
  name,
  className = '',
  weight = 'regular',
}: {
  name: IconName;
  className?: string;
  weight?: IconWeight;
}) {
  const Glyph = iconMap[name];
  return <Glyph aria-hidden className={className} weight={weight} />;
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg';
};
export function Button({
  variant = 'primary',
  size = 'md',
  children,
  className = '',
  ...props
}: ButtonProps) {
  const variants = {
    primary:
      'border-primary bg-primary text-white hover:border-primary-hover hover:bg-primary-hover',
    secondary:
      'border-secondary bg-secondary text-white hover:border-secondary-hover hover:bg-secondary-hover',
    outline:
      'border-border bg-white text-text-secondary hover:border-primary/50 hover:bg-primary-faint hover:text-primary',
    ghost:
      'border-transparent bg-transparent text-text-secondary hover:bg-surface-low hover:text-text-primary',
    danger: 'border-error/20 bg-error-soft text-error hover:border-error/40 hover:bg-error/15',
  };
  // Cỡ sm vẫn cao 44px để đủ vùng bấm trên màn cảm ứng, chỉ khác ở chữ và đệm ngang.
  const sizes = {
    sm: 'min-h-11 px-3 py-1.5 text-xs rounded-md',
    md: 'min-h-11 px-4 py-2 text-sm rounded-md',
    lg: 'min-h-12 px-5 py-3 text-sm rounded-md',
  };
  return (
    <button
      className={`inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap border font-semibold transition duration-200 active:translate-y-px disabled:cursor-not-allowed disabled:opacity-40 ${variants[variant]} ${sizes[size]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}

export function IconButton({
  icon,
  label,
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { icon: IconName; label: string }) {
  return (
    <button
      aria-label={label}
      title={label}
      className={`inline-flex size-11 shrink-0 items-center justify-center rounded-md border border-border bg-white text-text-secondary transition hover:border-primary/50 hover:bg-primary-faint hover:text-primary active:translate-y-px disabled:cursor-not-allowed disabled:opacity-40 ${className}`}
      {...props}
    >
      <Icon name={icon} className="size-5" />
    </button>
  );
}

const errorInputClass = 'border-error focus:border-error';

/** Dòng lỗi hoặc gợi ý dưới ô nhập. Lỗi có role="alert" để trình đọc màn hình đọc ngay khi xuất hiện. */
function FieldMessage({ id, error, hint }: { id: string; error?: string; hint?: string }) {
  if (error) {
    return (
      <span id={id} role="alert" className="mt-1.5 block text-xs text-error">
        {error}
      </span>
    );
  }
  if (hint) {
    return (
      <span id={id} className="mt-1.5 block text-xs text-text-muted">
        {hint}
      </span>
    );
  }
  return null;
}

export function Field(
  props: InputHTMLAttributes<HTMLInputElement> & { label?: string; error?: string; hint?: string },
) {
  const { label, error, hint, className = '', ...rest } = props;
  // useId sinh id duy nhất để aria-describedby trỏ đúng dòng lỗi/gợi ý của ô này.
  const messageId = useId();
  const hasMessage = Boolean(error || hint);
  return (
    <label className="block min-w-0">
      {label ? <span className="label">{label}</span> : null}
      <input
        className={`input ${error ? errorInputClass : ''} ${className}`}
        aria-invalid={error ? true : undefined}
        aria-describedby={hasMessage ? messageId : undefined}
        {...rest}
      />
      <FieldMessage id={messageId} error={error} hint={hint} />
    </label>
  );
}
export function TextArea(
  props: TextareaHTMLAttributes<HTMLTextAreaElement> & {
    label?: string;
    error?: string;
    hint?: string;
  },
) {
  const { label, error, hint, className = '', ...rest } = props;
  const messageId = useId();
  const hasMessage = Boolean(error || hint);
  return (
    <label className="block">
      {label ? <span className="label">{label}</span> : null}
      <textarea
        className={`input min-h-28 resize-y ${error ? errorInputClass : ''} ${className}`}
        aria-invalid={error ? true : undefined}
        aria-describedby={hasMessage ? messageId : undefined}
        {...rest}
      />
      <FieldMessage id={messageId} error={error} hint={hint} />
    </label>
  );
}
export function Select(
  props: SelectHTMLAttributes<HTMLSelectElement> & {
    label?: string;
    error?: string;
    hint?: string;
  },
) {
  const { label, error, hint, className = '', children, ...rest } = props;
  const messageId = useId();
  const hasMessage = Boolean(error || hint);
  return (
    <label className="block">
      {label ? <span className="label">{label}</span> : null}
      <select
        className={`input ${error ? errorInputClass : ''} ${className}`}
        aria-invalid={error ? true : undefined}
        aria-describedby={hasMessage ? messageId : undefined}
        {...rest}
      >
        {children}
      </select>
      <FieldMessage id={messageId} error={error} hint={hint} />
    </label>
  );
}
export function SearchField(props: InputHTMLAttributes<HTMLInputElement>) {
  // Ô tìm kiếm không có viền riêng; wrapper đổi sang một viền xanh khi focus.
  return (
    <div className="flex min-h-11 items-center rounded-md border border-border bg-white px-1 transition hover:border-text-muted focus-within:border-primary">
      <input
        className="w-full border-0 bg-transparent px-2.5 py-2 text-sm outline-none placeholder:text-text-muted"
        {...props}
      />
    </div>
  );
}

export function TypeBadge({ type }: { type: ItemType }) {
  return (
    <span
      className={`inline-flex items-center rounded-sm px-2 py-1 text-2xs font-bold ${type === 'gift' ? 'bg-primary-soft text-primary' : 'bg-secondary-soft text-secondary-hover'}`}
    >
      {type === 'gift' ? 'Cho tặng' : 'Trao đổi'}
    </span>
  );
}
export function ConditionBadge({ condition }: { condition: ItemCondition }) {
  return (
    <span className="inline-flex shrink-0 whitespace-nowrap rounded-sm bg-surface-low px-2 py-1 text-2xs font-medium text-text-secondary">
      {conditionLabel[condition]}
    </span>
  );
}
// Nhãn riêng của bài đăng; được ưu tiên hơn nhãn giao dịch (txLabel) như trước đây.
const itemStatusLabel: Record<string, string> = {
  RESERVED: 'Đã giữ chỗ',
  TRADED: 'Đã trao đổi',
  EXPIRED: 'Hết hạn',
};
// Nhãn cho các trạng thái còn lại (bài đăng viết thường, tài khoản, kiểm duyệt).
const otherStatusLabel: Record<string, string> = {
  approved: 'Đã duyệt',
  APPROVED: 'Đã duyệt',
  pending: 'Chờ duyệt',
  PENDING_REVIEW: 'Chờ duyệt',
  rejected: 'Từ chối',
  REJECTED: 'Từ chối',
  VIOLATION: 'Vi phạm',
  expired: 'Hết hạn',
  IN_TRANSACTION: 'Đang giao dịch',
  removed: 'Đã gỡ',
  locked: 'Đã khóa',
  suspended: 'Tạm khóa',
};
const dangerStatuses = ['rejected', 'REJECTED', 'VIOLATION', 'removed', 'locked', 'DISPUTED', 'CANCELLED'];
const okStatuses = ['approved', 'APPROVED', 'active', 'COMPLETED', 'TRADED'];

function statusLabel(status: string): string {
  if (status in itemStatusLabel) return itemStatusLabel[status];
  if (status in txLabel) return txLabel[status as TransactionStatus];
  return otherStatusLabel[status] ?? 'Hoạt động';
}

function statusTone(status: string): string {
  if (dangerStatuses.includes(status)) return 'bg-error-soft text-error';
  if (okStatuses.includes(status)) return 'bg-success-soft text-success';
  return 'bg-warning-soft text-warning';
}

export function StatusBadge({
  status,
}: {
  status: TransactionStatus | Item['status'] | User['status'];
}) {
  return (
    <span
      className={`inline-flex whitespace-nowrap rounded-sm px-2 py-1 text-2xs font-semibold ${statusTone(status)}`}
    >
      {statusLabel(status)}
    </span>
  );
}
export function RankBadge({ rank }: { rank: string }) {
  return (
    <span className="inline-flex rounded-sm bg-primary-soft px-2.5 py-1 text-xs font-semibold text-primary">
      {rank}
    </span>
  );
}

export function EmptyState({
  title,
  text,
  action,
}: {
  title: string;
  text: string;
  action?: ReactNode;
}) {
  return (
    <div className="rounded-lg border border-dashed border-border bg-white px-6 py-12 text-center">
      <h3 className="text-base font-bold">{title}</h3>
      <p className="mx-auto mt-1.5 max-w-md text-sm leading-6 text-text-muted">{text}</p>
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}
export function LoadingState() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {[0, 1, 2, 3].map((i) => (
        <div
          key={i}
          className="animate-pulse overflow-hidden rounded-lg bg-white ring-1 ring-border/70"
        >
          <div className="aspect-[4/3] bg-surface-container" />
          <div className="space-y-3 p-4">
            <div className="h-4 rounded bg-surface-container" />
            <div className="h-3 w-2/3 rounded bg-surface-low" />
          </div>
        </div>
      ))}
    </div>
  );
}

/** Vòng xoay chờ nhỏ, thay cho icon mũi tên quay. */
export function Spinner({ className = 'size-5' }: { className?: string }) {
  return (
    <span
      aria-hidden
      className={`inline-block shrink-0 animate-spin rounded-full border-2 border-current border-r-transparent ${className}`}
    />
  );
}

/** Khối chờ một hình chữ nhật; đặt kích thước bằng className, ví dụ "h-4 w-2/3". */
export function Skeleton({ className = '' }: { className?: string }) {
  return <div aria-hidden className={`animate-pulse rounded-md bg-surface-container ${className}`} />;
}

/** Các hàng chờ cho bảng hoặc danh sách chat. Một vùng role="status" duy nhất để trình đọc báo "đang tải". */
export function SkeletonRows({ rows = 5, label = 'Đang tải dữ liệu' }: { rows?: number; label?: string }) {
  return (
    <div role="status" aria-label={label} className="space-y-3">
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="flex items-center gap-3">
          <Skeleton className="size-10 shrink-0" />
          <div className="min-w-0 flex-1 space-y-2">
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-3 w-1/3" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function ItemImage({
  src,
  alt,
  className = '',
  loading,
  fetchPriority,
}: {
  src?: string;
  alt: string;
  className?: string;
  loading?: 'eager' | 'lazy';
  fetchPriority?: 'high' | 'low' | 'auto';
}) {
  const imageSrc = resolveMediaUrl(src);
  const [failedSrc, setFailedSrc] = useState<string | null>(null);

  return imageSrc && failedSrc !== imageSrc ? (
    <img
      src={imageSrc}
      alt={alt}
      loading={loading}
      decoding="async"
      fetchPriority={fetchPriority}
      className={className}
      onError={() => setFailedSrc(imageSrc)}
    />
  ) : (
    <div role="img" aria-label={alt} className={`grid place-items-center bg-surface-container text-xs font-medium text-text-muted ${className}`}>
      Chưa có ảnh
    </div>
  );
}

export function Alert({
  tone = 'info',
  children,
}: {
  tone?: 'info' | 'success' | 'warning' | 'error';
  children: ReactNode;
}) {
  const cls = {
    info: 'border-info/20 bg-info-soft text-info',
    success: 'border-success/20 bg-success-soft text-success',
    warning: 'border-warning/20 bg-warning-soft text-warning',
    error: 'border-error/20 bg-error-soft text-error',
  };
  return (
    <div className={`rounded-md border px-4 py-3 text-sm leading-6 ${cls[tone]}`}>{children}</div>
  );
}

export function ProductCard({ item, owner }: { item: Item; owner?: User }) {
  return (
    <Link
      to={`/items/${item.id}`}
      className="group block min-w-0 overflow-hidden rounded-lg bg-surface ring-1 ring-border/80 transition duration-300 hover:-translate-y-1 hover:shadow-md hover:ring-primary/20"
    >
      <div className="relative aspect-[4/3] overflow-hidden bg-surface-container">
        <ItemImage
          src={item.images[0]}
          alt={item.title}
          loading="lazy"
          className="h-full w-full object-cover transition duration-500 group-hover:scale-[1.03]"
        />
        <div className="absolute left-3 top-3">
          <TypeBadge type={item.type} />
        </div>
      </div>
      <div className="p-3 sm:p-4">
        <h3 className="line-clamp-2 min-h-10 text-sm font-bold leading-5 text-text-primary sm:min-h-11 sm:text-[15px] sm:leading-[1.45]">
          {item.title}
        </h3>
        <p className="mt-2 line-clamp-2 text-xs leading-5 text-text-muted">
          {item.description}
        </p>
        <div className="mt-3 flex min-w-0 items-center gap-2">
          <ConditionBadge condition={item.condition} />
          <span className="min-w-0 truncate text-xs text-text-muted">{item.category}</span>
        </div>
        {owner ? (
          <div className="mt-3 flex min-w-0 items-center gap-2 text-xs font-semibold text-text-secondary">
            <Avatar user={owner} size="sm" />
            <span className="min-w-0 truncate">{owner.name}</span>
          </div>
        ) : null}
        <div className="mt-3 flex items-center justify-between gap-2 border-t border-border/60 pt-3 text-xs text-text-muted">
          <span className="min-w-0 truncate">{item.district}</span>
          {owner ? (
            <span className="shrink-0 font-semibold text-text-secondary">
              {owner.reputationStars ? `${owner.reputationStars.toLocaleString('vi-VN')} sao` : 'Chưa có đánh giá'}
            </span>
          ) : null}
        </div>
      </div>
    </Link>
  );
}
export function HorizontalProductCard({ item, owner }: { item: Item; owner?: User }) {
  return (
    <Link
      to={`/items/${item.id}`}
      className="group flex min-w-0 gap-3 rounded-md p-2 transition hover:bg-primary-faint"
    >
      <ItemImage
        src={item.images[0]}
        alt={item.title}
        loading="lazy"
        className="h-20 w-24 shrink-0 rounded-md object-cover"
      />
      <div className="min-w-0 flex-1 py-1">
        <h3 className="truncate text-sm font-bold group-hover:text-primary">{item.title}</h3>
        <p className="mt-1 truncate text-xs text-text-muted">
          {item.district} · {owner?.name}
        </p>
        <div className="mt-2 flex gap-2">
          <TypeBadge type={item.type} />
          <ConditionBadge condition={item.condition} />
        </div>
      </div>
    </Link>
  );
}
export function OwnerCard({ owner }: { owner: User }) {
  return (
    <div className="rounded-lg bg-white p-5 ring-1 ring-border/80">
      <div className="flex items-center gap-3">
        <Avatar user={owner} size="lg" />
        <div className="min-w-0">
          <div className="truncate font-bold">{owner.name}</div>
          <div className="mt-0.5 text-xs text-text-muted">
            {owner.district} · {owner.totalTx} giao dịch
          </div>
        </div>
      </div>
      <div className="mt-4 flex items-center justify-between">
        <RankBadge rank={owner.rank} />
        <span className="text-sm font-bold text-text-secondary">
          {owner.reputationStars ? `${owner.reputationStars.toLocaleString('vi-VN')} sao` : 'Chưa có đánh giá'}
        </span>
      </div>
    </div>
  );
}
export function Avatar({
  user,
  size = 'md',
}: {
  user: Pick<User, 'name' | 'avatarInitials' | 'avatarUrl'>;
  size?: 'sm' | 'md' | 'lg';
}) {
  const cls = { sm: 'size-8 text-2xs', md: 'size-10 text-xs', lg: 'size-14 text-base' };
  const avatarSrc = resolveMediaUrl(user.avatarUrl);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [avatarSrc]);

  return avatarSrc && !failed ? (
    <img
      src={avatarSrc}
      alt=""
      loading="lazy"
      decoding="async"
      onError={() => setFailed(true)}
      className={`shrink-0 rounded-md object-cover ${cls[size]}`}
    />
  ) : (
    <span
      aria-label={user.name}
      className={`inline-flex shrink-0 items-center justify-center rounded-md bg-primary text-white font-bold ${cls[size]}`}
    >
      {user.avatarInitials.trim() ? user.avatarInitials : <Icon name="user" className="size-1/2" />}
    </span>
  );
}
export function Stat({
  label,
  value,
  detail,
}: {
  label: string;
  value: ReactNode;
  detail?: string;
}) {
  return (
    <div className="min-w-0 border-l-2 border-primary/20 pl-4">
      <p className="text-xs font-medium text-text-muted">{label}</p>
      <div className="mt-1 truncate text-2xl font-bold tabular-nums text-text-primary">{value}</div>
      {detail ? <p className="mt-1 text-xs text-text-muted">{detail}</p> : null}
    </div>
  );
}
export function PageHeader({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="mb-7 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="page-title">{title}</h1>
        {description ? <p className="page-intro">{description}</p> : null}
      </div>
      {action}
    </div>
  );
}
