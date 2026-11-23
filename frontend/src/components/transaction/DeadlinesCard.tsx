import type { RequestResponse } from '../../services/requestApi';
import { deadlineLines } from '../../utils/deadlines';

interface DeadlinesCardProps {
  request: RequestResponse;
  viewerIsDonor: boolean;
  partnerName: string;
}

/** Khối "Mốc thời gian" ở trang Tiến trình giao dịch. Mọi mốc do backend tính, ở đây chỉ dựng câu dễ hiểu. */
export function DeadlinesCard({ request, viewerIsDonor, partnerName }: DeadlinesCardProps) {
  const lines = deadlineLines(request, viewerIsDonor, partnerName);
  if (lines.length === 0) return null;
  return (
    <section className="card space-y-3 p-5 sm:p-6" aria-labelledby="deadlines-title">
      <h2 id="deadlines-title" className="section-title">
        Mốc thời gian
      </h2>
      <ul className="space-y-2 text-sm leading-6 text-text-secondary">
        {lines.map((line) => (
          <li key={line.key}>{line.text}.</li>
        ))}
      </ul>
    </section>
  );
}
