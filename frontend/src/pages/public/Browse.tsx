import { useEffect, useMemo, useRef, useState } from 'react';
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
import { useDebouncedValue } from '../../utils/useDebouncedValue';
import { viewerKeyFor } from '../../utils/viewerKey';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import './browse.css';
import { useRealtimeRefresh } from '../../utils/useRealtimeRefresh';

// Giá trị hình thức ở thanh địa chỉ (gift, trade) khác tên của API
const OFFER_TYPE_BY_TYPE: Record<ItemType, OfferType> = { gift: 'GIVE', trade: 'SWAP' };
const BROWSE_RESULTS_PAGE_SIZE = 20;
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

// Lượt xoay bài đẩy backend trả cho một bộ lọc (filterKey). Chỉ dùng lại khi chuyển trang trong đúng bộ lọc đó.
interface SavedRotation {
  filterKey: string;
  seed: number;
}

export function Browse() {
  const [params, setParams] = useSearchParams();
  const currentUser = useAppSelector(selectCurrentUser);
  // Chỉ gửi viewerKey khi chưa đăng nhập; đã đăng nhập thì backend dùng id người dùng.
  const viewerKey = useMemo(() => viewerKeyFor(Boolean(currentUser)), [currentUser]);
  // Ref thay vì state: đổi seed không cần vẽ lại trang, và không được làm effect tải lại danh sách.
  const savedRotationRef = useRef<SavedRotation | null>(null);
  const [itemResponses, setItemResponses] = useState<ItemResponse[]>([]);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [districts, setDistricts] = useState<AreaOption[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [itemsLoading, setItemsLoading] = useState(true);
  const [catalogError, setCatalogError] = useState('');
  const [itemsError, setItemsError] = useState('');
  const [catalogReloadKey, setCatalogReloadKey] = useState(0);
  const [itemsReloadKey, setItemsReloadKey] = useState(0);
  useRealtimeRefresh(['CATALOG'], () => setItemsReloadKey((value) => value + 1));
  const q = params.get('q') ?? '';
  const [queryInput, setQueryInput] = useState(q);
  const debouncedQuery = useDebouncedValue(queryInput);
  const lastAppliedQueryRef = useRef(q);
  const typeParam = params.get('type');
  const type: ItemType | '' = typeParam === 'gift' || typeParam === 'trade' ? typeParam : '';
  const categoryId = positiveIntegerParam(params.get('categoryId'));
  const category = categoryId ? String(categoryId) : '';
  const conditionParam = params.get('condition');
  const condition: ApiCondition | '' = (CONDITIONS as readonly string[]).includes(
    conditionParam ?? '',
  )
    ? (conditionParam as ApiCondition)
    : '';
  const legacyDistrict = params.get('district') ?? '';
  const rawAreaId = params.get('areaId') ?? legacyDistrict;
  const areaId = positiveIntegerParam(rawAreaId);
  const areaFilterValue = areaId ? String(areaId) : '';
  const boostedOnly = params.get('boosted') === 'true';
  const [mobileFilters, setMobileFilters] = useState(false);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  // Sắp xếp chỉ còn "Mới nhất" và "Tin nổi bật"; "Tin nổi bật" chính là lọc bài đang đẩy (?boosted=true).
  const sortMode = boostedOnly ? 'featured' : 'newest';

  useEffect(() => {
    if (debouncedQuery === lastAppliedQueryRef.current) return;
    lastAppliedQueryRef.current = debouncedQuery;
    setParams((current) => updateBrowseSearchParams(current, { q: debouncedQuery }), {
      replace: true,
    });
    setPage(0);
  }, [debouncedQuery, setParams]);

  useEffect(() => {
    if (q === lastAppliedQueryRef.current) return;
    lastAppliedQueryRef.current = q;
    setQueryInput(q);
  }, [q]);

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
    const requestParams = {
      categoryId,
      areaId,
      q: q.trim() || undefined,
      condition: condition || undefined,
      offerType: type ? OFFER_TYPE_BY_TYPE[type] : undefined,
    };
    // Mọi thứ quyết định tập kết quả, trừ số trang. Đổi bất kỳ phần nào thì là bộ lọc mới.
    const filterKey = JSON.stringify({ ...requestParams, boostedOnly });
    const savedRotation = savedRotationRef.current;
    // Trang đầu hoặc vừa đổi bộ lọc/sắp xếp: không gửi seed để backend tính lượt theo giờ hiện tại.
    // Chuyển trang trong cùng bộ lọc: gửi lại seed để các trang cùng một lượt, không trùng và không sót bài.
    const rotationSeed =
      page > 0 && savedRotation?.filterKey === filterKey ? savedRotation.seed : undefined;
    const request = loadBrowseItems({
      ...requestParams,
      boosted: boostedOnly || undefined,
      page,
      size: BROWSE_RESULTS_PAGE_SIZE,
      ...(rotationSeed !== undefined ? { rotationSeed } : {}),
      ...(viewerKey !== undefined ? { viewerKey } : {}),
    });
    request
      .then((response) => {
        if (!active) return;
        if (response.rotationSeed !== undefined && response.rotationSeed !== null) {
          savedRotationRef.current = { filterKey, seed: response.rotationSeed };
        }
        setItemResponses(response.items);
        setPage(response.page);
        setTotalElements(response.totalElements);
        setTotalPages(
          response.totalPages ?? Math.ceil(response.totalElements / BROWSE_RESULTS_PAGE_SIZE),
        );
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
  }, [areaId, boostedOnly, categoryId, condition, itemsReloadKey, page, q, type, viewerKey]);

  useEffect(() => {
    if (!legacyDistrict || params.has('areaId')) return;
    const matchedAreaId =
      positiveIntegerParam(legacyDistrict) ??
      districts.find((area) => area.label === legacyDistrict || area.name === legacyDistrict)?.id;
    if (!matchedAreaId) return;
    setParams(updateBrowseSearchParams(params, { areaId: String(matchedAreaId) }), {
      replace: true,
    });
    setPage(0);
  }, [districts, legacyDistrict, params, setParams]);

  const setFilter = (key: BrowseFilterKey, value: string) => {
    setParams(updateBrowseSearchParams(params, { [key]: value }));
    setPage(0);
  };

  // Xoá luôn ?sort cũ (link "Sắp hết hạn" lưu từ trước) để URL chỉ còn boosted.
  const changeSortMode = (value: string) => {
    setParams(
      updateBrowseSearchParams(params, { boosted: value === 'featured' ? 'true' : '', sort: '' }),
    );
    setPage(0);
  };

  const selectedCategoryName = categories.find((entry) => entry.id === categoryId)?.name;
  const selectedDistrictLabel = districts.find((area) => area.id === areaId)?.label ?? '';

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
    areaId && selectedDistrictLabel
      ? { key: 'areaId', label: selectedDistrictLabel, clear: () => setFilter('areaId', '') }
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
    <div className="browse-filters-body">
      <SearchField
        aria-label="Tìm tên món đồ"
        value={queryInput}
        onChange={(event) => setQueryInput(event.target.value)}
        placeholder="Tên món đồ"
      />
      <ChipGroup
        label="Hình thức"
        options={typeOptions}
        value={type}
        onChange={(value) => setFilter('type', value)}
      />
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
      <Select
        label="Quận"
        value={areaFilterValue}
        onChange={(event) => setFilter('areaId', event.target.value)}
      >
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
    <div className="browse-page page-shell">
      <div className="browse-heading">
        <PageHeader title={boostedOnly ? 'Tin nổi bật' : 'Tìm đồ'} />
      </div>
      <div className="browse-mobile-filter lg:hidden">
        <Button
          variant="outline"
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
          aria-label="Bộ lọc"
          className={`${mobileFilters ? 'block' : 'hidden'} browse-filter-card lg:sticky lg:top-24 lg:block`}
        >
          <div className="browse-filter-heading">
            <h2>Bộ lọc</h2>
            <button type="button" onClick={reset} className="browse-reset-button">
              Đặt lại
            </button>
          </div>
          {filters}
        </aside>

        <section className="browse-results" aria-label="Danh sách món đồ">
          <div className="browse-results-toolbar">
            <div className="browse-results-summary">
              <strong>{totalElements} món</strong>
              {activeFilters.length ? (
                <span className="text-sm text-text-muted">đang lọc:</span>
              ) : null}
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
                <button
                  type="button"
                  onClick={reset}
                  className="min-h-9 px-1 text-sm font-semibold text-primary hover:text-primary-hover"
                >
                  Xoá tất cả
                </button>
              ) : null}
            </div>
            <label className="browse-sort-control">
              Sắp xếp
              <select
                aria-label="Sắp xếp"
                className="input"
                value={sortMode}
                onChange={(event) => changeSortMode(event.target.value)}
              >
                <option value="newest">Mới nhất</option>
                <option value="featured">Tin nổi bật</option>
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
              action={
                <Button variant="outline" onClick={() => setItemsReloadKey((key) => key + 1)}>
                  Thử tải lại
                </Button>
              }
            />
          ) : items.length ? (
            <div data-browse-results className="browse-product-grid">
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
            <nav className="browse-pagination" aria-label="Phân trang sản phẩm">
              <Button
                variant="outline"
                disabled={page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                Trang trước
              </Button>
              <span className="text-sm text-text-secondary">
                Trang {page + 1} / {totalPages}
              </span>
              <Button
                variant="outline"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((current) => current + 1)}
              >
                Trang sau
              </Button>
            </nav>
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
    <fieldset className="browse-chip-group">
      <legend className="label">{label}</legend>
      <div>
        {options.map(([optionValue, optionLabel]) => {
          const active = optionValue === value;
          return (
            <button
              key={optionValue || 'all'}
              type="button"
              aria-pressed={active}
              onClick={() => onChange(optionValue)}
              className={active ? 'is-active' : ''}
            >
              {optionLabel}
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}
