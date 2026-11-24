const currency = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' });

export function formatReferencePrice(price: number | null | undefined): string {
  return price == null ? 'Chưa có giá tham khảo' : 'Giá tham khảo ' + currency.format(price);
}

export function parseReferencePrice(input: string): number | undefined {
  const price = Number(input);
  return /^\d+$/.test(input) && Number.isSafeInteger(price) && price > 0 ? price : undefined;
}
