import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { actions, selectCurrentUser, selectData } from '../../app/store';
import { Alert, Button, EmptyState, PageHeader, ProductCard, TextArea } from '../../components/ui';
import { CATEGORIES } from '../../constants/domain';
import { findNeedMatches, parseNeed } from '../../utils/aiMatching';
import type { Item, ItemType } from '../../types/domain';
import { AI_ASSISTANT_SEARCH_FEE } from '../../utils/credit';

export function AI() {
  const data = useAppSelector(selectData);
  const user = useAppSelector(selectCurrentUser);
  const dispatch = useAppDispatch();
  const districts = useMemo(
    () => data.districts.filter((entry) => entry.status === 'active').map((entry) => entry.name),
    [data.districts],
  );
  const [text, setText] = useState('');
  const [type, setType] = useState<ItemType | ''>('');
  const [district, setDistrict] = useState('');
  const [category, setCategory] = useState('');
  const [hasSearched, setHasSearched] = useState(false);
  const [creditError, setCreditError] = useState(false);
  const [heroPaused, setHeroPaused] = useState(false);
  const [reduceMotion, setReduceMotion] = useState(false);
  const [railAnimating, setRailAnimating] = useState(false);
  const [railResetting, setRailResetting] = useState(false);
  const slideshowItems = useMemo(
    () =>
      ['item_022', 'item_005', 'item_001', 'item_024', 'item_014', 'item_006', 'item_009', 'item_011', 'item_016', 'item_008']
        .map((id) => data.items.find((item) => item.id === id))
        .filter((item): item is Item => Boolean(item)),
    [data.items],
  );
  const [activeImageIndex, setActiveImageIndex] = useState(0);
  const results = useMemo(
    () => findNeedMatches(data.items, text, { type, district, category }),
    [category, data.items, district, text, type],
  );
  const carouselItems = useMemo(
    () => [...slideshowItems, ...slideshowItems.slice(0, Math.min(7, slideshowItems.length))],
    [slideshowItems],
  );
  const carouselOffset =
    activeImageIndex === 0
      ? '0px'
      : `calc(0px ${Array.from({ length: activeImageIndex }, () => '- var(--ai-step)').join(' ')})`;

  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const syncMotionPreference = () => setReduceMotion(media.matches);

    syncMotionPreference();
    media.addEventListener('change', syncMotionPreference);

    return () => media.removeEventListener('change', syncMotionPreference);
  }, []);

  useEffect(() => {
    if (reduceMotion || heroPaused || slideshowItems.length <= 1) return undefined;
    let fadeTimeout: number | undefined;
    const interval = window.setInterval(() => {
      setRailAnimating(true);
      setActiveImageIndex((current) => current + 1);
      fadeTimeout = window.setTimeout(() => setRailAnimating(false), 520);
    }, 2000);

    return () => {
      window.clearInterval(interval);
      if (fadeTimeout) window.clearTimeout(fadeTimeout);
    };
  }, [heroPaused, reduceMotion, slideshowItems.length]);

  useEffect(() => {
    if (activeImageIndex !== slideshowItems.length) return undefined;

    const timeout = window.setTimeout(() => {
      setRailAnimating(false);
      setRailResetting(true);
      setActiveImageIndex(0);
      window.requestAnimationFrame(() => setRailResetting(false));
    }, 520);

    return () => window.clearTimeout(timeout);
  }, [activeImageIndex, slideshowItems.length]);
  const analyze = () => {
    if (user && user.availableCredit < AI_ASSISTANT_SEARCH_FEE) {
      setCreditError(true);
      return;
    }
    setCreditError(false);
    if (user) dispatch(actions.useAiFeature({ userId: user.id, feature: 'ASSISTANT_SEARCH' }));
    const parsed = parseNeed(text, CATEGORIES, districts);
    setType(parsed.type);
    setDistrict(parsed.district);
    setCategory(parsed.category);
    setHasSearched(true);
  };

  return (
    <div className="page-shell">
      <PageHeader
        title="Trợ lý tìm đồ"
        description="Mô tả nhu cầu bằng tiếng Việt. Trợ lý chỉ tìm trong các bài đăng SHARELOOP."
      />
      <div className="space-y-10">
        <div className="ai-hero-panel overflow-hidden rounded-[24px] bg-[#f6efe4] ring-1 ring-border/70">
          <section className="mx-auto max-w-3xl text-center">
            <p className="text-[11px] font-bold uppercase tracking-[0.14em] text-primary">
              LoopAI · Tìm đồ thông minh
            </p>
            <h2 className="mt-3 text-2xl font-bold leading-tight text-text-primary sm:text-3xl">
              Bạn đang tìm món gì?
            </h2>
            <p className="mx-auto mt-2.5 max-w-xl text-sm leading-6 text-text-muted">
              Mô tả món đồ bạn cần bằng cách tự nhiên, LoopAI sẽ tìm trong các bài đăng của cộng đồng.
            </p>
            <TextArea
              className="ai-hero-textarea mt-6 text-left"
              value={text}
              placeholder="Ví dụ: Tìm xe đạp mini miễn phí ở Bình Thạnh"
              onChange={(event) => setText(event.target.value)}
              aria-label="Bạn đang cần món gì?"
            />
            <Button className="mt-5 w-full sm:w-auto" icon="ai" onClick={analyze}>
              Phân tích nhu cầu
            </Button>
            {creditError ? (
              <div className="mt-4 text-left">
                <Alert tone="error">
                <p>Bạn không đủ Credit để thực hiện thao tác này.</p>
                <Button className="mt-2" size="sm" variant="outline" onClick={() => (window.location.href = '/credit')}>
                  Nạp Credit
                </Button>
                </Alert>
              </div>
            ) : null}
          </section>
          <div
            className="ai-hero-visual"
            aria-label="Ảnh sản phẩm SHARELOOP"
            onMouseEnter={() => setHeroPaused(true)}
            onMouseLeave={() => setHeroPaused(false)}
          >
            <div
              className="ai-hero-stage"
            >
              <div className="ai-hero-window">
                <div
                  className={`ai-hero-rail ${railAnimating ? 'is-animating' : ''} ${railResetting ? 'is-resetting' : ''}`}
                  style={{ '--ai-offset': carouselOffset } as CSSProperties}
                >
                  {carouselItems.map((item, index) => (
                    <Link
                      key={`${item.id}-${index}`}
                      to={`/items/${item.id}`}
                      className="ai-hero-product-card"
                      aria-label={item.title}
                    >
                      <img src={item.images[0]} alt="" />
                    </Link>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>

        {hasSearched ? (
          <section>
            <h2 className="section-title">{results.length} món phù hợp</h2>
            <p className="mt-1 text-sm text-text-muted">
              Tất cả kết quả đều đang có trên SHARELOOP.
            </p>
            {results.length ? (
              <div className="mt-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                {results.map((item) => (
                  <ProductCard
                    key={item.id}
                    item={item}
                    owner={data.users.find((user) => user.id === item.ownerId)}
                  />
                ))}
              </div>
            ) : (
              <div className="mt-4">
                <EmptyState
                  title="Chưa có món khớp nhu cầu"
                  text="Thử đổi quận, hình thức hoặc mô tả rộng hơn."
                />
              </div>
            )}
          </section>
        ) : null}
      </div>
    </div>
  );
}
