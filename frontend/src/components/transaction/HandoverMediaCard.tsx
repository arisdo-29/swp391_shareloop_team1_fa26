import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Alert, Button, Field, Icon, ItemImage } from '../ui';
import { Dialog } from '../Dialog';
import {
  requestApi,
  type RequestMediaResponse,
  type RequestMediaStage,
  type RequestResponse,
} from '../../services/requestApi';
import { mediaApi } from '../../services/mediaApi';
import type { ApiError } from '../../services/http';
import { formatDateTime } from '../../utils/request';

// Chỉ thêm ảnh được từ khi chốt lịch trở đi (docs/api/request.md mục 11).
const mediaStatuses: RequestResponse['status'][] = [
  'LOGISTICS_CONFIRMED',
  'AWAITING_HANDOVER',
  'COMPLETED',
  'DISPUTED',
];

const stageLabels: Record<RequestMediaStage, string> = {
  HANDOVER: 'Giao nhận',
  DISPUTE: 'Khiếu nại',
};

const attachErrorMessages: Record<string, string> = {
  REQUEST_MEDIA_REQUIRED: 'Vui lòng chọn ít nhất một ảnh minh chứng.',
  REQUEST_MEDIA_STAGE_INVALID: 'Giai đoạn minh chứng không hợp lệ.',
  REQUEST_FORBIDDEN: 'Bạn không có quyền xem hoặc bổ sung minh chứng cho giao dịch này.',
  REQUEST_NOT_FOUND: 'Không tìm thấy giao dịch.',
  REQUEST_INVALID_STATE_TRANSITION: 'Giao dịch chưa đến giai đoạn được phép bổ sung minh chứng.',
  MEDIA_NOT_OWNER: 'Có ảnh không thuộc quyền sở hữu của bạn.',
  MEDIA_NOT_FOUND: 'Không tìm thấy một hoặc nhiều ảnh đã tải lên.',
};

/**
 * Còn gửi ảnh khiếu nại được không: đang khiếu nại, hoặc đã hoàn tất mà chưa qua hạn khiếu nại.
 * disputeExpired do backend tính (complaint_window_hours), FE không tự so giờ.
 */
function canAddDisputeMedia(request: RequestResponse) {
  if (request.status === 'DISPUTED') return true;
  return request.status === 'COMPLETED' && !request.disputeExpired;
}

/**
 * Hai khối "Ảnh giao nhận" và "Ảnh khiếu nại". Ảnh đã có lấy từ handoverMedia của GET /requests/{id}.
 * Ảnh đã gửi không xoá được (backend chưa có API), nên ảnh mới phải xem lại và xác nhận trước khi gửi.
 */
export function HandoverMediaCard({ request }: { request: RequestResponse }) {
  const [media, setMedia] = useState<RequestMediaResponse[]>(request.handoverMedia ?? []);
  const [preview, setPreview] = useState<RequestMediaResponse | null>(null);

  // Trang tải lại chi tiết (sau khi thao tác) thì danh sách ảnh theo bản mới.
  useEffect(() => {
    setMedia(request.handoverMedia ?? []);
  }, [request.handoverMedia]);

  if (!mediaStatuses.includes(request.status)) return null;

  const handoverMedia = media.filter((entry) => entry.stage === 'HANDOVER');
  const disputeMedia = media.filter((entry) => entry.stage === 'DISPUTE');
  const disputeOpen = canAddDisputeMedia(request);

  return (
    <>
      <MediaSection
        stage="HANDOVER"
        description="Lưu lại tình trạng món đồ lúc bàn giao."
        media={handoverMedia}
        onPreview={setPreview}
      >
        <MediaUploader requestId={request.id} stage="HANDOVER" onAttached={setMedia} />
      </MediaSection>

      {/* Hết hạn khiếu nại mà chưa từng gửi ảnh khiếu nại thì ẩn hẳn mục này. */}
      {disputeOpen || disputeMedia.length ? (
        <MediaSection
          stage="DISPUTE"
          description={disputeDescription(request, disputeOpen)}
          media={disputeMedia}
          onPreview={setPreview}
        >
          {disputeOpen ? (
            <MediaUploader requestId={request.id} stage="DISPUTE" onAttached={setMedia} />
          ) : null}
        </MediaSection>
      ) : null}

      {preview ? (
        <Dialog title={`Xem ảnh ${stageLabels[preview.stage].toLowerCase()}`} wide onClose={() => setPreview(null)}>
          <ItemImage
            src={preview.url}
            alt={`Ảnh ${stageLabels[preview.stage].toLowerCase()} kích thước lớn`}
            className="max-h-[70dvh] w-full rounded-md bg-surface-low object-contain"
          />
          <p className="mt-3 text-xs text-text-secondary">
            {stageLabels[preview.stage]} · {formatDateTime(preview.createdAt)}
          </p>
        </Dialog>
      ) : null}
    </>
  );
}

