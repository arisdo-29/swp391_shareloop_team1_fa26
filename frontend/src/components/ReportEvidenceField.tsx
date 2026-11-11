import './complaints.css';
import { useEffect, useId, useState } from 'react';
import { Icon, IconButton } from './ui';

export function ReportEvidenceField({
  files,
  onChange,
  disabled,
}: {
  files: File[];
  onChange: (files: File[]) => void;
  disabled: boolean;
}) {
  const labelId = useId();
  const hintId = useId();

  return (
    <div className="complaint-evidence-field space-y-3 rounded-lg border border-dashed border-border bg-surface p-4">
      <div>
        <p id={labelId} className="label">
          Ảnh minh chứng (không bắt buộc)
        </p>
        <p id={hintId} className="text-xs leading-5 text-text-secondary">
          Ví dụ: ảnh món đồ, ảnh lúc giao nhận hoặc ảnh chụp đoạn trao đổi liên quan.
        </p>
      </div>
      <label
        className={`inline-flex min-h-11 items-center gap-2 rounded-md border border-border px-3 text-sm font-semibold text-primary focus-within:ring-2 focus-within:ring-primary ${disabled ? 'opacity-40' : 'cursor-pointer hover:bg-primary-faint'}`}
      >
        <Icon name="image" className="size-4" />
        <span aria-hidden="true">Chọn ảnh</span>
        <input
          className="sr-only"
          type="file"
          accept="image/*"
          multiple
          aria-labelledby={labelId}
          aria-describedby={hintId}
          disabled={disabled}
          onChange={(event) => {
            const next = [...files];
            for (const file of Array.from(event.target.files ?? [])) {
              if (
                !next.some(
                  (entry) =>
                    entry.name === file.name &&
                    entry.size === file.size &&
                    entry.lastModified === file.lastModified,
                )
              ) {
                next.push(file);
              }
            }
            onChange(next);
            event.target.value = '';
          }}
        />
      </label>
      {files.length > 0 && (
        <ul aria-label="Ảnh đã chọn" className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {files.map((file, index) => (
            <EvidencePreview
              key={`${file.name}-${file.size}-${file.lastModified}`}
              file={file}
              disabled={disabled}
              onRemove={() => onChange(files.filter((_, fileIndex) => fileIndex !== index))}
            />
          ))}
        </ul>
      )}
    </div>
  );
}

/** Ảnh thu nhỏ của file vừa chọn để người dùng kiểm tra và bỏ ảnh chọn nhầm trước khi gửi. */
function EvidencePreview({
  file,
  disabled,
  onRemove,
}: {
  file: File;
  disabled: boolean;
  onRemove: () => void;
}) {
  const [previewUrl, setPreviewUrl] = useState('');

  useEffect(() => {
    // Môi trường không hỗ trợ object URL (vd. jsdom khi chạy test) thì hiện biểu tượng thay ảnh.
    if (typeof URL.createObjectURL !== 'function') return;
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    // Thu hồi URL khi bỏ ảnh hoặc rời trang để không giữ file trong bộ nhớ.
    return () => URL.revokeObjectURL(url);
  }, [file]);

  return (
    <li className="relative overflow-hidden rounded-md border border-border bg-surface-container/50">
      {previewUrl ? (
        <img src={previewUrl} alt={file.name} className="aspect-square w-full object-cover" />
      ) : (
        <div className="grid aspect-square w-full place-items-center text-primary">
          <Icon name="image" className="size-8" />
        </div>
      )}
      <p className="truncate px-2 py-1 text-xs text-text-secondary" title={file.name}>
        {file.name}
      </p>
      <IconButton
        type="button"
        icon="close"
        label={`Bỏ ảnh ${file.name}`}
        disabled={disabled}
        onClick={onRemove}
        className="absolute right-1.5 top-1.5 size-9 shadow-sm"
      />
    </li>
  );
}
