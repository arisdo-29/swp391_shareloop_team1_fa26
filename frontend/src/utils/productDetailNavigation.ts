export function productDetailBackLink(search: string) {
  const from = new URLSearchParams(search).get('from');
  if (from === 'messages') return { target: '/messages', label: 'Quay lại tin nhắn' };
  if (from === 'ai') return { target: '/ai', label: 'Quay lại gợi ý AI' };
  return { target: '/browse', label: 'Quay lại tìm đồ' };
}
