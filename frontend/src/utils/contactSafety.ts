const writtenNumbers: Record<string, string> = {
  khong: '0',
  k: '0',
  ko: '0',
  kh: '0',
  mot: '1',
  hai: '2',
  ba: '3',
  bon: '4',
  tu: '4',
  nam: '5',
  sau: '6',
  bay: '7',
  tam: '8',
  chin: '9',
  muoi: '0',
};

export type ContactModerationClassification = {
  classification: 'SAFE' | 'CONTACT_INFO';
  confidence: number;
};

export const CONTACT_MODERATION_THRESHOLD = 0.75;

export function normalizeContactText(input: string) {
  const withoutMarks = input
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[|!]/g, 'i')
    .replace(/[@]/g, ' at ')
    .replace(/[()[\]{},;:_\-.]/g, ' ');
  const withWrittenNumbers = withoutMarks.replace(
    /\b(khong|ko|kh|k|mot|hai|ba|bon|tu|nam|sau|bay|tam|chin|muoi)\b/g,
    (word) => writtenNumbers[word] ?? word,
  );
  return {
    spaced: withWrittenNumbers.replace(/\s+/g, ' ').trim(),
    compact: withWrittenNumbers.replace(/[^a-z0-9]/g, ''),
  };
}

export function contactSafetyLayer1(input: string) {
  const { spaced, compact } = normalizeContactText(input);
  const phone = /(?:^|[^0-9])(?:\+?84|0)?\d(?:[\s./-]*\d){8,10}(?:$|[^0-9])/.test(spaced) ||
    /(?:84|0)\d{8,10}/.test(compact);
  const email = /[a-z0-9._%+-]+\s*(?:@|at)\s*[a-z0-9.-]+\s*(?:\.|dot|\s)\s*[a-z]{2,}/i.test(spaced) ||
    /[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i.test(compact);
  const url = /(?:https?:\s*\/\s*\/|www\s*(?:\.|dot|\s)|[a-z0-9-]+\s*(?:\.|dot|\s)\s*(?:com|vn|net|org)\b)/i.test(spaced) ||
    /(?:https?|www|zalo|facebook|telegram|viber)/i.test(compact);
  return { blocked: phone || email || url, phone, email, url, normalized: spaced };
}

export function hasAmbiguousContactSignal(input: string) {
  const { spaced, compact } = normalizeContactText(input);
  const contactWords = /(lien he|goi|nhan tin|so dien thoai|email|dia chi|ket noi|inbox|ib|zalo|fb|facebook|tim minh)/i;
  const digitLike = (compact.match(/\d/g) ?? []).length;
  const separatedDigits = /(?:\d\D*){3,}/.test(spaced);
  return contactWords.test(spaced) || (digitLike >= 3 && separatedDigits);
}

// Layer 2 fallback classifier. The project currently has no configured AiClient
// module, so this keeps the same SAFE/CONTACT_INFO contract with timeout.
export function contactSafetyLayer2(input: string): ContactModerationClassification {
  const { spaced, compact } = normalizeContactText(input);
  const contactWords = /(lien he|goi|nhan tin|so dien thoai|email|dia chi|ket noi|inbox|ib|zalo|fb|facebook|tim minh)/i;
  const digitLike = (compact.match(/\d/g) ?? []).length;
  const contactHint = contactWords.test(spaced);
  if (contactHint && digitLike >= 2) return { classification: 'CONTACT_INFO', confidence: 0.9 };
  if (contactHint) return { classification: 'CONTACT_INFO', confidence: 0.78 };
  if (digitLike >= 3 && /(?:\d\D*){3,}/.test(spaced)) {
    return { classification: 'CONTACT_INFO', confidence: 0.82 };
  }
  return { classification: 'SAFE', confidence: 0.2 };
}

export function inspectContactMessage(input: string) {
  const layer1 = contactSafetyLayer1(input);
  const layer2 = contactSafetyLayer2(input);
  return {
    blocked:
      layer1.blocked ||
      (layer2.classification === 'CONTACT_INFO' &&
        layer2.confidence >= CONTACT_MODERATION_THRESHOLD),
    obvious: layer1.blocked,
    normalized: layer1.normalized,
  };
}

export async function classifyAmbiguousContact(input: string, timeoutMs = 1200) {
  const fallback = contactSafetyLayer2(input);
  const result = await Promise.race([
    Promise.resolve(fallback),
    new Promise<ContactModerationClassification>((resolve) =>
      window.setTimeout(() => resolve(fallback), timeoutMs),
    ),
  ]);
  return (
    result.classification === 'CONTACT_INFO' &&
    result.confidence >= CONTACT_MODERATION_THRESHOLD
  );
}
