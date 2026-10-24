import { describe, expect, it } from 'vitest';
import { moderationBlockedMessage, reputationBlockedMessage } from './apiErrorMessages';

describe('reputationBlockedMessage', () => {
  it('trả câu dễ hiểu cho lỗi đăng và sửa bài khi 0 sao', () => {
    expect(reputationBlockedMessage('ITEM_REPUTATION_BLOCKED')).toBe(
      'Tài khoản đang có 0 sao uy tín nên tạm thời không đăng hoặc sửa bài được.',
    );
  });

  it('trả câu dễ hiểu cho lỗi gửi yêu cầu khi 0 sao', () => {
    expect(reputationBlockedMessage('REQUEST_REPUTATION_BLOCKED')).toContain('không gửi yêu cầu được');
  });

  it('trả null với lỗi khác', () => {
    expect(reputationBlockedMessage('WALLET_INSUFFICIENT_BALANCE')).toBeNull();
    expect(reputationBlockedMessage(undefined)).toBeNull();
  });
});

describe('moderationBlockedMessage', () => {
  it('nêu từ khoá bị chặn và không hiện mã thô', () => {
    const message = moderationBlockedMessage({
      details: { reasonCode: 'LEXICON_BLOCK', matchedTerms: ['sổ đỏ', 'thẻ cào'] },
    });
    expect(message).toContain('Từ cần chỉnh: sổ đỏ, thẻ cào.');
    expect(message).not.toContain('LEXICON_BLOCK');
    expect(message).not.toContain(';');
  });

  it('báo riêng khi bài có thông tin liên hệ và không liệt kê loại kỹ thuật', () => {
    const message = moderationBlockedMessage({
      details: { reasonCode: 'CONTACT_INFO', matchedTerms: ['PHONE'] },
    });
    expect(message).toContain('thông tin liên hệ');
    expect(message).not.toContain('PHONE');
  });

  it('báo riêng khi danh mục bị hạn chế', () => {
    const message = moderationBlockedMessage({ details: { reasonCode: 'RESTRICTED_CATEGORY' } });
    expect(message).toContain('chọn danh mục khác');
  });

  it('dùng câu chung khi không có reasonCode', () => {
    expect(moderationBlockedMessage({ details: undefined })).toContain('quy định của ShareLoop');
  });
});
