import { Alert, Icon } from '../ui';

export interface AiSearchNoticesProps {
  remainingFreeSearches: number | null;
  feeCredits: number;
  isFallbackSearch: boolean;
}

export function AiSearchNotices({
  remainingFreeSearches,
  feeCredits,
  isFallbackSearch,
}: AiSearchNoticesProps) {
  return (
    <div className="space-y-3" aria-live="polite">
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="flex items-start gap-3 rounded-lg bg-primary-faint px-4 py-3 text-sm text-text-secondary">
          <span className="grid size-8 shrink-0 place-items-center rounded-full bg-white text-primary">
            <Icon name="gift" className="size-4" />
          </span>
          <div>
            <p className="font-bold text-text-primary">Lượt tìm miễn phí</p>
            <p className="mt-0.5 leading-5">
              {remainingFreeSearches === null
                ? 'Chưa tải được số lượt còn lại.'
                : 'Bạn còn ' + remainingFreeSearches + ' lượt miễn phí.'}
            </p>
          </div>
        </div>
        <div className="flex items-start gap-3 rounded-lg bg-secondary-soft px-4 py-3 text-sm text-text-secondary">
          <span className="grid size-8 shrink-0 place-items-center rounded-full bg-white text-secondary">
            <Icon name="wallet" className="size-4" />
          </span>
          <div>
            <p className="font-bold text-text-primary">Phí sau lượt miễn phí</p>
            <p className="mt-0.5 leading-5">Mỗi lượt tìm tiếp theo dùng {feeCredits} Credit.</p>
          </div>
        </div>
      </div>
      {isFallbackSearch ? (
        <Alert tone="warning">
          Trợ lý đang gặp sự cố, ShareLoop đã chuyển sang tìm kiếm thông thường để bạn không bị gián đoạn.
        </Alert>
      ) : null}
    </div>
  );
}
