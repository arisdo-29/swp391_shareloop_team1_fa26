import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import { Button } from '../../components/ui';
import { ITEM_IMAGES_MAX, ITEM_IMAGES_MIN } from '../../constants/domain';
import { itemApi, type ListingFeeConfigResponse } from '../../services/itemApi';

const reasons = [
  ['Gần nhà', 'Tìm theo quận. Hẹn gặp ở chỗ công cộng gần bạn, không tốn phí ship.'],
  ['Biết ai đáng tin', 'Mỗi người có hạng, sao uy tín và tỉ lệ đúng mô tả từ các lần giao trước.'],
  ['Bài được duyệt', 'Admin kiểm tra ảnh, hàng cấm và thông tin liên hệ trước khi bài hiện ra.'],
] as const;

const receiverSteps = [
  ['Tìm món', 'Lọc theo quận, danh mục, tình trạng. Hoặc mô tả món cần cho Trợ lý AI.'],
  ['Gửi yêu cầu, miễn phí', 'Bài Cho tặng: viết vài dòng vì sao bạn cần. Bài Trao đổi: chọn món của bạn mang đổi.'],
  ['Chờ chủ bài chọn', 'Được chọn thì cuộc trò chuyện mở ra. Không được chọn thì yêu cầu tự đóng và bạn được báo.'],
  ['Hẹn gặp và xác nhận đã nhận', 'Chốt lịch xong mới thấy số điện thoại của nhau. Nhận đồ rồi đánh giá có đúng mô tả không.'],
] as const;

const giverSteps = [
  ['Đăng bài', `Chụp từ ${ITEM_IMAGES_MIN} đến ${ITEM_IMAGES_MAX} ảnh thật, ghi rõ tình trạng. Bài Cho tặng đăng miễn phí.`],
  ['Chờ duyệt', 'Admin duyệt bài. Bị từ chối thì bạn biết lý do và phí đang giữ được trả lại.'],
  ['Chọn một người', 'Xem uy tín và lời nhắn của từng người gửi yêu cầu, chọn người phù hợp nhất.'],
  ['Giao và xác nhận đã giao', 'Hai bên xác nhận thì giao dịch hoàn tất, cả hai được cộng điểm uy tín.'],
] as const;

const safetyTips = [
  'Hẹn ở chỗ đông người: cổng chợ, quán cà phê, sảnh chung cư.',
  'Kiểm tra món đồ trước khi xác nhận đã nhận.',
  'Không chuyển tiền hay đặt cọc dưới bất kỳ hình thức nào.',
  'Gặp vấn đề thì báo cáo ngay trong cuộc trò chuyện, Admin sẽ xem xét.',
];

const faqs = [
  ['Tôi có phải trả tiền cho người cho đồ không?', 'Không. ShareLoop không có mua bán. Ai đòi tiền hay tiền cọc, hãy báo cáo người dùng đó.'],
  ['Vì sao tôi chưa thấy số điện thoại của người kia?', 'Số điện thoại chỉ hiện khi hai bên đã chốt lịch hẹn, để tránh làm phiền khi giao dịch chưa chắc chắn.'],
  ['Nếu người kia không đến hẹn thì sao?', 'Bạn có thể báo cáo "Không đến theo lịch hẹn" ngay trong cuộc trò chuyện để Admin xem xét.'],
  ['Điểm uy tín và sao uy tín khác nhau thế nào?', 'Điểm uy tín tăng khi bạn hoàn tất giao dịch và quyết định hạng. Sao uy tín bắt đầu ở 5 và giảm khi bạn bị báo cáo đúng.'],
] as const;

