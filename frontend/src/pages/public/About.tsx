import { useEffect, useState, type ReactNode } from 'react';
import {
  CaretDown,
  CheckCircle,
  Coins,
  MapPin,
  ShieldCheck,
  SealCheck,
} from '@phosphor-icons/react';
import { Link } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import { ITEM_IMAGES_MAX, ITEM_IMAGES_MIN } from '../../constants/domain';
import { itemApi, type ListingFeeConfigResponse } from '../../services/itemApi';
import './about.css';

/** Các con số là luật cố định (Business Rule v11.2) nên ghi thẳng trong text. Gom một chỗ để dễ rà soát. */
const RULES = {
  giveLimit: 5,
  pendingRequests: 5,
  requestPendingDays: 3,
  chatOpenDays: 10,
  autoConfirmDays: 3,
  complaintHours: 24,
  responseHours: 48,
  reportHideThreshold: 3,
  cleanStreakForStar: 10,
  aiFreePerDay: 5,
  aiFeePerUse: 2,
} as const;

const reasons = [
  [
    'Gần nhà',
    'Tìm theo quận. Hẹn gặp ở chỗ công cộng gần bạn, hoặc gửi qua đơn vị vận chuyển nếu hai bên đồng ý.',
  ],
  ['Biết ai đáng tin', 'Mỗi người có hạng, sao uy tín và tỉ lệ đúng mô tả từ các lần giao trước.'],
  [
    'Bài được duyệt',
    'Hệ thống tự kiểm khi bạn gửi bài, rồi Admin duyệt từng bài theo checklist. Không có AI trong khâu duyệt bài và kiểm tin nhắn.',
  ],
] as const;

const reasonIcons = [MapPin, ShieldCheck, SealCheck] as const;

const receiverSteps = [
  [
    'Tìm món',
    'Khách chưa đăng nhập vẫn xem, tìm và lọc bài được. Muốn làm thêm thì đăng nhập. Bạn cũng có thể mô tả món cần cho Trợ lý AI.',
  ],
  [
    'Gửi yêu cầu, miễn phí',
    `Bài Cho tặng: viết vài dòng vì sao bạn cần, có thể kèm một bài của bạn làm món đổi (không bắt buộc). Bài Trao đổi: chọn món mang đổi hoặc gửi mà không kèm. Bạn chờ tối đa ${RULES.pendingRequests} yêu cầu cùng lúc, mỗi bài chỉ một yêu cầu.`,
  ],
  [
    'Chờ chủ bài chọn',
    `Chủ bài xem sao, hạng, tỉ lệ đúng mô tả của bạn rồi chọn đúng 1 người. Yêu cầu chờ quá ${RULES.requestPendingDays} ngày thì tự huỷ, bạn cũng tự rút được khi còn chờ. Được chọn thì chat mở, không được chọn thì bạn nhận thông báo.`,
  ],
  [
    'Chốt lịch và xác nhận đã nhận',
    `Hai bên cùng đồng ý thì lịch được chốt. Quá ${RULES.chatOpenDays} ngày từ lúc được chọn mà chưa chốt thì giao dịch tự huỷ. Nhận đồ rồi bấm "Đã nhận" và cho biết món có đúng mô tả không.`,
  ],
] as const;

const giverSteps = [
  [
    'Đăng bài',
    `Chụp từ ${ITEM_IMAGES_MIN} đến ${ITEM_IMAGES_MAX} ảnh thật của chính bạn, mỗi ảnh tối đa 5 MB. Bài Trao đổi ghi rõ món mong muốn. Đồ có lỗi thì mô tả lỗi và chụp ảnh lỗi. Bài Cho tặng đăng miễn phí.`,
  ],
  [
    'Chờ duyệt',
    'Admin duyệt bài. Bị từ chối thì bạn thấy lý do, sửa và nộp lại được. Phí đăng đang giữ sẽ được trả lại.',
  ],
  [
    'Chọn một người',
    'Xem sao, hạng, tỉ lệ đúng mô tả và món đem đổi của từng người, rồi chọn đúng 1 người. Những người còn lại tự nhận thông báo.',
  ],
  [
    'Giao và xác nhận đã giao',
    `Bạn bấm "Đã giao", người nhận bấm "Đã nhận". Hai bên xác nhận và đồ đúng mô tả thì giao dịch hoàn tất, cả hai được cộng điểm uy tín. Một bên xác nhận mà bên kia im lặng ${RULES.autoConfirmDays} ngày và không có khiếu nại thì giao dịch tự hoàn tất.`,
  ],
] as const;

