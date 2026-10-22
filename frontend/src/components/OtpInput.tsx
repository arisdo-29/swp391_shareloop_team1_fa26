import { useRef, type ChangeEvent, type ClipboardEvent, type KeyboardEvent } from 'react';
import { applyOtpBackspace, applyOtpChange, applyOtpPaste } from '../utils/otp';

interface OtpInputProps {
  value: readonly string[];
  onChange: (value: string[]) => void;
  label?: string;
  disabled?: boolean;
  invalid?: boolean;
  autoFocus?: boolean;
}

export function OtpInput({
  value,
  onChange,
  label = 'Mã OTP',
  disabled = false,
  invalid = false,
  autoFocus = false,
}: OtpInputProps) {
  const inputRefs = useRef<Array<HTMLInputElement | null>>([]);

  const focusInput = (index: number) => {
    const input = inputRefs.current[index];
    input?.focus();
    input?.select();
  };

  const commit = (nextValue: string[], focusIndex: number) => {
    onChange(nextValue);
    focusInput(focusIndex);
  };

  const handleChange = (index: number, event: ChangeEvent<HTMLInputElement>) => {
    const result = applyOtpChange(value, index, event.target.value);
    onChange(result.value);
    if (/\d/.test(event.target.value)) {
      focusInput(Math.min(index + 1, value.length - 1));
    }
  };

  const handleKeyDown = (index: number, event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Backspace') {
      event.preventDefault();
      const result = applyOtpBackspace(value, index);
      commit(result.value, result.focusIndex);
      return;
    }
    if (event.key === 'ArrowLeft' && index > 0) {
      event.preventDefault();
      focusInput(index - 1);
    }
    if (event.key === 'ArrowRight' && index < value.length - 1) {
      event.preventDefault();
      focusInput(index + 1);
    }
  };

  const handlePaste = (index: number, event: ClipboardEvent<HTMLInputElement>) => {
    event.preventDefault();
    const result = applyOtpPaste(value, index, event.clipboardData.getData('text'));
    commit(result.value, result.focusIndex);
  };

  return (
    <div>
      {label ? <span className="label">{label}</span> : null}
      <div className="grid grid-cols-6 gap-2" role="group" aria-label={label || 'Mã OTP'}>
        {value.map((digit, index) => (
          <input
            key={index}
            ref={(element) => {
              inputRefs.current[index] = element;
            }}
            aria-label={`Số OTP ${index + 1}`}
            aria-invalid={invalid || undefined}
            type="text"
            inputMode="numeric"
            pattern="[0-9]*"
            autoComplete={index === 0 ? 'one-time-code' : 'off'}
            autoFocus={autoFocus && index === 0}
            maxLength={1}
            value={digit}
            disabled={disabled}
            onChange={(event) => handleChange(index, event)}
            onKeyDown={(event) => handleKeyDown(index, event)}
            onPaste={(event) => handlePaste(index, event)}
            onFocus={(event) => event.currentTarget.select()}
            onClick={(event) => event.currentTarget.select()}
            className={`aspect-square min-w-0 rounded-md border text-center text-lg font-bold outline-none focus:ring-4 ${
              invalid
                ? 'border-error focus:border-error focus:ring-error/10'
                : 'border-border focus:border-primary focus:ring-primary/10'
            } disabled:cursor-not-allowed disabled:bg-surface-container disabled:opacity-60`}
          />
        ))}
      </div>
    </div>
  );
}
