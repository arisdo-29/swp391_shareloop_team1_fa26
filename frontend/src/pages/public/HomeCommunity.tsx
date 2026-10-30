import { ItemImage } from '../../components/ui';
import { Link } from 'react-router-dom';
import type { Item } from '../../types/domain';

const values = [
  ['01', 'CHO ĐI', 'Món đồ bạn không còn dùng'],
  ['02', 'TÌM ĐỒ', 'Tìm món đồ bạn đang cần'],
  ['03', 'GIAO NHẬN & TRAO ĐỔI', 'Gặp trực tiếp hoặc gửi vận chuyển, hoàn tất một vòng đời mới'],
];
const steps = [
  ['Đăng hoặc tìm món', 'Chia sẻ món bạn có, hoặc lọc theo nhu cầu và quận. Khách xem được, muốn gửi yêu cầu thì đăng nhập.'],
  ['Chọn người và chốt lịch', 'Chủ bài chọn một người, hai bên trò chuyện rồi cùng đồng ý lịch gặp trực tiếp hoặc gửi qua đơn vị vận chuyển.'],
  ['Xác nhận hoàn tất', 'Chủ bài bấm "Đã giao", người nhận bấm "Đã nhận" và cho biết đồ có đúng mô tả không.'],
];

export function HomeLifecycle({ items }: { items: Item[] }) {
  return (
      <section className="home-values">
        <div className="home-inner">
          <div className="home-lifecycle">
          <div className="home-lifecycle-copy">
            <p className="home-lifecycle-eyebrow">SHARELOOP • VÒNG ĐỜI MỚI</p>
            <h2>Mỗi món đồ đều có thể có thêm một vòng đời</h2>
            <p className="home-lifecycle-description">
              Thay vì để những món đồ còn tốt bị lãng phí, ShareLoop giúp chúng tiếp tục hữu ích
              với một người khác trong cộng đồng.
            </p>
            <span className="home-lifecycle-divider" aria-hidden="true" />
            <div className="home-lifecycle-steps">
              {values.map(([number, title, description]) => (
                <article key={number} className="home-value">
                  <span>{number}</span>
                  <h3>{title}</h3>
                  <p>{description}</p>
                </article>
              ))}
            </div>
            <Link to="/browse" className="home-lifecycle-cta">
              Khám phá món đồ
            </Link>
          </div>
          <div className="home-lifecycle-visual">
            <div className="home-lifecycle-collage" aria-label="Một số món đồ trong cộng đồng">
              {items
                .filter((item) => item.images.length > 0)
                .slice(0, 3)
                .map((item, index) => (
                  <ItemImage
                    key={item.id}
                    src={item.images[0]}
                    alt={item.title}
                    loading="lazy"
                    className={`home-lifecycle-collage-image home-lifecycle-collage-image-${index + 1}`}
                  />
                ))}
            </div>
          </div>
          </div>
        </div>
      </section>
  );
}

export function HomeProcess() {
  return (
      <section className="home-process home-inner">
        <div className="home-process-intro">
          <h2>Một vòng chia sẻ thật đơn giản</h2>
          <p>
            Mọi kết nối bắt đầu từ một món đồ còn giá trị.{' '}
            <Link to="/about" className="font-semibold text-primary">
              Xem hướng dẫn chi tiết
            </Link>
          </p>
        </div>
        <ol>
          {steps.map(([title, description], index) => (
            <li key={title}>
              <div className="home-step-top">
                <span>0{index + 1}</span>
              </div>
              <h3>{title}</h3>
              <p>{description}</p>
            </li>
          ))}
        </ol>
      </section>
  );
}

export function HomeCommunity({ item }: { item?: Item }) {
  return (
    <>
      <HomeLifecycle items={item ? [item] : []} />
      <HomeProcess />
    </>
  );
}