/** Trang giới thiệu ShareLoop và hướng dẫn sử dụng. Ai cũng xem được, kể cả khách chưa đăng nhập. */
export function About() {
  const user = useAppSelector(selectCurrentUser);
  const fees = useListingFees(Boolean(user));

  return (
    <div>
      <section className="bg-primary-hover text-white">
        <div className="mx-auto w-full max-w-[1280px] px-4 py-12 sm:px-6 lg:px-8 lg:py-16">
          <p className="text-xs font-bold uppercase tracking-[0.08em] text-primary-fixed">Giới thiệu</p>
          <h1 className="mt-3 max-w-3xl text-3xl font-extrabold leading-tight sm:text-4xl lg:text-5xl">
            ShareLoop giúp món đồ còn tốt đến tay người cần, ngay trong quận của bạn.
          </h1>
          <p className="mt-4 max-w-2xl text-base leading-7 text-primary-soft">
            Không mua bán. Bạn cho tặng món không dùng nữa, hoặc đổi lấy món mình cần, rồi gặp nhau giao nhận trực tiếp.
          </p>
          <nav aria-label="Mục trong trang" className="mt-6 flex flex-wrap gap-2 text-sm font-semibold">
            <AnchorChip href="#cach-dung">Cách dùng</AnchorChip>
            <AnchorChip href="#credit">Credit</AnchorChip>
            <AnchorChip href="#an-toan">An toàn</AnchorChip>
            <AnchorChip href="#hoi-dap">Hỏi đáp</AnchorChip>
          </nav>
        </div>
      </section>

      <div className="page-shell space-y-12">
        <section aria-label="Vì sao chọn ShareLoop" className="grid gap-4 md:grid-cols-3">
          {reasons.map(([title, text]) => (
            <div key={title} className="card p-6">
              <h2 className="text-lg font-bold">{title}</h2>
              <p className="mt-2 text-sm leading-6 text-text-secondary">{text}</p>
            </div>
          ))}
        </section>

        <section id="cach-dung" aria-labelledby="how-title" className="scroll-mt-24">
          <h2 id="how-title" className="page-title">Cách dùng trong 4 bước</h2>
          <div className="mt-5 grid gap-4 lg:grid-cols-2">
            <StepCard title="Khi bạn muốn nhận hoặc đổi đồ" steps={receiverSteps} />
            <StepCard title="Khi bạn muốn cho hoặc đổi đồ" steps={giverSteps} />
          </div>
        </section>

        <section aria-labelledby="compare-title" className="table-wrap">
          <h2 id="compare-title" className="section-title px-5 pt-5 sm:px-6">
            Cho tặng và Trao đổi khác nhau thế nào
          </h2>
          <table className="mt-4 w-full min-w-[560px] border-collapse text-left text-sm">
            <thead className="bg-surface-low text-xs font-bold text-text-secondary">
              <tr>
                <th scope="col" className="px-5 py-3 sm:px-6"><span className="sr-only">Nội dung</span></th>
                <th scope="col" className="px-5 py-3">Cho tặng</th>
                <th scope="col" className="px-5 py-3">Trao đổi</th>
              </tr>
            </thead>
            <tbody>
              <CompareRow label="Người đăng trả">
                <td className="px-5 py-3">Miễn phí</td>
                <td className="px-5 py-3">
                  {fees ? `${fees.postFeeSwap} Credit, giữ khi gửi bài và chỉ trừ khi bài được duyệt` : 'Phí đăng bài, giữ khi gửi bài và chỉ trừ khi bài được duyệt'}
                </td>
              </CompareRow>
              <CompareRow label="Người gửi yêu cầu">
                <td className="px-5 py-3">Lời nhắn vì sao cần</td>
                <td className="px-5 py-3">Một món của mình để đổi</td>
              </CompareRow>
              <CompareRow label="Khi giao dịch hoàn tất">
                <td className="px-5 py-3">Hai bên được cộng điểm uy tín</td>
                <td className="px-5 py-3">Hai bên được cộng điểm uy tín</td>
              </CompareRow>
            </tbody>
          </table>
        </section>

        <div className="grid gap-4 lg:grid-cols-2">
          <section id="credit" aria-labelledby="credit-title" className="card scroll-mt-24 p-6">
            <h2 id="credit-title" className="section-title">Credit dùng vào đâu</h2>
            <p className="mt-3 text-sm leading-6 text-text-secondary">
              Credit nạp bằng chuyển khoản trong Ví Credit. Xin đồ, đổi đồ và trò chuyện luôn miễn phí.
            </p>
            {fees ? (
              <ul className="mt-3 list-disc space-y-1.5 pl-5 text-sm leading-6">
                <li>Đăng bài Trao đổi: {fees.postFeeSwap} Credit</li>
                <li>Gia hạn bài thêm {fees.postDisplayDays} ngày: {fees.renewFee} Credit</li>
                <li>Đẩy bài lên đầu {fees.boostDays} ngày: {fees.boostFee} Credit</li>
                <li>Sửa bài sau lượt sửa miễn phí: {fees.editFee} Credit</li>
              </ul>
            ) : (
              <ul className="mt-3 list-disc space-y-1.5 pl-5 text-sm leading-6">
                <li>Đăng bài Trao đổi</li>
                <li>Gia hạn bài khi sắp hết hạn hiển thị</li>
                <li>Đẩy bài lên đầu danh sách</li>
                <li>Sửa bài sau lượt sửa miễn phí</li>
              </ul>
            )}
            <p className="mt-3 text-xs text-text-muted">
              {fees ? 'Mức phí theo cấu hình hiện tại của hệ thống.' : 'Đăng nhập để xem mức phí hiện tại.'}
            </p>
          </section>

          <section id="an-toan" aria-labelledby="safety-title" className="card scroll-mt-24 p-6">
            <h2 id="safety-title" className="section-title">An toàn khi giao nhận</h2>
            <ul className="mt-3 list-disc space-y-1.5 pl-5 text-sm leading-6">
              {safetyTips.map((tip) => (
                <li key={tip}>{tip}</li>
              ))}
            </ul>
          </section>
        </div>

        <section id="hoi-dap" aria-labelledby="faq-title" className="card scroll-mt-24 p-6">
          <h2 id="faq-title" className="section-title">Câu hỏi thường gặp</h2>
          <div className="mt-2 divide-y divide-border">
            {faqs.map(([question, answer], index) => (
              <details key={question} open={index === 0} className="group py-3">
                <summary className="flex min-h-11 cursor-pointer items-center text-sm font-semibold marker:text-text-muted">
                  {question}
                </summary>
                <p className="pb-1 text-sm leading-6 text-text-secondary">{answer}</p>
              </details>
            ))}
          </div>
        </section>

        <section className="flex flex-wrap items-center justify-between gap-4 rounded-xl bg-primary p-6 text-white sm:p-8">
          <div>
            <h2 className="text-2xl font-bold">Nhà bạn có món nào còn tốt?</h2>
            <p className="mt-1.5 text-primary-soft">Đăng bài Cho tặng đầu tiên, miễn phí.</p>
          </div>
          <Link to={user ? '/post' : '/register'}>
            <Button size="lg" variant="outline" className="!border-white !text-primary">
              {user ? 'Đăng đồ' : 'Tạo tài khoản'}
            </Button>
          </Link>
        </section>
      </div>
    </div>
  );
}

