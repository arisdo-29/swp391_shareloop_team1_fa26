const currency = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' });

export function formatReferencePrice(price: number | null | undefined): string {
  return price == null ? 'Chưa có giá tham khảo' : 'Giá tham khảo ' + currency.format(price);
}

export function parseReferencePrice(input: string): number | undefined {
  const price = Number(input);
  return /^\d+$/.test(input) && Number.isSafeInteger(price) && price > 0 ? price : undefined;
}

export function rankOfferedItems<T extends { id: string; referencePrice?: number }>(
  items: T[],
  targetPrice: number | undefined,
  percent: number,
) {
  const similar: Array<{ item: T; difference: number }> = [];
  const others: T[] = [];
  if (targetPrice === undefined) return { items, similarIds: new Set<string>() };

  const threshold = BigInt(targetPrice) * BigInt(percent);
  for (const item of items) {
    if (item.referencePrice !== undefined) {
      const difference = Math.abs(item.referencePrice - targetPrice);
      if (BigInt(difference) * 100n <= threshold) {
        similar.push({ item, difference });
        continue;
      }
    }
    others.push(item);
  }

  similar.sort((left, right) => left.difference - right.difference);
  const recommended = similar.map(({ item }) => item);
  return { items: [...recommended, ...others], similarIds: new Set(recommended.map(({ id }) => id)) };
}
