import type { Item } from '../types/domain';

export function BoostBadge({ boostedUntil }: Pick<Item, 'boostedUntil'>) {
  if (!boostedUntil || new Date(boostedUntil).getTime() <= Date.now()) return null;
  return <span className="rounded-sm bg-secondary-soft px-2 py-1 text-xs font-semibold text-secondary">Được đẩy</span>;
}
