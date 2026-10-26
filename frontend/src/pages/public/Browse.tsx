import { memo, useEffect, useMemo, useState, type ReactNode, type SelectHTMLAttributes } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import browseBanner from '../../assets/browse-banner-shareloop-dark.webp';
import { Button, ConditionBadge, EmptyState, Icon, ItemImage, LoadingState, TypeBadge } from '../../components/ui';
import { CONDITIONS } from '../../constants/domain';
import { conditionLabel } from '../../utils/formatting';
import type { Item, ItemType } from '../../types/domain';
import { catalogApi, type CategoryResponse } from '../../services/catalogApi';
import { itemApi, type ApiCondition, type ItemResponse, type OfferType } from '../../services/itemApi';
import { BoostBadge } from '../../components/BoostBadge';
import type { ApiError } from '../../services/http';
import {
  toAreaOptions,
  toCategoryNameMap,
  toFeItem,
  type AreaOption,
} from '../../services/mappers';
import {
  positiveIntegerParam,
  resetBrowseSearchParams,
  updateBrowseSearchParams,
  type BrowseFilterKey,
} from '../../utils/browseFilters';
import './browse.css';

const PAGE_SIZE = 20;
// Giá trị hình thức ở thanh địa chỉ (gift, trade) khác tên của API
const OFFER_TYPE_BY_TYPE: Record<ItemType, OfferType> = { gift: 'GIVE', trade: 'SWAP' };
const pendingBrowseItemRequests = new Map<string, ReturnType<typeof itemApi.list>>();

function loadBrowseItems(params: Parameters<typeof itemApi.list>[0]) {
  const requestKey = JSON.stringify(params);
  const pendingRequest = pendingBrowseItemRequests.get(requestKey);
  if (pendingRequest) return pendingRequest;

  const request = itemApi.list(params).finally(() => {
    if (pendingBrowseItemRequests.get(requestKey) === request) {
      pendingBrowseItemRequests.delete(requestKey);
    }
  });
  pendingBrowseItemRequests.set(requestKey, request);
  return request;
}

