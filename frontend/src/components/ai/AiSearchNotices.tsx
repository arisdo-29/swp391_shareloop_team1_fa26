export interface AiSearchNoticesProps {
  remainingFreeSearches: number | null;
  feeCredits: number;
  chargedCredits: number;
}

/** Dòng hạn mức tìm kiếm cạnh nút Tìm: còn bao nhiêu lượt miễn phí và lượt vừa rồi có bị trừ Credit không. */
export function AiSearchNotices({ remainingFreeSearches, feeCredits, chargedCredits }: AiSearchNoticesProps) {
  return (
    <p className="text-sm leading-6 text-text-secondary" aria-live="polite">
      <strong className="text-text-primary">
        {remainingFreeSearches === null
          ? 'Chưa tải được số lượt còn lại.'
          : remainingFreeSearches > 0
            ? `Còn ${remainingFreeSearches} lượt miễn phí`
            : `Lượt tiếp theo tính phí ${feeCredits} Credit`}
      </strong>
      {chargedCredits > 0 ? <span className="ml-2 text-success">Đã trừ {chargedCredits} Credit</span> : null}
    </p>
  );
}
