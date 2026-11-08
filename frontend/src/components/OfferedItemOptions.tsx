import type { Item } from '../types/domain';
import { ItemImage } from './ui';

interface OfferedItemOptionsProps {
  // Chỉ cần vài trường để vẽ thẻ, nên trang chat có thể truyền món dựng từ ItemResponse.
  items: Array<Pick<Item, 'id' | 'title' | 'images' | 'district'>>;
  selectedItemId: string;
  onSelect: (itemId: string) => void;
}

// Danh sách thẻ để chọn món của mình mang đi đổi; dùng chung cho form gửi yêu cầu và đề nghị đổi món trong chat.
export function OfferedItemOptions({ items, selectedItemId, onSelect }: OfferedItemOptionsProps) {
  return (
    <>
      {items.map((entry) => (
        <button
          key={entry.id}
          type="button"
          aria-pressed={selectedItemId === entry.id}
          onClick={() => onSelect(entry.id)}
          className={`box-border flex min-w-0 w-full max-w-full items-center gap-3 overflow-hidden rounded-md border p-2.5 text-left transition ${selectedItemId === entry.id ? 'border-primary bg-primary-faint' : 'border-border hover:border-primary/40'}`}
        >
          <ItemImage src={entry.images[0]} alt={entry.title} className="h-11 w-14 shrink-0 rounded-md object-cover" />
          <span className="min-w-0 max-w-full flex-1 overflow-hidden">
            <strong className="block whitespace-normal break-words text-sm leading-5">{entry.title}</strong>
            <span className="block break-words text-xs text-text-muted">{entry.district}</span>
          </span>
        </button>
      ))}
    </>
  );
}
