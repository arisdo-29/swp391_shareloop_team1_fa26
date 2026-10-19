import type { Item, ItemCondition, ItemType } from '../types/domain';

// Dữ liệu mô phỏng chỉ phục vụ dựng giao diện issue #130.
// Khi contract #125 hoàn tất, bỏ module này và gọi aiApi.search(query, page, size).
export const AI_SEARCH_PREVIEW_ENABLED = true;

export interface PreviewUnderstoodFilter {
  label: string;
  value: string;
}

export interface PreviewSearchResult {
  filters: PreviewUnderstoodFilter[];
  items: Item[];
}

const STOP_WORDS = new Set(['ban', 'can', 'cho', 'dang', 'do', 'giup', 'minh', 'mon', 'muon', 'tim', 'toi', 'voi']);

const normalize = (value: string) =>
  value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase();

const tokensOf = (value: string) =>
  normalize(value)
    .split(/[^a-z0-9]+/)
    .filter((token) => token.length > 2 && !STOP_WORDS.has(token));

const isAvailable = (item: Item) =>
  (item.status === 'approved' || item.status === 'APPROVED')
  && (!item.expiresAt || new Date(item.expiresAt).getTime() >= Date.now());

function understoodType(query: string): ItemType | '' {
  if (query.includes('trao doi')) return 'trade';
  if (query.includes('mien phi') || query.includes('cho tang') || query.includes('tang lai')) return 'gift';
  return '';
}

function understoodCondition(query: string): ItemCondition | '' {
  if (query.includes('nhu moi')) return 'LIKE_NEW';
  if (query.includes('con moi') || query.includes('hang moi')) return 'NEW';
  if (query.includes('con tot') || query.includes('tinh trang tot')) return 'GOOD';
  return '';
}

export function searchPreviewItems(query: string, sourceItems: Item[]): PreviewSearchResult {
  if (!AI_SEARCH_PREVIEW_ENABLED) throw new Error('Preview search is disabled.');

  const normalizedQuery = normalize(query);
  const availableItems = sourceItems.filter(isAvailable);
  const categories = [...new Set(availableItems.map((item) => item.category).filter(Boolean))];
  const districts = [...new Set(availableItems.map((item) => item.district).filter(Boolean))];
  const category = categories.find((value) => normalizedQuery.includes(normalize(value))) ?? '';
  const district = districts.find((value) => normalizedQuery.includes(normalize(value))) ?? '';
  const type = understoodType(normalizedQuery);
  const condition = understoodCondition(normalizedQuery);
  const queryTokens = tokensOf(query);

  const filters: PreviewUnderstoodFilter[] = [
    ...(category ? [{ label: 'Danh mục', value: category }] : []),
    ...(district ? [{ label: 'Khu vực', value: district }] : []),
    ...(type ? [{ label: 'Hình thức', value: type === 'gift' ? 'Cho tặng' : 'Trao đổi' }] : []),
    ...(condition
      ? [{
          label: 'Tình trạng',
          value: condition === 'NEW' ? 'Mới' : condition === 'LIKE_NEW' ? 'Như mới' : 'Còn tốt',
        }]
      : []),
  ];

  const items = availableItems
    .filter((item) => !category || item.category === category)
    .filter((item) => !district || item.district === district)
    .filter((item) => !type || item.type === type)
    .filter((item) => !condition || item.condition === condition)
    .map((item) => {
      const searchable = normalize([
        item.title,
        item.description,
        item.category,
        item.district,
        item.tradeFor ?? '',
      ].join(' '));
      const score = queryTokens.reduce((total, token) => total + (searchable.includes(token) ? 1 : 0), 0);
      return { item, score };
    })
    .filter(({ score }) => score > 0 || filters.length > 0)
    .sort((left, right) => right.score - left.score || right.item.postedAt.localeCompare(left.item.postedAt))
    .map(({ item }) => item);

  return { filters, items };
}
