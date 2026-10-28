import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import { useWallet } from '../../app/useWallet';
import { AiSearchNotices } from '../../components/ai/AiSearchNotices';
import { Alert, Button, EmptyState, ProductCard, Spinner, TextArea } from '../../components/ui';
import { aiApi, type AiQuotaResponse } from '../../services/aiApi';
import { catalogApi, type CategoryResponse, type AreaResponse } from '../../services/catalogApi';
import { toAreaNameMap, toCategoryNameMap, toFeItem } from '../../services/mappers';
import { conditionLabel } from '../../utils/formatting';
import type { ApiError } from '../../services/http';
import type { Item } from '../../types/domain';

const MAX_QUERY_LENGTH = 500;
const PAGE_SIZE = 20;
type SearchStatus = 'idle' | 'loading' | 'success' | 'error';

export function AI() {
  const navigate = useNavigate();
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
  const [chargedCredits, setChargedCredits] = useState(0);
  const [fallback, setFallback] = useState(false);
  const [broadened, setBroadened] = useState(false);
  const [needsTopUp, setNeedsTopUp] = useState(false);
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
    setChargedCredits(0);
    setFallback(false);
    setBroadened(false);
    setNeedsTopUp(false);
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
    setChargedCredits(0);
    setFallback(false);
    setBroadened(false);
    setNeedsTopUp(false);
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
      if (parsed.categoryId) understood.push({ label: 'Danh mục', value: categoryNames.get(parsed.categoryId) ?? 'Danh mục phù hợp' });
      if (parsed.areaId) understood.push({ label: 'Khu vực', value: areaNames.get(parsed.areaId) ?? 'Khu vực phù hợp' });
      if (parsed.condition) understood.push({ label: 'Tình trạng', value: conditionLabel[parsed.condition] });
      if (parsed.offerType) understood.push({ label: 'Hình thức', value: parsed.offerType === 'GIVE' ? 'Cho tặng' : 'Trao đổi' });
      setFilters(understood);
      setResults(response.results.items.map((item) => toFeItem(item, categoryNames, areaNames)));
      setTotalElements(response.results.totalElements);
      setPage(response.results.page);
      setQuota(response.quota);
      setChargedCredits(response.chargedCredits);
      setFallback(response.fallback);
      setBroadened(response.broadened);
      setStatus('success');
      if (response.chargedCredits > 0) refreshWallet();
      try {
        const latest = await aiApi.quota();
        if (currentGeneration === generation.current) setQuota(latest);
      } catch {
        // Giữ snapshot từ response tìm kiếm; Backend vẫn là nơi kiểm tra hạn mức cho lần sau.
      }
    } catch (error) {
      if (currentGeneration !== generation.current) return;
      const apiError = error as ApiError;
      const insufficientCredit = apiError.code === 'WALLET_INSUFFICIENT_BALANCE';
      setNeedsTopUp(insufficientCredit);
      setSearchError(insufficientCredit ? 'Bạn đã hết lượt miễn phí và chưa đủ Credit cho lượt tìm tiếp theo.' : apiError.message || 'Không tìm được đồ. Vui lòng thử lại.');
      setStatus('error');
      setResults([]);
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
    <div className="page-shell space-y-6">
      <section aria-labelledby="ai-title" className="space-y-4 rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-7">
        <div>
          <h1 id="ai-title" className="page-title">Bạn cần món gì?</h1>
          <p className="page-intro">
            Tả bằng lời thường: món gì, tình trạng, cho tặng hay đổi, ở quận nào. Trợ lý chuyển thành bộ lọc cho bạn.
          </p>
        </div>
        <form className="space-y-3" onSubmit={submit}>
          <TextArea
            value={query}
            maxLength={MAX_QUERY_LENGTH}
            rows={3}
            placeholder="Ví dụ: Mình cần một cái bàn học còn tốt ở Bình Thạnh, ưu tiên đồ cho tặng."
            aria-label="Mô tả món đồ cần tìm"
            error={queryError}
            onChange={(event) => {
              setQuery(event.target.value);
              if (queryError) setQueryError('');
            }}
          />
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="min-w-0 flex-1">
              {quota ? (
                <AiSearchNotices
                  remainingFreeSearches={quota.freeUsesRemaining}
                  feeCredits={quota.feeCredits}
                  chargedCredits={chargedCredits}
                />
              ) : user ? (
                <div className="flex flex-wrap items-center gap-3">
                  <span className={`text-sm ${quotaError ? 'text-error' : 'text-text-muted'}`}>
                    {quotaError || 'Đang tải hạn mức tìm kiếm...'}
                  </span>
                  {quotaError ? (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => {
                        void aiApi
                          .quota()
                          .then((response) => {
                            setQuota(response);
                            setQuotaError('');
                          })
                          .catch((error: ApiError) => setQuotaError(error.message));
                      }}
                    >
                      Thử tải hạn mức
                    </Button>
                  ) : null}
                </div>
              ) : (
                <span className="text-sm text-text-muted">Đăng nhập để dùng trợ lý tìm đồ.</span>
              )}
            </div>
            <Button type="submit" size="lg" disabled={status === 'loading' || !query.trim() || !quota || !user}>
              {status === 'loading' ? 'Đang tìm...' : 'Tìm giúp tôi'}
            </Button>
          </div>
        </form>
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm text-text-muted">Thử:</span>
          {AI_EXAMPLES.map((example) => (
            <button
              key={example}
              type="button"
              onClick={() => setQuery(example)}
              className="min-h-9 rounded-full border border-border bg-white px-3 text-xs font-semibold text-text-secondary transition hover:border-primary/50 hover:text-primary"
            >
              {example}
            </button>
          ))}
        </div>
        {quota ? (
          <p className="text-xs leading-5 text-text-muted">
            Mỗi lần tìm hoặc chuyển trang dùng một lượt. Hết lượt miễn phí, mỗi lượt tốn {quota.feeCredits} Credit. Nếu hệ thống chuyển sang tìm kiếm thường, lượt và phí được hoàn lại.
          </p>
        ) : null}
      </section>

      {status === 'loading' ? (
        <div role="status" className="flex items-center gap-3 rounded-lg bg-white px-4 py-5 text-sm text-text-muted ring-1 ring-border/80">
          <Spinner className="size-5 text-primary" />
          Đang đọc mô tả và chuẩn bị kết quả...
        </div>
      ) : null}

      {status === 'error' ? (
        <EmptyState
          title="Chưa thể tìm đồ lúc này"
          text={searchError}
          action={
            <Button variant={needsTopUp ? 'primary' : 'outline'} onClick={() => (needsTopUp ? navigate('/credit') : void search(submittedQuery, page))}>
              {needsTopUp ? 'Nạp Credit' : 'Thử lại'}
            </Button>
          }
        />
      ) : null}

      {status === 'success' ? (
        <section aria-labelledby="ai-results" className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <h2 id="ai-results" className="mr-2 text-xl font-bold">
              {totalElements} món phù hợp
            </h2>
            <span className="text-sm text-text-muted">{fallback ? 'Từ khóa tìm kiếm thường' : 'Bộ lọc trợ lý hiểu được'}</span>
            {filters.length ? (
              filters.map((filter) => (
                <span
                  key={filter.label}
                  className="inline-flex min-h-9 items-center gap-1.5 rounded-full border border-primary bg-primary-soft px-3 text-xs text-text-secondary"
                >
                  <span className="font-semibold text-primary">{filter.label}:</span>
                  {filter.value}
                </span>
              ))
            ) : (
              <span className="text-sm text-text-secondary">chưa nhận ra bộ lọc cụ thể, kết quả chọn theo từ khóa.</span>
            )}
          </div>
          {broadened ? (
            <Alert tone="warning">
              Không có món khớp tất cả bộ lọc AI đã chọn. Các món dưới đây khớp từ khóa nhưng có thể khác danh mục, khu vực, tình trạng hoặc hình thức bạn yêu cầu.
            </Alert>
          ) : null}
          {results.length ? (
            <>
              <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-3 xl:grid-cols-4">
                {results.map((item) => (
                  <ProductCard key={item.id} item={item} />
                ))}
              </div>
              {totalPages > 1 ? (
                <div className="flex flex-wrap items-center justify-center gap-3">
                  <Button variant="outline" disabled={page === 0} onClick={() => void search(submittedQuery, page - 1)}>
                    Trang trước
                  </Button>
                  <span className="text-sm text-text-muted">
                    {page + 1} / {totalPages}
                  </span>
                  <Button variant="outline" disabled={page + 1 >= totalPages} onClick={() => void search(submittedQuery, page + 1)}>
                    Trang sau
                  </Button>
                  <span className="w-full text-center text-xs text-text-muted">Chuyển trang dùng thêm 1 lượt.</span>
                </div>
              ) : null}
            </>
          ) : (
            <EmptyState
              title="Chưa tìm thấy món phù hợp"
              text="Hãy thử mô tả rộng hơn, đổi khu vực hoặc bỏ bớt yêu cầu về tình trạng."
            />
          )}
        </section>
      ) : null}
    </div>
  );
}

const AI_EXAMPLES = ['Sách tiếng Anh lớp 10 ở Quận 3', 'Xe đạp trẻ em, đổi được', 'Nồi cơm điện còn dùng tốt'];
