import { useEffect, useState } from 'react';
import { Alert, Button, Field, ItemImage, Select } from '../ui';
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

/** Khối "Ảnh giao nhận". Ảnh đã có lấy từ handoverMedia của GET /requests/{id}. */
export function HandoverMediaCard({ request }: { request: RequestResponse }) {
  const [media, setMedia] = useState<RequestMediaResponse[]>(request.handoverMedia ?? []);
  const [preview, setPreview] = useState<RequestMediaResponse | null>(null);
  const [files, setFiles] = useState<File[]>([]);
  const [stage, setStage] = useState<RequestMediaStage>(
    request.status === 'DISPUTED' ? 'DISPUTE' : 'HANDOVER',
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Trang tải lại chi tiết (sau khi thao tác) thì danh sách ảnh theo bản mới.
  useEffect(() => {
    setMedia(request.handoverMedia ?? []);
  }, [request.handoverMedia]);

  if (!mediaStatuses.includes(request.status)) return null;

  const attach = async () => {
    if (!files.length || submitting) return;
    setSubmitting(true);
    setError('');
    try {
      const uploaded = await Promise.all(files.map((file) => mediaApi.upload(file)));
      setMedia(
        await requestApi.attachMedia(request.id, {
          mediaIds: uploaded.map((entry) => entry.mediaId),
          stage,
        }),
      );
      setFiles([]);
    } catch (caught) {
      const apiError = caught as ApiError;
      setError(
        attachErrorMessages[apiError.code] ||
          apiError.message ||
          'Không bổ sung được ảnh minh chứng.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="card space-y-4 p-5 sm:p-6" aria-labelledby="photo-title">
      <div className="flex items-baseline justify-between gap-2">
        <h2 id="photo-title" className="section-title">
          Ảnh giao nhận
        </h2>
        <span className="text-xs text-text-secondary">{media.length} ảnh</span>
      </div>
      <p className="text-xs leading-5 text-text-secondary">
        Chụp món đồ lúc giao. Admin xem khi có khiếu nại.
      </p>
      {media.length ? (
        <ul className="flex flex-wrap gap-3">
          {media.map((entry) => (
            <li key={entry.mediaId} className="w-[100px] min-w-0">
              <div className="relative size-[100px]">
                <button
                  type="button"
                  aria-label={`Xem ảnh ${stageLabels[entry.stage].toLowerCase()}`}
                  onClick={() => setPreview(entry)}
                  className="block size-[100px] overflow-hidden rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  <ItemImage
                    src={entry.url}
                    alt={`Ảnh ${stageLabels[entry.stage].toLowerCase()}`}
                    className="size-[100px] rounded-md object-cover"
                  />
                </button>
              </div>
              <p className="mt-1 line-clamp-2 text-2xs leading-4 text-text-secondary">
                {stageLabels[entry.stage]} {formatDateTime(entry.createdAt)}
              </p>
            </li>
          ))}
        </ul>
      ) : (
        <div className="rounded-md px-4 py-6 text-center ring-1 ring-border">
          <p className="text-sm font-semibold">Chưa có ảnh giao nhận</p>
          <p className="mt-1 text-xs text-text-secondary">Chọn ảnh bên dưới rồi bấm Thêm ảnh.</p>
        </div>
      )}
      <div className="grid items-end gap-3 sm:grid-cols-[180px_minmax(0,1fr)_auto]">
        <Select
          label="Giai đoạn"
          value={stage}
          disabled={submitting}
          onChange={(event) => setStage(event.target.value as RequestMediaStage)}
        >
          <option value="HANDOVER">{stageLabels.HANDOVER}</option>
          <option value="DISPUTE">{stageLabels.DISPUTE}</option>
        </Select>
        <Field
          label="Chọn ảnh"
          type="file"
          accept="image/*"
          multiple
          disabled={submitting}
          onChange={(event) => setFiles(Array.from(event.target.files ?? []))}
        />
        <Button disabled={submitting || !files.length} onClick={() => void attach()}>
          {submitting ? 'Đang tải...' : files.length ? `Thêm ${files.length} ảnh` : 'Thêm ảnh'}
        </Button>
      </div>
      {error ? <Alert tone="error">{error}</Alert> : null}
      {preview ? (
        <Dialog title="Xem ảnh giao nhận" wide onClose={() => setPreview(null)}>
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
    </section>
  );
}
