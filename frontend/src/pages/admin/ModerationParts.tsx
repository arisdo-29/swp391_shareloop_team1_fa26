import { useState } from 'react';
import { Dialog } from '../../components/Dialog';
import { Alert, Button, ItemImage, TextArea } from '../../components/ui';
import type { ReviewChecklistItem } from '../../services/adminModerationApi';
import { resolveMediaUrl } from '../../services/mediaUrl';

export function ModerationChecklistContent({
  cancelLabel = 'Hủy',
  checklist,
  answers,
  disabled,
  error,
  onToggle,
  onCancel,
  onConfirm,
}: {
  cancelLabel?: string;
  checklist: ReviewChecklistItem[];
  answers: boolean[];
  disabled: boolean;
  error?: string;
  onToggle: (index: number, checked: boolean) => void;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const completed = checklist.reduce((total, _, index) => total + (answers[index] ? 1 : 0), 0);
  const isComplete = checklist.length > 0 && completed === checklist.length;
  const progress = checklist.length ? Math.round((completed / checklist.length) * 100) : 0;

  return (
    <div>
      <div className="mb-4 flex items-center justify-between gap-4">
        <div>
          <p className="text-sm font-bold text-text-primary">Tiến độ kiểm duyệt</p>
          <p className="mt-1 text-xs text-text-muted">Tích chọn từng tiêu chí sau khi đã kiểm tra bài đăng.</p>
        </div>
        <span className="shrink-0 rounded-full bg-primary/10 px-3 py-1 text-sm font-bold text-primary" aria-live="polite">
          {completed}/{checklist.length}
        </span>
      </div>
      <div
        className="mb-4 h-2 overflow-hidden rounded-full bg-surface-low"
        role="progressbar"
        aria-label="Tiến độ checklist"
        aria-valuemin={0}
        aria-valuemax={checklist.length}
        aria-valuenow={completed}
      >
        <div className="h-full rounded-full bg-primary transition-[width]" style={{ width: `${progress}%` }} />
      </div>

      {checklist.length ? (
        <div className="max-h-[50dvh] space-y-2 overflow-y-auto overscroll-contain pr-1">
          {checklist.map((entry, index) => (
            <label
              key={entry.id}
              className="flex cursor-pointer items-start gap-3 rounded-md border border-border/80 p-3 text-sm leading-6 transition hover:bg-surface-low has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-primary/40"
            >
              <input
                type="checkbox"
                checked={answers[index] ?? false}
                disabled={disabled}
                autoFocus={index === 0}
                onChange={(event) => onToggle(index, event.target.checked)}
                className="mt-1 size-4 shrink-0 accent-primary"
              />
              <span>{entry.statement}</span>
            </label>
          ))}
        </div>
      ) : (
        <Alert tone="warning">Backend chưa cung cấp checklist kiểm duyệt. Không thể xác nhận duyệt bài.</Alert>
      )}

      {error ? <div className="mt-4"><Alert tone="error">{error}</Alert></div> : null}
      <div className="mt-5 flex flex-wrap justify-end gap-2 border-t border-border/70 pt-4">
        <Button variant="ghost" onClick={onCancel} disabled={disabled}>{cancelLabel}</Button>
        <Button onClick={onConfirm} disabled={!isComplete || disabled}>
          {disabled ? 'Đang xử lý...' : 'Xác nhận duyệt bài'}
        </Button>
      </div>
    </div>
  );
}

function composeRejectReason(selectedReasons: string[], includeOther: boolean, otherReason: string) {
  const reasons = selectedReasons.map((reason) => reason.trim()).filter(Boolean);
  if (includeOther && otherReason.trim()) reasons.push(otherReason.trim());
  return reasons.join('\n');
}

export function RejectReasonsContent({
  reasons,
  disabled,
  error,
  onChange,
  onCancel,
  onConfirm,
}: {
  reasons: string[];
  disabled: boolean;
  error?: string;
  onChange?: () => void;
  onCancel: () => void;
  onConfirm: (reason: string) => void;
}) {
  const normalizedReasons = Array.from(new Set(reasons.map((reason) => reason.trim()).filter(Boolean)));
  const [selectedReasons, setSelectedReasons] = useState<string[]>([]);
  const [includeOther, setIncludeOther] = useState(false);
  const [otherReason, setOtherReason] = useState('');
  const selectedCount = selectedReasons.length + (includeOther ? 1 : 0);
  const totalCount = normalizedReasons.length + 1;
  const customReasonValid = !includeOther || Boolean(otherReason.trim());
  const canConfirm = selectedCount > 0 && customReasonValid;
  const combinedReason = composeRejectReason(selectedReasons, includeOther, otherReason);

  const toggleReason = (reason: string, checked: boolean) => {
    setSelectedReasons((current) => (
      checked ? [...current, reason] : current.filter((entry) => entry !== reason)
    ));
    onChange?.();
  };

  return (
    <div>
      <div className="mb-4 flex items-center justify-between gap-4">
        <div>
          <p className="text-sm font-bold text-text-primary">Lý do từ chối</p>
          <p className="mt-1 text-xs text-text-muted">Có thể chọn một hoặc nhiều lý do phù hợp.</p>
        </div>
        <span className="shrink-0 rounded-full bg-error-soft px-3 py-1 text-sm font-bold text-error" aria-live="polite">
          {selectedCount}/{totalCount}
        </span>
      </div>

      {normalizedReasons.length ? (
        <div className="max-h-[36dvh] space-y-2 overflow-y-auto overscroll-contain pr-1" aria-label="Danh sách lý do từ chối">
          {normalizedReasons.map((reason) => (
            <label
              key={reason}
              className="flex cursor-pointer items-start gap-3 rounded-md border border-border/80 p-3 text-sm leading-6 transition hover:bg-surface-low has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-primary/40"
            >
              <input
                type="checkbox"
                checked={selectedReasons.includes(reason)}
                disabled={disabled}
                onChange={(event) => toggleReason(reason, event.target.checked)}
                className="mt-1 size-4 shrink-0 accent-primary"
              />
              <span>{reason}</span>
            </label>
          ))}
        </div>
      ) : (
        <Alert tone="warning">Backend chưa cung cấp lý do mẫu. Bạn vẫn có thể nhập lý do khác.</Alert>
      )}

      <div className="mt-4 border-t border-border/70 pt-4">
        <label className="flex cursor-pointer items-start gap-3 rounded-md border border-border/80 p-3 text-sm font-semibold leading-6 transition hover:bg-surface-low has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-primary/40">
          <input
            type="checkbox"
            checked={includeOther}
            disabled={disabled}
            onChange={(event) => {
              setIncludeOther(event.target.checked);
              onChange?.();
            }}
            className="mt-1 size-4 shrink-0 accent-primary"
          />
          <span>Lý do khác</span>
        </label>
        {includeOther ? (
          <TextArea
            className="mt-3"
            label="Nội dung lý do khác"
            required
            value={otherReason}
            disabled={disabled}
            error={!otherReason.trim() ? 'Vui lòng nhập nội dung cho lý do khác.' : undefined}
            placeholder="Nhập lý do cụ thể để người đăng biết nội dung cần chỉnh sửa..."
            onChange={(event) => {
              setOtherReason(event.target.value);
              onChange?.();
            }}
          />
        ) : null}
      </div>

      {error ? <div className="mt-4"><Alert tone="error">{error}</Alert></div> : null}
      <div className="sticky bottom-0 mt-5 flex flex-wrap justify-end gap-2 border-t border-border/70 bg-white pt-4">
        <Button variant="ghost" onClick={onCancel} disabled={disabled}>Hủy</Button>
        <Button
          variant="danger"
          onClick={() => {
            if (canConfirm && !disabled) onConfirm(combinedReason);
          }}
          disabled={!canConfirm || disabled}
        >
          {disabled ? 'Đang xử lý...' : 'Xác nhận từ chối'}
        </Button>
      </div>
    </div>
  );
}

export function ModerationImageGallery({
  images,
  defectImages,
}: {
  images: string[];
  defectImages: string[];
}) {
  const [preview, setPreview] = useState<{ images: string[]; index: number; label: string } | null>(null);
  const itemImages = images.map(resolveMediaUrl).filter(Boolean);
  const itemDefectImages = defectImages.map(resolveMediaUrl).filter(Boolean);

  if (!itemImages.length && !itemDefectImages.length) {
    return (
      <div className="mt-4 rounded-md border border-dashed border-border p-4 text-sm text-text-muted">
        Bài đăng không có hình ảnh.
      </div>
    );
  }

  return (
    <div className="mt-4 space-y-3">
      {itemImages.length ? (
        <ModerationThumbnailGroup
          label="Ảnh món đồ"
          images={itemImages}
          mainThumbnail
          onPreview={(_, index) => setPreview({ images: itemImages, index, label: 'Ảnh món đồ' })}
        />
      ) : null}
      {itemDefectImages.length ? (
        <ModerationThumbnailGroup
          label="Ảnh lỗi"
          images={itemDefectImages}
          onPreview={(_, index) => setPreview({ images: itemDefectImages, index, label: 'Ảnh lỗi' })}
        />
      ) : null}
      {preview ? (
        <Dialog wide title={`${preview.label} ${preview.index + 1}/${preview.images.length}`} onClose={() => setPreview(null)}>
          <ItemImage
            src={preview.images[preview.index]}
            alt={`${preview.label} ${preview.index + 1}`}
            className="max-h-[70vh] w-full rounded-md object-contain"
          />
          {preview.images.length > 1 ? (
            <div className="mt-4 flex items-center justify-between gap-3">
              <Button
                variant="outline"
                onClick={() => setPreview((current) => current ? { ...current, index: (current.index - 1 + current.images.length) % current.images.length } : null)}
              >
                Ảnh trước
              </Button>
              <span className="text-xs text-text-muted">{preview.index + 1}/{preview.images.length}</span>
              <Button
                variant="outline"
                onClick={() => setPreview((current) => current ? { ...current, index: (current.index + 1) % current.images.length } : null)}
              >
                Ảnh sau
              </Button>
            </div>
          ) : null}
        </Dialog>
      ) : null}
    </div>
  );
}

function ModerationThumbnailGroup({
  label,
  images,
  onPreview,
  mainThumbnail = false,
}: {
  label: string;
  images: string[];
  onPreview: (src: string, index: number) => void;
  mainThumbnail?: boolean;
}) {
  const [selectedIndex, setSelectedIndex] = useState(0);
  const activeIndex = Math.min(selectedIndex, images.length - 1);

  return (
    <div>
      <p className="mb-2 text-xs font-semibold text-text-secondary">{label}</p>
      {mainThumbnail ? (
        <button
          type="button"
          aria-label={`Xem lớn ${label.toLowerCase()} ${activeIndex + 1}`}
          onClick={() => onPreview(images[activeIndex], activeIndex)}
          className="mx-auto mb-2 block w-fit max-w-full overflow-hidden rounded-md bg-white ring-1 ring-border transition hover:ring-primary/50"
        >
          <ItemImage
            src={images[activeIndex]}
            alt={`${label} ${activeIndex + 1}`}
            className="h-auto w-auto max-h-56 max-w-full object-contain"
          />
        </button>
      ) : null}
      <div className="flex flex-wrap gap-2">
        {images.map((image, index) => (
          <button
            key={`${image}-${index}`}
            type="button"
            aria-label={`${mainThumbnail ? 'Chọn' : 'Xem lớn'} ${label.toLowerCase()} ${index + 1}`}
            aria-pressed={mainThumbnail ? index === activeIndex : undefined}
            onClick={() => mainThumbnail ? setSelectedIndex(index) : onPreview(image, index)}
            className={`overflow-hidden rounded-md bg-white ring-1 ring-border transition hover:ring-primary/50 ${
              mainThumbnail ? 'block shrink-0 self-start' : ''
            }`}
          >
            <ItemImage
              src={image}
              alt={`${mainThumbnail ? 'Ảnh thu nhỏ món đồ' : label} ${index + 1}`}
              className={mainThumbnail ? 'h-auto w-auto max-h-20 max-w-20 object-contain' : 'size-20 object-cover'}
            />
          </button>
        ))}
      </div>
    </div>
  );
}
