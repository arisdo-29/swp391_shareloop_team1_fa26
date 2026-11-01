import { type FormEvent, type ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { MapPin } from '@phosphor-icons/react/dist/csr/MapPin';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import {
  Button,
  EmptyState,
  ItemImage,
  ProductCard,
  SearchField,
} from '../../components/ui';
import type { Item, ItemType } from '../../types/domain';
import { catalogApi } from '../../services/catalogApi';
import type { AreaResponse, CategoryResponse } from '../../services/catalogApi';
import { itemApi, type ItemResponse, type PageResponse } from '../../services/itemApi';
import { toAreaNameMap, toAreaOptions, toCategoryNameMap, toFeItem, type AreaOption } from '../../services/mappers';
import {
  BROWSE_PAGE_SIZE,
  browseSearchPath,
  categoryBrowsePath,
  districtBrowsePath,
} from '../../utils/browseFilters';
import { HomeLifecycle, HomeProcess } from './HomeCommunity';
import { HomeDiscoveryRail, HomeMotion } from './HomeMotion';
import browseSuggestionBox from '../../assets/browse-suggestion-box.png';
import { loginPath } from '../../utils/authRedirect';
import './home-content.css';
const aiSuggestions = [
  {
    title: 'Đèn bàn để đọc sách',
    description: 'Tìm ánh sáng phù hợp cho góc đọc sách.',
  },
  {
    title: 'Sách cho cuối tuần',
    description: 'Khám phá những cuốn sách đang được sẻ chia.',
  },
  {
    title: 'Đồ dùng góc học tập',
    description: 'Bổ sung món đồ hữu ích cho góc học tập.',
  },
] as const;
const newItemFilters: Array<{ value: 'all' | ItemType; label: string }> = [
  { value: 'all', label: 'Tất cả' },
  { value: 'gift', label: 'Cho tặng' },
  { value: 'trade', label: 'Trao đổi' },
];

let pendingHomeItemsRequest: Promise<PageResponse<ItemResponse>> | null = null;
let pendingHomeFeaturedItemsRequest: Promise<ItemResponse[]> | null = null;
let pendingHomeCategoriesRequest: Promise<CategoryResponse[]> | null = null;

function loadHomeCategories() {
  if (pendingHomeCategoriesRequest) return pendingHomeCategoriesRequest;

  const request = catalogApi.categories()
    .then(async (categories) => {
      const visibleCategories = categories.filter((category) => !category.restricted);
      const counts = await Promise.all(
        visibleCategories.map(async (category) => {
          const response = await itemApi.list({
            categoryId: category.id,
            page: 0,
            size: BROWSE_PAGE_SIZE,
          });
          return [category.id, response.totalElements] as const;
        }),
      );
      const countByCategoryId = new Map(counts);
      return categories.map((category) => ({
        ...category,
        activeItemCount: countByCategoryId.get(category.id) ?? category.activeItemCount,
      }));
    })
    .finally(() => {
      if (pendingHomeCategoriesRequest === request) pendingHomeCategoriesRequest = null;
    });
  pendingHomeCategoriesRequest = request;
  return request;
}

function loadHomeItems() {
  if (pendingHomeItemsRequest) return pendingHomeItemsRequest;

  const request = itemApi.list({ page: 0, size: 100 }).finally(() => {
    if (pendingHomeItemsRequest === request) pendingHomeItemsRequest = null;
  });
  pendingHomeItemsRequest = request;
  return request;
}

function loadHomeFeaturedItems() {
  if (pendingHomeFeaturedItemsRequest) return pendingHomeFeaturedItemsRequest;

  const request = loadHomeItems()
    .then(async (firstPage) => {
      const totalPages =
        firstPage.totalPages ?? Math.ceil(firstPage.totalElements / (firstPage.size || 100));
      const remainingPages = await Promise.all(
        Array.from({ length: Math.max(0, totalPages - 1) }, (_, index) =>
          itemApi.list({ page: index + 1, size: 100 }),
        ),
      );
      return [firstPage.items, ...remainingPages.map((response) => response.items)].flat();
    })
    .finally(() => {
      if (pendingHomeFeaturedItemsRequest === request) pendingHomeFeaturedItemsRequest = null;
    });
  pendingHomeFeaturedItemsRequest = request;
  return request;
}

function SectionHeading({
  title,
  description,
  href,
  linkLabel = 'Xem tất cả',
}: {
  title: string;
  description?: string;
  href?: string;
  linkLabel?: string;
}) {
  return (
    <div className="home-section-heading mb-5 flex flex-wrap items-end justify-between gap-4 sm:mb-6">
      <div className="min-w-0">
        <h2 className="text-xl font-bold leading-tight text-text-primary sm:text-2xl">{title}</h2>
        {description ? (
          <p className="mt-1.5 max-w-2xl text-sm leading-6 text-text-muted">{description}</p>
        ) : null}
      </div>
      {href ? (
        <Link
          to={href}
          className="shrink-0 text-sm font-semibold text-link transition hover:text-secondary"
        >
          {linkLabel}
        </Link>
      ) : null}
    </div>
  );
}

function HomeSection({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <section className={`w-full ${className}`}>
      <div className="home-inner">
        {children}
      </div>
    </section>
  );
}

function HomeHeroCarousel({ items, children }: { items: Item[]; children: ReactNode }) {
  const slides = useMemo(
    () => items.filter((item) => item.images.length > 0).slice(0, 8),
    [items],
  );
  const [activeIndex, setActiveIndex] = useState(0);
  const thumbnailStripRef = useRef<HTMLDivElement>(null);
  const thumbnailRefs = useRef<Array<HTMLButtonElement | null>>([]);

  useEffect(() => {
    if (slides.length <= 1) {
      return undefined;
    }

    const interval = window.setInterval(() => {
      setActiveIndex((prev) => (prev + 1) % slides.length);
    }, 3000);

    return () => window.clearInterval(interval);
  }, [slides.length]);

  useEffect(() => {
    if (activeIndex >= slides.length) {
      setActiveIndex(0);
    }
  }, [activeIndex, slides.length]);

  useEffect(() => {
    if (slides.length <= 1) {
      return;
    }

    const nextImage = new Image();
    nextImage.src = slides[(activeIndex + 1) % slides.length].images[0];
  }, [activeIndex, slides]);

  useEffect(() => {
    const strip = thumbnailStripRef.current;
    const activeThumbnail = thumbnailRefs.current[activeIndex];

    if (!strip || !activeThumbnail || strip.scrollWidth <= strip.clientWidth) {
      return;
    }

    const centeredPosition =
      activeThumbnail.offsetLeft - (strip.clientWidth - activeThumbnail.clientWidth) / 2;
    strip.scrollTo({ left: Math.max(0, centeredPosition), behavior: 'smooth' });
  }, [activeIndex]);

  if (slides.length === 0) {
    return (
      <section className="home-hero-shell border-b" data-home-hero>
        <div className="home-inner home-hero-inner home-hero-content">
          {children}
        </div>
      </section>
    );
  }

  const activeItem = slides[activeIndex];

  return (
    <section className="home-hero-shell relative isolate overflow-hidden border-b" data-home-hero>
      <div className="absolute inset-0 z-0 overflow-hidden" aria-hidden="true">
        {slides.map((item, index) => (
          <ItemImage
            key={`background-${item.id}`}
            src={item.images[0]}
            alt=""
            loading={index === activeIndex ? 'eager' : 'lazy'}
            fetchPriority={index === activeIndex ? 'high' : undefined}
            className={`absolute inset-0 size-full object-cover object-center transition-all duration-[900ms] ease-out ${
              index === activeIndex
                ? 'scale-[1.05] opacity-100 blur-[2px]'
                : 'scale-[1.07] opacity-0 blur-[2px]'
            }`}
          />
        ))}
      </div>
      <div className="home-hero-overlay absolute inset-0 z-0" aria-hidden="true" />
      <div className="home-hero-bottom-fade absolute inset-0 z-0" aria-hidden="true" />

      <div className="home-inner home-hero-inner home-hero-content relative z-10">
        <div className="grid items-center gap-8 lg:grid-cols-[1.04fr_.96fr] lg:gap-10">
          <div className="min-w-0">{children}</div>

          <div className="min-w-0">
            <Link
              to={`/items/${activeItem.id}`}
              className="group relative block h-[300px] overflow-hidden rounded-xl bg-black/20 shadow-lg ring-1 ring-white/20 sm:h-[380px] lg:h-[430px]"
              aria-label={`Xem chi tiết ${activeItem.title}`}
            >
              {slides.map((item, index) => (
                <ItemImage
                  key={`main-${item.id}`}
                  src={item.images[0]}
                  alt={index === activeIndex ? item.title : ''}
                  loading={index === activeIndex ? 'eager' : 'lazy'}
                  fetchPriority={index === activeIndex ? 'high' : undefined}
                  className={`absolute inset-0 size-full object-cover transition-all duration-700 ease-out ${
                    index === activeIndex
                      ? 'scale-100 opacity-100'
                      : 'pointer-events-none scale-[1.025] opacity-0'
                  }`}
                />
              ))}
              <div className="home-hero-card-gradient absolute inset-0 flex flex-col justify-end px-5 pb-5 sm:px-6 sm:pb-6">
                <p className="home-hero-card-eyebrow text-xs font-bold uppercase tracking-[0.12em]">
                  SHARELOOP
                </p>
                <h2 className="mt-2 line-clamp-2 text-xl font-extrabold leading-tight text-white sm:text-[22px]">
                  {activeItem.title}
                </h2>
                <p className="mt-2 flex items-center gap-1.5 text-xs font-medium text-white/80 sm:text-sm">
                  <MapPin aria-hidden className="size-3.5 shrink-0" weight="fill" />
                  <span>{activeItem.district}</span>
                </p>
              </div>
            </Link>

            <div
              ref={thumbnailStripRef}
              className="mt-4 overflow-x-auto px-1 pb-2 pt-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden sm:mt-5"
              aria-label="Chọn món đồ nổi bật"
            >
              <div className="mx-auto flex w-max min-w-full items-center justify-start gap-2.5 sm:justify-center sm:gap-3">
                {slides.map((item, index) => {
                  const isActive = index === activeIndex;

                  return (
                    <button
                      key={item.id}
                      ref={(element) => {
                        thumbnailRefs.current[index] = element;
                      }}
                      type="button"
                      aria-label={`Hiển thị ảnh ${item.title}`}
                      aria-current={isActive ? 'true' : undefined}
                      onClick={() => setActiveIndex(index)}
                      className={`h-14 w-[72px] shrink-0 overflow-hidden rounded-md border bg-white/10 p-0 shadow-md transition duration-500 ease-out focus-visible:outline-white sm:h-16 sm:w-[84px] ${
                        isActive
                          ? '-translate-y-1 scale-105 border-2 border-[#E0B25A] opacity-100 shadow-lg'
                          : 'scale-95 border-white/35 opacity-60 hover:scale-100 hover:border-white/70 hover:opacity-100'
                      }`}
                    >
                      <ItemImage
                        src={item.images[0]}
                        alt={item.title}
                        loading={isActive ? 'eager' : 'lazy'}
                        fetchPriority={isActive ? 'high' : undefined}
                        className="size-full object-cover"
                      />
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="flex justify-center gap-1.5" aria-hidden="true">
              {slides.map((item, index) => (
                <span
                  key={`dot-${item.id}`}
                  className={`size-1.5 rounded-full transition ${
                    index === activeIndex ? 'bg-[#E0B25A]' : 'bg-white/35'
                  }`}
                />
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

export function Home() {
  const currentUser = useAppSelector(selectCurrentUser);
  const navigate = useNavigate();
  const [itemResponses, setItemResponses] = useState<ItemResponse[]>([]);
  const [featuredItemResponses, setFeaturedItemResponses] = useState<ItemResponse[]>([]);
  const [categoryResponses, setCategoryResponses] = useState<CategoryResponse[]>([]);
  const [areaResponses, setAreaResponses] = useState<AreaResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [q, setQ] = useState('');
  const [district, setDistrict] = useState('');
  const [newItemFilter, setNewItemFilter] = useState<'all' | ItemType>('all');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setLoadError('');

    void loadHomeCategories().then((categoryData) => {
      if (active) setCategoryResponses(categoryData);
    }).catch(() => undefined);

    void catalogApi.areas().then((areaData) => {
      if (active) setAreaResponses(areaData);
    }).catch(() => undefined);

    void Promise.all([loadHomeItems(), loadHomeFeaturedItems()])
      .then(([response, featuredResponses]) => {
        if (!active) return;
        setItemResponses(response.items);
        setFeaturedItemResponses(featuredResponses);
      })
      .catch((caught: { message?: string }) => {
        if (!active) return;
        setItemResponses([]);
        setFeaturedItemResponses([]);
        setLoadError(caught.message || 'Không tải được bài đăng.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [reloadKey]);

  const categories = useMemo(
    () =>
      categoryResponses
        .filter((entry) => !entry.restricted)
        .sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name, 'vi')),
    [categoryResponses],
  );
  const districtOptions: AreaOption[] = useMemo(() => toAreaOptions(areaResponses), [areaResponses]);
  const categoryNameMap = useMemo(() => toCategoryNameMap(categoryResponses), [categoryResponses]);
  const areaNameMap = useMemo(() => toAreaNameMap(areaResponses), [areaResponses]);
  const items = useMemo(
    () => itemResponses.map((entry) => toFeItem(entry, categoryNameMap, areaNameMap)),
    [areaNameMap, categoryNameMap, itemResponses],
  );
  const featuredItems = useMemo(
    () => featuredItemResponses.map((entry) => toFeItem(entry, categoryNameMap, areaNameMap)),
    [areaNameMap, categoryNameMap, featuredItemResponses],
  );

  const approved = useMemo(
    () =>
      items
        .filter((item) => item.status === 'approved' || item.status === 'APPROVED')
        .filter((item) => !item.expiresAt || new Date(item.expiresAt).getTime() >= Date.now())
        .sort((a, b) => {
          const aBoosted = Boolean(a.boostedUntil && new Date(a.boostedUntil).getTime() > Date.now());
          const bBoosted = Boolean(b.boostedUntil && new Date(b.boostedUntil).getTime() > Date.now());
          return Number(bBoosted) - Number(aBoosted) || b.postedAt.localeCompare(a.postedAt);
        }),
    [items],
  );
  const gifts = approved.filter((item) => item.type === 'gift');
  const newestApproved = [...approved].sort((a, b) => b.postedAt.localeCompare(a.postedAt));
  const trades = newestApproved.filter((item) => item.type === 'trade');
  const newItems = (
    newItemFilter === 'all'
      ? newestApproved
      : newestApproved.filter((item) => item.type === newItemFilter)
  ).slice(0, 5);
  const boostedItems = featuredItems
    .filter((item) => item.status === 'approved' || item.status === 'APPROVED')
    .filter((item) => !item.expiresAt || new Date(item.expiresAt).getTime() >= Date.now())
    .filter((item) => item.boostedUntil && new Date(item.boostedUntil).getTime() > Date.now())
    .sort((a, b) => b.boostedUntil!.localeCompare(a.boostedUntil!));
  const heroItems = boostedItems.filter((item) => item.images.length > 0).slice(0, 8);
  const featured = boostedItems.slice(0, 5);
  const aiPreviewItems = boostedItems.filter((item) => item.images.length > 0).slice(0, 3);
  const districtLinks = districtOptions.slice(0, 6).map((area) => ({
    name: area.label,
    id: area.id,
  }));
  const quickLinks = [
    { label: 'Đồ cho tặng', to: '/browse?type=gift' },
    ...categories.slice(0, 2).map((category) => ({
      label: category.name,
      to: categoryBrowsePath(category.id),
    })),
    ...(districtOptions[0]
      ? [{ label: districtOptions[0].label, to: districtBrowsePath(districtOptions[0].id) }]
      : []),
  ];

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const selectedAreaId = districtOptions.find((area) => String(area.id) === district)?.id;
    navigate(browseSearchPath(q, selectedAreaId));
  };

  return (
    <div className="home-page pb-4 sm:pb-6">
      <HomeHeroCarousel items={heroItems}>
        <div className="max-w-2xl">
          <p className="home-hero-eyebrow text-xs font-bold uppercase tracking-[0.12em]">
            Cộng đồng cho tặng và trao đổi tại TP.HCM
          </p>
          <h1 className="mt-3 text-4xl font-extrabold leading-[1.12] text-white sm:text-5xl lg:text-[52px]">
            Món đồ cũ.
            <br />
            <span className="home-hero-title-accent">Một vòng đời mới.</span>
          </h1>
          <p className="home-hero-description mt-4 max-w-xl text-sm leading-6 sm:text-base sm:leading-7">
            Tìm những món đồ còn tốt từ người trong cộng đồng, theo đúng quận bạn thuận tiện gặp gỡ.
          </p>

          <form
            onSubmit={submit}
            className="home-hero-search-form mt-6 grid gap-2 rounded-lg p-2 sm:grid-cols-[1fr_176px_auto]"
          >
            <SearchField
              value={q}
              onChange={(event) => setQ(event.target.value)}
              placeholder="Bạn đang tìm món gì?"
              aria-label="Từ khóa tìm kiếm"
            />
            <select
              aria-label="Chọn quận"
              className="input appearance-none"
              value={district}
              onChange={(event) => setDistrict(event.target.value)}
            >
              <option value="">Tất cả khu vực</option>
              {districtOptions.map((area) => (
                <option key={area.id} value={area.id}>{area.label}</option>
              ))}
            </select>
            <Button
              type="submit"
              className="home-hero-submit"
            >
              Tìm đồ
            </Button>
          </form>

          <div className="mt-4 flex gap-2 overflow-x-auto pb-1 sm:flex-wrap">
            {quickLinks.map((link) => (
              <Link
                key={link.label}
                to={link.to}
                className="home-hero-chip shrink-0 rounded-full border px-3 py-1.5 text-xs font-semibold backdrop-blur-sm transition"
              >
                {link.label}
              </Link>
            ))}
          </div>
        </div>
      </HomeHeroCarousel>

      {loading || loadError ? (
        <div className="home-inner py-5">
          <div role={loadError ? 'alert' : 'status'} className={`rounded-lg p-4 text-sm ${loadError ? 'bg-error-soft text-error' : 'bg-primary-faint text-primary'}`}>
            {loadError ? (
              <div className="flex flex-wrap items-center justify-between gap-3">
                <span>{loadError}</span>
                <Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>Thử lại</Button>
              </div>
            ) : 'Đang tải bài đăng mới nhất...'}
          </div>
        </div>
      ) : null}

      <HomeMotion>
        {!loading && !loadError && approved.length === 0 ? (
          <HomeSection className="home-band">
            <EmptyState title="Chưa có bài đăng đã duyệt" text="Các món đồ mới sẽ xuất hiện tại đây sau khi được duyệt." />
          </HomeSection>
        ) : null}
        <HomeSection className="home-band home-categories-band">
          <SectionHeading title="Khám phá theo danh mục" description="Tìm nhanh những món đồ đang được chia sẻ trong cộng đồng." href="/browse" />
          <HomeDiscoveryRail label="Khám phá theo danh mục" variant="categories">
            {categories.map((category) => {
              return (
                <Link key={category.id} to={categoryBrowsePath(category.id)} className="home-category group">
                  <span className="home-category-label">{category.name}</span>
                  <span className="home-category-count">{category.activeItemCount} món đồ</span>
                </Link>
              );
            })}
          </HomeDiscoveryRail>
        </HomeSection>

        <HomeSection className="home-band home-new-items-band">
          <SectionHeading title="Món đồ mới" description="Những món đồ vừa được chia sẻ và đang chờ một vòng đời mới." href="/browse" />
          <div className="home-new-filters" role="tablist" aria-label="Lọc món đồ mới">
            {newItemFilters.map((filter) => (
              <button
                key={filter.value}
                type="button"
                role="tab"
                aria-selected={newItemFilter === filter.value}
                onClick={() => setNewItemFilter(filter.value)}
              >
                {filter.label}
              </button>
            ))}
          </div>
          <div className="home-product-grid home-new-items-grid home-featured-items-grid">
            {newItems.map((item) => (
              <ProductCard key={item.id} item={item} />
            ))}
          </div>
        </HomeSection>

        <HomeSection className="home-band">
          <SectionHeading title="Tin nổi bật" description="Những món đồ nổi bật đang sẵn sàng cho một kết nối mới." href="/browse?boosted=true" />
          <div className="home-product-grid home-new-items-grid home-featured-items-grid">
            {featured.map((item) => (
              <ProductCard key={item.id} item={item} />
            ))}
          </div>
        </HomeSection>

        <div className="home-gift-band">
          <HomeSection className="home-band">
            <SectionHeading title="Đồ đang được cho tặng" description="Những món còn hữu ích, được gửi tiếp không kèm giá bán." href="/browse?type=gift" linkLabel="Xem đồ tặng" />
            <div className="home-product-grid">
              {gifts.slice(0, 5).map((item) => (
                <ProductCard key={item.id} item={item} />
              ))}
            </div>
          </HomeSection>
        </div>

        <HomeSection className="home-band">
          <SectionHeading title="Đang tìm người trao đổi" description="Xem món chủ sở hữu đang có và điều họ mong muốn nhận lại." href="/browse?type=trade" linkLabel="Xem đồ trao đổi" />
          <div className="home-product-grid home-trade-grid">
            {trades.slice(0, 5).map((item) => (
              <ProductCard key={item.id} item={item} tradeFor={item.tradeFor} />
            ))}
          </div>
        </HomeSection>

        <HomeSection className="home-band">
          <SectionHeading title="Khám phá quanh quận của bạn" description="Chọn khu vực thuận tiện để hẹn giao nhận trực tiếp." href="/browse" linkLabel="Xem mọi khu vực" />
          <div className="home-district-panel" aria-label="Khám phá quanh quận của bạn">
            {districtLinks.map(({ name, id }) => (
              <Link key={id} to={districtBrowsePath(id)} className="home-district">
                <MapPin aria-hidden className="home-district-pin" weight="fill" />
                <h3>{name}</h3>
              </Link>
            ))}
          </div>
        </HomeSection>

        <div className="home-final-cluster">
          <HomeLifecycle items={aiPreviewItems} />
          <HomeProcess />
          <HomeSection className="home-band home-cta-band">
            <div className="home-final">
              <div>
                <h2>Có món đồ vẫn còn tốt?</h2>
                <p>Đăng lên SHARELOOP để tặng lại hoặc tìm một món phù hợp để trao đổi.</p>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                {currentUser ? (
                  <Link to="/post" className="home-final-primary inline-flex min-h-12 items-center justify-center gap-2 rounded-md bg-white px-5 py-3 text-sm font-bold text-primary transition">Đăng món đồ</Link>
                ) : (
                  <button type="button" onClick={() => navigate(loginPath('/post'))} className="home-final-primary inline-flex min-h-12 items-center justify-center gap-2 rounded-md bg-white px-5 py-3 text-sm font-bold text-primary transition">Đăng món đồ</button>
                )}
                <Link to="/browse" className="text-sm font-bold text-link">Tìm đồ</Link>
              </div>
            </div>
          </HomeSection>
          <HomeSection className="home-band home-ai-section">
            <div className="home-ai home-suggestion-feature" aria-labelledby="home-suggestion-title">
              <div className="home-suggestion-copy">
                <span className="home-suggestion-eyebrow">
                  Gợi ý từ LoopAI
                </span>
                <h2 id="home-suggestion-title">Bạn đang tìm món gì?</h2>
                <p>
                  Mô tả món đồ bạn cần, LoopAI sẽ gợi ý những lựa chọn phù hợp từ cộng đồng ShareLoop.
                </p>
                <div className="home-ai-suggestions">
                  {aiPreviewItems.map((item, index) => {
                    const suggestion = aiSuggestions[index];
                    return (
                      <Link key={item.id} to="/ai">
                        <span className="home-suggestion-card-copy">
                          <strong>{suggestion.title}</strong>
                          <span>{suggestion.description}</span>
                        </span>
                      </Link>
                    );
                  })}
                </div>
              </div>

              <div className="home-suggestion-visual" aria-hidden="true">
                <span />
                <img
                  src={browseSuggestionBox}
                  alt=""
                  width="1536"
                  height="1024"
                  loading="lazy"
                  decoding="async"
                />
              </div>
            </div>
          </HomeSection>
        </div>

      </HomeMotion>
    </div>
  );
}
