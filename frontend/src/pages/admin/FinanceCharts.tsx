import { Link } from 'react-router-dom';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Pie,
  PieChart,
  Rectangle,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type BarShapeProps,
} from 'recharts';
import { formatVnd } from '../../utils/formatting';
import type { DailyCash } from '../../services/adminFinanceApi';

const CASH_COLOR = 'var(--color-info)';
const PIE_COLORS = [
  'var(--color-primary)',
  'var(--color-info)',
  'var(--color-warning)',
  'var(--color-error)',
  'var(--color-success)',
  'var(--color-text-muted)',
  'var(--color-text-secondary)',
];
const AXIS_INK = 'var(--color-text-secondary)';
const GRID_LINE = 'var(--color-border)';
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

/** Kích thước lát cắt dùng độ lớn của số Credit. Danh sách dưới biểu đồ hiển thị cả giá trị và tỷ lệ. */
export function CreditByTypeChart({ title, rows }: { title: string; rows: ChartRow[] }) {
  const slices = rows
    .filter((row) => row.value !== 0)
    .map((row, index) => ({
      ...row,
      value: Math.abs(row.value),
      fill: PIE_COLORS[index % PIE_COLORS.length],
    }));
  const total = slices.reduce((sum, slice) => sum + slice.value, 0);
  if (!total) return <ChartEmpty />;
  return (
    <figure aria-label={title} className="m-0">
      <ResponsiveContainer width="100%" height={220}>
        <PieChart>
          <Pie
            data={slices}
            dataKey="value"
            nameKey="label"
            outerRadius="90%"
            stroke="var(--color-surface)"
            isAnimationActive={false}
          />
          <Tooltip content={<ChartTooltip unit="Credit" />} />
        </PieChart>
      </ResponsiveContainer>
      <ul className="mx-auto max-w-lg divide-y divide-border text-sm">
        {slices.map((slice) => (
          <li
            key={slice.label}
            className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 py-2"
          >
            <span className="inline-flex items-center gap-2 text-text-primary">
              <span
                aria-hidden
                className="size-3 shrink-0 rounded-full"
                style={{ backgroundColor: slice.fill }}
              />
              {slice.label}
            </span>
            <span className="tabular-nums text-text-secondary">
              {slice.value.toLocaleString('vi-VN')} Credit ·{' '}
              {(slice.value / total).toLocaleString('vi-VN', {
                style: 'percent',
                maximumFractionDigits: 1,
              })}
            </span>
          </li>
        ))}
      </ul>
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

function dailyOrdersPath(date: string) {
  const query = new URLSearchParams({ from: date, to: date, paidDate: 'true', status: 'SUCCESS' });
  return `/admin/finance/topup-orders?${query}`;
}

/** Tiền thu theo từng ngày (giờ Việt Nam). Ngày không có đơn thanh toán không xuất hiện. */
export function CashByDayChart({ days }: { days: DailyCash[] }) {
  if (!days.length) return <ChartEmpty />;
  const chartData = days.map((day) => ({
    date: day.date,
    axisLabel: shortDate(day.date),
    label: fullDate(day.date),
    value: day.amountVnd,
    detail: `${day.orderCount} đơn nạp`,
  }));
  const renderBar = ({ index, x, y, width, height }: BarShapeProps) => {
    const day = chartData[index];
    if (!day) return null;
    return (
      <Link
        to={dailyOrdersPath(day.date)}
        className="cursor-pointer outline-none hover:opacity-80 focus-visible:opacity-80"
        aria-label={`Xem giao dịch nạp tiền ngày ${day.label}`}
      >
        <Rectangle
          x={x}
          y={y}
          width={width}
          height={height}
          fill={CASH_COLOR}
          radius={[4, 4, 0, 0]}
        />
      </Link>
    );
  };
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
          <Tooltip cursor={{ fill: 'var(--color-surface-low)' }} content={<VndTooltip />} />
          <Bar
            dataKey="value"
            fill={CASH_COLOR}
            shape={renderBar}
            maxBarSize={28}
            isAnimationActive={false}
          />
        </BarChart>
      </ResponsiveContainer>
      <details className="mt-3 text-sm">
        <summary className="cursor-pointer font-semibold text-link">Xem dạng bảng</summary>
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
                  <td>
                    <Link
                      to={dailyOrdersPath(day.date)}
                      className="font-semibold text-link hover:underline"
                    >
                      {fullDate(day.date)}
                    </Link>
                  </td>
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