export function Browse() {
  const [params, setParams] = useSearchParams();
  const [itemResponses, setItemResponses] = useState<ItemResponse[]>([]);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [districts, setDistricts] = useState<AreaOption[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [itemsLoading, setItemsLoading] = useState(true);
  const [catalogError, setCatalogError] = useState('');
  const [itemsError, setItemsError] = useState('');
  const [catalogReloadKey, setCatalogReloadKey] = useState(0);
  const [itemsReloadKey, setItemsReloadKey] = useState(0);
  const q = params.get('q') ?? '';
  const typeParam = params.get('type');
  const type: ItemType | '' = typeParam === 'gift' || typeParam === 'trade' ? typeParam : '';
  const categoryId = positiveIntegerParam(params.get('categoryId'));
  const category = categoryId ? String(categoryId) : '';
  const conditionParam = params.get('condition');
  const condition: ApiCondition | '' = (CONDITIONS as readonly string[]).includes(conditionParam ?? '')
    ? conditionParam as ApiCondition
    : '';
  const district = params.get('district') ?? '';
  const [mobileFilters, setMobileFilters] = useState(false);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [sortMode, setSortMode] = useState<'newest' | 'expiring'>('newest');
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

  useEffect(() => {
    let active = true;
    setCatalogLoading(true);
    setCatalogError('');
    Promise.all([catalogApi.categories(), catalogApi.areas()])
      .then(([categoryData, areaData]) => {
        if (!active) return;
        setCategories(
          categoryData
            .filter((entry) => !entry.restricted)
            .sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name, 'vi')),
        );
        setDistricts(toAreaOptions(areaData));
      })
      .catch((caught) => {
        if (!active) return;
        const apiError = caught as ApiError;
        setCategories([]);
        setDistricts([]);
        setCatalogError(apiError.message || 'Không tải được danh mục hoặc khu vực.');
      })
      .finally(() => {
        if (!active) return;
        setCatalogLoading(false);
      });
    return () => {
      active = false;
    };
  }, [catalogReloadKey]);

  useEffect(() => {
    let active = true;
    setItemsLoading(true);
    setItemsError('');
    const parsedAreaId = district ? Number(district) : undefined;
    loadBrowseItems({
      categoryId,
      areaId:
        parsedAreaId !== undefined && Number.isSafeInteger(parsedAreaId) && parsedAreaId > 0
          ? parsedAreaId
          : undefined,
      q: q.trim() || undefined,
      condition: condition || undefined,
      offerType: type ? OFFER_TYPE_BY_TYPE[type] : undefined,
      page,
      size: PAGE_SIZE,
    })
      .then((response) => {
        if (!active) return;
        setItemResponses(response.items);
        setPage(response.page);
        setTotalElements(response.totalElements);
        setTotalPages(response.totalPages ?? Math.ceil(response.totalElements / PAGE_SIZE));
      })
      .catch((caught) => {
        if (!active) return;
        const apiError = caught as ApiError;
        setItemResponses([]);
        setTotalElements(0);
        setTotalPages(0);
        setItemsError(apiError.message || 'Không tải được danh sách bài đăng.');
      })
      .finally(() => {
        if (active) setItemsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [categoryId, condition, district, itemsReloadKey, page, q, type]);

  useEffect(() => {
    if (!district || districts.some((area) => String(area.id) === district)) return;
    const matched = districts.find((area) => area.label === district || area.name === district);
    if (matched) {
      setParams(updateBrowseSearchParams(params, { district: String(matched.id) }), { replace: true });
      setPage(0);
    }
  }, [district, districts, params, setParams]);

  const setFilter = (key: BrowseFilterKey, value: string) => {
    setParams(updateBrowseSearchParams(params, { [key]: value }));
    setPage(0);
  };

  const selectedCategoryName = categories.find((entry) => entry.id === categoryId)?.name;
  const selectedDistrictLabel = districts.find((area) => String(area.id) === district)?.label ?? district;

  const items = useMemo(() => {
    const categoryNames = toCategoryNameMap(categories);
    const areaNames = new Map(districts.map((area) => [area.id, area.label]));
    return itemResponses.map((item) => toFeItem(item, categoryNames, areaNames));
  }, [categories, districts, itemResponses]);

  // Hình thức và tình trạng đã được server lọc, ở đây chỉ sắp xếp trong trang hiện tại
  const sortedResults = useMemo(() => {
    const boosted = items.filter((item) => item.boostedUntil && new Date(item.boostedUntil).getTime() > Date.now());
    const next = items.filter((item) => !item.boostedUntil || new Date(item.boostedUntil).getTime() <= Date.now());
    if (sortMode === 'expiring') {
      return [...boosted, ...next.sort((a, b) => {
        const aExpiry = a.expiresAt ? new Date(a.expiresAt).getTime() : Number.POSITIVE_INFINITY;
        const bExpiry = b.expiresAt ? new Date(b.expiresAt).getTime() : Number.POSITIVE_INFINITY;
        return aExpiry - bExpiry;
      })];
    }
    return [...boosted, ...next.sort((a, b) => b.postedAt.localeCompare(a.postedAt))];
  }, [items, sortMode]);
  const reset = () => {
    setParams(resetBrowseSearchParams(params));
    setPage(0);
  };
  const activeFilters = [
    type
      ? {
          key: 'type',
          label: type === 'gift' ? 'Cho tặng' : 'Trao đổi',
          clear: () => setFilter('type', ''),
        }
      : null,
    // Chưa tải xong danh mục hoặc không tìm thấy tên thì ẩn chip, không hiện id kỹ thuật
    selectedCategoryName
      ? {
          key: 'category',
          label: selectedCategoryName,
          clear: () => setFilter('categoryId', ''),
        }
      : null,
    condition
      ? {
          key: 'condition',
          label: conditionLabel[condition],
          clear: () => setFilter('condition', ''),
        }
      : null,
    district
      ? { key: 'district', label: selectedDistrictLabel, clear: () => setFilter('district', '') }
      : null,
  ].filter(Boolean) as Array<{ key: string; label: string; clear: () => void }>;

  const filters = (
    <>
      <div className="flex h-11 items-center rounded-xl border border-border bg-white px-3 transition hover:border-primary/40 focus-within:border-primary focus-within:ring-4 focus-within:ring-primary/25">
        <Icon name="search" className="size-4 shrink-0 text-text-muted" />
        <input
          value={q}
          onChange={(e) => {
            setFilter('q', e.target.value);
          }}
          placeholder="Tìm tên món đồ..."
          className="min-w-0 flex-1 border-0 bg-transparent px-2.5 text-sm outline-none placeholder:text-text-muted"
        />
      </div>
      {activeFilters.length ? (
        <div className="flex flex-wrap gap-1.5">
          {activeFilters.map((filter) => (
            <button
              key={filter.key}
              type="button"
              onClick={filter.clear}
              className="inline-flex min-h-11 max-w-full items-center gap-1.5 rounded-full border border-primary/15 bg-primary-faint px-2.5 py-1.5 text-left text-xs font-semibold leading-4 text-primary transition hover:border-primary/40 hover:bg-primary-soft"
            >
              <span className="min-w-0 break-words">{filter.label}</span>
              <Icon name="close" className="mt-0.5 size-3 shrink-0" />
            </button>
          ))}
        </div>
      ) : null}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
        <FilterSelect
          label="Hình thức"
          icon="transaction"
          value={type}
          onChange={(e) => setFilter('type', e.target.value)}
        >
          <option value="">Tất cả</option>
          <option value="gift">Cho tặng</option>
          <option value="trade">Trao đổi</option>
        </FilterSelect>
        <FilterSelect
          label="Danh mục"
          icon="package"
          value={category}
          title={categories.find((entry) => entry.id === categoryId)?.name}
          onChange={(e) => {
            setFilter('categoryId', e.target.value);
          }}
        >
          <option value="">Tất cả</option>
          {catalogLoading ? <option disabled>Đang tải danh mục...</option> : null}
          {!catalogLoading && !categories.length ? <option disabled>Chưa có danh mục</option> : null}
          {categories.map((entry) => (
            <option key={entry.id} value={entry.id}>{entry.name}</option>
          ))}
        </FilterSelect>
        {selectedCategoryName ? (
          <p className="-mt-1 break-words px-1 text-xs leading-5 text-text-muted">
            Đã chọn: <span className="font-semibold text-text-secondary">{selectedCategoryName}</span>
          </p>
        ) : null}
        <FilterSelect
          label="Tình trạng"
          icon="check"
          value={condition}
          onChange={(e) => setFilter('condition', e.target.value)}
        >
          <option value="">Tất cả</option>
          {CONDITIONS.map((c) => (
            <option key={c} value={c}>
              {conditionLabel[c]}
            </option>
          ))}
        </FilterSelect>
        <FilterSelect
          label="Khu vực"
          icon="location"
          value={district}
          onChange={(e) => {
            setFilter('district', e.target.value);
          }}
        >
          <option value="">Tất cả quận</option>
          {catalogLoading ? <option disabled>Đang tải khu vực...</option> : null}
          {!catalogLoading && !districts.length ? <option disabled>Chưa có khu vực</option> : null}
          {districts.map((d) => (
            <option key={d.id} value={d.id}>{d.label}</option>
          ))}
        </FilterSelect>
        {catalogError ? (
          <div className="space-y-2">
            <p className="text-xs text-error">{catalogError}</p>
            <Button variant="outline" onClick={() => setCatalogReloadKey((key) => key + 1)}>
              Thử tải lại
            </Button>
          </div>
        ) : null}
      </div>
      <Button
        type="button"
        className="browse-filter-apply"
        onClick={() => setMobileFilters(false)}
      >
        Áp dụng bộ lọc
      </Button>
    </>
  );

  return (
    <div className="browse-page">
      <section className="browse-banner" aria-label="Tìm đồ, sống xanh cùng ShareLoop">
        <img
          src={browseBanner}
          alt="Tìm đồ, khám phá những món đồ đã được chia sẻ trong cộng đồng ShareLoop"
          width="2158"
          height="729"
          decoding="async"
          fetchPriority="high"
        />
      </section>

      <div className="browse-shell">
        <div className="browse-mobile-filter">
          <Button
            variant="outline"
            icon="filter"
            onClick={() => setMobileFilters(!mobileFilters)}
            aria-expanded={mobileFilters}
            aria-controls="browse-filters"
          >
            Bộ lọc
          </Button>
        </div>

        <div className="browse-layout">
          <aside
            id="browse-filters"
            className={`browse-filters ${mobileFilters ? 'is-open' : ''}`}
          >
            <div className="browse-filters__header">
              <div className="browse-filters__title">
                <span>
                  <Icon name="filter" className="size-4" weight="bold" />
                </span>
                Bộ lọc
              </div>
              <button type="button" onClick={reset} className="browse-filter-reset">
                Đặt lại
              </button>
            </div>
            <div className="browse-filters__body">{filters}</div>
          </aside>

          <section className="min-w-0" aria-label="Danh sách món đồ">
            <div className="browse-toolbar">
              <p>
                <strong>{totalElements}</strong> món phù hợp
              </p>
              <div className="browse-toolbar__controls">
                <label className="browse-sort">
                  <span>Sắp xếp</span>
                  <select
                    aria-label="Sắp xếp"
                    value={sortMode}
                    onChange={(event) => setSortMode(event.target.value as 'newest' | 'expiring')}
                  >
                    <option value="newest">Mới nhất</option>
                    <option value="expiring">Gần ngày hết hạn</option>
                  </select>
                  <Icon name="chevronDown" className="size-4" />
                </label>
                <div className="browse-view-toggle" role="group" aria-label="Kiểu hiển thị">
                  <button
                    type="button"
                    className={viewMode === 'grid' ? 'is-active' : ''}
                    aria-label="Xem dạng lưới"
                    aria-pressed={viewMode === 'grid'}
                    onClick={() => setViewMode('grid')}
                  >
                    <Icon name="dashboard" className="size-[18px]" />
                  </button>
                  <button
                    type="button"
                    className={viewMode === 'list' ? 'is-active' : ''}
                    aria-label="Xem dạng danh sách"
                    aria-pressed={viewMode === 'list'}
                    onClick={() => setViewMode('list')}
                  >
                    <Icon name="menu" className="size-[18px]" />
                  </button>
                </div>
              </div>
            </div>

            {itemsLoading ? (
              <div role="status" aria-label="Đang tải bài đăng">
                <LoadingState />
              </div>
            ) : itemsError ? (
              <EmptyState
                title="Không tải được bài đăng"
                text={itemsError}
                action={<Button variant="outline" onClick={() => setItemsReloadKey((key) => key + 1)}>Thử tải lại</Button>}
              />
            ) : sortedResults.length ? (
              <div className={viewMode === 'grid' ? 'browse-products-grid' : 'browse-products-list'}>
                {sortedResults.map((item) => (
                  <BrowseProductCard key={item.id} item={item} />
                ))}
              </div>
            ) : (
              <EmptyState
                title="Chưa tìm thấy món phù hợp"
                text="Thử bỏ bớt một bộ lọc hoặc tìm với từ khóa rộng hơn."
                action={
                  <Button variant="outline" onClick={reset}>
                    Đặt lại bộ lọc
                  </Button>
                }
              />
            )}
            {!itemsLoading && !itemsError && totalPages > 1 ? (
              <div className="browse-pagination">
                <Button
                  variant="outline"
                  disabled={page === 0}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                >
                  Trang trước
                </Button>
                <span>
                  Trang {page + 1} / {totalPages}
                </span>
                <Button
                  variant="outline"
                  disabled={page + 1 >= totalPages}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Trang sau
                </Button>
              </div>
            ) : null}
          </section>
        </div>
      </div>
    </div>
  );
}

const BrowseProductCard = memo(function BrowseProductCard({ item }: { item: Item }) {
  return (
    <Link to={`/items/${item.id}`} className="browse-product-card">
      <div className="browse-product-card__image">
        <ItemImage
          src={item.images[0]}
          alt={item.title}
          loading="lazy"
          className="size-full object-cover"
        />
        <span className="browse-product-card__badge">
          <TypeBadge type={item.type} />
        </span>
        <span className="absolute right-3 top-3"><BoostBadge boostedUntil={item.boostedUntil} /></span>
      </div>
      <div className="browse-product-card__content">
        <h2>{item.title}</h2>
        <p className="browse-product-card__description">{item.description}</p>
        <div className="browse-product-card__meta">
          <ConditionBadge condition={item.condition} />
          <span title={item.category}>{item.category}</span>
        </div>
        <div className="browse-product-card__footer">
          <span>
            <Icon name="location" className="size-4" />
            {item.district}
          </span>
          <span className="browse-product-card__link">
            Xem chi tiết
            <Icon name="arrow" className="size-3.5" />
          </span>
        </div>
      </div>
    </Link>
  );
});

function FilterSelect({
  label,
  icon,
  children,
  className = '',
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string; icon: string; children: ReactNode }) {
  return (
    <label className="browse-filter-field">
      <span className="browse-filter-field__label">
        <Icon name={icon} className="size-3.5" weight="bold" />
        {label}
      </span>
      <span className="relative block">
        <select
          className={`browse-filter-field__select ${className}`}
          {...props}
        >
          {children}
        </select>
        <Icon
          name="chevronDown"
          className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-text-muted"
        />
      </span>
    </label>
  );
}
