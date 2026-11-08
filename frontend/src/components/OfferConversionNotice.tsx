import { Link } from 'react-router-dom';
import type { OfferConversion } from '../utils/requestOffer';
import { Button } from './ui';

// Cảnh báo trong hộp chọn người khi lựa chọn làm bài đổi hình thức (Cho ↔ Trao đổi).
// Số Credit lấy từ conversionFee backend trả; frontend không tự tính hay trừ tiền.
export function OfferConversionWarning({ conversion }: { conversion: OfferConversion | null }) {
  if (!conversion) return null;
  const message =
    conversion.to === 'SWAP'
      ? `Bài đang là Cho tặng. Chọn người này thì bài chuyển sang Trao đổi và bạn trả ${conversionFeeText(conversion.fee)} phí đăng Trao đổi. Các yêu cầu khác sẽ bị từ chối.`
      : 'Bài đang là Trao đổi. Chọn người này thì bài chuyển sang Cho tặng; phí đăng đã trả không được hoàn.';
  return (
    <p role="note" className="mt-3 rounded-md bg-warning-soft p-3 text-xs leading-5 text-warning">
      {message}
    </p>
  );
}

// Hiện khi backend trả 402 WALLET_INSUFFICIENT_BALANCE lúc chuyển bài sang Trao đổi; hộp xác nhận vẫn mở.
export function ConversionTopUpNotice({ fee }: { fee: number | null }) {
  return (
    <div role="alert" className="mt-3 rounded-md bg-warning-soft p-3 text-xs leading-5 text-warning">
      <p>{`Cần ${conversionFeeText(fee)} để chuyển bài sang Trao đổi.`}</p>
      <Link className="mt-2 inline-block" to="/credit">
        <Button size="sm">Nạp Credit</Button>
      </Link>
    </div>
  );
}

// Backend chưa trả phí thì không đoán số, chỉ nói chung chung.
function conversionFeeText(fee: number | null) {
  return fee === null ? 'Credit' : `${fee} Credit`;
}
