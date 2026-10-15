import type { Item, ItemCondition, ItemStatus, User } from '../types/domain';
import type { AuthUserResponse, MeResponse } from './authApi';
import type { AreaResponse, CategoryResponse } from './catalogApi';
import type { ItemResponse } from './itemApi';
import { resolveMediaUrl } from './mediaUrl';

export interface AreaOption { id: number; name: string; label: string }

export const apiUserId = (id: number) => `api_${id}`;

export function toFeUser(user: AuthUserResponse | MeResponse): User {
  const profile = user as MeResponse;
  return {
    id: apiUserId(user.id),
    username: user.email,
    password: '',
    name: user.fullName,
    email: user.email,
    phone: profile.phone ?? '',
    district: '',
    avatarInitials: user.fullName.split(' ').map((p) => p[0]).slice(-2).join('').toUpperCase(),
    avatarUrl: profile.avatarUrl ?? undefined,
    totalCredit: 0,
    availableCredit: 0,
    holdCredit: 0,
    rewardPoints: 0,
    reputationStars: 5,
    rank: 'Thành viên mới',
    status: 'active',
    role: user.isAdmin ? 'admin' : 'user',
    joinedAt: new Date().toISOString(),
    totalTx: 0,
  };
}

export function toCategoryNames(categories: CategoryResponse[]): string[] {
  return categories
    .filter((category) => !category.restricted)
    .sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name, 'vi'))
    .map((category) => category.name);
}

export function toCategoryNameMap(categories: CategoryResponse[]): Map<number, string> {
  return new Map(categories.map((category) => [category.id, category.name]));
}

export function toCategoryIdByName(categories: CategoryResponse[]): Map<string, number> {
  return new Map(categories.map((category) => [category.name, category.id]));
}

export function toAreaOptions(areas: AreaResponse[]): AreaOption[] {
  const entries = flattenAreas(areas);
  const nameCounts = entries.reduce((counts, entry) => {
    counts.set(entry.area.name, (counts.get(entry.area.name) ?? 0) + 1);
    return counts;
  }, new Map<string, number>());
  return entries.map(({ area, parents }) => ({
    id: area.id,
    name: area.name,
    label: (nameCounts.get(area.name) ?? 0) > 1 && parents.length
      ? `${area.name} (${parents.join(' / ')})`
      : area.name,
  }));
}

export function toAreaNames(areas: AreaResponse[]): string[] {
  return toAreaOptions(areas).map((area) => area.label);
}

export function toAreaNameMap(areas: AreaResponse[]): Map<number, string> {
  return new Map(toAreaOptions(areas).map((area) => [area.id, area.label]));
}

export function toAreaIdByName(areas: AreaResponse[]): Map<string, number> {
  return new Map(toAreaOptions(areas).map((area) => [area.label, area.id]));
}

function flattenAreas(areas: AreaResponse[]): Array<{ area: AreaResponse; parents: string[] }> {
  const leaves: Array<{ area: AreaResponse; parents: string[] }> = [];
  const collect = (entries: AreaResponse[], parents: string[]) => {
    entries.forEach((area) => {
      if (!area.children.length) leaves.push({ area, parents });
      else collect(area.children, [...parents, area.name]);
    });
  };
  collect(areas, []);
  return leaves;
}

const CONDITION_TO_FE: Record<ItemResponse['condition'], ItemCondition> = {
  NEW: 'new',
  LIKE_NEW: 'good',
  GOOD: 'good',
  DEFECTIVE: 'used',
};

const STATUS_TO_FE: Record<ItemResponse['status'], ItemStatus> = {
  PENDING_REVIEW: 'PENDING_REVIEW',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  RESERVED: 'IN_TRANSACTION',
  TRADED: 'COMPLETED',
  EXPIRED: 'expired',
};

export function toFeItem(
  item: ItemResponse,
  categoryNames: Map<number, string>,
  areaNames: Map<number, string>,
): Item {
  return {
    id: String(item.id),
    ownerId: apiUserId(item.donorId),
    title: item.title,
    description: item.description,
    type: item.offerType === 'GIVE' ? 'gift' : 'trade',
    category: categoryNames.get(item.categoryId) ?? '',
    condition: CONDITION_TO_FE[item.condition],
    district: areaNames.get(item.areaId) ?? '',
    images: Array.isArray(item.images) ? item.images.map(resolveMediaUrl).filter(Boolean) : [],
    tradeFor: item.desiredItem ?? undefined,
    brand: item.brand ?? undefined,
    defectNote: item.defectNote ?? undefined,
    status: STATUS_TO_FE[item.status],
    postedAt: item.createdAt,
  };
}
