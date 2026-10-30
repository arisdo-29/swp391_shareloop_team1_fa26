import { Link } from 'react-router-dom';
import { Button, PageHeader } from '../../components/ui';

const safetySections = [
  ['Trước khi gặp', [
    'Trao đổi trong cuộc trò chuyện của ShareLoop. Trước khi chốt lịch, tin chứa số điện thoại, email, link, mạng xã hội bị chặn. Sau khi chốt, họ tên, email, số điện thoại của nhau hiện trong giao dịch, chỉ trong app.',
    'Gặp trực tiếp thì chọn nơi công cộng, đông người. Gửi qua đơn vị vận chuyển thì hai bên tự gửi và đều bấm cam kết tự chịu trách nhiệm.',
    'Không chuyển tiền, đặt cọc hoặc cung cấp thông tin tài chính.',
  ]],
  ['Khi giao nhận', [
    'Kiểm tra món đồ và đối chiếu với hình ảnh, mô tả trong bài đăng trước khi bấm "Đã nhận". ShareLoop không bồi thường tiền hàng hay phí gửi.',
    'Không nhận món có dấu hiệu nguy hiểm, bị cấm hoặc khác đáng kể so với mô tả.',
    'Chỉ xác nhận đã giao hoặc đã nhận sau khi việc giao nhận thực sự hoàn tất.',
  ]],
  ['Khi có vấn đề', [
    'Giữ lại nội dung trao đổi và bằng chứng liên quan.',
    'Dùng chức năng Báo cáo trên bài đăng hoặc trong giao dịch để gửi thông tin cho quản trị viên.',
    'Báo "không đúng mô tả", "chưa nhận được đồ" hoặc "chưa giao được": giao dịch sang tranh chấp, bên kia có 48 giờ giải trình và Admin kết luận. Lúc này bạn không tự huỷ được.',
    'Khiếu nại giao dịch trong 24 giờ sau khi hoàn tất.',
    'Dừng cuộc hẹn nếu bạn cảm thấy không an toàn.',
  ]],
] as const;

export function Safety() {
  return (
    <div className="page-shell">
      <PageHeader
        title="An toàn khi giao nhận"
        description="Một vài nguyên tắc đơn giản giúp việc cho tặng và trao đổi diễn ra an toàn, minh bạch."
      />

      <div className="grid gap-4 lg:grid-cols-3">
        {safetySections.map(([title, tips]) => (
          <section key={title} className="card p-6">
            <h2 className="text-lg font-bold">{title}</h2>
            <ul className="mt-3 list-disc space-y-2 pl-5 text-sm leading-6 text-text-secondary">
              {tips.map((tip) => <li key={tip}>{tip}</li>)}
            </ul>
          </section>
        ))}
      </div>

      <section className="mt-8 rounded-lg border border-warning/30 bg-warning-soft p-6">
        <h2 className="section-title">Lưu ý quan trọng</h2>
        <p className="mt-2 text-sm leading-6 text-text-secondary">
          ShareLoop không phải nền tảng mua bán. Hãy báo cáo nếu có người yêu cầu thanh toán hoặc đặt cọc.
        </p>
      </section>

      <div className="mt-8 flex flex-wrap gap-2">
        <Link to="/guide"><Button variant="outline">Xem hướng dẫn sử dụng</Button></Link>
        <a href="mailto:support@shareloop.vn"><Button>Liên hệ hỗ trợ</Button></a>
      </div>
    </div>
  );
}
