export function productDetailBackLink(search: string) {
  return new URLSearchParams(search).get('from') === 'messages'
    ? { target: '/messages', label: 'Quay lại tin nhắn' }
    : { target: '/browse', label: 'Quay lại tìm đồ' };
}
