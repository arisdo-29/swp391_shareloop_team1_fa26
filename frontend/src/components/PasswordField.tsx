import { type FocusEventHandler, useId, useState } from 'react';
import { Icon } from './ui';

export function PasswordField({
  label,
  value,
  onChange,
  error,
  autoComplete,
  onBlur,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  autoComplete?: string;
  onBlur?: FocusEventHandler<HTMLInputElement>;
}) {
  const [visible, setVisible] = useState(false);
  const inputId = useId();
  const errorId = `${inputId}-error`;

  return (
    <div>
      <label htmlFor={inputId} className="label">
        {label}
      </label>
      <div className="relative">
        <input
          id={inputId}
          required
          type={visible ? 'text' : 'password'}
          value={value}
          aria-invalid={Boolean(error) || undefined}
          aria-describedby={error ? errorId : undefined}
          onChange={(event) => onChange(event.target.value)}
          onBlur={onBlur}
          autoComplete={autoComplete}
          className={`input password-input pr-12 ${error ? 'border-error focus:border-error' : ''}`}
        />
        <button
          type="button"
          aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
          aria-pressed={visible}
          onClick={() => setVisible((current) => !current)}
          className="absolute inset-y-0 right-0 my-auto grid size-11 place-items-center rounded-md text-text-muted transition hover:bg-surface-low hover:text-primary"
        >
          <Icon name={visible ? 'eyeSlash' : 'eye'} className="size-5" />
        </button>
      </div>
      {error ? (
        <span id={errorId} className="mt-1.5 block text-xs text-error">
          {error}
        </span>
      ) : null}
    </div>
  );
}
