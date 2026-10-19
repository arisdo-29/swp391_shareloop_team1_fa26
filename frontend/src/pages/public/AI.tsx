import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { useAppSelector } from '../../app/hooks';
import { selectData } from '../../app/store';
import { AiSearchNotices } from '../../components/ai/AiSearchNotices';
import { Button, EmptyState, Icon, PageHeader, ProductCard, TextArea } from '../../components/ui';
import {
  AI_SEARCH_PREVIEW_ENABLED,
  searchPreviewItems,
  type PreviewUnderstoodFilter,
} from '../../mocks/aiSearchPreview';
import type { Item } from '../../types/domain';

const MAX_QUERY_LENGTH = 500;
const PAGE_SIZE = 6;

// Chỉ là dữ liệu trình bày trong lúc chờ contract #125, không phải cấu trúc response.
const NOTICE_PREVIEW = {
  remainingFreeSearches: null,
  feeCredits: 2,
  isFallbackSearch: false,
} as const;

type SearchStatus = 'idle' | 'loading' | 'success' | 'error';

export function AI() {
  const data = useAppSelector(selectData);
  const [query, setQuery] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');
  const [queryError, setQueryError] = useState('');
  const [status, setStatus] = useState<SearchStatus>('idle');
  const [filters, setFilters] = useState<PreviewUnderstoodFilter[]>([]);
  const [results, setResults] = useState<Item[]>([]);
  const [page, setPage] = useState(0);
  const searchTimerRef = useRef<number | null>(null);

  useEffect(
    () => () => {
      if (searchTimerRef.current !== null) window.clearTimeout(searchTimerRef.current);
    },
    [],
  );

  const totalPages = Math.max(1, Math.ceil(results.length / PAGE_SIZE));
  const visibleResults = useMemo(
    () => results.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE),
    [page, results],
  );

  const search = (value = query) => {
    const normalizedQuery = value.trim();
    if (!normalizedQuery) {
      setQueryError('Vui lòng mô tả món đồ bạn đang tìm.');
      return;
    }

    if (searchTimerRef.current !== null) window.clearTimeout(searchTimerRef.current);
    setQueryError('');
    setSubmittedQuery(normalizedQuery);
    setStatus('loading');
    setPage(0);

    searchTimerRef.current = window.setTimeout(() => {
      try {
        if (!AI_SEARCH_PREVIEW_ENABLED) throw new Error('Search preview is unavailable.');

        // TODO(#125): thay searchPreviewItems bằng aiApi.search(normalizedQuery, page, PAGE_SIZE).
        const preview = searchPreviewItems(normalizedQuery, data.items);
        setFilters(preview.filters);
        setResults(preview.items);
        setStatus('success');
      } catch {
        setFilters([]);
        setResults([]);
        setStatus('error');
      } finally {
        searchTimerRef.current = null;
      }
    }, 350);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    search();
  };

  return (
    <div className="page-shell">
      <PageHeader
        title="Trợ lý tìm đồ"
        description="Mô tả món đồ bạn cần, ShareLoop sẽ giúp bạn thu hẹp kết quả phù hợp."
      />

      <div className="space-y-8">
        <section className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-border/80">
          <div className="bg-gradient-to-br from-primary-faint via-white to-secondary-soft px-5 py-7 sm:px-8 sm:py-9">
            <div className="mx-auto max-w-3xl">
              <div className="flex items-center gap-3">
                <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-primary text-white shadow-sm">
                  <Icon name="ai" className="size-6" weight="bold" />
                </span>
                <div>
                  <h2 className="text-xl font-bold text-text-primary">Bạn đang tìm món đồ nào?</h2>
                  <p className="mt-1 text-sm leading-6 text-text-muted">
                    Bạn có thể nói rõ món đồ, tình trạng, hình thức và khu vực mong muốn.
                  </p>
                </div>
              </div>

              <form className="mt-6" onSubmit={submit}>
                <TextArea
                  value={query}
                  maxLength={MAX_QUERY_LENGTH}
                  rows={5}
                  placeholder="Ví dụ: Mình cần tìm một chiếc bàn học còn tốt ở Quận 7, ưu tiên đồ cho tặng."
                  aria-label="Mô tả món đồ cần tìm"
                  error={queryError}
                  onChange={(event) => {
                    setQuery(event.target.value);
                    if (queryError) setQueryError('');
                  }}
                />
                <div className="mt-2 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <span className="text-xs tabular-nums text-text-muted">
                    {query.length}/{MAX_QUERY_LENGTH} ký tự
                  </span>
                  <Button
                    type="submit"
                    icon="search"
                    disabled={status === 'loading' || !query.trim()}
                  >
                    {status === 'loading' ? 'Đang tìm...' : 'Tìm'}
                  </Button>
                </div>
              </form>
            </div>
          </div>

          <div className="border-t border-border/70 p-5 sm:p-6">
            <AiSearchNotices {...NOTICE_PREVIEW} />
          </div>
        </section>

        {status !== 'idle' ? (
          <section className="rounded-2xl bg-white p-5 ring-1 ring-border/80 sm:p-6">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <p className="text-xs font-bold uppercase tracking-[0.12em] text-primary">
                  Bộ lọc trợ lý hiểu được
                </p>
                <h2 className="mt-1 text-lg font-bold text-text-primary">
                  {submittedQuery ? 'Từ mô tả của bạn' : 'Bộ lọc tìm kiếm'}
                </h2>
              </div>
              {status === 'success' ? (
                <span className="text-sm text-text-muted">
                  {results.length} món phù hợp
                </span>
              ) : null}
            </div>

            {status === 'loading' ? (
              <div className="mt-5 flex items-center gap-3 rounded-lg bg-surface-low px-4 py-5 text-sm text-text-muted">
                <Icon name="renew" className="size-5 animate-spin text-primary" />
                Đang đọc mô tả và chuẩn bị kết quả...
              </div>
            ) : status === 'error' ? (
              <div className="mt-5">
                <EmptyState
                  title="Chưa thể tìm đồ lúc này"
                  text="Vui lòng thử lại sau ít phút."
                  action={
                    <Button variant="outline" onClick={() => search(submittedQuery)}>
                      Thử lại
                    </Button>
                  }
                />
              </div>
            ) : (
              <div className="mt-5">
                {filters.length ? (
                  <div className="flex flex-wrap gap-2">
                    {filters.map((filter) => (
                      <span
                        key={filter.label}
                        className="inline-flex items-center gap-2 rounded-full bg-primary-faint px-3 py-2 text-sm text-text-secondary ring-1 ring-primary/10"
                      >
                        <span className="font-semibold text-primary">{filter.label}:</span>
                        {filter.value}
                      </span>
                    ))}
                  </div>
                ) : (
                  <p className="rounded-lg bg-surface-low px-4 py-3 text-sm leading-6 text-text-muted">
                    Chưa nhận ra bộ lọc cụ thể. Kết quả được chọn theo các từ khóa trong mô tả.
                  </p>
                )}
              </div>
            )}
          </section>
        ) : null}

        {status === 'success' ? (
          <section>
            <div className="flex items-end justify-between gap-4">
              <div>
                <h2 className="section-title">Kết quả phù hợp</h2>
                <p className="mt-1 text-sm text-text-muted">
                  Các món đồ đang có trên ShareLoop.
                </p>
              </div>
              {results.length ? (
                <span className="text-sm text-text-muted">
                  Trang {page + 1}/{totalPages}
                </span>
              ) : null}
            </div>

            {results.length ? (
              <>
                <div className="mt-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                  {visibleResults.map((item) => (
                    <ProductCard
                      key={item.id}
                      item={item}
                      owner={data.users.find((user) => user.id === item.ownerId)}
                    />
                  ))}
                </div>
                {totalPages > 1 ? (
                  <div className="mt-6 flex items-center justify-center gap-3">
                    <Button
                      variant="outline"
                      disabled={page === 0}
                      onClick={() => setPage((current) => Math.max(0, current - 1))}
                    >
                      Trang trước
                    </Button>
                    <span className="text-sm text-text-muted">
                      {page + 1} / {totalPages}
                    </span>
                    <Button
                      variant="outline"
                      disabled={page + 1 >= totalPages}
                      onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}
                    >
                      Trang sau
                    </Button>
                  </div>
                ) : null}
              </>
            ) : (
              <div className="mt-5">
                <EmptyState
                  title="Chưa tìm thấy món phù hợp"
                  text="Hãy thử mô tả rộng hơn, đổi khu vực hoặc bỏ bớt yêu cầu về tình trạng."
                />
              </div>
            )}
          </section>
        ) : status === 'idle' ? (
          <section className="rounded-2xl border border-dashed border-border bg-white/60 px-5 py-10 text-center">
            <Icon name="search" className="mx-auto size-8 text-primary" />
            <h2 className="mt-3 font-bold text-text-primary">Kết quả sẽ xuất hiện tại đây</h2>
            <p className="mx-auto mt-1 max-w-lg text-sm leading-6 text-text-muted">
              Hãy mô tả món đồ bạn cần để xem bộ lọc và danh sách gợi ý.
            </p>
          </section>
        ) : null}
      </div>
    </div>
  );
}
