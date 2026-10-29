import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Button,
  EmptyState,
  Icon,
  LoadingState,
  PageHeader,
  ProductCard,
  SearchField,
  Select,
} from '../../components/ui';
import { CONDITIONS } from '../../constants/domain';
import { conditionLabel } from '../../utils/formatting';
import type { ItemType } from '../../types/domain';
import { catalogApi, type CategoryResponse } from '../../services/catalogApi';
import {
  itemApi,
  type ApiCondition,
  type ItemResponse,
  type OfferType,
} from '../../services/itemApi';
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

const PAGE_SIZE = 24;
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
  const boostedOnly = params.get('boosted') === 'true';
  const [mobileFilters, setMobileFilters] = useState(false);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const sortMode = params.get('sort') === 'expiring' ? 'expiring' : 'newest';

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
    const requestParams = {
      categoryId,
      areaId:
        parsedAreaId !== undefined && Number.isSafeInteger(parsedAreaId) && parsedAreaId > 0
          ? parsedAreaId
          : undefined,
      q: q.trim() || undefined,
      condition: condition || undefined,
      offerType: type ? OFFER_TYPE_BY_TYPE[type] : undefined,
    };
    const request = loadBrowseItems({
      ...requestParams,
      boosted: boostedOnly || undefined,
      ...(sortMode === 'expiring' ? { sort: sortMode } : {}),
      page,
      size: PAGE_SIZE,
    });
    request
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
  }, [boostedOnly, categoryId, condition, district, itemsReloadKey, page, q, sortMode, type]);

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

  const typeOptions: Array<[ItemType | '', string]> = [
    ['', 'Tất cả'],
    ['gift', 'Cho tặng'],
    ['trade', 'Trao đổi'],
  ];
  const conditionOptions: Array<[ApiCondition | '', string]> = [
    ['', 'Tất cả'],
    ...CONDITIONS.map((value) => [value, conditionLabel[value]] as [ApiCondition, string]),
  ];

  const filters = (
    <div className="space-y-5">
      <SearchField
        aria-label="Tìm tên món đồ"
        value={q}
        onChange={(event) => setFilter('q', event.target.value)}
        placeholder="Tên món đồ"
      />
      <ChipGroup label="Hình thức" options={typeOptions} value={type} onChange={(value) => setFilter('type', value)} />
      <Select
        label="Danh mục"
        value={category}
        title={selectedCategoryName}
        onChange={(event) => setFilter('categoryId', event.target.value)}
      >
        <option value="">Tất cả danh mục</option>
        {catalogLoading ? <option disabled>Đang tải danh mục...</option> : null}
        {!catalogLoading && !categories.length ? <option disabled>Chưa có danh mục</option> : null}
        {categories.map((entry) => (
          <option key={entry.id} value={entry.id}>
            {entry.name}
          </option>
        ))}
      </Select>
      <ChipGroup
        label="Tình trạng"
        options={conditionOptions}
        value={condition}
        onChange={(value) => setFilter('condition', value)}
      />
      <Select label="Quận" value={district} onChange={(event) => setFilter('district', event.target.value)}>
        <option value="">Tất cả quận</option>
        {catalogLoading ? <option disabled>Đang tải khu vực...</option> : null}
        {!catalogLoading && !districts.length ? <option disabled>Chưa có khu vực</option> : null}
        {districts.map((entry) => (
          <option key={entry.id} value={entry.id}>
            {entry.label}
          </option>
        ))}
      </Select>
      {catalogError ? (
        <div className="space-y-2">
          <p className="text-xs text-error">{catalogError}</p>
          <Button variant="outline" onClick={() => setCatalogReloadKey((key) => key + 1)}>
            Thử tải lại
          </Button>
        </div>
      ) : null}
      <Button type="button" className="w-full lg:hidden" onClick={() => setMobileFilters(false)}>
        Áp dụng bộ lọc
      </Button>
    </div>
  );

  return (
    <div className="page-shell">
      <PageHeader
        title={boostedOnly ? 'Tin nổi bật' : 'Tìm đồ'}
        description="Bộ lọc áp dụng ngay khi bạn chọn. Tìm theo quận, không theo khoảng cách."
      />
      <div className="mb-4 lg:hidden">
        <Button
          variant="outline"
          onClick={() => setMobileFilters(!mobileFilters)}
          aria-expanded={mobileFilters}
          aria-controls="browse-filters"
        >
          Bộ lọc
        </Button>
      </div>

      <div className="grid items-start gap-6 lg:grid-cols-[280px_minmax(0,1fr)]">
        <aside
          id="browse-filters"
          aria-label="Bộ lọc"
          className={`${mobileFilters ? 'block' : 'hidden'} rounded-lg bg-white p-5 ring-1 ring-border/80 lg:sticky lg:top-24 lg:block`}
        >
          <div className="mb-4 flex items-center justify-between gap-3">
            <h2 className="text-base font-bold">Bộ lọc</h2>
            <button
              type="button"
              onClick={reset}
              className="min-h-11 px-1 text-sm font-semibold text-primary hover:text-primary-hover"
            >
              Đặt lại
            </button>
          </div>
          {filters}
        </aside>

        <section className="min-w-0 space-y-4" aria-label="Danh sách món đồ">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <strong className="text-base">{totalElements} món</strong>
              {activeFilters.length ? <span className="text-sm text-text-muted">đang lọc:</span> : null}
              {activeFilters.map((filter) => (
                <button
                  key={filter.key}
                  type="button"
                  onClick={filter.clear}
                  aria-label={`Bỏ lọc ${filter.label}`}
                  className="inline-flex min-h-9 max-w-full items-center gap-1.5 rounded-full border border-primary bg-primary-soft px-3 text-xs font-semibold text-primary transition hover:bg-primary-fixed"
                >
                  <span className="min-w-0 truncate">{filter.label}</span>
                  <Icon name="close" className="size-3 shrink-0" />
                </button>
              ))}
              {activeFilters.length > 1 ? (
                <button type="button" onClick={reset} className="min-h-9 px-1 text-sm font-semibold text-primary hover:text-primary-hover">
                  Xoá tất cả
                </button>
              ) : null}
            </div>
            <label className="flex items-center gap-2 text-sm text-text-secondary">
              Sắp xếp
              <select
                aria-label="Sắp xếp"
                className="input min-h-10 w-auto py-2"
                value={sortMode}
                onChange={(event) => setFilter('sort', event.target.value === 'newest' ? '' : 'expiring')}
              >
                <option value="newest">Mới nhất</option>
                <option value="expiring">Sắp hết hạn</option>
              </select>
            </label>
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
          ) : items.length ? (
            <div data-browse-results className="grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-3">
              {items.map((item) => (
                <ProductCard key={item.id} item={item} />
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
            <div className="flex items-center justify-center gap-3 pt-2">
              <Button variant="outline" disabled={page === 0} onClick={() => setPage((current) => Math.max(0, current - 1))}>
                Trang trước
              </Button>
              <span className="text-sm text-text-secondary">
                Trang {page + 1} / {totalPages}
              </span>
              <Button variant="outline" disabled={page + 1 >= totalPages} onClick={() => setPage((current) => current + 1)}>
                Trang sau
              </Button>
            </div>
          ) : null}
        </section>
      </div>
    </div>
  );
}

/** Nhóm nút chọn một giá trị (hình thức, tình trạng). Bấm là lọc ngay, giống ô chọn. */
function ChipGroup<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Array<[T, string]>;
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <fieldset>
      <legend className="label">{label}</legend>
      <div className="flex flex-wrap gap-1.5">
        {options.map(([optionValue, optionLabel]) => {
          const active = optionValue === value;
          return (
            <button
              key={optionValue || 'all'}
              type="button"
              aria-pressed={active}
              onClick={() => onChange(optionValue)}
              className={`min-h-9 rounded-full border px-3 text-xs font-semibold transition ${active ? 'border-primary bg-primary-soft text-primary' : 'border-border bg-white text-text-secondary hover:border-primary/50'}`}
            >
              {optionLabel}
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}