const safetyTips = [
  'Hẹn ở chỗ đông người: cổng chợ, quán cà phê, sảnh chung cư.',
  'Nếu gửi qua đơn vị vận chuyển, hai bên tự gửi và đều phải bấm cam kết tự chịu trách nhiệm.',
  'ShareLoop không bồi thường tiền hàng hay phí gửi. Hãy kiểm đồ kỹ trước khi bấm "Đã nhận".',
  'Không chuyển tiền hay đặt cọc dưới bất kỳ hình thức nào.',
  'Trước khi chốt lịch, tin nhắn chứa số điện thoại, email, link, mạng xã hội sẽ bị chặn. Sau khi chốt, họ tên, email, số điện thoại của nhau hiện trong trang giao dịch, chỉ trong app.',
  `Bạn báo cáo được một bài hoặc một người. Bài có ${RULES.reportHideThreshold} báo cáo đang chờ thì tạm ẩn, chủ bài có ${RULES.responseHours} giờ giải trình.`,
  `Khiếu nại giao dịch trong ${RULES.complaintHours} giờ sau khi hoàn tất. Bên bị khiếu nại có ${RULES.responseHours} giờ giải trình, Admin chỉ kết luận khi đã có giải trình hoặc hết hạn giải trình.`,
];

const faqs = [
  [
    'Tôi có phải trả tiền cho người cho đồ không?',
    'Không. ShareLoop không có mua bán và hai người dùng không trả tiền cho nhau qua hệ thống. Ai đòi tiền hay tiền cọc, hãy báo cáo người dùng đó.',
  ],
  [
    'Tôi chưa đăng nhập thì làm được gì?',
    'Bạn xem trang chủ, tìm, lọc bài và xem chi tiết bài. Mọi thao tác khác cần đăng nhập; đăng ký bằng email và xác thực qua mã gửi về email.',
  ],
  [
    'Credit dùng để làm gì?',
    'Credit trả cho đăng bài Trao đổi, đăng vượt hạn mức, chuyển bài Cho tặng sang Trao đổi, sửa bài từ lần thứ hai, gia hạn, đẩy bài và Trợ lý AI khi hết lượt miễn phí. Tìm kiếm thường, gửi yêu cầu, chat, chốt lịch, giao nhận, báo cáo và khiếu nại đều miễn phí.',
  ],
  [
    'Đăng quá hạn mức bài Trao đổi thì sao?',
    'Bạn vẫn đăng được, nhưng mỗi bài vượt hạn mức có phí cao hơn và không giới hạn số bài thêm. Hệ thống báo mức phí trước khi bạn gửi bài.',
  ],
  [
    'Bài Cho tặng của tôi có thành bài Trao đổi được không?',
    'Có, khi bạn chọn một người có kèm món đổi, hoặc đồng ý yêu cầu đưa món vào đổi trong chat trước khi chốt lịch. Bạn trả phí đăng Trao đổi, và nếu không đồng ý chuyển thì bạn không chọn được người đó.',
  ],
  [
    'Trợ lý AI có mất phí không?',
    `Chỉ thành viên đã đăng nhập dùng được: đã từng nạp Credit có ${RULES.aiFreePerDay} lượt miễn phí mỗi ngày, chưa nạp lần nào có 1 lượt dùng thử, hết lượt thì ${RULES.aiFeePerUse} Credit mỗi lượt. AI lỗi hoặc chạy quá lâu thì hệ thống chuyển sang tìm thường, không tính lượt và không trừ Credit.`,
  ],
  [
    'Tôi có huỷ giao dịch được không?',
    'Được. Trước khi hoàn tất, bên nào cũng huỷ được, kèm lý do và không mất phí. Giao dịch đang tranh chấp thì bạn phải chờ Admin.',
  ],
  [
    'Bên kia không bấm xác nhận thì sao?',
    `Một bên đã xác nhận, bên kia im lặng ${RULES.autoConfirmDays} ngày và không có khiếu nại thì giao dịch tự hoàn tất. Quá ${RULES.chatOpenDays} ngày từ lúc được chọn mà chưa chốt lịch thì giao dịch tự huỷ.`,
  ],
  [
    'Đồ nhận được không đúng mô tả thì sao?',
    `Bạn báo "không đúng mô tả" khi bấm "Đã nhận": giao dịch chuyển sang tranh chấp, chủ bài có ${RULES.responseHours} giờ giải trình và Admin kết luận, trong lúc này chưa cộng điểm. ShareLoop không bồi thường tiền hàng hay phí gửi; khiếu nại đúng chỉ dẫn tới trừ sao người vi phạm.`,
  ],
  [
    'Nếu người kia không đến hẹn hoặc tôi chưa nhận được đồ thì sao?',
    `Bạn báo "chưa nhận được đồ" (người giao thì báo "chưa giao được"). Giao dịch sang tranh chấp và bên kia có ${RULES.responseHours} giờ giải trình.`,
  ],
  [
    'Bao lâu thì hết hạn khiếu nại?',
    `Bạn khiếu nại được trong ${RULES.complaintHours} giờ sau khi giao dịch hoàn tất. Bên bị khiếu nại có ${RULES.responseHours} giờ giải trình.`,
  ],
  [
    'Món nào không được đăng?',
    'Vật cần sang tên pháp lý (xe có biển số, giấy tờ, sim chính chủ, tài khoản), vật quy đổi ngay ra tiền (vàng, ngoại tệ, thẻ cào, voucher, tiền mã hoá), hàng giả và hàng nhái.',
  ],
  [
    'Điểm uy tín và sao uy tín khác nhau thế nào?',
    `Điểm tăng khi hoàn tất giao dịch và quyết định hạng: Đồng dưới 50, Bạc từ 50, Vàng từ 150, Kim cương từ 400. Sao bắt đầu ở 5, chỉ bị trừ 1 khi Admin chấp thuận một báo cáo hoặc khiếu nại, và hồi 1 sao sau mỗi ${RULES.cleanStreakForStar} giao dịch hoàn tất liên tiếp không bị trừ.`,
  ],
  [
    'Mỗi giao dịch được cộng bao nhiêu điểm?',
    'Bài Cho tặng: người cho +10, người nhận +2. Bài Trao đổi: mỗi bên +6. Tối đa 30 điểm mỗi ngày, và từ giao dịch thứ 3 giữa cùng hai người trong 90 ngày thì không cộng điểm.',
  ],
  ['Sao về 0 thì sao?', 'Tài khoản bị cấm và không đăng nhập được. Không có sao ân hạn.'],
  [
    'Tài khoản bị khoá thì sao?',
    'Admin có thể khoá tài khoản kèm lý do. Khi đăng nhập, bạn sẽ thấy lý do đó.',
  ],
] as const;

