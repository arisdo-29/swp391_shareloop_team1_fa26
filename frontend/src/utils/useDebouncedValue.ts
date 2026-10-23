import { useEffect, useState } from 'react';

/** Trả về giá trị sau khi người dùng ngừng gõ một lúc, để ô tìm kiếm không gọi API ở từng phím. */
export function useDebouncedValue<T>(value: T, delayMs = 350): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}
