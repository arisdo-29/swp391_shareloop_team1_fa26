import { useId } from 'react';
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
    <div className="space-y-3 rounded-lg border border-dashed border-border bg-surface p-4">
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
        <ul aria-label="Ảnh đã chọn" className="space-y-2">
          {files.map((file, index) => (
            <li
              key={`${file.name}-${file.size}-${file.lastModified}`}
              className="flex min-w-0 items-center gap-2 rounded-md bg-surface-container/50 px-3 py-1"
            >
              <Icon name="image" className="size-4 shrink-0 text-primary" />
              <span className="min-w-0 flex-1 break-all text-sm">{file.name}</span>
              <IconButton
                type="button"
                icon="close"
                label={`Bỏ ảnh ${file.name}`}
                disabled={disabled}
                onClick={() => onChange(files.filter((_, fileIndex) => fileIndex !== index))}
              />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
