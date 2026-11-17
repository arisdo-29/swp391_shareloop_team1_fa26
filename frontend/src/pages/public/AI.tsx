import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import { useWallet } from '../../app/useWallet';
import { AiSearchNotices } from '../../components/ai/AiSearchNotices';
import { Dialog } from '../../components/Dialog';
import { Button, EmptyState, ProductCard, Spinner, TextArea } from '../../components/ui';
import { aiApi, type AiQuotaResponse } from '../../services/aiApi';
import {
  clearAiSearchKeepMark,
  clearAiSearchSnapshot,
  markAiSearchForReturn,
  readAiSearchSnapshot,
  saveAiSearchSnapshot,
  shouldKeepAiSearch,
} from '../../services/aiSearchSession';
import { catalogApi, type CategoryResponse, type AreaResponse } from '../../services/catalogApi';
import { toAreaNameMap, toCategoryNameMap, toFeItem } from '../../services/mappers';
import type { ApiError } from '../../services/http';
import type { Item } from '../../types/domain';

const MAX_QUERY_LENGTH = 500;
type SearchStatus = 'idle' | 'loading' | 'success' | 'error';

export function AI() {
  const navigate = useNavigate();
  const user = useAppSelector(selectCurrentUser);
  const userId = user?.id;
  const { wallet, refresh: refreshWallet } = useWallet();
  const [query, setQuery] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');
  const [queryError, setQueryError] = useState('');
  const [status, setStatus] = useState<SearchStatus>('idle');
  const [message, setMessage] = useState('');
  const [results, setResults] = useState<Item[]>([]);
  const [quota, setQuota] = useState<AiQuotaResponse | null>(null);
  const [quotaError, setQuotaError] = useState('');
  const [searchError, setSearchError] = useState('');
  const [chargedCredits, setChargedCredits] = useState(0);
  const [needsTopUp, setNeedsTopUp] = useState(false);
  const [pendingChargeQuery, setPendingChargeQuery] = useState<string | null>(null);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [areas, setAreas] = useState<AreaResponse[]>([]);
  const inFlight = useRef(false);
  const generation = useRef(0);

  useEffect(() => {
    let active = true;
    // Chỉ giữ kết quả khi người dùng vừa mở một bài từ danh sách rồi quay lại; sang tab khác thì xoá.
    const keepSnapshot = shouldKeepAiSearch();
    if (!keepSnapshot) clearAiSearchSnapshot();
    // Gỡ dấu sau một nhịp (không gỡ ngay) để React StrictMode ở môi trường dev chạy effect hai lần vẫn đọc đúng.
    window.setTimeout(clearAiSearchKeepMark, 0);
    const snapshot = userId && keepSnapshot ? readAiSearchSnapshot(userId) : null;
    setQuota(null);
    setQuotaError('');
    // Quay lại từ trang chi tiết thì giữ nguyên câu đã hỏi trong ô nhập.
    setQuery(snapshot?.query ?? '');
    setSubmittedQuery(snapshot?.query ?? '');
    setStatus(snapshot ? 'success' : 'idle');
    setResults(snapshot?.items ?? []);
    setMessage(snapshot?.message ?? '');
    setChargedCredits(snapshot?.chargedCredits ?? 0);
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

  const search = async (value = query, confirmCharge = false) => {
    const normalizedQuery = value.trim();
    if (!normalizedQuery || normalizedQuery.length > MAX_QUERY_LENGTH) {
      setQueryError('Vui lòng nhập mô tả từ 1 đến 500 ký tự.');
      return;
    }
    if (!user || !quota || inFlight.current) return;
    if (quota.freeUsesRemaining === 0 && !confirmCharge) {
      setQueryError('');
      setSubmittedQuery(normalizedQuery);
      if (!wallet || wallet.available < quota.feeCredits) {
        setNeedsTopUp(true);
        setSearchError('Bạn đã hết lượt miễn phí và chưa đủ Credit cho lượt tìm tiếp theo.');
        setStatus('error');
        setResults([]);
        setMessage('');
        return;
      }
      setPendingChargeQuery(normalizedQuery);
      return;
    }
    inFlight.current = true;
    setPendingChargeQuery(null);
    const currentGeneration = generation.current;
    clearAiSearchSnapshot();
    setQueryError('');
    setSearchError('');
    setChargedCredits(0);
    setNeedsTopUp(false);
    setResults([]);
    setMessage('');
    setSubmittedQuery(normalizedQuery);
    setStatus('loading');
    try {
      const response = await aiApi.search(normalizedQuery, confirmCharge);
      if (currentGeneration !== generation.current) return;
      const categoryNames = toCategoryNameMap(categories);
      const areaNames = toAreaNameMap(areas);
      const items = response.results.items.map((item) => toFeItem(item, categoryNames, areaNames));
      setMessage(response.message);
      setResults(items);
      saveAiSearchSnapshot({ userId: user.id, query: normalizedQuery, message: response.message, items, chargedCredits: response.chargedCredits });
      setQuota(response.quota);
      setChargedCredits(response.chargedCredits);
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
      const confirmationRequired = apiError.code === 'SEARCH_CHARGE_CONFIRMATION_REQUIRED';
      setNeedsTopUp(insufficientCredit);
      setSearchError(insufficientCredit ? 'Bạn đã hết lượt miễn phí và chưa đủ Credit cho lượt tìm tiếp theo.' : apiError.message || 'Không tìm được đồ. Vui lòng thử lại.');
      setStatus('error');
      setResults([]);
      setMessage('');
      if (confirmationRequired) setPendingChargeQuery(normalizedQuery);
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
      {pendingChargeQuery && quota ? (
        <Dialog title="Xác nhận lượt tìm có phí" onClose={() => setPendingChargeQuery(null)} busy={status === 'loading'}>
          <p className="text-sm leading-6 text-text-primary">
            Lượt này tính {quota.feeCredits} Credit. Số dư hiện có {wallet?.available ?? 0} Credit. Tiếp tục?
          </p>
          <div className="mt-5 flex justify-end gap-3">
            <Button variant="outline" onClick={() => setPendingChargeQuery(null)}>Huỷ</Button>
            <Button onClick={() => void search(pendingChargeQuery, true)}>Tiếp tục</Button>
          </div>
        </Dialog>
      ) : null}
      <section aria-labelledby="ai-title" className="space-y-4 rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-7">
        <div>
          <h1 id="ai-title" className="page-title">Bạn cần món gì?</h1>
          <p className="page-intro">
            Kể điều bạn muốn món đồ giúp mình làm được, hoặc nói tên món cụ thể. Trợ lý sẽ tìm trong các bài đăng còn khả dụng.
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
            Mỗi tin nhắn là một lượt tìm mới. Chỉ lượt tìm ra món phù hợp mới dùng lượt miễn phí hoặc tính {quota.feeCredits} Credit khi đã hết lượt.
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
            <Button variant={needsTopUp ? 'primary' : 'outline'} onClick={() => (needsTopUp ? navigate('/credit') : void search(submittedQuery))}>
              {needsTopUp ? 'Nạp Credit' : 'Thử lại'}
            </Button>
          }
        />
      ) : null}

      {status === 'success' ? (
        <section aria-labelledby="ai-results" className="space-y-4">
          <p id="ai-results" role="status" className="rounded-xl bg-primary-soft p-5 text-sm leading-7 text-text-primary ring-1 ring-primary/20">
            {message}
          </p>
          {results.length ? (
            <div
              className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-3 xl:grid-cols-4"
              onClickCapture={(event) => {
                // Bấm vào thẻ bài (liên kết) là sắp rời trang: đánh dấu để lúc quay lại còn kết quả.
                if ((event.target as HTMLElement).closest('a')) markAiSearchForReturn();
              }}
            >
              {results.map((item) => <ProductCard key={item.id} item={item} from="ai" />)}
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}

const AI_EXAMPLES = ['Sách tiếng Anh lớp 10 ở Quận 3', 'Xe đạp trẻ em, đổi được', 'Nồi cơm điện còn dùng tốt'];
