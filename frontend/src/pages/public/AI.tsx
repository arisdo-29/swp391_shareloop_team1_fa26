import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import { useWallet } from '../../app/useWallet';
import { AiSearchNotices } from '../../components/ai/AiSearchNotices';
import { Alert, Button, EmptyState, Icon, PageHeader, ProductCard, TextArea } from '../../components/ui';
import { aiApi, type AiQuotaResponse } from '../../services/aiApi';
import { catalogApi, type CategoryResponse, type AreaResponse } from '../../services/catalogApi';
import { toAreaNameMap, toCategoryNameMap, toFeItem } from '../../services/mappers';
import { conditionLabel } from '../../utils/formatting';
import type { ApiError } from '../../services/http';
import type { Item } from '../../types/domain';

const MAX_QUERY_LENGTH = 500;
const PAGE_SIZE = 6;
type SearchStatus = 'idle' | 'loading' | 'success' | 'error';

export function AI() {
  const user = useAppSelector(selectCurrentUser);
  const userId = user?.id;
  const { refresh: refreshWallet } = useWallet();
  const [query, setQuery] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');
  const [queryError, setQueryError] = useState('');
  const [status, setStatus] = useState<SearchStatus>('idle');
  const [filters, setFilters] = useState<{ label: string; value: string }[]>([]);
  const [results, setResults] = useState<Item[]>([]);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [quota, setQuota] = useState<AiQuotaResponse | null>(null);
  const [quotaError, setQuotaError] = useState('');
  const [searchError, setSearchError] = useState('');
  const [fallback, setFallback] = useState(false);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [areas, setAreas] = useState<AreaResponse[]>([]);
  const inFlight = useRef(false);
  const generation = useRef(0);

  useEffect(() => {
    let active = true;
    setQuota(null);
    setQuotaError('');
    setStatus('idle');
    setResults([]);
    if (userId) {
      aiApi.quota().then((response) => { if (active) setQuota(response); })
        .catch((error: ApiError) => { if (active) setQuotaError(error.message || 'Không tải được hạn mức tìm kiếm.'); });
    }
    Promise.all([catalogApi.categories(), catalogApi.areas()]).then(([nextCategories, nextAreas]) => {
      if (active) { setCategories(nextCategories); setAreas(nextAreas); }
    }).catch(() => { /* Kết quả vẫn hiển thị được khi danh mục tạm thời không tải được. */ });
    return () => { active = false; generation.current += 1; inFlight.current = false; };
  }, [userId]);

  const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE));
  const search = async (value = query, nextPage = 0) => {
    const normalizedQuery = value.trim();
    if (!normalizedQuery || normalizedQuery.length > MAX_QUERY_LENGTH) {
      setQueryError('Vui lòng nhập mô tả từ 1 đến 500 ký tự.');
      return;
    }
    if (!user || !quota || inFlight.current) return;
    inFlight.current = true;
    const currentGeneration = generation.current;
    setQueryError('');
    setSearchError('');
    setSubmittedQuery(normalizedQuery);
    setStatus('loading');
    try {
      const response = await aiApi.search(normalizedQuery, nextPage, PAGE_SIZE);
      if (currentGeneration !== generation.current) return;
      const categoryNames = toCategoryNameMap(categories);
      const areaNames = toAreaNameMap(areas);
      const parsed = response.filters;
      const understood: { label: string; value: string }[] = [];
      if (parsed.keywords) understood.push({ label: 'Từ khóa', value: parsed.keywords });
      if (parsed.categoryId) understood.push({ label: 'Danh mục', value: categoryNames.get(parsed.categoryId) ?? String(parsed.categoryId) });
      if (parsed.areaId) understood.push({ label: 'Khu vực', value: areaNames.get(parsed.areaId) ?? String(parsed.areaId) });
      if (parsed.condition) understood.push({ label: 'Tình trạng', value: conditionLabel[parsed.condition] });
      if (parsed.offerType) understood.push({ label: 'Hình thức', value: parsed.offerType === 'GIVE' ? 'Cho tặng' : 'Trao đổi' });
      setFilters(understood);
      setResults(response.results.items.map((item) => toFeItem(item, categoryNames, areaNames)));
      setTotalElements(response.results.totalElements);
      setPage(response.results.page);
      setQuota(response.quota);
      setFallback(response.fallback);
      setStatus('success');
      refreshWallet();
    } catch (error) {
      if (currentGeneration !== generation.current) return;
      const apiError = error as ApiError;
      setSearchError(apiError.code === 'WALLET_INSUFFICIENT_BALANCE' ? 'Hết lượt miễn phí và số dư Credit không đủ. Vui lòng nạp thêm Credit.' : apiError.message || 'Không tìm được đồ. Vui lòng thử lại.');
      setStatus('error');
      setResults([]);
      refreshWallet();
      try { const latest = await aiApi.quota(); if (currentGeneration === generation.current) setQuota(latest); } catch { /* Giữ hạn mức đã tải, lần tìm tiếp theo vẫn do backend kiểm tra. */ }
    } finally {
      if (currentGeneration === generation.current) inFlight.current = false;
    }
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    void search();
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
                    disabled={status === 'loading' || !query.trim() || !quota || !user}
                  >
                    {status === 'loading' ? 'Đang tìm...' : 'Tìm'}
                  </Button>
                </div>
              </form>
            </div>
          </div>

          <div className="border-t border-border/70 p-5 sm:p-6">
            {!user ? <Alert><Link to="/login" className="font-semibold underline">Đăng nhập</Link> để sử dụng trợ lý tìm đồ.</Alert> : quota ? (
              <AiSearchNotices remainingFreeSearches={quota.freeUsesRemaining} feeCredits={quota.feeCredits} isFallbackSearch={fallback} />
            ) : <Alert tone={quotaError ? 'error' : 'info'}>{quotaError || 'Đang tải hạn mức tìm kiếm...'}</Alert>}
            {quotaError ? <Button variant="outline" size="sm" onClick={() => { void aiApi.quota().then((response) => { setQuota(response); setQuotaError(''); }).catch((error: ApiError) => setQuotaError(error.message)); }}>Thử tải hạn mức</Button> : null}
            {quota ? <p className="mt-3 text-xs text-text-muted">Mỗi lần tìm hoặc chuyển trang dùng một lượt; hết lượt miễn phí sẽ dùng {quota.feeCredits} Credit. Tìm dự phòng được hoàn lượt và phí.</p> : null}
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
                  text={searchError}
                  action={
                    <Button variant="outline" onClick={() => void search(submittedQuery, page)}>
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
                  {results.map((item) => (
                    <ProductCard
                      key={item.id}
                      item={item}
                    />
                  ))}
                </div>
                {totalPages > 1 ? (
                  <div className="mt-6 flex items-center justify-center gap-3">
                    <Button
                      variant="outline"
                      disabled={page === 0}
                      onClick={() => void search(submittedQuery, page - 1)}
                    >
                      Trang trước
                    </Button>
                    <span className="text-sm text-text-muted">
                      {page + 1} / {totalPages}
                    </span>
                    <Button
                      variant="outline"
                      disabled={page + 1 >= totalPages}
                      onClick={() => void search(submittedQuery, page + 1)}
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
