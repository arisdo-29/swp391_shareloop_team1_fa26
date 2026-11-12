import '../../styles/marketplace.css';
import { useEffect, useState } from 'react';
import { Alert, Button, EmptyState, LoadingState, ProductCard, StatusBadge } from '../ui';
import { Pagination } from '../Pagination';
import { catalogApi } from '../../services/catalogApi';
import { toAreaNameMap, toCategoryNameMap, toFeItem } from '../../services/mappers';
import { userApi, type UserItemTab } from '../../services/userApi';
import type { Item } from '../../types/domain';

const PAGE_SIZE = 8;

const sectionText: Record<UserItemTab, { title: string; emptyTitle: string; emptyText: string }> = {
  ACTIVE: {
    title: 'Đang hoạt động',
    emptyTitle: 'Chưa có món đang hoạt động',
    emptyText: 'Người này hiện không có bài đăng nào đang hiển thị.',
  },
  DONE: {
    title: 'Đã giao dịch thành công',
    emptyTitle: 'Chưa có món đã giao dịch',
    emptyText: 'Các món đã trao đổi xong sẽ hiện ở đây.',
  },
};

interface ItemPage {
  items: Item[];
  totalElements: number;
}

/** Một mục món đồ ở hồ sơ công khai (tab ACTIVE hoặc DONE), có phân trang riêng. */
export function ProfileItemsSection({ userId, tab }: { userId: number; tab: UserItemTab }) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<ItemPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const text = sectionText[tab];
  const titleId = `profile-items-${tab.toLowerCase()}`;

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    // Tên danh mục và khu vực của thẻ lấy từ catalogApi (có bộ nhớ đệm), giống trang Tìm đồ.
    Promise.all([userApi.getUserItems(userId, tab, page, PAGE_SIZE), catalogApi.categories(), catalogApi.areas()])
      .then(([response, categories, areas]) => {
        if (!active) return;
        const categoryNames = toCategoryNameMap(categories);
        const areaNames = toAreaNameMap(areas);
        setData({
          items: response.items.map((item) => toFeItem(item, categoryNames, areaNames)),
          totalElements: response.totalElements,
        });
      })
      .catch(() => {
        if (active) setError('Không tải được danh sách món đồ.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [page, reloadKey, tab, userId]);

  return (
    <section aria-labelledby={titleId} className="profile-listing-section space-y-4">
      <div>
        <h2 id={titleId} className="section-title">
          {text.title}
          {data ? (
            <span className="ml-2 text-sm font-semibold text-text-secondary">
              ({data.totalElements} món)
            </span>
          ) : null}
        </h2>
        {tab === 'DONE' ? (
          <p className="mt-1 text-sm text-text-secondary">
            Các món này đã trao đổi xong nên đang tạm ẩn khỏi trang tìm đồ. Bạn vẫn xem được chi
            tiết bài đăng.
          </p>
        ) : null}
      </div>
      {loading && !data ? (
        <LoadingState />
      ) : error ? (
        <div className="space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
            Thử tải lại
          </Button>
        </div>
      ) : data && data.items.length ? (
        <>
          <div className="profile-listing-grid">
            {data.items.map((item) =>
              tab === 'DONE' ? <DoneItemCard key={item.id} item={item} /> : (
                <ProductCard variant="marketplace" key={item.id} item={item} />
              ),
            )}
          </div>
          {data.totalElements > PAGE_SIZE ? (
            <Pagination
              page={page}
              size={PAGE_SIZE}
              totalElements={data.totalElements}
              disabled={loading}
              onPageChange={setPage}
            />
          ) : null}
        </>
      ) : (
        <EmptyState title={text.emptyTitle} text={text.emptyText} />
      )}
    </section>
  );
}

// Thẻ món đã giao dịch: hơi mờ, rê chuột hoặc focus thì sáng lại. Nhãn "Đã giao dịch" và ngày hoàn tất ở chân thẻ.
function DoneItemCard({ item }: { item: Item }) {
  return (
    <div className="opacity-70 transition hover:opacity-100 focus-within:opacity-100">
      <ProductCard
        variant="marketplace"
        item={item}
        imageLabel="Tạm ẩn"
        footer={
          <span className="flex min-w-0 flex-wrap items-center gap-2">
            <StatusBadge status="TRADED" label="Đã giao dịch" />
            {item.completedAt ? (
              <span>{new Date(item.completedAt).toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</span>
            ) : null}
          </span>
        }
      />
    </div>
  );
}