/** Câu mô tả mục khiếu nại: còn hạn thì nói hạn chót, hết hạn thì nói không thêm được nữa. */
function disputeDescription(request: RequestResponse, disputeOpen: boolean) {
  if (!disputeOpen) return 'Đã hết hạn khiếu nại, không thêm ảnh khiếu nại được nữa.';
  if (request.status === 'COMPLETED') {
    const deadline = request.complaintDeadline
      ? `Có thể bổ sung ảnh vào giao dịch trước ${formatDateTime(request.complaintDeadline)}. `
      : '';
    return `${deadline}Để gửi hồ sơ cho quản trị viên, dùng nút “Khiếu nại giao dịch” ở mục Hỗ trợ giao dịch.`;
  }
  return 'Ảnh bổ sung được lưu vào giao dịch. Nếu nhận được yêu cầu giải trình, hãy mở thông báo để gửi nội dung và ảnh giải trình.';
}

/** Một khối ảnh theo giai đoạn: tiêu đề, số ảnh, lưới ảnh đã gửi và phần thêm ảnh (children). */
function MediaSection({
  stage,
  description,
  media,
  onPreview,
  children,
}: {
  stage: RequestMediaStage;
  description: string;
  media: RequestMediaResponse[];
  onPreview: (entry: RequestMediaResponse) => void;
  children: ReactNode;
}) {
  const label = stageLabels[stage].toLowerCase();
  const titleId = `media-title-${stage.toLowerCase()}`;
  return (
    <section className="card space-y-4 p-5 sm:p-6" aria-labelledby={titleId}>
      <div className="flex items-baseline justify-between gap-2">
        <h2 id={titleId} className="section-title">
          Ảnh {label}
        </h2>
        <span className="text-xs text-text-secondary">{media.length} ảnh</span>
      </div>
      <p className="text-xs leading-5 text-text-secondary">{description}</p>
      {media.length ? (
        <ul className="flex flex-wrap gap-3">
          {media.map((entry) => (
            <li key={entry.mediaId} className="w-[100px] min-w-0">
              <button
                type="button"
                aria-label={`Xem ảnh ${label}`}
                onClick={() => onPreview(entry)}
                className="block size-[100px] overflow-hidden rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                <ItemImage src={entry.url} alt={`Ảnh ${label}`} className="size-[100px] rounded-md object-cover" />
              </button>
              <p className="mt-1 line-clamp-2 text-2xs leading-4 text-text-secondary">
                {stageLabels[entry.stage]} {formatDateTime(entry.createdAt)}
              </p>
            </li>
          ))}
        </ul>
      ) : (
        <div className="rounded-md px-4 py-6 text-center ring-1 ring-border">
          <p className="text-sm font-semibold">Chưa có ảnh {label}</p>
        </div>
      )}
      {children}
    </section>
  );
}

interface PendingImage {
  file: File;
  previewUrl: string;
}

/**
 * Chọn ảnh → xem lại và bỏ bớt → bấm Gửi → hộp xác nhận "không thu hồi được" → tải lên và đính kèm.
 * Ảnh chờ gửi chỉ nằm ở trình duyệt (object URL) nên bỏ thoải mái; chỉ khi xác nhận mới gọi API.
 */
