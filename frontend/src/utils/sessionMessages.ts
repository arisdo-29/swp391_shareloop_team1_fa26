const SILENT_SESSION_EXPIRY_MESSAGE = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';

export function isSilentSessionExpiryMessage(message: string | null | undefined) {
  return message?.trim() === SILENT_SESSION_EXPIRY_MESSAGE;
}
