import { Link } from 'react-router-dom';
import { Button, PageHeader } from '../../components/ui';

const guideSteps = [
  ['Tìm món phù hợp', 'Dùng từ khóa, danh mục và bộ lọc quận để tìm món đồ thuận tiện cho việc giao nhận.'],
  ['Gửi yêu cầu', 'Với bài Cho tặng, gửi yêu cầu nhận. Với bài Trao đổi, chọn món của bạn và gửi đề nghị.'],
  ['Trao đổi với chủ bài', 'Khi được chủ bài chọn, cuộc trò chuyện sẽ mở để hai bên thống nhất thời gian và địa điểm.'],
  ['Giao nhận và xác nhận', 'Kiểm tra món đồ khi gặp mặt, sau đó hai bên xác nhận để hoàn tất giao dịch.'],
] as const;

export function Guide() {
  return (
    <div className="page-shell">
      <PageHeader
        title="Hướng dẫn sử dụng"
        description="Các bước cơ bản để tìm, cho tặng hoặc trao đổi món đồ trên ShareLoop."
      />

      <ol className="grid gap-4 md:grid-cols-2">
        {guideSteps.map(([title, description], index) => (
          <li key={title} className="card p-6">
            <span className="grid size-9 place-items-center rounded-full bg-primary-soft text-sm font-bold text-primary">
              {index + 1}
            </span>
            <h2 className="mt-4 text-lg font-bold">{title}</h2>
            <p className="mt-2 text-sm leading-6 text-text-secondary">{description}</p>
          </li>
        ))}
      </ol>

      <section className="mt-8 card p-6">
        <h2 className="section-title">Khi đăng món đồ</h2>
        <ul className="mt-3 list-disc space-y-2 pl-5 text-sm leading-6 text-text-secondary">
          <li>Chọn đúng hình thức Cho tặng hoặc Trao đổi.</li>
          <li>Đăng ảnh thật, mô tả rõ tình trạng và các lỗi nếu có.</li>
          <li>Chọn đúng quận để người nhận dễ sắp xếp giao nhận.</li>
          <li>Theo dõi yêu cầu và chọn một người phù hợp tại trang Hoạt động.</li>
        </ul>
      </section>

      <div className="mt-8 flex flex-wrap gap-2">
        <Link to="/browse"><Button>Tìm đồ</Button></Link>
        <Link to="/safety"><Button variant="outline">Xem hướng dẫn an toàn</Button></Link>
      </div>
    </div>
  );
}
