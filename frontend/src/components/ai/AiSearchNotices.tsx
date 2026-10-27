import { Alert } from '../ui';

export interface AiSearchNoticesProps {
  remainingFreeSearches: number | null;
  feeCredits: number;
  chargedCredits: number;
}

export function AiSearchNotices({
  remainingFreeSearches,
  feeCredits,
  chargedCredits,
}: AiSearchNoticesProps) {
  return (
    <div className="space-y-3" aria-live="polite">
      <div className="rounded-lg bg-primary-faint px-4 py-3 text-sm text-text-secondary">
        <div>
          <p className="font-bold text-text-primary">Hạn mức tìm kiếm</p>
          <p className="mt-0.5 leading-5">
            {remainingFreeSearches === null
              ? 'Chưa tải được số lượt còn lại.'
              : remainingFreeSearches > 0
                ? `Còn ${remainingFreeSearches} lượt miễn phí`
                : `Lượt tiếp theo tính phí ${feeCredits} Credit`}
          </p>
        </div>
      </div>
      {chargedCredits > 0 ? <Alert tone="success">Đã trừ {chargedCredits} Credit</Alert> : null}
    </div>
  );
}
