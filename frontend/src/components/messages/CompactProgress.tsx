import type { ItemRequestStatus } from '../../types/domain';
import { requestStatusLabel } from '../../utils/request';
import { completedStepCount, transactionSteps } from '../../utils/transactionSteps';

/**
 * Thanh 4 đoạn thu gọn ở khung chat. Chỉ hiển thị trạng thái backend trả về; chưa vào giao dịch (chờ chọn,
 * bị từ chối, đã huỷ) thì không vẽ thanh.
 */
export function CompactProgress({ status }: { status: ItemRequestStatus }) {
  const done = completedStepCount(status);
  if (done < 0) return null;
  return (
    <div className="flex min-w-0 items-center gap-2">
      <ol
        aria-label={`Đã xong ${Math.min(done, transactionSteps.length)} trên ${transactionSteps.length} bước`}
        className="flex w-24 shrink-0 gap-1"
      >
        {transactionSteps.map((label, index) => (
          <li
            key={label}
            className={`h-1.5 flex-1 rounded-full ${index < done ? 'bg-primary' : 'bg-border'}`}
          />
        ))}
      </ol>
      <span className="truncate text-xs font-semibold text-text-secondary">
        {requestStatusLabel[status]}
      </span>
    </div>
  );
}
