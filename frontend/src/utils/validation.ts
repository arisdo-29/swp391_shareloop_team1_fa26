export const passwordRules = {
  required: 'Vui lòng nhập mật khẩu.',
  length: 'Mật khẩu phải có từ 8–64 ký tự.',
  different: 'Mật khẩu mới không được trùng với mật khẩu hiện tại.',
  mismatch: 'Mật khẩu xác nhận không khớp.',
};

export function validatePassword(password: string) {
  const errors: string[] = [];
  if (!password.trim()) errors.push(passwordRules.required);
  else if (password.length < 8 || password.length > 64) errors.push(passwordRules.length);
  return errors;
}

export function validEmail(email: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
}

export function validVietnamPhone(phone: string) {
  return /^(?:\+84|0)[35789]\d{8}$/.test(phone.replace(/[\s.-]/g, ''));
}

export function normalizeVietnamPhone(phone: string) {
  return phone.replace(/[\s.-]/g, '');
}