/** Trang giới thiệu ShareLoop và hướng dẫn sử dụng. Ai cũng xem được, kể cả khách chưa đăng nhập. */
export function About() {
  const user = useAppSelector(selectCurrentUser);
  const fees = useListingFees(Boolean(user));

  return (
    <div className="about-page">
      <section className="about-hero text-white">
        <div className="about-container about-hero-inner">
          <p className="about-eyebrow">Giới thiệu</p>
          <h1 className="about-hero-title">
            ShareLoop giúp <span>món đồ còn tốt</span> đến tay người cần, ngay trong quận của bạn.
          </h1>
          <p className="about-hero-description">
            Không mua bán. Bạn cho tặng món không dùng nữa, hoặc đổi lấy món mình cần, rồi giao nhận
            trực tiếp hoặc qua đơn vị vận chuyển.
          </p>
          <nav aria-label="Mục trong trang" className="about-anchor-nav">
            <AnchorChip href="#cach-dung">Cách dùng</AnchorChip>
            <AnchorChip href="#credit">Credit</AnchorChip>
            <AnchorChip href="#an-toan">An toàn</AnchorChip>
            <AnchorChip href="#hoi-dap">Hỏi đáp</AnchorChip>
          </nav>
        </div>
      </section>

      <div className="about-container about-content">
        <section aria-label="Vì sao chọn ShareLoop" className="about-reasons">
          {reasons.map(([title, text], index) => {
            const ReasonIcon = reasonIcons[index];
            return (
              <article key={title} className="about-reason-card">
                <span className="about-icon-box" aria-hidden="true">
                  <ReasonIcon weight="duotone" />
                </span>
                <h2>{title}</h2>
                <p>{text}</p>
              </article>
            );
          })}
        </section>

        <section id="cach-dung" aria-labelledby="how-title" className="about-section scroll-mt-24">
          <SectionHeading
            id="how-title"
            eyebrow="Quy trình"
            title="Cách dùng trong 4 bước"
            description="Hai hành trình rõ ràng cho người nhận và người đăng đồ."
          />
          <div className="about-step-grid">
            <StepCard title="Khi bạn muốn nhận hoặc đổi đồ" steps={receiverSteps} />
            <StepCard title="Khi bạn muốn cho hoặc đổi đồ" steps={giverSteps} />
          </div>
        </section>

        <section aria-labelledby="compare-title" className="about-compare-card">
          <div className="about-compare-heading">
            <p className="about-section-eyebrow">So sánh nhanh</p>
            <h2 id="compare-title">Cho tặng và Trao đổi khác nhau thế nào</h2>
          </div>
          <div className="about-table-scroll">
            <table className="about-compare-table">
              <thead>
                <tr>
                  <th scope="col">
                    <span className="sr-only">Nội dung</span>
                  </th>
                  <th scope="col">Cho tặng</th>
                  <th scope="col">Trao đổi</th>
                </tr>
              </thead>
              <tbody>
                <CompareRow label="Phí đăng">
                  <td>Miễn phí</td>
                  <td>
                    {fees
                      ? `${fees.postFeeSwap} Credit nếu còn trong hạn mức. Giữ khi gửi bài, chỉ trừ khi Admin duyệt lần đầu`
                      : 'Có phí đăng nếu còn trong hạn mức. Giữ khi gửi bài, chỉ trừ khi Admin duyệt lần đầu'}
                  </td>
                </CompareRow>
                <CompareRow label="Hạn mức">
                  <td>
                    Tối đa {RULES.giveLimit} bài đang hiển thị hoặc chờ duyệt, áp dụng cho mọi hạng
                  </td>
                  <td>
                    Đồng 5 bài, Bạc 8 bài, Vàng 15 bài, Kim cương không giới hạn. Vượt hạn mức vẫn
                    đăng được
                  </td>
                </CompareRow>
                <CompareRow label="Món đổi">
                  <td>Người xin được kèm một bài của mình, không bắt buộc</td>
                  <td>Người xin có thể gửi yêu cầu mà không kèm món đổi</td>
                </CompareRow>
                <CompareRow label="Điểm khi hoàn tất">
                  <td>Người cho +10, người nhận +2</td>
                  <td>Mỗi bên +6</td>
                </CompareRow>
                <CompareRow label="Khi chọn người">
                  <td>
                    Chọn người có kèm món đổi thì bài chuyển sang Trao đổi, bạn trả phí đăng Trao
                    đổi
                  </td>
                  <td>
                    Chọn người không kèm món đổi thì bài chuyển sang Cho tặng, phí đã trả không được
                    hoàn
                  </td>
                </CompareRow>
              </tbody>
            </table>
          </div>
          <p className="about-table-note">
            Chủ bài không đồng ý chuyển hình thức thì không chọn được người đó. Điểm uy tín tính
            theo hình thức cuối cùng của bài khi giao dịch hoàn tất.
          </p>
        </section>

        <div className="about-info-grid">
          <CreditSection fees={fees} />

          <section
            id="an-toan"
            aria-labelledby="safety-title"
            className="about-info-card scroll-mt-24"
          >
            <InfoHeading
              icon={<ShieldCheck weight="duotone" />}
              title="An toàn khi giao nhận"
              id="safety-title"
            />
            <SafetyGroup title="Gặp và giao đồ" tips={safetyTips.slice(0, 3)} />
            <SafetyGroup title="Thông tin và thanh toán" tips={safetyTips.slice(3, 5)} />
            <SafetyGroup title="Báo cáo và khiếu nại" tips={safetyTips.slice(5)} />
          </section>
        </div>

        <section id="hoi-dap" aria-labelledby="faq-title" className="about-faq scroll-mt-24">
          <SectionHeading
            id="faq-title"
            eyebrow="Hỗ trợ"
            title="Câu hỏi thường gặp"
            description="Những điều cần biết trước khi bắt đầu cho tặng hoặc trao đổi."
          />
          <div className="about-faq-list">
            {faqs.map(([question, answer], index) => (
              <details key={question} open={index === 0}>
                <summary>
                  <span>{question}</span>
                  <CaretDown className="about-faq-caret" weight="bold" aria-hidden="true" />
                </summary>
                <div className="about-faq-answer">
                  <p>{answer}</p>
                </div>
              </details>
            ))}
          </div>
        </section>

        <section className="about-cta">
          <div>
            <p className="about-cta-eyebrow">Bắt đầu một vòng đời mới</p>
            <h2>Nhà bạn có món nào còn tốt?</h2>
            <p>Đăng bài Cho tặng đầu tiên, miễn phí.</p>
          </div>
          <Link to={user ? '/post' : '/register'} className="about-cta-button">
            {user ? 'Đăng đồ' : 'Tạo tài khoản'}
          </Link>
        </section>
      </div>
    </div>
  );
}

