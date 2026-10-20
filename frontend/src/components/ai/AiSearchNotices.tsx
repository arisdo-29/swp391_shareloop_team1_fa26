import { Alert, Icon } from '../ui';

export interface AiSearchNoticesProps {
  remainingFreeSearches: number | null;
  feeCredits: number;
  chargedCredits: number;
  isFallbackSearch: boolean;
}

export function AiSearchNotices({
  remainingFreeSearches,
  feeCredits,
  chargedCredits,
  isFallbackSearch,
}: AiSearchNoticesProps) {
  return (
    <div className="space-y-3" aria-live="polite">
      <div className="flex items-start gap-3 rounded-lg bg-primary-faint px-4 py-3 text-sm text-text-secondary">
        <span className="grid size-8 shrink-0 place-items-center rounded-full bg-white text-primary">
          <Icon name={remainingFreeSearches && remainingFreeSearches > 0 ? 'gift' : 'wallet'} className="size-4" />
        </span>
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
      {isFallbackSearch ? (
        <Alert tone="warning">
          Trợ lý AI đang bận, đã chuyển sang tìm kiếm thường
        </Alert>
      ) : null}
    </div>
  );
}
