export function productDetailBackLink(search: string) {
  const params = new URLSearchParams(search);
  const from = params.get('from');
  if (from === 'messages') {
    const requestId = params.get('requestId');
    const selectRequest = params.get('selectRequest');
    const requestTarget = requestId && /^[1-9]\d*$/.test(requestId)
      ? `/requests/${requestId}${selectRequest === requestId ? `?selectRequest=${requestId}` : ''}`
      : '/messages';
    return { target: requestTarget, label: 'Quay lại tin nhắn' };
  }
  if (from === 'ai') return { target: '/ai', label: 'Quay lại gợi ý AI' };
  return { target: '/browse', label: 'Quay lại tìm đồ' };
}

export function productDetailFromRequestPath(
  itemId: number,
  requestId: number,
  reopenSelection = false,
) {
  return `/items/${itemId}?from=messages&requestId=${requestId}${reopenSelection ? `&selectRequest=${requestId}` : ''}`;
}
