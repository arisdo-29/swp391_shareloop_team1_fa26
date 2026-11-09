import { useState } from 'react';
import { itemApi, type ItemResponse } from '../../services/itemApi';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import type { ApiError } from '../../services/http';
import { Alert, Button } from '../ui';

interface Props {
  requestId: number;
  currentUserId: number;
  currentItemId: number;
  onUpdated: (request: RequestResponse) => void;
}

export function ChangeOfferedItem({ requestId, currentUserId, currentItemId, onUpdated }: Props) {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<ItemResponse[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const openOptions = async () => {
    setOpen(true);
    setLoading(true);
    setError('');
    try {
      const mine = await itemApi.mine();
      setItems(mine.filter((item) => item.id !== currentItemId && item.status === 'APPROVED' && !item.hidden));
    } catch (caught) {
      setError((caught as ApiError).message || 'Không tải được bài đăng của bạn.');
    } finally {
      setLoading(false);
    }
  };

  const save = async () => {
    if (selectedId === null) return;
    setLoading(true);
    setError('');
    try {
      onUpdated(await requestApi.changeOfferedItem(requestId, currentUserId, selectedId));
      setOpen(false);
      setSelectedId(null);
    } catch (caught) {
      setError((caught as ApiError).message || 'Không đổi được món đề nghị.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-2">
      {!open ? (
        <Button size="sm" variant="outline" onClick={() => void openOptions()}>
          Đổi món đề nghị
        </Button>
      ) : (
        <div className="space-y-2 rounded-md border border-border p-3">
          <p className="text-xs leading-5 text-text-secondary">
            Chọn một bài khác đã được duyệt. Chủ bài sẽ nhận thông báo và cần xem lại món trước khi chốt lịch.
          </p>
          {loading && !items.length ? <p className="text-xs">Đang tải bài đăng...</p> : null}
          {!loading && !items.length ? <p className="text-xs">Bạn chưa có bài đăng phù hợp để đổi.</p> : null}
          {items.map((item) => (
            <label key={item.id} className="flex items-center gap-2 rounded-md border border-border p-2 text-sm">
              <input
                type="radio"
                name={`offered-item-${requestId}`}
                checked={selectedId === item.id}
                onChange={() => setSelectedId(item.id)}
              />
              {item.title}
            </label>
          ))}
          {error ? <Alert tone="error">{error}</Alert> : null}
          <div className="flex gap-2">
            <Button size="sm" disabled={loading || selectedId === null} onClick={() => void save()}>
              Xác nhận đổi món
            </Button>
            <Button size="sm" variant="ghost" disabled={loading} onClick={() => setOpen(false)}>
              Đóng
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
