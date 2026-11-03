import { useEffect, useRef, useState } from 'react';
import { aiApi, type AiDescriptionResponse } from '../../services/aiApi';
import type { ApiError } from '../../services/http';
import { Button } from '../ui';

const FALLBACK_MESSAGE = 'Chưa gợi ý được, bạn tự viết mô tả nhé.';

export function DescribeItemSuggestion({
  title,
  disabled = false,
  onUse,
}: {
  title: string;
  disabled?: boolean;
  onUse: (description: string) => void;
}) {
  const [suggestion, setSuggestion] = useState<AiDescriptionResponse | null>(null);
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const generation = useRef(0);

  useEffect(() => {
    generation.current += 1;
    setSuggestion(null);
    setMessage('');
    setLoading(false);
    return () => { generation.current += 1; };
  }, [title]);

  const generate = async () => {
    if (loading || !title.trim() || title.trim().length > 150) return;
    const currentGeneration = ++generation.current;
    setLoading(true);
    setSuggestion(null);
    setMessage('');
    try {
      const response = await aiApi.describeItem(title.trim());
      if (currentGeneration !== generation.current) return;
      if (response.description) setSuggestion(response);
      else setMessage(response.message || FALLBACK_MESSAGE);
    } catch (error) {
      if (currentGeneration !== generation.current) return;
      const apiError = error as ApiError;
      setMessage(apiError.code === 'AI_DESCRIPTION_LIMIT_REACHED'
        ? 'Bạn đã dùng hết lượt gợi ý mô tả hôm nay.'
        : FALLBACK_MESSAGE);
    } finally {
      if (currentGeneration === generation.current) setLoading(false);
    }
  };

  return (
    <div className="space-y-3 rounded-lg bg-surface-low p-4 ring-1 ring-border">
      <Button type="button" variant="outline" disabled={disabled || loading || !title.trim() || title.trim().length > 150} onClick={() => void generate()}>
        {loading ? 'Đang gợi ý...' : 'Gợi ý mô tả bằng AI'}
      </Button>
      <p className="text-xs text-text-muted">AI chỉ đọc tên món đồ, không đọc hoặc gửi ảnh. Hãy tự kiểm tra thông số và tình trạng thực tế.</p>
      {message ? <p role="status" className="text-sm text-text-secondary">{message}</p> : null}
      {suggestion ? (
        <div className="space-y-3">
          <p className="text-sm text-text-secondary">{suggestion.message} Còn {suggestion.remainingUses} lượt hôm nay.</p>
          <pre className="whitespace-pre-wrap break-words rounded-md bg-white p-3 font-sans text-sm leading-6 text-text-primary">{suggestion.description}</pre>
          <ul className="space-y-1 text-xs text-text-secondary">
            {suggestion.attributes.filter((attribute) => attribute.requiresUserInput).map((attribute) => (
              <li key={attribute.name}>{attribute.prompt || `${attribute.name}: bạn cần tự điền hoặc xác nhận.`}</li>
            ))}
          </ul>
          <Button type="button" onClick={() => { onUse(suggestion.description); setSuggestion(null); }}>
            Dùng gợi ý
          </Button>
        </div>
      ) : null}
    </div>
  );
}
