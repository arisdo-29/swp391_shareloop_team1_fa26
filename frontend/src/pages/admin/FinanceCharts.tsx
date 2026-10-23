import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatVnd } from '../../utils/formatting';
import type { DailyCash } from '../../services/adminFinanceApi';

// Màu lấy từ token trong tailwind.config.js: primary, secondary, info. Mỗi biểu đồ chỉ có một chuỗi
// nên không cần bảng màu phân loại, nhãn nằm ngay trên trục nên không phụ thuộc vào màu.
const CHART_COLORS = { credit: '#145C4E', debit: '#B85C35', cash: '#356A8A' } as const;
const AXIS_INK = '#46534F';
const GRID_LINE = '#D7DEDB';
const AXIS_FONT = 12;

export interface ChartRow {
  label: string;
  value: number;
  detail?: string;
}

function ChartTooltip({
  active,
  payload,
  unit,
}: {
  active?: boolean;
  payload?: ReadonlyArray<{ payload?: ChartRow }>;
  unit: string;
}) {
  const row = payload?.[0]?.payload;
  if (!active || !row) return null;
  return (
    <div className="rounded-md bg-white px-3 py-2 text-xs shadow-md ring-1 ring-border">
      <p className="font-semibold text-text-primary">{row.label}</p>
      <p className="mt-0.5 tabular-nums text-text-secondary">
        {row.value.toLocaleString('vi-VN')} {unit}
      </p>
      {row.detail ? <p className="mt-0.5 text-text-muted">{row.detail}</p> : null}
    </div>
  );
}

function ChartEmpty() {
  return (
    <p className="rounded-md bg-surface-low px-4 py-8 text-center text-sm text-text-muted">
      Chưa có dữ liệu trong khoảng đã chọn.
    </p>
  );
}

/** Cột ngang: mỗi loại bút toán một cột, nhãn tiếng Việt nằm bên trái nên dễ đọc hơn cột đứng. */
export function CreditByTypeChart({
  title,
  rows,
  tone,
}: {
  title: string;
  rows: ChartRow[];
  tone: 'credit' | 'debit';
}) {
  if (!rows.length) return <ChartEmpty />;
  return (
    <figure aria-label={title} className="m-0">
      <ResponsiveContainer width="100%" height={Math.max(140, rows.length * 44 + 24)}>
        <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 16, bottom: 4, left: 0 }}>
          <CartesianGrid horizontal={false} stroke={GRID_LINE} strokeOpacity={0.7} />
          <XAxis
            type="number"
            allowDecimals={false}
            tick={{ fill: AXIS_INK, fontSize: AXIS_FONT }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            type="category"
            dataKey="label"
            width={116}
            tick={{ fill: AXIS_INK, fontSize: AXIS_FONT }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip cursor={{ fill: '#F0F3F2' }} content={<ChartTooltip unit="Credit" />} />
          <Bar dataKey="value" fill={CHART_COLORS[tone]} radius={[0, 4, 4, 0]} maxBarSize={20} />
        </BarChart>
      </ResponsiveContainer>
    </figure>
  );
}

/** "2026-10-05" thành "05/10" cho trục và "05/10/2026" cho tooltip. */
function shortDate(date: string) {
  const [, month, day] = date.split('-');
  return `${day}/${month}`;
}

function fullDate(date: string) {
  const [year, month, day] = date.split('-');
  return `${day}/${month}/${year}`;
}

function compactVnd(value: number) {
  if (value >= 1_000_000) return `${+(value / 1_000_000).toFixed(1)}tr`;
  if (value >= 1_000) return `${+(value / 1_000).toFixed(0)}k`;
  return String(value);
}

/** Tiền thu theo từng ngày (giờ Việt Nam). Ngày không có đơn thanh toán không xuất hiện. */
export function CashByDayChart({ days }: { days: DailyCash[] }) {
  if (!days.length) return <ChartEmpty />;
  const rows: ChartRow[] = days.map((day) => ({
    label: fullDate(day.date),
    value: day.amountVnd,
    detail: `${day.orderCount} đơn nạp`,
  }));
  const chartData = days.map((day, index) => ({ ...rows[index], axisLabel: shortDate(day.date) }));
  return (
    <figure aria-label="Tiền thu theo ngày" className="m-0">
      <ResponsiveContainer width="100%" height={260}>
        <BarChart data={chartData} margin={{ top: 8, right: 8, bottom: 4, left: 0 }}>
          <CartesianGrid vertical={false} stroke={GRID_LINE} strokeOpacity={0.7} />
          <XAxis
            dataKey="axisLabel"
            tick={{ fill: AXIS_INK, fontSize: AXIS_FONT }}
            axisLine={{ stroke: GRID_LINE }}
            tickLine={false}
            minTickGap={16}
          />
          <YAxis
            width={52}
            tickFormatter={compactVnd}
            tick={{ fill: AXIS_INK, fontSize: AXIS_FONT }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip cursor={{ fill: '#F0F3F2' }} content={<VndTooltip />} />
          <Bar dataKey="value" fill={CHART_COLORS.cash} radius={[4, 4, 0, 0]} maxBarSize={28} />
        </BarChart>
      </ResponsiveContainer>
      <details className="mt-3 text-sm">
        <summary className="cursor-pointer font-semibold text-primary">Xem dạng bảng</summary>
        <div className="table-wrap mt-2">
          <table className="data-table !min-w-0">
            <thead>
              <tr>
                <th>Ngày</th>
                <th>Tiền thu</th>
                <th>Số đơn nạp</th>
              </tr>
            </thead>
            <tbody>
              {days.map((day) => (
                <tr key={day.date}>
                  <td>{fullDate(day.date)}</td>
                  <td>{formatVnd(day.amountVnd)}</td>
                  <td>{day.orderCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  );
}

function VndTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: ReadonlyArray<{ payload?: ChartRow }>;
}) {
  const row = payload?.[0]?.payload;
  if (!active || !row) return null;
  return (
    <div className="rounded-md bg-white px-3 py-2 text-xs shadow-md ring-1 ring-border">
      <p className="font-semibold text-text-primary">{row.label}</p>
      <p className="mt-0.5 tabular-nums text-text-secondary">{formatVnd(row.value)}</p>
      {row.detail ? <p className="mt-0.5 text-text-muted">{row.detail}</p> : null}
    </div>
  );
}
