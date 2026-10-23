// @vitest-environment jsdom

import { StrictMode, useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createOtpValue } from '../utils/otp';
import { OtpInput } from './OtpInput';

afterEach(cleanup);

function OtpHarness({ onSubmit = () => undefined }: { onSubmit?: () => void }) {
  const [otp, setOtp] = useState(() => createOtpValue());

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
    >
      <OtpInput value={otp} onChange={setOtp} />
      <button type="submit">Xác nhận</button>
    </form>
  );
}

function renderOtp(onSubmit?: () => void) {
  return render(
    <StrictMode>
      <OtpHarness onSubmit={onSubmit} />
    </StrictMode>,
  );
}

function otpInputs() {
  return screen.getAllByRole('textbox') as HTMLInputElement[];
}

describe('OtpInput focus interaction', () => {
  it('nhập 123456 theo đúng từng ô, không bỏ qua và không tự submit', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    renderOtp(onSubmit);
    const inputs = otpInputs();

    await user.click(inputs[0]);
    for (let index = 0; index < 6; index += 1) {
      await user.keyboard(String(index + 1));
      expect(inputs[index].value).toBe(String(index + 1));
      expect(document.activeElement).toBe(inputs[Math.min(index + 1, 5)]);
    }

    expect(inputs.map((input) => input.value)).toEqual(['1', '2', '3', '4', '5', '6']);
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('một input event có giá trị ghép vẫn chỉ thay một ô và đi đúng một bước', async () => {
    const user = userEvent.setup();
    renderOtp();
    const inputs = otpInputs();

    await user.click(inputs[0]);
    fireEvent.change(inputs[0], { target: { value: '12' } });
    expect(inputs.map((input) => input.value)).toEqual(['2', '', '', '', '', '']);
    expect(document.activeElement).toBe(inputs[1]);
  });

  it('click đúng ô, thay thế chữ số và re-render không làm đổi focus', async () => {
    const user = userEvent.setup();
    const view = renderOtp();
    const inputs = otpInputs();

    await user.click(inputs[0]);
    await user.keyboard('123');
    await user.click(inputs[1]);
    expect(document.activeElement).toBe(inputs[1]);
    await user.keyboard('9');
    expect(inputs.map((input) => input.value)).toEqual(['1', '9', '3', '', '', '']);
    expect(document.activeElement).toBe(inputs[2]);

    await user.click(inputs[2]);
    expect(document.activeElement).toBe(inputs[2]);
    view.rerender(
      <StrictMode>
        <OtpHarness />
      </StrictMode>,
    );
    expect(document.activeElement).toBe(inputs[2]);
  });

  it('Backspace xóa tại chỗ rồi mới lùi về ô trước', async () => {
    const user = userEvent.setup();
    renderOtp();
    const inputs = otpInputs();

    await user.click(inputs[0]);
    await user.keyboard('12');
    await user.click(inputs[1]);
    await user.keyboard('{Backspace}');
    expect(inputs[1].value).toBe('');
    expect(document.activeElement).toBe(inputs[1]);
    await user.keyboard('{Backspace}');
    expect(document.activeElement).toBe(inputs[0]);
    expect(inputs[0].value).toBe('1');
  });

  it('paste đủ 6 chữ số vào đúng sáu ô và giữ focus ở ô cuối', async () => {
    const user = userEvent.setup();
    renderOtp();
    const inputs = otpInputs();

    await user.click(inputs[0]);
    await user.paste('123456');
    expect(inputs.map((input) => input.value)).toEqual(['1', '2', '3', '4', '5', '6']);
    expect(document.activeElement).toBe(inputs[5]);
  });
});