/** Bảng phí chỉ trả về cho người đã đăng nhập; khách thấy mô tả chung không kèm số. */
function useListingFees(signedIn: boolean) {
  const [fees, setFees] = useState<ListingFeeConfigResponse | null>(null);

  useEffect(() => {
    if (!signedIn) {
      setFees(null);
      return undefined;
    }
    let active = true;
    itemApi
      .getListingFees()
      .then((data) => {
        if (active) setFees(data);
      })
      .catch(() => {
        if (active) setFees(null);
      });
    return () => {
      active = false;
    };
  }, [signedIn]);

  return fees;
}

function AnchorChip({ href, children }: { href: string; children: ReactNode }) {
  return (
    <a
      href={href}
      className="inline-flex min-h-11 items-center rounded-full border border-white/35 px-4 text-white transition hover:bg-white/10"
    >
      {children}
    </a>
  );
}

function StepCard({ title, steps }: { title: string; steps: ReadonlyArray<readonly [string, string]> }) {
  return (
    <div className="card p-6">
      <h3 className="text-lg font-bold">{title}</h3>
      <ol className="mt-4 space-y-4">
        {steps.map(([name, text], index) => (
          <li key={name} className="grid grid-cols-[32px_1fr] gap-3">
            <span className="grid size-8 place-items-center rounded-full bg-primary-soft text-sm font-bold text-primary">
              {index + 1}
            </span>
            <div>
              <p className="text-sm font-bold">{name}</p>
              <p className="mt-1 text-sm leading-6 text-text-secondary">{text}</p>
            </div>
          </li>
        ))}
      </ol>
    </div>
  );
}

function CompareRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <tr className="border-t border-border/70">
      <th scope="row" className="px-5 py-3 font-semibold sm:px-6">
        {label}
      </th>
      {children}
    </tr>
  );
}