function MediaUploader({
  requestId,
  stage,
  onAttached,
}: {
  requestId: number;
  stage: RequestMediaStage;
  onAttached: (media: RequestMediaResponse[]) => void;
}) {
  const [pending, setPending] = useState<PendingImage[]>([]);
  const [confirming, setConfirming] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  // Đổi key để làm mới ô chọn tệp, nhờ vậy chọn lại đúng tệp vừa bỏ vẫn nhận.
  const [inputKey, setInputKey] = useState(0);
  const label = stageLabels[stage].toLowerCase();

  // Giải phóng object URL còn lại khi rời trang để không rò bộ nhớ.
  const pendingRef = useRef(pending);
  pendingRef.current = pending;
  useEffect(() => () => pendingRef.current.forEach((entry) => URL.revokeObjectURL(entry.previewUrl)), []);

  const addFiles = (files: File[]) => {
    const added = files.map((file) => ({ file, previewUrl: URL.createObjectURL(file) }));
    setPending((current) => [...current, ...added]);
    setInputKey((current) => current + 1);
    setError('');
  };

  const removePending = (target: PendingImage) => {
    URL.revokeObjectURL(target.previewUrl);
    setPending((current) => current.filter((entry) => entry !== target));
  };

  const send = async () => {
    setSubmitting(true);
    setError('');
    try {
      const uploaded = await Promise.all(pending.map((entry) => mediaApi.upload(entry.file)));
      onAttached(await requestApi.attachMedia(requestId, { mediaIds: uploaded.map((entry) => entry.mediaId), stage }));
      pending.forEach((entry) => URL.revokeObjectURL(entry.previewUrl));
      setPending([]);
    } catch (caught) {
      const apiError = caught as ApiError;
      setError(attachErrorMessages[apiError.code] || apiError.message || 'Không bổ sung được ảnh minh chứng.');
    } finally {
      setSubmitting(false);
      setConfirming(false);
    }
  };

  return (
    <div className="space-y-3 border-t border-border pt-4">
      <Field
        key={inputKey}
        label={`Chọn ảnh ${label}`}
        type="file"
        accept="image/*"
        multiple
        disabled={submitting}
        onChange={(event) => addFiles(Array.from(event.target.files ?? []))}
      />
      {pending.length ? <PendingGrid pending={pending} disabled={submitting} onRemove={removePending} /> : null}
      <div className="flex justify-end">
        <Button disabled={submitting || !pending.length} onClick={() => setConfirming(true)}>
          {submitting ? 'Đang tải...' : pending.length ? `Gửi ${pending.length} ảnh` : 'Gửi ảnh'}
        </Button>
      </div>
      {error ? <Alert tone="error">{error}</Alert> : null}
      {confirming ? (
        <Dialog title={`Gửi ${pending.length} ảnh ${label}?`} busy={submitting} onClose={() => setConfirming(false)}>
          <Alert tone="warning">
            Ảnh đã gửi sẽ không thể xoá hoặc thu hồi. Bên còn lại và Admin (khi có khiếu nại) sẽ xem được các ảnh này.
          </Alert>
          <div className="mt-5 flex justify-end gap-2">
            <Button variant="ghost" disabled={submitting} onClick={() => setConfirming(false)}>
              Xem lại
            </Button>
            <Button disabled={submitting} onClick={() => void send()}>
              {submitting ? 'Đang tải...' : 'Gửi ảnh'}
            </Button>
          </div>
        </Dialog>
      ) : null}
    </div>
  );
}

/** Lưới ảnh chờ gửi, mỗi ảnh có nút bỏ. */
function PendingGrid({
  pending,
  disabled,
  onRemove,
}: {
  pending: PendingImage[];
  disabled: boolean;
  onRemove: (entry: PendingImage) => void;
}) {
  return (
    <div>
      <p className="mb-2 text-xs font-semibold text-text-secondary">Ảnh chờ gửi · xem lại trước khi gửi</p>
      <ul className="flex flex-wrap gap-3">
        {pending.map((entry) => (
          <li key={entry.previewUrl} className="relative size-[100px]">
            <img
              src={entry.previewUrl}
              alt={`Ảnh chờ gửi ${entry.file.name}`}
              className="size-[100px] rounded-md object-cover ring-1 ring-border"
            />
            <button
              type="button"
              aria-label={`Bỏ ảnh ${entry.file.name}`}
              disabled={disabled}
              onClick={() => onRemove(entry)}
              className="absolute -right-2 -top-2 grid size-7 place-items-center rounded-full bg-surface text-text-primary shadow-md ring-1 ring-border transition hover:bg-error-soft hover:text-error disabled:opacity-50"
            >
              <Icon name="close" className="size-4" />
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
