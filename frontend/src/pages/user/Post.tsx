import { type ChangeEvent, type FormEvent, type ReactNode, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import { Alert, Button, Field, Icon, ItemImage, PageHeader, Select, TextArea } from '../../components/ui';
import { DialogFrame } from '../../components/Dialog';
import { CONDITIONS, ITEM_IMAGES_MAX, ITEM_IMAGES_MIN } from '../../constants/domain';
import { conditionDescription, conditionLabel } from '../../utils/formatting';
import type { ItemCondition, ItemType } from '../../types/domain';
import { fileToDataUrl } from '../../utils/files';
import { catalogApi, type CategoryAttributeResponse } from '../../services/catalogApi';
import { toAreaOptions, toCategoryIdByName, toCategoryNames, type AreaOption } from '../../services/mappers';
import { itemApi, type ApiCondition, type OperatingStatus, type PostingQuota } from '../../services/itemApi';
import { postingQuotaRow } from '../../utils/postingQuota';
import { mediaApi } from '../../services/mediaApi';
import type { ApiError } from '../../services/http';
import { toBackendUserId } from '../../services/requestApi';
import { useWallet } from '../../app/useWallet';
import {
  ITEM_MODERATION_BLOCKED,
  giveLimitMessage,
  moderationBlockedMessage,
  reputationBlockedMessage,
} from '../../utils/apiErrorMessages';

type FormErrors = Partial<Record<'type' | 'title' | 'category' | 'condition' | 'district' | 'description' | 'tradeFor' | 'brand' | 'defectNote' | 'images' | 'defectImages' | 'operatingStatus', string>>;
type MediaUploadState = 'idle' | 'uploading' | 'error';

const SWAP_POST_SUCCESS =
  'Đăng bài trao đổi thành công. Bài đăng đã được gửi để xét duyệt.';
const GIVE_POST_SUCCESS =
  'Đăng bài cho tặng thành công. Bài đăng đã được gửi để xét duyệt.';
const PENDING_REVIEW_NOTICE = 'Bài đăng đang chờ quản trị viên duyệt.';
const WALLET_INSUFFICIENT_BALANCE = 'WALLET_INSUFFICIENT_BALANCE';
const INSUFFICIENT_CREDIT_MESSAGE = 'Số dư Credit khả dụng không đủ để đăng bài.';
const MODERATION_BLOCKED_TITLE = 'Bài đăng chưa đáp ứng quy định';
const BACKEND_SESSION_REQUIRED = 'Vui lòng đăng nhập bằng tài khoản thật để đăng bài.';
const SKIP_MEDIA_UPLOAD_FOR_ITEM_TEST =
  import.meta.env.DEV && import.meta.env.VITE_POST_SKIP_MEDIA_UPLOAD === 'true';

const CONDITION_TO_API: Record<ItemCondition, ApiCondition> = {
  NEW: 'NEW',
  LIKE_NEW: 'LIKE_NEW',
  GOOD: 'GOOD',
  DEFECTIVE: 'DEFECTIVE',
  new: 'NEW',
  good: 'GOOD',
  used: 'DEFECTIVE',
};

const OPERATING_STATUS_LABELS: Record<OperatingStatus, string> = {
  NORMAL: 'Hoạt động bình thường',
  PARTIAL: 'Hoạt động một phần',
  NOT_WORKING: 'Không hoạt động',
};

const isOperatingStatus = (value: string): value is OperatingStatus =>
  value === 'NORMAL' || value === 'PARTIAL' || value === 'NOT_WORKING';

export function Post() {
  const user = useAppSelector(selectCurrentUser)!;
  const { wallet, loading: walletLoading, error: walletError, refresh: refreshWallet } = useWallet();
  const navigate = useNavigate();
  const [categories, setCategories] = useState<string[]>([]);
  const [districts, setDistricts] = useState<AreaOption[]>([]);
  const [categoryIds, setCategoryIds] = useState<Map<string, number>>(new Map());
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [catalogError, setCatalogError] = useState('');
  const [categoryAttributes, setCategoryAttributes] = useState<CategoryAttributeResponse[]>([]);
  const [attributesLoading, setAttributesLoading] = useState(false);
  const [attributesError, setAttributesError] = useState('');
  const [type, setType] = useState<ItemType | ''>('');
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState('');
  const [condition, setCondition] = useState<ItemCondition | ''>('');
  const [district, setDistrict] = useState('');
  const [description, setDescription] = useState('');
  const [tradeFor, setTradeFor] = useState('');
  const [brand, setBrand] = useState('');
  const [defectNote, setDefectNote] = useState('');
  const [operatingStatus, setOperatingStatus] = useState<OperatingStatus | ''>('');
  const [imageFiles, setImageFiles] = useState<File[]>([]);
  const [images, setImages] = useState<string[]>([]);
  const [uploadedImageUrls, setUploadedImageUrls] = useState<Map<File, string>>(new Map());
  const [defectImageFiles, setDefectImageFiles] = useState<File[]>([]);
  const [defectImagePreviews, setDefectImagePreviews] = useState<string[]>([]);
  const [uploadedDefectImageUrls, setUploadedDefectImageUrls] = useState<Map<File, string>>(new Map());
  const [mediaUploadState, setMediaUploadState] = useState<MediaUploadState>('idle');
  const [mediaUploadError, setMediaUploadError] = useState('');
  const [error, setError] = useState('');
  const [errorCode, setErrorCode] = useState('');
  const [errors, setErrors] = useState<FormErrors>({});
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const submitInFlight = useRef(false);
  const currentUserId = toBackendUserId(user.id);
  const mediaUploading = mediaUploadState === 'uploading';
  const availableCredit = wallet?.available ?? null;
  const [postFeeError, setPostFeeError] = useState('');
  // Hạn mức bài Cho tặng và Trao đổi; null khi chưa tải được, khi đó ô hạn mức hiện "--".
  const [postingQuota, setPostingQuota] = useState<PostingQuota | null>(null);
  // Báo giá bài kế tiếp đã tính theo hạng và số bài đang hoạt động tại Backend.
  const postFee = postingQuota?.nextPostFee ?? null;
  const sortedCategoryAttributes = [...categoryAttributes].sort(
    (left, right) => left.sortOrder - right.sortOrder || left.id - right.id,
  );
  const operatingStatusAttribute = sortedCategoryAttributes.find(
    (attribute) =>
      attribute.dataType === 'SELECT' &&
      ['NORMAL', 'PARTIAL', 'NOT_WORKING'].every((option) => attribute.options.includes(option)),
  );
  const operatingStatusRequired = operatingStatusAttribute?.required ?? false;
  const operatingStatusOptions = operatingStatusAttribute?.options.filter(isOperatingStatus) ?? [];

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

  useEffect(() => {
    let active = true;
    itemApi
      .postingQuota()
      .then((quota) => {
        if (active) {
          setPostingQuota(quota);
          setPostFeeError('');
        }
      })
      .catch(() => {
        if (active) {
          setPostingQuota(null);
          setPostFeeError('Chưa tải được phí đăng bài. Vui lòng thử lại.');
        }
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const categoryId = categoryIds.get(category);
    setOperatingStatus('');
    setErrors((current) => ({ ...current, operatingStatus: undefined }));
    if (!categoryId) {
      setCategoryAttributes([]);
      setAttributesError('');
      setAttributesLoading(false);
      return;
    }
    let active = true;
    setAttributesLoading(true);
    setAttributesError('');
    catalogApi.categoryAttributes(categoryId)
      .then((response) => { if (active) setCategoryAttributes(response); })
      .catch(() => {
        if (!active) return;
        setCategoryAttributes([]);
        setAttributesError('Không tải được thông tin bổ sung của danh mục.');
      })
      .finally(() => { if (active) setAttributesLoading(false); });
    return () => { active = false; };
  }, [category, categoryIds]);

  const validate = () => {
    const nextErrors: FormErrors = {};
    if (!type) nextErrors.type = 'Vui lòng chọn hình thức.';
    if (!title.trim()) nextErrors.title = 'Vui lòng nhập tiêu đề.';
    if (!category) nextErrors.category = 'Vui lòng chọn danh mục.';
    if (!condition) nextErrors.condition = 'Vui lòng chọn tình trạng món đồ.';
    if (!district) nextErrors.district = 'Vui lòng chọn khu vực.';
    if (!description.trim()) nextErrors.description = 'Vui lòng nhập mô tả.';
    if (!SKIP_MEDIA_UPLOAD_FOR_ITEM_TEST && imageFiles.length < ITEM_IMAGES_MIN) {
      nextErrors.images = `Vui lòng chọn ít nhất ${ITEM_IMAGES_MIN} ảnh.`;
    } else if (imageFiles.length > ITEM_IMAGES_MAX) {
      nextErrors.images = `Chỉ được chọn tối đa ${ITEM_IMAGES_MAX} ảnh.`;
    }
    if (title.trim().length > 150) nextErrors.title = 'Tiêu đề tối đa 150 ký tự.';
    if (type === 'trade' && !tradeFor.trim()) nextErrors.tradeFor = 'Vui lòng nhập món đồ mong muốn.';
    if (brand.trim().length > 80) nextErrors.brand = 'Thương hiệu tối đa 80 ký tự.';
    if (condition === 'DEFECTIVE' && !defectNote.trim()) nextErrors.defectNote = 'Vui lòng mô tả lỗi của món đồ.';
    if (condition === 'DEFECTIVE' && defectImageFiles.length === 0) nextErrors.defectImages = 'Vui lòng chọn ít nhất một ảnh lỗi.';
    if (operatingStatusRequired && !operatingStatus) nextErrors.operatingStatus = 'Vui lòng chọn trạng thái hoạt động.';
    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setErrorCode('');
    if (!validate()) return;
    if (!currentUserId) {
      setError(BACKEND_SESSION_REQUIRED);
      return;
    }
    if (type === 'trade' && availableCredit === null) {
      setError(walletLoading ? 'Đang tải số dư Credit. Vui lòng chờ trong giây lát.' : walletError || 'Chưa thể xác minh số dư Credit.');
      return;
    }
    if (type === 'trade') {
      try {
        const currentQuota = await itemApi.postingQuota();
        setPostingQuota(currentQuota);
        setPostFeeError('');
        if (availableCredit !== null && availableCredit < currentQuota.nextPostFee) {
          setErrorCode(WALLET_INSUFFICIENT_BALANCE);
          setError(INSUFFICIENT_CREDIT_MESSAGE);
          setConfirmOpen(false);
          return;
        }
      } catch {
        setPostFeeError('Chưa tải được phí đăng bài. Vui lòng thử lại.');
        setError('Chưa tải được phí đăng bài. Vui lòng thử lại.');
        return;
      }
    }
    setError('');
    setConfirmOpen(true);
  };

  const confirmSubmit = async () => {
    if (submitInFlight.current) return;
    setErrorCode('');
    if (!validate()) {
      setConfirmOpen(false);
      return;
    }
    if (type === 'trade' && availableCredit === null) {
      setError(walletLoading ? 'Đang tải số dư Credit. Vui lòng chờ trong giây lát.' : walletError || 'Chưa thể xác minh số dư Credit.');
      setConfirmOpen(false);
      return;
    }
    if (type === 'trade' && postFee === null) {
      setError(postFeeError || 'Đang tải phí đăng bài. Vui lòng chờ trong giây lát.');
      setConfirmOpen(false);
      return;
    }
    if (type === 'trade') {
      try {
        const currentQuota = await itemApi.postingQuota();
        if (currentQuota.nextPostFee !== postFee) {
          setPostingQuota(currentQuota);
          setError('Mức phí vừa thay đổi. Vui lòng xem lại số Credit trong hộp xác nhận.');
          return;
        }
      } catch {
        setError('Chưa xác minh được phí đăng bài. Vui lòng thử lại.');
        return;
      }
    }
    if (type === 'trade' && postFee !== null && availableCredit !== null && availableCredit < postFee) {
      setErrorCode(WALLET_INSUFFICIENT_BALANCE);
      setError(INSUFFICIENT_CREDIT_MESSAGE);
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
            const url = media.url;
            if (!url) throw new Error('Không nhận được địa chỉ ảnh sau khi tải lên.');
            uploadedUrls.set(file, url);
            setUploadedImageUrls(new Map(uploadedUrls));
          }
        } catch (caught) {
          const apiError = caught as ApiError;
          setMediaUploadState('error');
          setMediaUploadError(apiError.message || 'Không tải được ảnh lên. Các ảnh đã tải thành công sẽ được giữ lại khi thử lại.');
          setConfirmOpen(false);
          return;
        }
      }
      setMediaUploadState('idle');
      const itemImages = SKIP_MEDIA_UPLOAD_FOR_ITEM_TEST
        ? []
        : imageFiles.map((file) => uploadedUrls.get(file)).filter((url): url is string => Boolean(url));
      const uploadedDefectUrls = new Map(uploadedDefectImageUrls);
      if (condition === 'DEFECTIVE') {
        setMediaUploadState('uploading');
        for (const file of defectImageFiles) {
          if (uploadedDefectUrls.has(file)) continue;
          const media = await mediaApi.upload(file);
          uploadedDefectUrls.set(file, media.url);
          setUploadedDefectImageUrls(new Map(uploadedDefectUrls));
        }
      }
      setMediaUploadState('idle');
      const item = await itemApi.create(
        {
          categoryId,
          areaId,
          offerType: type === 'gift' ? 'GIVE' : 'SWAP',
          title: title.trim(),
          description: description.trim(),
          desiredItem: type === 'trade' ? tradeFor.trim() || null : null,
          condition: CONDITION_TO_API[condition],
          defectNote: condition === 'DEFECTIVE' ? defectNote.trim() : null,
          brand: brand.trim() || null,
          images: itemImages,
          defectImages: condition === 'DEFECTIVE'
            ? defectImageFiles.map((file) => uploadedDefectUrls.get(file)).filter((url): url is string => Boolean(url))
            : [],
          operatingStatus: operatingStatusRequired ? operatingStatus || null : null,
        },
      );
      refreshWallet();
      const baseNotice = type === 'gift' ? GIVE_POST_SUCCESS : SWAP_POST_SUCCESS;
      const reviewNotice = item.status === 'PENDING_REVIEW' ? ` ${PENDING_REVIEW_NOTICE}` : '';
      navigate('/activities', { state: { notice: `${baseNotice}${reviewNotice}` } });
    } catch (caught) {
      const apiError = caught as ApiError;
      const fieldNames: Record<string, keyof FormErrors> = {
        title: 'title', description: 'description', desiredItem: 'tradeFor',
        categoryId: 'category', areaId: 'district', condition: 'condition',
        defectNote: 'defectNote', images: 'images', defectImages: 'defectImages',
        brand: 'brand', operatingStatus: 'operatingStatus', offerType: 'type',
      };
      const field = apiError.field ? fieldNames[apiError.field] : undefined;
      if (field) setErrors((current) => ({ ...current, [field]: apiError.message }));
      setErrorCode(apiError.code);
      setError(apiError.code === WALLET_INSUFFICIENT_BALANCE
        ? INSUFFICIENT_CREDIT_MESSAGE
        : apiError.code === ITEM_MODERATION_BLOCKED
          ? moderationBlockedMessage(apiError)
          : giveLimitMessage(apiError)
            ?? reputationBlockedMessage(apiError.code)
            ?? (apiError.message || 'Không đăng được bài. Vui lòng kiểm tra thông tin và thử lại.'));
      if (apiError.code === WALLET_INSUFFICIENT_BALANCE) refreshWallet();
      setConfirmOpen(false);
    } finally {
      submitInFlight.current = false;
      setSubmitting(false);
    }
  };

  const moveImage = (index: number, direction: number) => {
    const target = index + direction;
    if (target < 0 || target >= imageFiles.length) return;
    const nextFiles = [...imageFiles];
    const nextImages = [...images];
    [nextFiles[index], nextFiles[target]] = [nextFiles[target], nextFiles[index]];
    [nextImages[index], nextImages[target]] = [nextImages[target], nextImages[index]];
    setImageFiles(nextFiles);
    setImages(nextImages);
  };

  const selectImages = async (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []);
    event.currentTarget.value = '';
    setMediaUploadState('idle');
    setMediaUploadError('');
    if (!files.length) return;
    const nextImageCount = imageFiles.length + files.length;
    if (nextImageCount > ITEM_IMAGES_MAX) {
      setErrors((current) => ({ ...current, images: `Chỉ được chọn tối đa ${ITEM_IMAGES_MAX} ảnh.` }));
      return;
    }
    if (files.some((file) => !file.type.startsWith('image/'))) {
      setErrors((current) => ({ ...current, images: 'Vui lòng chỉ chọn tệp hình ảnh.' }));
      return;
    }
    setErrors((current) => ({
      ...current,
      images: nextImageCount >= ITEM_IMAGES_MIN
        ? undefined
        : current.images === `Vui lòng chọn ít nhất ${ITEM_IMAGES_MIN} ảnh.`
          ? current.images
          : undefined,
    }));
    try {
      const previews = await Promise.all(files.map(fileToDataUrl));
      setImageFiles((current) => [...current, ...files]);
      setImages((current) => [...current, ...previews]);
    } catch {
      setMediaUploadState('error');
      setMediaUploadError('Không đọc được ảnh vừa chọn. Vui lòng thử lại.');
    }
  };

  const removeImage = (index: number) => {
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
    const remainingCount = imageFiles.length - 1;
    setErrors((current) => ({
      ...current,
      images: remainingCount < ITEM_IMAGES_MIN
        && current.images === `Vui lòng chọn ít nhất ${ITEM_IMAGES_MIN} ảnh.`
        ? current.images
        : undefined,
    }));
  };

  const missingImages = Math.max(0, ITEM_IMAGES_MIN - images.length);
  const infoReady = Boolean(title.trim() && category && condition && description.trim());
  const quotaRow = postingQuotaRow(type, postingQuota);
  const feeLabel = type === 'gift' ? 'Miễn phí' : type === 'trade' ? (postFee === null ? '--' : `${postFee} Credit`) : 'Chọn hình thức';

  return (
    <div className="page-shell">
      <PageHeader
        title="Đăng tin"
        description="Admin duyệt bài trước khi bài hiện ra. Phí đăng chỉ trừ khi bài được duyệt, bị từ chối thì hoàn lại."
      />
      {error ? (
        <div className="mb-5">
          <Alert tone="error">
            {errorCode === ITEM_MODERATION_BLOCKED ? (
              <div>
                <p className="font-bold text-error">{MODERATION_BLOCKED_TITLE}</p>
                <p className="mt-1 whitespace-normal break-words leading-6">{error}</p>
              </div>
            ) : (
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <span>{error}</span>
                {errorCode === WALLET_INSUFFICIENT_BALANCE ? (
                  <Link to="/credit">
                    <Button>Nạp Credit</Button>
                  </Link>
                ) : null}
              </div>
            )}
          </Alert>
        </div>
      ) : null}
      <form onSubmit={submit} className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="min-w-0 space-y-4">
          <PostStep number={1} title="Bạn muốn cho hay đổi?">
            <div className="grid grid-cols-2 gap-2.5">
              {([
                ['gift', 'Cho tặng', 'Tặng miễn phí cho người cần'],
                ['trade', 'Trao đổi', 'Đổi lấy một món bạn cần'],
              ] as const).map(([value, label, hint]) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={type === value}
                  onClick={() => {
                    setType(value);
                    setErrors((current) => ({ ...current, type: undefined }));
                  }}
                  className={`flex flex-col gap-1 rounded-md border p-4 text-left transition ${type === value ? 'border-2 border-primary bg-primary-faint' : 'border-border hover:border-primary/40'}`}
                >
                  <strong className="text-sm">{label}</strong>
                  <span className="text-xs text-text-secondary">{hint}</span>
                </button>
              ))}
            </div>
            {errors.type ? <span className="block text-xs text-error">{errors.type}</span> : null}
            {type === 'trade' ? (
              <Field
                label="Bạn muốn đổi lấy gì?"
                value={tradeFor}
                onChange={(e) => setTradeFor(e.target.value)}
                placeholder="Ví dụ: áo sơ mi nam size M"
                hint="Hiện nổi bật trên bài để người khác biết nên đề nghị món gì."
                error={errors.tradeFor}
              />
            ) : null}
          </PostStep>

          <PostStep number={2} title="Ảnh món đồ">
            <div className="grid grid-cols-3 gap-2.5 sm:grid-cols-5">
              {images.map((image, index) => (
                <div key={image.slice(-16) + index} className="relative">
                  <ItemImage src={image} alt={`Ảnh món đồ ${index + 1}`} className="aspect-square w-full rounded-md object-cover" />
                  {index === 0 ? (
                    <span className="absolute left-1.5 top-1.5 rounded-sm bg-primary px-1.5 py-0.5 text-2xs font-bold text-white">Ảnh bìa</span>
                  ) : null}
                  <button
                    type="button"
                    aria-label="Xóa ảnh"
                    disabled={submitting || mediaUploading}
                    onClick={() => removeImage(index)}
                    className="absolute right-1 top-1 grid size-8 place-items-center rounded-full bg-white text-error shadow-sm transition hover:bg-error-soft"
                  >
                    <Icon name="close" className="size-3" />
                  </button>
                  <div className="absolute bottom-1 left-1 right-1 flex justify-between gap-1">
                    <button type="button" className="min-h-8 rounded-md bg-white px-2 text-2xs font-semibold shadow-sm transition hover:bg-primary-faint disabled:opacity-40" aria-label={`Đưa ảnh ${index + 1} về trước`} disabled={index === 0 || submitting || mediaUploading} onClick={() => moveImage(index, -1)}>Trước</button>
                    <button type="button" className="min-h-8 rounded-md bg-white px-2 text-2xs font-semibold shadow-sm transition hover:bg-primary-faint disabled:opacity-40" aria-label={`Đưa ảnh ${index + 1} ra sau`} disabled={index === images.length - 1 || submitting || mediaUploading} onClick={() => moveImage(index, 1)}>Sau</button>
                  </div>
                </div>
              ))}
              <label className={`flex aspect-square cursor-pointer flex-col items-center justify-center rounded-md border border-dashed text-center text-sm font-semibold text-primary transition hover:bg-primary-faint ${errors.images ? 'border-error' : 'border-text-muted'}`}>
                Thêm ảnh
                <input
                  className="sr-only"
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  multiple
                  disabled={submitting || mediaUploading || imageFiles.length >= ITEM_IMAGES_MAX}
                  onChange={(event) => void selectImages(event)}
                />
              </label>
            </div>
            <p className="text-xs leading-5 text-text-secondary">
              Cần {ITEM_IMAGES_MIN} đến {ITEM_IMAGES_MAX} ảnh chụp thật, có thể chọn nhiều lần.
              {images.length ? ` Đã có ${images.length}/${ITEM_IMAGES_MAX} ảnh.` : ''} Ảnh đầu là ảnh bìa.
            </p>
            {errors.images ? <span className="block text-xs text-error">{errors.images}</span> : null}
            {mediaUploadError ? <span className="block text-xs text-error">{mediaUploadError}</span> : null}
          </PostStep>

          <PostStep number={3} title="Thông tin món đồ">
            <Field
              label="Tên món đồ"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Ví dụ: Xe đạp mini màu xanh"
              hint="Tối đa 150 ký tự."
              error={errors.title}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <Select label="Danh mục" value={category} error={errors.category} onChange={(e) => setCategory(e.target.value)}>
                <option value="">Chọn danh mục</option>
                {catalogLoading ? <option disabled>Đang tải danh mục...</option> : null}
                {!catalogLoading && !categories.length ? <option disabled>Chưa có danh mục</option> : null}
                {categories.map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </Select>
              <Field
                label="Thương hiệu (không bắt buộc)"
                value={brand}
                onChange={(event) => setBrand(event.target.value)}
                placeholder="Ví dụ: Casio"
                error={errors.brand}
              />
            </div>
            {category && (attributesLoading || Boolean(attributesError) || categoryAttributes.length > 0) ? (
              <div className="rounded-md bg-surface-low p-3 text-sm">
                <p className="font-semibold">Nên nêu trong mô tả</p>
                {attributesLoading ? <p className="mt-1 text-text-muted">Đang tải thuộc tính...</p> : null}
                {attributesError ? <p className="mt-1 text-error">{attributesError}</p> : null}
                {!attributesLoading && !attributesError && categoryAttributes.length ? (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {sortedCategoryAttributes.map((attribute) => (
                      <span key={attribute.id} className="rounded-full bg-white px-2.5 py-1 text-xs ring-1 ring-border">
                        {attribute.name}
                        {attribute.required ? ' · bắt buộc' : ''}
                        {attribute.options.length ? ` · ${attribute.options.join(', ')}` : ''}
                      </span>
                    ))}
                  </div>
                ) : null}
              </div>
            ) : null}
            <fieldset>
              <legend className="label">Tình trạng</legend>
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                {CONDITIONS.map((value) => (
                  <button
                    key={value}
                    type="button"
                    aria-pressed={condition === value}
                    onClick={() => {
                      setCondition(value);
                      setErrors((current) => ({ ...current, condition: undefined }));
                    }}
                    className={`flex flex-col gap-0.5 rounded-md border p-3 text-left transition ${condition === value ? 'border-2 border-primary bg-primary-faint' : 'border-border hover:border-primary/40'}`}
                  >
                    <strong className="text-sm">{conditionLabel[value]}</strong>
                    <span className="text-2xs text-text-muted">{conditionDescription[value]}</span>
                  </button>
                ))}
              </div>
              {errors.condition ? <span className="mt-1.5 block text-xs text-error">{errors.condition}</span> : null}
            </fieldset>
            {condition === 'DEFECTIVE' ? (
              <div className="space-y-3 rounded-md border border-secondary-fixed bg-secondary-soft p-4">
                <TextArea
                  label="Lỗi cụ thể"
                  value={defectNote}
                  placeholder="Ví dụ: công tắc lúc bật lúc không"
                  onChange={(event) => setDefectNote(event.target.value)}
                  error={errors.defectNote}
                />
                <div>
                  <span className="label">Ảnh chụp rõ chỗ lỗi</span>
                  <input
                    className="block w-full text-xs"
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    multiple
                    disabled={submitting || mediaUploading}
                    onChange={async (event) => {
                      const files = Array.from(event.target.files ?? []);
                      setDefectImageFiles(files);
                      setUploadedDefectImageUrls(new Map());
                      setDefectImagePreviews(await Promise.all(files.map(fileToDataUrl)));
                      setErrors((current) => ({ ...current, defectImages: files.length ? undefined : current.defectImages }));
                    }}
                  />
                  {errors.defectImages ? <span className="mt-2 block text-xs text-error">{errors.defectImages}</span> : null}
                  {defectImagePreviews.length ? (
                    <div className="mt-3 grid grid-cols-4 gap-2">
                      {defectImagePreviews.map((preview, index) => (
                        <ItemImage key={`${preview.slice(-16)}-${index}`} src={preview} alt={`Ảnh lỗi ${index + 1}`} className="aspect-square rounded-md object-cover" />
                      ))}
                    </div>
                  ) : null}
                </div>
              </div>
            ) : null}
            {operatingStatusAttribute ? (
              <Select
                label={`${operatingStatusAttribute.name}${operatingStatusRequired ? '' : ' (không bắt buộc)'}`}
                value={operatingStatus}
                error={errors.operatingStatus}
                onChange={(event) => setOperatingStatus(event.target.value as OperatingStatus | '')}
              >
                <option value="">Chọn trạng thái hoạt động</option>
                {operatingStatusOptions.map((option) => (
                  <option key={option} value={option}>{OPERATING_STATUS_LABELS[option]}</option>
                ))}
              </Select>
            ) : null}
            <TextArea
              label="Mô tả"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Kích thước, đã dùng bao lâu, phụ kiện kèm theo, cách nhận."
              hint="Không ghi số điện thoại, Zalo hay link. Bài có thông tin liên hệ sẽ bị từ chối."
              error={errors.description}
            />
          </PostStep>

          <PostStep number={4} title="Khu vực giao nhận">
            <Select
              label="Quận"
              value={district}
              error={errors.district || catalogError || undefined}
              hint="Người tìm theo quận sẽ thấy bài của bạn."
              onChange={(e) => setDistrict(e.target.value)}
            >
              <option value="">Chọn quận</option>
              {catalogLoading ? <option disabled>Đang tải khu vực...</option> : null}
              {!catalogLoading && !districts.length ? <option disabled>Chưa có khu vực</option> : null}
              {districts.map((d) => (
                <option key={d.id} value={d.id}>{d.label}</option>
              ))}
            </Select>
          </PostStep>
        </div>

        <aside aria-label="Tóm tắt" className="space-y-4 rounded-lg bg-white p-5 ring-1 ring-border/80 lg:sticky lg:top-24">
          <h2 className="text-base font-bold">Tóm tắt</h2>
          <dl className="grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-2.5 text-sm">
            <dt className="text-text-secondary">Hình thức</dt>
            <dd className="font-semibold">{type === 'gift' ? 'Cho tặng' : type === 'trade' ? 'Trao đổi' : '--'}</dd>
            <dt className="text-text-secondary">Phí đăng</dt>
            <dd className="font-bold">{feeLabel}</dd>
            {quotaRow ? (
              <>
                <dt className="text-text-secondary">{quotaRow.label}</dt>
                <dd className="font-semibold tabular-nums">{quotaRow.value}</dd>
              </>
            ) : null}
            <dt className="text-text-secondary">Số dư khả dụng</dt>
            <dd className="font-semibold tabular-nums">{availableCredit === null ? '--' : `${availableCredit} Credit`}</dd>
          </dl>
          {type === 'trade' && quotaRow && postFee !== null ? (
            <p className="text-sm text-text-secondary">
              {quotaRow.label} {quotaRow.value} – bài này phí {postFee} Credit.
            </p>
          ) : null}
          <ul className="list-disc space-y-1 pl-5 text-xs leading-5">
            <li className={missingImages ? 'text-text-secondary' : 'text-success'}>
              Đã có {images.length}/{ITEM_IMAGES_MAX} ảnh
            </li>
            <li className={infoReady ? 'text-success' : 'text-warning'}>
              {infoReady ? 'Đã điền tên, danh mục, tình trạng, mô tả' : 'Còn thiếu tên, danh mục, tình trạng hoặc mô tả'}
            </li>
            <li className={district ? 'text-success' : 'text-warning'}>{district ? 'Đã chọn quận' : 'Chưa chọn quận'}</li>
          </ul>
          <Button className="w-full" size="lg" disabled={submitting || mediaUploading || catalogLoading}>
            Gửi duyệt bài
          </Button>
        </aside>
      </form>
      {confirmOpen ? (
        <DialogFrame labelledBy="post-fee-title">
          <h2 id="post-fee-title" className="text-xl font-bold text-text-primary">
            Xác nhận đăng bài
          </h2>
          {error ? <div className="mt-3"><Alert tone="error">{error}</Alert></div> : null}
          {type === 'gift' ? (
            <p className="mt-2 text-sm leading-6 text-text-muted">Bài cho tặng được đăng miễn phí.</p>
          ) : (
            <ul className="mt-2 list-disc space-y-1 pl-5 text-sm leading-6 text-text-muted">
              <li>Giữ {postFee === null ? '--' : postFee} Credit khi gửi duyệt.</li>
              <li>Chỉ trừ khi bài được duyệt lần đầu.</li>
              <li>Nhả lại toàn bộ nếu bài bị từ chối hoặc bạn gỡ bài trước khi duyệt.</li>
            </ul>
          )}
          <div className="mt-5 space-y-3 rounded-lg bg-surface-low p-4 text-sm">
            <SummaryRow
              label="Phí đăng bài"
              value={type === 'gift' ? 'Miễn phí' : postFee === null ? '--' : `${postFee} Credit`}
            />
          </div>
          <div className="mt-6 flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setConfirmOpen(false)} disabled={submitting || mediaUploading}>
              Hủy
            </Button>
            <Button
              onClick={confirmSubmit}
              disabled={submitting || mediaUploading || (type === 'trade' && postFee === null)}
            >
              {submitting ? 'Đang đăng...' : 'Xác nhận đăng bài'}
            </Button>
          </div>
        </DialogFrame>
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

function PostStep({ number, title, children }: { number: number; title: string; children: ReactNode }) {
  return (
    <section aria-labelledby={`post-step-${number}`} className="space-y-4 rounded-lg bg-white p-5 ring-1 ring-border/80 sm:p-6">
      <h2 id={`post-step-${number}`} className="flex items-center gap-2.5 text-base font-bold">
        <span className="grid size-7 place-items-center rounded-full bg-primary-soft text-sm text-primary">{number}</span>
        {title}
      </h2>
      {children}
    </section>
  );
}