function SectionHeading({
  eyebrow,
  title,
  description,
  id,
}: {
  eyebrow: string;
  title: string;
  description: string;
  id?: string;
}) {
  return (
    <div className="about-section-heading">
      <p className="about-section-eyebrow">{eyebrow}</p>
      <h2 id={id}>{title}</h2>
      <p>{description}</p>
    </div>
  );
}

function InfoHeading({ icon, title, id }: { icon: ReactNode; title: string; id: string }) {
  return (
    <div className="about-info-heading">
      <span className="about-icon-box" aria-hidden="true">
        {icon}
      </span>
      <h2 id={id}>{title}</h2>
    </div>
  );
}

function SafetyGroup({ title, tips }: { title: string; tips: readonly string[] }) {
  return (
    <div className="about-info-group">
      <h3>{title}</h3>
      <ul className="about-safety-list">
        {tips.map((tip) => (
          <li key={tip}>
            <CheckCircle aria-hidden="true" weight="fill" />
            <span>{tip}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Mục Credit: có số phí khi đã đăng nhập, khách chỉ thấy mô tả chung không kèm số. */
function CreditSection({ fees }: { fees: ListingFeeConfigResponse | null }) {
  return (
    <section id="credit" aria-labelledby="credit-title" className="about-info-card scroll-mt-24">
      <InfoHeading
        icon={<Coins weight="duotone" />}
        title="Credit dùng vào đâu"
        id="credit-title"
      />
      <div className="about-info-group">
        <h3>Giá trị và cách nạp</h3>
        <p>
          1 Credit = 1.000đ. Bạn nạp qua VNPay, tối thiểu 10 Credit, số tiền là bội số của 1.000đ.
          Credit không đổi ra tiền, không tặng và không chuyển cho người khác.
        </p>
      </div>
      <div className="about-free-note">
        <CheckCircle aria-hidden="true" weight="fill" />
        <p>
          <strong>Miễn phí hoàn toàn:</strong> tìm kiếm thường, gửi yêu cầu, chat, chốt lịch, giao
          nhận, báo cáo và khiếu nại.
        </p>
      </div>
      <div className="about-info-group">
        <h3>Các hoạt động dùng Credit</h3>
        {fees ? <FeeList fees={fees} /> : <GenericFeeList />}
      </div>
      <p className="about-info-note">
        {fees
          ? 'Mức phí theo cấu hình hiện tại của hệ thống.'
          : 'Đăng nhập để xem mức phí hiện tại.'}
      </p>
      <p className="about-info-note">
        Phí đăng được giữ khi gửi duyệt, trừ thật khi Admin duyệt lần đầu, trả lại khi bị từ chối
        hoặc khi bạn gỡ bài trước lúc duyệt. Bài đã duyệt rồi bị gỡ, hết hạn hay giao dịch không
        thành thì không hoàn. Phí gia hạn và đẩy bài trừ ngay khi bấm, không hoàn.
      </p>
    </section>
  );
}

function FeeList({ fees }: { fees: ListingFeeConfigResponse }) {
  const overQuotaText =
    fees.postFeeSwapOverQuota === undefined
      ? 'Đăng vượt hạn mức: phí cao hơn, hệ thống báo trước khi đăng'
      : `Đăng vượt hạn mức: ${fees.postFeeSwapOverQuota} Credit mỗi bài`;

  return (
    <ul className="about-compact-list">
      <li>Đăng bài Trao đổi trong hạn mức: {fees.postFeeSwap} Credit</li>
      <li>{overQuotaText}</li>
      <li>Chuyển bài Cho tặng sang Trao đổi: {fees.postFeeSwap} Credit</li>
      <li>
        Sửa bài từ lần thứ hai: {fees.editFee} Credit, chỉ trừ khi bản sửa được duyệt. Lần sửa đầu
        sau khi bài được duyệt miễn phí
      </li>
      <li>
        Gia hạn bài thêm {fees.postDisplayDays} ngày: {fees.renewFee} Credit
      </li>
      <li>
        Đẩy bài lên đầu {fees.boostDays} ngày: {fees.boostFee} Credit
      </li>
      <li>
        Đẩy cộng dồn được, nhưng không quá {fees.boostMaxDays} ngày và không quá hạn hiển thị của
        bài
      </li>
      <li>
        Bài hiển thị {fees.postDisplayDays} ngày từ lần duyệt đầu, hạng Vàng trở lên{' '}
        {fees.postDisplayDaysGold} ngày
      </li>
      <li>Trợ lý AI khi hết lượt miễn phí: {RULES.aiFeePerUse} Credit mỗi lượt</li>
    </ul>
  );
}

function GenericFeeList() {
  return (
    <ul className="about-compact-list">
      <li>Đăng bài Trao đổi, kể cả khi vượt hạn mức</li>
      <li>Chuyển bài Cho tặng sang Trao đổi</li>
      <li>Sửa bài từ lần thứ hai</li>
      <li>Gia hạn bài đang hiển thị. Bài đã hết hạn thì không gia hạn được</li>
      <li>Đẩy bài lên đầu kết quả tìm kiếm</li>
      <li>Trợ lý AI khi hết lượt miễn phí: {RULES.aiFeePerUse} Credit mỗi lượt</li>
    </ul>
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
    <a href={href} className="about-anchor-chip">
      {children}
    </a>
  );
}

function StepCard({
  title,
  steps,
}: {
  title: string;
  steps: ReadonlyArray<readonly [string, string]>;
}) {
  return (
    <article className="about-step-card">
      <h3>{title}</h3>
      <ol className="about-timeline">
        {steps.map(([name, text], index) => (
          <li key={name}>
            <span className="about-step-number">{String(index + 1).padStart(2, '0')}</span>
            <div className="about-step-copy">
              <h4>{name}</h4>
              <p>{text}</p>
            </div>
          </li>
        ))}
      </ol>
    </article>
  );
}

function CompareRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <tr>
      <th scope="row">{label}</th>
      {children}
    </tr>
  );
}
