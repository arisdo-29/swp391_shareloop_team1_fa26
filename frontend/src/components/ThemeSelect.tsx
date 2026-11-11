import { useEffect, useState } from 'react';
import { applyTheme, readTheme, THEME_KEY, type ThemePreference } from '../app/theme';

export function ThemeSelect() {
  const [preference, setPreference] = useState(readTheme);

  useEffect(() => {
    applyTheme(preference);
    const media = window.matchMedia?.('(prefers-color-scheme: dark)');
    const onSystemChange = () => applyTheme(preference);
    const onStorage = (event: StorageEvent) => {
      if (event.key === THEME_KEY || event.key === null) setPreference(readTheme());
    };
    media?.addEventListener('change', onSystemChange);
    window.addEventListener('storage', onStorage);
    return () => {
      media?.removeEventListener('change', onSystemChange);
      window.removeEventListener('storage', onStorage);
    };
  }, [preference]);

  return (
    <label className="block shrink-0">
      <span className="sr-only">Giao diện</span>
      <select
        aria-label="Giao diện"
        className="theme-select min-h-11 max-w-24 rounded-full border border-border bg-surface px-2 text-xs font-semibold text-text-primary sm:max-w-none sm:px-3"
        value={preference}
        onChange={(event) => {
          const next = event.target.value as ThemePreference;
          try {
            localStorage.setItem(THEME_KEY, next);
          } catch {
            /* Keep the selection for this session. */
          }
          applyTheme(next);
          setPreference(next);
        }}
      >
        <option value="light">Sáng</option>
        <option value="dark">Tối</option>
        <option value="system">Hệ thống</option>
      </select>
    </label>
  );
}
