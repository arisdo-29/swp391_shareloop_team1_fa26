import { describe, expect, it } from 'vitest';
import { offerConversion, requestOfferLabel } from './requestOffer';

describe('requestOfferLabel', () => {
  it('có món đề nghị thì là Trao đổi, kể cả khi type còn là gift', () => {
    expect(requestOfferLabel({ offeredItemId: '12' })).toBe('Trao đổi');
  });

  it('không kèm món thì là Xin nhận', () => {
    expect(requestOfferLabel({ offeredItemId: undefined })).toBe('Xin nhận');
  });
});

describe('offerConversion', () => {
  it('kèm món ở bài Cho thì chuyển sang Trao đổi với phí backend trả', () => {
    expect(
      offerConversion({ offeredItemId: '12', itemOfferType: 'gift', conversionFee: 7 }),
    ).toEqual({ to: 'SWAP', fee: 7 });
  });

  it('không kèm món ở bài Trao đổi thì chuyển sang Cho', () => {
    expect(offerConversion({ offeredItemId: undefined, itemOfferType: 'trade' })).toEqual({
      to: 'GIVE',
    });
  });

  it('cùng loại với bài thì không đổi hình thức', () => {
    expect(offerConversion({ offeredItemId: '12', itemOfferType: 'trade' })).toBeNull();
    expect(offerConversion({ offeredItemId: undefined, itemOfferType: 'gift' })).toBeNull();
  });

  it('chưa biết hình thức bài thì không cảnh báo', () => {
    expect(offerConversion({ offeredItemId: '12', itemOfferType: undefined })).toBeNull();
  });

  it('thiếu conversionFee thì để null, không tự đoán số', () => {
    expect(offerConversion({ offeredItemId: '12', itemOfferType: 'gift' })).toEqual({
      to: 'SWAP',
      fee: null,
    });
  });
});
