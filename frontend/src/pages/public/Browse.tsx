import { useEffect, useMemo, useState, type ReactNode, type SelectHTMLAttributes } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Button, EmptyState, Icon, PageHeader, ProductCard } from '../../components/ui';
import { CONDITIONS } from '../../constants/domain';
import { conditionLabel } from '../../utils/formatting';
import type { ItemCondition, ItemType } from '../../types/domain';
import { catalogApi, type CategoryResponse } from '../../services/catalogApi';
import { itemApi, type ItemResponse } from '../../services/itemApi';
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

const PAGE_SIZE = 20;

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
  const condition: ItemCondition | '' = (CONDITIONS as readonly string[]).includes(conditionParam ?? '')
    ? conditionParam as ItemCondition
    : '';
  const district = params.get('district') ?? '';
  const [mobileFilters, setMobileFilters] = useState(false);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

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
    itemApi
      .list({
        categoryId,
        areaId:
          parsedAreaId !== undefined && Number.isSafeInteger(parsedAreaId) && parsedAreaId > 0
            ? parsedAreaId
            : undefined,
        q: q.trim() || undefined,
        page,
        size: PAGE_SIZE,
      })
      .then((response) => {
        if (!active) return;
        setItemResponses(response.items);
        setPage(response.page);
        setTotalElements(response.totalElements);
      })
      .catch((caught) => {
        if (!active) return;
        const apiError = caught as ApiError;
        setItemResponses([]);
        setTotalElements(0);
        setItemsError(apiError.message || 'Không tải được danh sách bài đăng.');
      })
      .finally(() => {
        if (active) setItemsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [categoryId, district, itemsReloadKey, page, q]);

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

  const selectedDistrictLabel = districts.find((area) => String(area.id) === district)?.label ?? district;

  const items = useMemo(() => {
    const categoryNames = toCategoryNameMap(categories);
    const areaNames = new Map(districts.map((area) => [area.id, area.label]));
    return itemResponses.map((item) => toFeItem(item, categoryNames, areaNames));
  }, [categories, districts, itemResponses]);

  const results = useMemo(
    () =>
      items
        .filter((i) => !type || i.type === type)
        .filter((i) => !condition || i.condition === condition),
    [items, type, condition],
  );
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
    categoryId
      ? {
          key: 'category',
          label: categories.find((entry) => entry.id === categoryId)?.name ?? `Danh mục #${categoryId}`,
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
      <div className="flex h-11 items-center rounded-xl border border-border bg-white px-3 transition hover:border-primary/40 focus-within:border-primary focus-within:ring-4 focus-within:ring-primary/10">
        <Icon name="search" className="size-4 shrink-0 text-text-muted" />
        <input
          value={q}
          onChange={(e) => {
            setFilter('q', e.target.value);
          }}
          placeholder="Tìm tên món đồ..."
          className="min-w-0 flex-1 border-0 bg-transparent px-2.5 text-sm outline-none placeholder:text-text-muted/70"
        />
      </div>
      {activeFilters.length ? (
        <div className="flex flex-wrap gap-1.5">
          {activeFilters.map((filter) => (
            <button
              key={filter.key}
              type="button"
              onClick={filter.clear}
              className="inline-flex min-h-7 max-w-full items-start gap-1.5 rounded-full border border-primary/15 bg-primary-faint px-2.5 py-1.5 text-left text-xs font-semibold leading-4 text-primary transition hover:border-primary/40 hover:bg-primary-soft"
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
          value={type}
          onChange={(e) => setFilter('type', e.target.value)}
        >
          <option value="">Tất cả</option>
          <option value="gift">Cho tặng</option>
          <option value="trade">Trao đổi</option>
        </FilterSelect>
        <FilterSelect
          label="Danh mục"
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
        {categoryId ? (
          <p className="-mt-1 break-words px-1 text-xs leading-5 text-text-muted">
            Đã chọn: <span className="font-semibold text-text-secondary">{categories.find((entry) => entry.id === categoryId)?.name ?? `Danh mục #${categoryId}`}</span>
          </p>
        ) : null}
        <FilterSelect
          label="Tình trạng"
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
            <Button variant="outline" size="sm" onClick={() => setCatalogReloadKey((key) => key + 1)}>
              Thử tải lại
            </Button>
          </div>
        ) : null}
      </div>
    </>
  );

  return (
    <div className="page-shell">
      <PageHeader
        title="Tìm đồ"
        description="Lọc các món đã được duyệt theo nhu cầu và quận của bạn."
        action={
          <Button
            variant="outline"
            icon="filter"
            className="lg:hidden"
            onClick={() => setMobileFilters(!mobileFilters)}
          >
            Bộ lọc
          </Button>
        }
      />
      <div className="grid gap-6 lg:grid-cols-[300px_1fr]">
        <aside
          className={`${mobileFilters ? 'block' : 'hidden'} h-fit overflow-hidden rounded-2xl border border-border/80 bg-white shadow-sm lg:block`}
        >
          <div className="flex items-center justify-between border-b border-border/70 px-4 py-3">
            <div className="flex items-center gap-2 text-sm font-bold text-text-primary">
              <span className="grid size-8 place-items-center rounded-lg bg-primary-faint text-primary">
                <Icon name="filter" className="size-4" weight="bold" />
              </span>
              Bộ lọc
            </div>
            <button
              type="button"
              onClick={reset}
              className="rounded-md px-2 py-1 text-xs font-bold text-primary transition hover:bg-primary-faint"
            >
              Đặt lại
            </button>
          </div>
          <div className="space-y-3 p-4">{filters}</div>
        </aside>
        <section className="min-w-0">
          <div className="mb-4 flex items-center justify-between">
            <p className="text-sm text-text-muted">
              <strong className="text-text-primary">{results.length}</strong> món phù hợp
            </p>
            <select className="rounded-md border-0 bg-transparent text-xs font-semibold text-text-secondary outline-none">
              <option>Mới nhất</option>
              <option>Gần ngày hết hạn</option>
            </select>
          </div>
          {itemsLoading ? (
            <EmptyState title="Đang tải bài đăng" text="Vui lòng chờ trong giây lát." />
          ) : itemsError ? (
            <EmptyState
              title="Không tải được bài đăng"
              text={itemsError}
              action={<Button variant="outline" onClick={() => setItemsReloadKey((key) => key + 1)}>Thử tải lại</Button>}
            />
          ) : results.length ? (
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {results.map((item) => (
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
          {!itemsLoading && !itemsError && totalElements > PAGE_SIZE ? (
            <div className="mt-6 flex items-center justify-center gap-3">
              <Button
                variant="outline"
                disabled={page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                Trang trước
              </Button>
              <span className="text-sm text-text-muted">
                Trang {page + 1} / {Math.ceil(totalElements / PAGE_SIZE)}
              </span>
              <Button
                variant="outline"
                disabled={(page + 1) * PAGE_SIZE >= totalElements}
                onClick={() => setPage((current) => current + 1)}
              >
                Trang sau
              </Button>
            </div>
          ) : null}
        </section>
      </div>
    </div>
  );
}

function FilterSelect({
  label,
  children,
  className = '',
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string; children: ReactNode }) {
  return (
    <label className="block min-w-0">
      <span className="mb-1 block text-xs font-bold text-text-secondary">{label}</span>
      <span className="relative block">
        <select
          className={`h-11 w-full appearance-none rounded-xl border border-border bg-white px-3 pr-9 text-sm font-medium text-text-primary outline-none transition hover:border-primary/40 focus:border-primary focus:ring-4 focus:ring-primary/10 ${className}`}
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
