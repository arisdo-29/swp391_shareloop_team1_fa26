import { Select } from '../ui';
import {
  itemChoiceConsequences,
  itemChoiceLabels,
  type DisputeItemRow,
  type ItemChoice,
} from '../../utils/disputeItemOutcomes';

interface DisputeItemOutcomesProps {
  rows: DisputeItemRow[];
  choices: Record<number, ItemChoice>;
  onChange: (itemId: number, choice: ItemChoice) => void;
  disabled?: boolean;
}

/** Mỗi món một dòng: tên, ảnh nhỏ, chủ món và nơi món đang nằm, ô chọn ba lựa chọn kèm câu giải thích hậu quả. */
export function DisputeItemOutcomes({ rows, choices, onChange, disabled }: DisputeItemOutcomesProps) {
  return (
    <fieldset className="space-y-3" data-testid="dispute-item-outcomes">
      <legend className="label">Xử lý từng món của giao dịch</legend>
      {rows.map((row) => {
        const choice = choices[row.itemId] ?? 'REOPEN';
        return (
          <div key={row.itemId} className="flex gap-3 rounded-md border border-border bg-surface p-3">
            {row.imageUrl ? (
              <img src={row.imageUrl} alt="" className="size-14 shrink-0 rounded-md object-cover" />
            ) : null}
            <div className="min-w-0 flex-1 space-y-2">
              <div>
                <p className="font-semibold">{row.title}</p>
                <p className="text-xs text-text-secondary">
                  Của {row.ownerName}. Món nằm ở chỗ {row.holderName} nếu đã được trao.
                </p>
              </div>
              <Select
                label={`Cách xử lý món ${row.title}`}
                value={choice}
                disabled={disabled}
                onChange={(event) => onChange(row.itemId, event.target.value as ItemChoice)}
              >
                {(Object.keys(itemChoiceLabels) as ItemChoice[]).map((value) => (
                  <option key={value} value={value}>
                    {itemChoiceLabels[value]}
                  </option>
                ))}
              </Select>
              <p className="text-xs leading-5 text-text-secondary">{itemChoiceConsequences[choice]}</p>
            </div>
          </div>
        );
      })}
    </fieldset>
  );
}
