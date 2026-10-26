import { useEffect, useState } from 'react';
import { Button, SearchField } from '../../components/ui';
import { adminUserApi, type AdminAccountRole, type AdminUserSummary } from '../../services/adminUserApi';
import { useDebouncedValue } from '../../utils/useDebouncedValue';

const SEARCH_RESULT_LIMIT = 5;

/** Tìm người dùng theo tên, email hoặc số điện thoại để admin chọn, thay cho việc nhập ID. */
export function AdminUserPicker({
  selected,
  disabled,
  role,
  searchLabel = 'Tìm người dùng',
  onSelect,
}: {
  selected: AdminUserSummary | null;
  disabled: boolean;
  // Bỏ trống để tìm cả quản trị viên lẫn người dùng.
  role?: AdminAccountRole;
  searchLabel?: string;
  onSelect: (user: AdminUserSummary | null) => void;
}) {
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebouncedValue(keyword.trim());
  const [results, setResults] = useState<AdminUserSummary[]>([]);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState('');

  useEffect(() => {
    if (!debouncedKeyword) {
      setResults([]);
      return;
    }
    let active = true;
    setSearching(true);
    setSearchError('');
    adminUserApi
      .list({ keyword: debouncedKeyword, role, page: 0, size: SEARCH_RESULT_LIMIT })
      .then((response) => {
        if (active) setResults(response.items);
      })
      .catch(() => {
        if (!active) return;
        setResults([]);
        setSearchError('Không tìm được người dùng. Vui lòng thử lại.');
      })
      .finally(() => {
        if (active) setSearching(false);
      });
    return () => {
      active = false;
    };
  }, [debouncedKeyword, role]);

  if (selected) {
    return (
      <div className="flex items-center justify-between gap-3 rounded-md border border-border bg-surface-low px-4 py-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-text-primary">{selected.fullName}</p>
          <p className="truncate text-xs text-text-muted">{selected.email}</p>
        </div>
        <Button variant="outline" disabled={disabled} onClick={() => onSelect(null)}>
          Đổi người
        </Button>
      </div>
    );
  }

  return (
    <div>
      <SearchField
        aria-label={searchLabel}
        placeholder="Nhập tên, email hoặc số điện thoại..."
        value={keyword}
        disabled={disabled}
        onChange={(event) => setKeyword(event.target.value)}
      />
      {searchError ? <p className="mt-2 text-xs text-error">{searchError}</p> : null}
      {searching ? <p className="mt-2 text-xs text-text-muted">Đang tìm...</p> : null}
      {!searching && debouncedKeyword && !searchError && results.length === 0 ? (
        <p className="mt-2 text-xs text-text-muted">Không tìm thấy người dùng phù hợp.</p>
      ) : null}
      {results.length ? (
        <ul className="mt-2 divide-y divide-border overflow-hidden rounded-md border border-border bg-white">
          {results.map((user) => (
            <li key={user.id}>
              <button
                type="button"
                className="block w-full px-4 py-2.5 text-left transition hover:bg-primary-faint"
                onClick={() => onSelect(user)}
              >
                <span className="block text-sm font-semibold text-text-primary">{user.fullName}</span>
                <span className="block text-xs text-text-muted">{user.email}</span>
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
