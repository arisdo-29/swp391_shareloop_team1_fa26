import { useMemo, useState, type ReactNode, type SelectHTMLAttributes } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectData } from '../../app/store';
import { Button, EmptyState, Icon, PageHeader, ProductCard } from '../../components/ui';
import { CATEGORIES, CONDITIONS } from '../../constants/domain';
import { conditionLabel } from '../../utils/formatting';
import type { ItemCondition, ItemType } from '../../types/domain';

export function Browse() {
  const [params] = useSearchParams();
  const data = useAppSelector(selectData);
  const { items, users } = data;
  const districts = useMemo(() => dataDistricts(data), [data]);
  const [q, setQ] = useState(params.get('q') ?? '');
  const typeParam = params.get('type');
  const [type, setType] = useState<ItemType | ''>(
    typeParam === 'gift' || typeParam === 'trade' ? typeParam : '',
  );
  const [category, setCategory] = useState(params.get('category') ?? '');
  const [condition, setCondition] = useState<ItemCondition | ''>('');
  const [district, setDistrict] = useState(params.get('district') ?? '');
  const [mobileFilters, setMobileFilters] = useState(false);
  const results = useMemo(
    () =>
      items
        .filter((i) => i.status === 'approved' || i.status === 'APPROVED')
        .filter((i) => new Date(i.expiresAt).getTime() >= Date.now())
        .filter((i) => !q || `${i.title} ${i.description}`.toLowerCase().includes(q.toLowerCase()))
        .filter((i) => !type || i.type === type)
        .filter((i) => !category || i.category === category)
        .filter((i) => !condition || i.condition === condition)
        .filter((i) => !district || i.district === district),
    [items, q, type, category, condition, district],
  );
  const reset = () => {
    setQ('');
    setType('');
    setCategory('');
    setCondition('');
    setDistrict('');
  };
  const activeFilters = [
    type
      ? {
          key: 'type',
          label: type === 'gift' ? 'Cho tặng' : 'Trao đổi',
          clear: () => setType('' as ItemType | ''),
        }
      : null,
    category ? { key: 'category', label: category, clear: () => setCategory('') } : null,
    condition
      ? {
          key: 'condition',
          label: conditionLabel[condition],
          clear: () => setCondition('' as ItemCondition | ''),
        }
      : null,
    district ? { key: 'district', label: district, clear: () => setDistrict('') } : null,
  ].filter(Boolean) as Array<{ key: string; label: string; clear: () => void }>;

  const filters = (
    <>
      <div className="flex h-11 items-center rounded-xl border border-border bg-white px-3 transition hover:border-primary/40 focus-within:border-primary focus-within:ring-4 focus-within:ring-primary/10">
        <Icon name="search" className="size-4 shrink-0 text-text-muted" />
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
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
              className="inline-flex h-7 items-center gap-1 rounded-full border border-primary/15 bg-primary-faint px-2.5 text-xs font-semibold text-primary transition hover:border-primary/40 hover:bg-primary-soft"
            >
              {filter.label}
              <Icon name="close" className="size-3" />
            </button>
          ))}
        </div>
      ) : null}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
        <FilterSelect
          label="Hình thức"
          value={type}
          onChange={(e) => setType(e.target.value as ItemType | '')}
        >
          <option value="">Tất cả</option>
          <option value="gift">Cho tặng</option>
          <option value="trade">Trao đổi</option>
        </FilterSelect>
        <FilterSelect label="Danh mục" value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="">Tất cả</option>
          {CATEGORIES.map((c) => (
            <option key={c}>{c}</option>
          ))}
        </FilterSelect>
        <FilterSelect
          label="Tình trạng"
          value={condition}
          onChange={(e) => setCondition(e.target.value as ItemCondition | '')}
        >
          <option value="">Tất cả</option>
          {CONDITIONS.map((c) => (
            <option key={c} value={c}>
              {conditionLabel[c]}
            </option>
          ))}
        </FilterSelect>
        <FilterSelect label="Khu vực" value={district} onChange={(e) => setDistrict(e.target.value)}>
          <option value="">Tất cả quận</option>
          {districts.map((d) => (
            <option key={d}>{d}</option>
          ))}
        </FilterSelect>
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
          {results.length ? (
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {results.map((item) => (
                <ProductCard
                  key={item.id}
                  item={item}
                  owner={users.find((u) => u.id === item.ownerId)}
                />
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

function dataDistricts(data: ReturnType<typeof selectData>) {
  return data.districts.filter((district) => district.status === 'active').map((district) => district.name);
}
