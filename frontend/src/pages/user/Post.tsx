import { type FormEvent, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import { Alert, Button, Field, Icon, PageHeader, Select, TextArea } from '../../components/ui';
import { CONDITIONS } from '../../constants/domain';
import { CREDIT_TO_VND, POST_LISTING_FEE } from '../../utils/credit';
import { conditionLabel } from '../../utils/formatting';
import type { ItemCondition, ItemType } from '../../types/domain';
import { fileToDataUrl } from '../../utils/files';
import { catalogApi } from '../../services/catalogApi';
import { toAreaOptions, toCategoryIdByName, toCategoryNames, type AreaOption } from '../../services/mappers';
import { itemApi, type ApiCondition } from '../../services/itemApi';
import { mediaApi } from '../../services/mediaApi';
import { resolveMediaUrl } from '../../services/mediaUrl';
import { toBackendUserId } from '../../services/requestApi';
import { useWallet } from '../../app/WalletContext';

type FormErrors = Partial<Record<'type' | 'title' | 'category' | 'condition' | 'district' | 'description' | 'images', string>>;
type MediaUploadState = 'idle' | 'uploading' | 'error';

const POST_FEE_SUCCESS =
  'Đăng bài trao đổi thành công. Bài đăng đã được gửi để xét duyệt.';
const FREE_GIFT_SUCCESS =
  'Đăng bài cho tặng thành công. Bài đăng đã được gửi để xét duyệt.';
const PENDING_REVIEW_NOTICE = 'Bài đăng đang chờ quản trị viên duyệt.';
const INSUFFICIENT_CREDIT = 'Bạn không đủ Credit để đăng bài.';
const BACKEND_SESSION_REQUIRED = 'Vui lòng đăng nhập bằng tài khoản thật để đăng bài.';
const SKIP_MEDIA_UPLOAD_FOR_ITEM_TEST =
  import.meta.env.DEV && import.meta.env.VITE_POST_SKIP_MEDIA_UPLOAD === 'true';

const CONDITION_TO_API: Record<ItemCondition, ApiCondition> = {
  new: 'NEW',
  good: 'GOOD',
  used: 'DEFECTIVE',
};

export function Post() {
  const user = useAppSelector(selectCurrentUser)!;
  const { isApiAccount, wallet, loading: walletLoading, error: walletError } = useWallet();
  const navigate = useNavigate();
  const [categories, setCategories] = useState<string[]>([]);
  const [districts, setDistricts] = useState<AreaOption[]>([]);
  const [categoryIds, setCategoryIds] = useState<Map<string, number>>(new Map());
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [catalogError, setCatalogError] = useState('');
  const [type, setType] = useState<ItemType | ''>('');
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState('');
  const [condition, setCondition] = useState<ItemCondition | ''>('');
  const [district, setDistrict] = useState('');
  const [description, setDescription] = useState('');
  const [tradeFor, setTradeFor] = useState('');
  const [imageFiles, setImageFiles] = useState<File[]>([]);
  const [images, setImages] = useState<string[]>([]);
  const [uploadedImageUrls, setUploadedImageUrls] = useState<Map<File, string>>(new Map());
  const [mediaUploadState, setMediaUploadState] = useState<MediaUploadState>('idle');
  const [mediaUploadError, setMediaUploadError] = useState('');
  const [error, setError] = useState('');
  const [errors, setErrors] = useState<FormErrors>({});
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const submitInFlight = useRef(false);
  const feeVnd = POST_LISTING_FEE * CREDIT_TO_VND;
  const currentUserId = toBackendUserId(user.id);
  const mediaUploading = mediaUploadState === 'uploading';
  const availableCredit = isApiAccount ? wallet?.available ?? null : user.availableCredit;

  useEffect(() => {
    let active = true;
    setCatalogLoading(true);
    setCatalogError('');
    Promise.all([catalogApi.categories(), catalogApi.areas()])
      .then(([categoryData, areaData]) => {
        if (!active) return;
        setCategories(toCategoryNames(categoryData));
        setDistricts(toAreaOptions(areaData));
        setCategoryIds(toCategoryIdByName(categoryData));
      })
      .catch(() => {
        if (!active) return;
        setCategories([]);
        setDistricts([]);
        setCategoryIds(new Map());
        setCatalogError('Không tải được danh mục hoặc khu vực. Vui lòng thử lại.');
      })
      .finally(() => {
        if (active) setCatalogLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const validate = () => {
    const nextErrors: FormErrors = {};
    if (!type) nextErrors.type = 'Vui lòng chọn hình thức.';
    if (!title.trim()) nextErrors.title = 'Vui lòng nhập tiêu đề.';
    if (!category) nextErrors.category = 'Vui lòng chọn danh mục.';
    if (!condition) nextErrors.condition = 'Vui lòng chọn tình trạng món đồ.';
    if (!district) nextErrors.district = 'Vui lòng chọn khu vực.';
    if (!description.trim()) nextErrors.description = 'Vui lòng nhập mô tả.';
    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    if (!isApiAccount && user.reputationStars <= 0) {
      setError('Uy tín của bạn đang ở mức 0 nên hiện không thể đăng bài mới.');
      return;
    }
    if (!currentUserId) {
      setError(BACKEND_SESSION_REQUIRED);
      return;
    }
    if (type === 'trade' && availableCredit === null) {
      setError(walletLoading ? 'Đang tải số dư Credit. Vui lòng chờ trong giây lát.' : walletError || 'Chưa thể xác minh số dư Credit.');
      return;
    }
    if (type === 'trade' && availableCredit !== null && availableCredit < POST_LISTING_FEE) {
      setError(INSUFFICIENT_CREDIT);
      setConfirmOpen(false);
      return;
    }
    setError('');
    setConfirmOpen(true);
  };

  const confirmSubmit = async () => {
    if (submitInFlight.current) return;
    if (!validate()) {
      setConfirmOpen(false);
      return;
    }
    if (type === 'trade' && availableCredit === null) {
      setError(walletLoading ? 'Đang tải số dư Credit. Vui lòng chờ trong giây lát.' : walletError || 'Chưa thể xác minh số dư Credit.');
      setConfirmOpen(false);
      return;
    }
    if (type === 'trade' && availableCredit !== null && availableCredit < POST_LISTING_FEE) {
      setError(INSUFFICIENT_CREDIT);
      setConfirmOpen(false);
      return;
    }
    if (!currentUserId) {
      setError(BACKEND_SESSION_REQUIRED);
      setConfirmOpen(false);
      return;
    }
    if (!type || !condition) return;
    const categoryId = categoryIds.get(category);
    const areaId = Number(district);
    if (!categoryId || !areaId) {
      setError('Danh mục hoặc khu vực chưa hợp lệ. Vui lòng chọn lại.');
      setConfirmOpen(false);
      return;
    }
    submitInFlight.current = true;
    setSubmitting(true);
    setError('');
    setMediaUploadError('');
    try {
      const uploadedUrls = new Map(uploadedImageUrls);
      if (!SKIP_MEDIA_UPLOAD_FOR_ITEM_TEST && imageFiles.length) {
        setMediaUploadState('uploading');
        try {
          for (const file of imageFiles) {
            if (uploadedUrls.has(file)) continue;
            const media = await mediaApi.upload(file);
            const url = resolveMediaUrl(media.url);
            if (!url) throw new Error('Media API did not return an image URL.');
            uploadedUrls.set(file, url);
            setUploadedImageUrls(new Map(uploadedUrls));
          }
        } catch {
          setMediaUploadState('error');
          setMediaUploadError('Không tải được ảnh lên. Các ảnh đã tải thành công sẽ được giữ lại khi thử lại.');
          setConfirmOpen(false);
          return;
        }
      }
      setMediaUploadState('idle');
      const itemImages = SKIP_MEDIA_UPLOAD_FOR_ITEM_TEST
        ? []
        : imageFiles.map((file) => uploadedUrls.get(file)).filter((url): url is string => Boolean(url));
      const item = await itemApi.create(
        {
          categoryId,
          areaId,
          offerType: type === 'gift' ? 'GIVE' : 'SWAP',
          title: title.trim(),
          description: description.trim(),
          desiredItem: type === 'trade' ? tradeFor.trim() || null : null,
          condition: CONDITION_TO_API[condition],
          defectNote: null,
          brand: null,
          images: itemImages,
        },
      );
      const baseNotice = type === 'gift' ? FREE_GIFT_SUCCESS : POST_FEE_SUCCESS;
      const reviewNotice = item.status === 'PENDING_REVIEW' ? ` ${PENDING_REVIEW_NOTICE}` : '';
      navigate('/activities', { state: { notice: `${baseNotice}${reviewNotice} Mã bài: #${item.id}.` } });
    } catch {
      setError('Không đăng được bài. Vui lòng kiểm tra thông tin và thử lại.');
      setConfirmOpen(false);
    } finally {
      submitInFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <div className="page-shell max-w-4xl">
      <PageHeader
        title="Đăng món đồ"
        description="Thông tin rõ ràng giúp món đồ sớm tìm được người phù hợp."
      />
      {error ? (
        <Alert tone="error">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <span>
              {error}
              {error === INSUFFICIENT_CREDIT ? (
                <span className="mt-1 block">Phí đăng bài là 5 Credit.</span>
              ) : null}
            </span>
            {error === INSUFFICIENT_CREDIT ? (
              <Link to="/credit">
                <Button size="sm">Nạp Credit</Button>
              </Link>
            ) : null}
          </div>
        </Alert>
      ) : null}
      <form onSubmit={submit} className="grid gap-6 lg:grid-cols-[1fr_280px]">
        <section className="space-y-5 rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-6">
          <div>
            <span className="label">Hình thức</span>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => {
                  setType('gift');
                  setErrors((current) => ({ ...current, type: undefined }));
                }}
                className={`rounded-md border p-4 text-left transition ${type === 'gift' ? 'border-primary bg-primary-faint text-primary' : 'border-border hover:border-primary/40'}`}
              >
                <strong className="block text-sm">Cho tặng</strong>
                <span className="mt-1 block text-xs text-text-muted">
                  Trao món đồ mà không cần đổi lại
                </span>
              </button>
              <button
                type="button"
                onClick={() => {
                  setType('trade');
                  setErrors((current) => ({ ...current, type: undefined }));
                }}
                className={`rounded-md border p-4 text-left transition ${type === 'trade' ? 'border-secondary bg-secondary-soft text-secondary' : 'border-border hover:border-secondary/40'}`}
              >
                <strong className="block text-sm">Trao đổi</strong>
                <span className="mt-1 block text-xs text-text-muted">
                  Đổi lấy một món phù hợp khác
                </span>
              </button>
            </div>
            {errors.type ? <span className="mt-1.5 block text-xs text-error">{errors.type}</span> : null}
          </div>
          <Field
            label="Tiêu đề"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Ví dụ: Xe đạp mini màu xanh"
            error={errors.title}
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <Select label="Danh mục" value={category} onChange={(e) => setCategory(e.target.value)}>
              <option value="">Chọn danh mục</option>
              {catalogLoading ? <option disabled>Đang tải danh mục...</option> : null}
              {!catalogLoading && !categories.length ? <option disabled>Chưa có danh mục</option> : null}
              {categories.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </Select>
            {errors.category ? <span className="mt-[-10px] block text-xs text-error sm:hidden">{errors.category}</span> : null}
            <Select
              label="Tình trạng món đồ"
              value={condition}
              onChange={(e) => setCondition(e.target.value as ItemCondition | '')}
            >
              <option value="">Chọn tình trạng</option>
              {CONDITIONS.map((c) => (
                <option key={c} value={c}>
                  {conditionLabel[c]}
                </option>
              ))}
            </Select>
          </div>
          <div className="grid gap-1 sm:grid-cols-2">
            {errors.category ? <span className="hidden text-xs text-error sm:block">{errors.category}</span> : <span />}
            {errors.condition ? <span className="text-xs text-error">{errors.condition}</span> : null}
          </div>
          <Select
            label="Khu vực"
            value={district}
            onChange={(e) => setDistrict(e.target.value)}
          >
            <option value="">Chọn khu vực</option>
            {catalogLoading ? <option disabled>Đang tải khu vực...</option> : null}
            {!catalogLoading && !districts.length ? <option disabled>Chưa có khu vực</option> : null}
            {districts.map((d) => (
              <option key={d.id} value={d.id}>{d.label}</option>
            ))}
          </Select>
          {errors.district ? <span className="mt-[-14px] block text-xs text-error">{errors.district}</span> : null}
          {catalogError ? <span className="mt-[-14px] block text-xs text-error">{catalogError}</span> : null}
          <TextArea
            label="Mô tả"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Tình trạng thực tế, kích thước và những lưu ý cần biết..."
            error={errors.description}
          />
          {type === 'trade' ? (
            <Field
              label="Bạn muốn đổi lấy gì?"
              value={tradeFor}
              onChange={(e) => setTradeFor(e.target.value)}
              placeholder="Ví dụ: Sách thiếu nhi hoặc cây để bàn"
            />
          ) : null}
        </section>
        <aside className="space-y-4">
          <label className={`flex aspect-[4/3] w-full flex-col items-center justify-center rounded-lg border border-dashed bg-white text-center transition hover:border-primary ${errors.images ? 'border-error' : 'border-border'}`}>
            <Icon name="image" className="size-8 text-primary" />
            <strong className="mt-3 text-sm">Thêm hình ảnh</strong>
            <span className="mt-1 text-xs text-text-muted">Tối đa 6 ảnh</span>
            <input
              className="hidden"
              type="file"
              accept="image/*"
              multiple
              disabled={submitting || mediaUploading}
              onChange={async (event) => {
                const selectedFiles = Array.from(event.target.files ?? []);
                const files = selectedFiles.slice(0, 6);
                setMediaUploadState('idle');
                setMediaUploadError('');
                if (selectedFiles.length > 6) {
                  setErrors((current) => ({ ...current, images: 'Chỉ được chọn tối đa 6 ảnh.' }));
                } else if (files.some((file) => !file.type.startsWith('image/'))) {
                  setErrors((current) => ({ ...current, images: 'Vui lòng chỉ chọn tệp hình ảnh.' }));
                  setImageFiles([]);
                  setImages([]);
                  setUploadedImageUrls(new Map());
                  return;
                } else {
                  setErrors((current) => ({ ...current, images: undefined }));
                }
                try {
                  const previews = await Promise.all(files.map(fileToDataUrl));
                  setImageFiles(files);
                  setImages(previews);
                  setUploadedImageUrls((current) =>
                    new Map(files.flatMap((file) => {
                      const url = current.get(file);
                      return url ? [[file, url] as const] : [];
                    })),
                  );
                } catch {
                  setImageFiles([]);
                  setImages([]);
                  setUploadedImageUrls(new Map());
                  setMediaUploadState('error');
                  setMediaUploadError('Không đọc được ảnh đã chọn. Vui lòng chọn lại.');
                }
              }}
            />
          </label>
          {errors.images ? <span className="block text-xs text-error">{errors.images}</span> : null}
          {mediaUploadError ? <span className="block text-xs text-error">{mediaUploadError}</span> : null}
          {images.length ? (
            <div className="grid grid-cols-3 gap-2">
              {images.map((image, index) => (
                <div key={image.slice(-16) + index} className="relative">
                  <img
                    src={image}
                    alt={`Ảnh món đồ ${index + 1}`}
                    className="aspect-square rounded-md object-cover"
                  />
                  <button
                    type="button"
                    aria-label="Xóa ảnh"
                    disabled={submitting || mediaUploading}
                    onClick={() => {
                      const removedFile = imageFiles[index];
                      setImageFiles(imageFiles.filter((_, itemIndex) => itemIndex !== index));
                      setImages(images.filter((_, itemIndex) => itemIndex !== index));
                      setUploadedImageUrls((current) => {
                        const next = new Map(current);
                        if (removedFile) next.delete(removedFile);
                        return next;
                      });
                      setMediaUploadState('idle');
                      setMediaUploadError('');
                    }}
                    className="absolute right-1 top-1 grid size-6 place-items-center rounded-full bg-white text-error shadow-sm"
                  >
                    <Icon name="close" className="size-3" />
                  </button>
                </div>
              ))}
            </div>
          ) : null}
          <div className="rounded-lg bg-primary-faint p-4 text-xs leading-5 text-text-secondary">
            <strong className="text-primary">Trước khi đăng</strong>
            <p className="mt-1">
              Bài viết sẽ chờ quản trị viên duyệt. Không đăng thông tin liên hệ trong mô tả.
            </p>
          </div>
          <div className="rounded-lg bg-white p-4 text-xs leading-5 text-text-secondary ring-1 ring-border/80">
            {type === 'gift' ? (
              <strong className="text-primary">Đăng bài cho tặng miễn phí</strong>
            ) : (
              <>
                <strong className="text-text-primary">Phí đăng bài: {POST_LISTING_FEE} Credit</strong>
                <p className="mt-1 font-bold text-primary">
                  {POST_LISTING_FEE} Credit ({feeVnd.toLocaleString('vi-VN')}đ)
                </p>
                <p className="mt-1">5 Credit sẽ được trừ khi bạn xác nhận đăng bài.</p>
              </>
            )}
          </div>
          <Button className="w-full" size="lg" disabled={mediaUploading}>
            Đăng bài
          </Button>
        </aside>
      </form>
      {confirmOpen ? (
        <div className="fixed inset-0 z-40 grid place-items-end bg-black/30 sm:place-items-center sm:p-4">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="post-fee-title"
            className="w-full rounded-t-xl bg-white p-5 shadow-lg sm:max-w-lg sm:rounded-xl sm:p-6"
          >
            <h2 id="post-fee-title" className="text-xl font-bold text-text-primary">
              Xác nhận đăng bài
            </h2>
            <p className="mt-2 text-sm leading-6 text-text-muted">
              {type === 'gift'
                ? 'Bài cho tặng được đăng miễn phí sau khi bạn xác nhận.'
                : 'Phí đăng bài sẽ được trừ một lần sau khi bạn xác nhận.'}
            </p>
            <div className="mt-5 space-y-3 rounded-lg bg-surface-low p-4 text-sm">
              <SummaryRow
                label={type === 'gift' ? 'Phí đăng bài' : 'Phí đăng bài'}
                value={type === 'gift' ? 'Miễn phí' : `${POST_LISTING_FEE} Credit`}
              />
              <SummaryRow label="Số dư hiện tại" value={availableCredit === null ? '--' : `${availableCredit} Credit`} />
              <SummaryRow
                label="Số dư sau khi đăng"
                value={availableCredit === null ? '--' : `${availableCredit} Credit`}
              />
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setConfirmOpen(false)} disabled={submitting || mediaUploading}>
                Hủy
              </Button>
              <Button onClick={confirmSubmit} disabled={submitting || mediaUploading}>
                {submitting ? 'Đang đăng...' : 'Xác nhận đăng bài'}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function SummaryRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-text-muted">{label}</span>
      <strong className="text-text-primary">{value}</strong>
    </div>
  );
}
