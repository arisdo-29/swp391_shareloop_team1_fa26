import { useId, useState, type ReactNode } from 'react';
import { DialogFrame } from '../../components/Dialog';
import { Button, Icon } from '../../components/ui';

/** Chỉ mở phần trình bày của dữ liệu hàng hiện tại, không tải hoặc giữ bản sao hồ sơ. */
export function AdminRecordDetails({ title, children }: { title: string; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const titleId = useId();
  return (
    <>
      <Button
        type="button"
        variant="outline"
        size="sm"
        aria-label={`Xem ${title}`}
        onClick={() => setOpen(true)}
      >
        Chi tiết
      </Button>
      {open ? (
        <DialogFrame
          size="xl"
          className="admin-record-dialog"
          labelledBy={titleId}
          onClose={() => setOpen(false)}
        >
          <header className="admin-record-dialog-header">
            <h2 id={titleId}>{title}</h2>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => setOpen(false)}
              aria-label="Đóng chi tiết"
            >
              <Icon name="close" className="size-4" />
              <span>Danh sách</span>
            </Button>
          </header>
          <div className="admin-record-dialog-body">{children}</div>
        </DialogFrame>
      ) : null}
    </>
  );
}

export function AdminRecordFact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}
