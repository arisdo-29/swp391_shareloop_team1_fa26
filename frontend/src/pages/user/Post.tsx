import { type FormEvent, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import { Alert, Button, Field, Icon, PageHeader, Select, TextArea } from '../../components/ui';
import { CREDIT_TO_VND, POST_LISTING_FEE } from '../../utils/credit';
import type { ItemType } from '../../types/domain';
import { fileToDataUrl } from '../../utils/files';
import { catalogApi, type CategoryAttributeResponse } from '../../services/catalogApi';
import { toAreaOptions, toCategoryIdByName, toCategoryNames, type AreaOption } from '../../services/mappers';
import { itemApi, itemErrorMessage, type ApiCondition, type OperatingStatus } from '../../services/itemApi';
import { mediaApi } from '../../services/mediaApi';
import { toBackendUserId } from '../../services/requestApi';
import { useWallet } from '../../app/useWallet';

type FormErrors = Partial<Record<
  | 'type'
  | 'title'
  | 'category'
  | 'condition'
  | 'district'
  | 'description'
  | 'tradeFor'
  | 'images'
  | 'defectNote'
  | 'defectImages'
  | 'operatingStatus',
  string
>>;
type MediaUploadState = 'idle' | 'uploading' | 'error';

const POST_FEE_SUCCESS =
  'Đăng bài trao đổi thành công. Bài đăng đã được gửi để xét duyệt.';
const FREE_GIFT_SUCCESS =
  'Đăng bài cho tặng thành công. Bài đăng đã được gửi để xét duyệt.';
const PENDING_REVIEW_NOTICE = 'Bài đăng đang chờ quản trị viên duyệt.';
const INSUFFICIENT_CREDIT = 'Bạn không đủ Credit để đăng bài.';
const BACKEND_SESSION_REQUIRED = 'Vui lòng đăng nhập bằng tài khoản thật để đăng bài.';
const ITEM_IMAGES_MIN = 3;
const ITEM_IMAGES_MAX = 8;
const IMAGE_MAX_BYTES = 5 * 1024 * 1024;
const ACCEPTED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const OPERATING_STATUSES: OperatingStatus[] = ['NORMAL', 'PARTIAL', 'NOT_WORKING'];
const OPERATING_STATUS_LABELS: Record<OperatingStatus, string> = {
  NORMAL: 'Hoạt động bình thường',
  PARTIAL: 'Hoạt động một phần',
  NOT_WORKING: 'Không hoạt động',
};

const POST_CONDITIONS: Array<{ value: ApiCondition; label: string }> = [
  { value: 'NEW', label: 'Mới' },
  { value: 'LIKE_NEW', label: 'Như mới' },
  { value: 'GOOD', label: 'Đã qua sử dụng, còn tốt' },
  { value: 'DEFECTIVE', label: 'Có lỗi' },
];

export function Post() {
  const user = useAppSelector(selectCurrentUser)!;
  const { isApiAccount, wallet, loading: walletLoading, error: walletError } = useWallet();
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
  const [condition, setCondition] = useState<ApiCondition | ''>('');
  const [district, setDistrict] = useState('');
  const [description, setDescription] = useState('');
  const [tradeFor, setTradeFor] = useState('');
  const [defectNote, setDefectNote] = useState('');
  const [defectImageFiles, setDefectImageFiles] = useState<File[]>([]);
  const [defectImages, setDefectImages] = useState<string[]>([]);
  const [uploadedDefectImageUrls, setUploadedDefectImageUrls] = useState<Map<File, string>>(new Map());
  const [operatingStatus, setOperatingStatus] = useState<OperatingStatus | ''>('');
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
  const operatingStatusAttribute = categoryAttributes.find(
    (attribute) =>
      attribute.name === 'Trạng thái hoạt động'
      && attribute.dataType === 'SELECT'
      && attribute.required,
  );
  const operatingStatusOptions = operatingStatusAttribute?.options.filter(
    (option): option is OperatingStatus => OPERATING_STATUSES.includes(option as OperatingStatus),
  ) ?? [];

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
      .catch((error) => {
        if (!active) return;
        setCategories([]);
        setDistricts([]);
        setCategoryIds(new Map());
        setCatalogError(itemErrorMessage(error, 'Không tải được danh mục hoặc khu vực. Vui lòng thử lại.'));
      })
      .finally(() => {
        if (active) setCatalogLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const categoryId = categoryIds.get(category);
    setOperatingStatus('');
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
      .catch((error) => {
        if (!active) return;
        setCategoryAttributes([]);
        setAttributesError(itemErrorMessage(error, 'Không tải được thông tin bổ sung của danh mục.'));
      })
      .finally(() => { if (active) setAttributesLoading(false); });
    return () => { active = false; };
  }, [category, categoryIds]);

  const validate = () => {
    const nextErrors: FormErrors = {};
    if (!type) nextErrors.type = 'Vui lòng chọn hình thức.';
    if (!title.trim()) nextErrors.title = 'Vui lòng nhập tiêu đề.';
    else if (title.trim().length > 150) nextErrors.title = 'Tiêu đề tối đa 150 ký tự.';
    if (!category) nextErrors.category = 'Vui lòng chọn danh mục.';
    if (!condition) nextErrors.condition = 'Vui lòng chọn tình trạng món đồ.';
    if (!district) nextErrors.district = 'Vui lòng chọn khu vực.';
    if (!description.trim()) nextErrors.description = 'Vui lòng nhập mô tả.';
    if (type === 'trade' && !tradeFor.trim()) {
      nextErrors.tradeFor = 'Vui lòng nhập món đồ bạn muốn nhận khi trao đổi.';
    }
    if (condition === 'DEFECTIVE' && !defectNote.trim()) {
      nextErrors.defectNote = 'Vui lòng mô tả lỗi của món đồ.';
    }
    if (condition === 'DEFECTIVE' && defectImageFiles.length < 1) {
      nextErrors.defectImages = 'Vui lòng chọn ít nhất 1 ảnh thể hiện lỗi.';
    }
    if (operatingStatusAttribute && !operatingStatus) {
      nextErrors.operatingStatus = 'Vui lòng chọn trạng thái hoạt động.';
    }
    if (imageFiles.length < ITEM_IMAGES_MIN) {
      nextErrors.images = `Vui lòng chọn ít nhất ${ITEM_IMAGES_MIN} ảnh.`;
    } else if (imageFiles.length > ITEM_IMAGES_MAX) {
      nextErrors.images = `Chỉ được chọn tối đa ${ITEM_IMAGES_MAX} ảnh.`;
    }
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
    if (!isApiAccount && type === 'trade' && availableCredit === null) {
      setError(walletLoading ? 'Đang tải số dư Credit. Vui lòng chờ trong giây lát.' : walletError || 'Chưa thể xác minh số dư Credit.');
      return;
    }
    if (!isApiAccount && type === 'trade' && availableCredit !== null && availableCredit < POST_LISTING_FEE) {
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
    if (!isApiAccount && type === 'trade' && availableCredit === null) {
      setError(walletLoading ? 'Đang tải số dư Credit. Vui lòng chờ trong giây lát.' : walletError || 'Chưa thể xác minh số dư Credit.');
      setConfirmOpen(false);
      return;
    }
    if (!isApiAccount && type === 'trade' && availableCredit !== null && availableCredit < POST_LISTING_FEE) {
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
      const uploadedDefectUrls = new Map(uploadedDefectImageUrls);
      if (imageFiles.length) {
        setMediaUploadState('uploading');
        try {
          for (const file of imageFiles) {
            if (uploadedUrls.has(file)) continue;
            const media = await mediaApi.upload(file);
            const url = media.url;
            if (!url) throw new Error('Media API did not return an image URL.');
            uploadedUrls.set(file, url);
            setUploadedImageUrls(new Map(uploadedUrls));
          }
        } catch (uploadError) {
          setMediaUploadState('error');
          setMediaUploadError(itemErrorMessage(uploadError, 'Không tải được ảnh lên. Các ảnh đã tải thành công sẽ được giữ lại khi thử lại.'));
          setConfirmOpen(false);
          return;
        }
      }
      if (condition === 'DEFECTIVE' && defectImageFiles.length) {
        setMediaUploadState('uploading');
        try {
          for (const file of defectImageFiles) {
            if (uploadedDefectUrls.has(file)) continue;
            const media = await mediaApi.upload(file);
            const url = media.url;
            uploadedDefectUrls.set(file, url);
            setUploadedDefectImageUrls(new Map(uploadedDefectUrls));
          }
        } catch (uploadError) {
          setMediaUploadState('error');
          setMediaUploadError(itemErrorMessage(uploadError, 'Không tải được ảnh lỗi lên. Các ảnh đã tải thành công sẽ được giữ lại khi thử lại.'));
          setConfirmOpen(false);
          return;
        }
      }
      setMediaUploadState('idle');
      const itemImages = imageFiles.map((file) => uploadedUrls.get(file)).filter((url): url is string => Boolean(url));
      const itemDefectImages = defectImageFiles
        .map((file) => uploadedDefectUrls.get(file))
        .filter((url): url is string => Boolean(url));
      const item = await itemApi.create(
        {
          categoryId,
          areaId,
          offerType: type === 'gift' ? 'GIVE' : 'SWAP',
          title: title.trim(),
          description: description.trim(),
          desiredItem: type === 'trade' ? tradeFor.trim() || null : null,
          condition,
          defectNote: condition === 'DEFECTIVE' ? defectNote.trim() : null,
          brand: null,
          images: itemImages,
          defectImages: condition === 'DEFECTIVE' ? itemDefectImages : null,
          operatingStatus: operatingStatusAttribute ? operatingStatus || null : null,
        },
      );
      const baseNotice = type === 'gift' ? FREE_GIFT_SUCCESS : POST_FEE_SUCCESS;
      const reviewNotice = item.status === 'PENDING_REVIEW' ? ` ${PENDING_REVIEW_NOTICE}` : '';
      navigate('/activities', { state: { notice: `${baseNotice}${reviewNotice} Mã bài: #${item.id}.` } });
    } catch (submitError) {
      setError(itemErrorMessage(submitError, 'Không đăng được bài. Vui lòng kiểm tra thông tin và thử lại.'));
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
            maxLength={150}
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
              onChange={(e) => {
                const nextCondition = e.target.value as ApiCondition | '';
                setCondition(nextCondition);
                setErrors((current) => ({ ...current, condition: undefined }));
                if (nextCondition !== 'DEFECTIVE') {
                  setDefectNote('');
                  setDefectImageFiles([]);
                  setDefectImages([]);
                  setUploadedDefectImageUrls(new Map());
                  setErrors((current) => ({
                    ...current,
                    defectNote: undefined,
                    defectImages: undefined,
                  }));
                }
              }}
            >
              <option value="">Chọn tình trạng</option>
              {POST_CONDITIONS.map((entry) => (
                <option key={entry.value} value={entry.value}>
                  {entry.label}
                </option>
              ))}
            </Select>
          </div>
          <div className="grid gap-1 sm:grid-cols-2">
            {errors.category ? <span className="hidden text-xs text-error sm:block">{errors.category}</span> : <span />}
            {errors.condition ? <span className="text-xs text-error">{errors.condition}</span> : null}
          </div>
          {isApiAccount && category ? (
            <div className="rounded-lg bg-surface-low p-4 text-sm">
              <p className="font-semibold text-text-primary">Thông tin theo danh mục</p>
              {attributesLoading ? <p className="mt-2 text-text-muted">Đang tải thuộc tính...</p> : null}
              {attributesError ? <p className="mt-2 text-error">{attributesError}</p> : null}
              {!attributesLoading && !attributesError && categoryAttributes.length ? (
                <div className="mt-2 flex flex-wrap gap-2">
                  {categoryAttributes.map((attribute) => <span key={attribute.id} className="rounded-full bg-white px-3 py-1 text-xs ring-1 ring-border">{attribute.name}{attribute.required ? ' · bắt buộc' : ''}</span>)}
                </div>
              ) : null}
              {!attributesLoading && !attributesError && !categoryAttributes.length ? <p className="mt-2 text-text-muted">Danh mục này chưa có thuộc tính bổ sung.</p> : null}
              {categoryAttributes.length ? <p className="mt-2 text-xs text-text-muted">Hãy nêu các thông tin trên trong phần mô tả để người xem dễ đánh giá món đồ.</p> : null}
            </div>
          ) : null}
          {operatingStatusAttribute ? (
            <div>
              <Select
                label="Trạng thái hoạt động"
                value={operatingStatus}
                onChange={(event) => {
                  setOperatingStatus(event.target.value as OperatingStatus | '');
                  setErrors((current) => ({ ...current, operatingStatus: undefined }));
                }}
              >
                <option value="">Chọn trạng thái hoạt động</option>
                {operatingStatusOptions.map((status) => (
                  <option key={status} value={status}>{OPERATING_STATUS_LABELS[status]}</option>
                ))}
              </Select>
              {errors.operatingStatus ? <span className="mt-1.5 block text-xs text-error">{errors.operatingStatus}</span> : null}
            </div>
          ) : null}
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
          {condition === 'DEFECTIVE' ? (
            <div className="space-y-4 rounded-lg border border-border bg-surface-low p-4">
              <TextArea
                label="Mô tả lỗi"
                value={defectNote}
                onChange={(event) => {
                  setDefectNote(event.target.value);
                  setErrors((current) => ({ ...current, defectNote: undefined }));
                }}
                placeholder="Mô tả rõ vị trí và mức độ lỗi của món đồ..."
                error={errors.defectNote}
              />
              <label className={`block rounded-lg border border-dashed bg-white p-4 text-center transition hover:border-primary ${errors.defectImages ? 'border-error' : 'border-border'}`}>
                <Icon name="image" className="mx-auto size-7 text-primary" />
                <strong className="mt-2 block text-sm">Thêm ảnh thể hiện lỗi</strong>
                <span className="mt-1 block text-xs text-text-muted">Ít nhất 1 ảnh khi tình trạng là có lỗi</span>
                <input
                  className="hidden"
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  multiple
                  disabled={submitting || mediaUploading}
                  onChange={async (event) => {
                    const files = Array.from(event.target.files ?? []);
                    setMediaUploadState('idle');
                    setMediaUploadError('');
                    if (files.some((file) => !ACCEPTED_IMAGE_TYPES.has(file.type))) {
                      setDefectImageFiles([]);
                      setDefectImages([]);
                      setUploadedDefectImageUrls(new Map());
                      setErrors((current) => ({ ...current, defectImages: 'Ảnh phải có định dạng JPG, PNG hoặc WebP.' }));
                      return;
                    }
                    if (files.some((file) => file.size > IMAGE_MAX_BYTES)) {
                      setDefectImageFiles([]);
                      setDefectImages([]);
                      setUploadedDefectImageUrls(new Map());
                      setErrors((current) => ({ ...current, defectImages: 'Mỗi ảnh phải có dung lượng không quá 5 MB.' }));
                      return;
                    }
                    try {
                      const previews = await Promise.all(files.map(fileToDataUrl));
                      setDefectImageFiles(files);
                      setDefectImages(previews);
                      setUploadedDefectImageUrls((current) =>
                        new Map(files.flatMap((file) => {
                          const url = current.get(file);
                          return url ? [[file, url] as const] : [];
                        })),
                      );
                      setErrors((current) => ({
                        ...current,
                        defectImages: files.length < 1
                          ? 'Vui lòng chọn ít nhất 1 ảnh thể hiện lỗi.'
                          : undefined,
                      }));
                    } catch {
                      setDefectImageFiles([]);
                      setDefectImages([]);
                      setUploadedDefectImageUrls(new Map());
                      setMediaUploadState('error');
                      setMediaUploadError('Không đọc được ảnh lỗi đã chọn. Vui lòng chọn lại.');
                    }
                  }}
                />
              </label>
              {errors.defectImages ? <span className="block text-xs text-error">{errors.defectImages}</span> : null}
              {defectImages.length ? (
                <div className="grid grid-cols-3 gap-2">
                  {defectImages.map((image, index) => (
                    <div key={image.slice(-16) + index} className="relative">
                      <img src={image} alt={`Ảnh lỗi ${index + 1}`} className="aspect-square rounded-md object-cover" />
                      <button
                        type="button"
                        aria-label="Xóa ảnh lỗi"
                        disabled={submitting || mediaUploading}
                        onClick={() => {
                          const removedFile = defectImageFiles[index];
                          setDefectImageFiles(defectImageFiles.filter((_, itemIndex) => itemIndex !== index));
                          setDefectImages(defectImages.filter((_, itemIndex) => itemIndex !== index));
                          setUploadedDefectImageUrls((current) => {
                            const next = new Map(current);
                            if (removedFile) next.delete(removedFile);
                            return next;
                          });
                          const remainingCount = defectImageFiles.length - 1;
                          setErrors((current) => ({
                            ...current,
                            defectImages: remainingCount < 1
                              ? 'Vui lòng chọn ít nhất 1 ảnh thể hiện lỗi.'
                              : undefined,
                          }));
                        }}
                        className="absolute right-1 top-1 grid size-6 place-items-center rounded-full bg-white text-error shadow-sm"
                      >
                        <Icon name="close" className="size-3" />
                      </button>
                    </div>
                  ))}
                </div>
              ) : null}
            </div>
          ) : null}
          {type === 'trade' ? (
            <Field
              label="Bạn muốn đổi lấy gì?"
              value={tradeFor}
              onChange={(e) => setTradeFor(e.target.value)}
              placeholder="Ví dụ: Sách thiếu nhi hoặc cây để bàn"
              error={errors.tradeFor}
            />
          ) : null}
        </section>
        <aside className="space-y-4">
          <label className={`flex aspect-[4/3] w-full flex-col items-center justify-center rounded-lg border border-dashed bg-white text-center transition hover:border-primary ${errors.images ? 'border-error' : 'border-border'}`}>
            <Icon name="image" className="size-8 text-primary" />
            <strong className="mt-3 text-sm">Thêm hình ảnh</strong>
            <span className="mt-1 text-xs text-text-muted">
              Từ {ITEM_IMAGES_MIN} đến {ITEM_IMAGES_MAX} ảnh · thứ tự chọn là thứ tự hiển thị
            </span>
            <input
              className="hidden"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              multiple
              disabled={submitting || mediaUploading}
              onChange={async (event) => {
                const selectedFiles = Array.from(event.target.files ?? []);
                setMediaUploadState('idle');
                setMediaUploadError('');
                if (selectedFiles.length > ITEM_IMAGES_MAX) {
                  setErrors((current) => ({ ...current, images: `Chỉ được chọn tối đa ${ITEM_IMAGES_MAX} ảnh.` }));
                  setImageFiles([]);
                  setImages([]);
                  setUploadedImageUrls(new Map());
                  event.currentTarget.value = '';
                  return;
                }
                const files = selectedFiles;
                if (files.some((file) => !ACCEPTED_IMAGE_TYPES.has(file.type))) {
                  setErrors((current) => ({ ...current, images: 'Ảnh phải có định dạng JPG, PNG hoặc WebP.' }));
                  setImageFiles([]);
                  setImages([]);
                  setUploadedImageUrls(new Map());
                  return;
                } else if (files.some((file) => file.size > IMAGE_MAX_BYTES)) {
                  setErrors((current) => ({ ...current, images: 'Mỗi ảnh phải có dung lượng không quá 5 MB.' }));
                  setImageFiles([]);
                  setImages([]);
                  setUploadedImageUrls(new Map());
                  return;
                } else if (files.length < ITEM_IMAGES_MIN) {
                  setErrors((current) => ({ ...current, images: `Vui lòng chọn ít nhất ${ITEM_IMAGES_MIN} ảnh.` }));
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
                      const remainingCount = imageFiles.length - 1;
                      setErrors((current) => ({
                        ...current,
                        images: remainingCount < ITEM_IMAGES_MIN
                          ? `Vui lòng chọn ít nhất ${ITEM_IMAGES_MIN} ảnh.`
                          : undefined,
                      }));
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
            ) : isApiAccount ? (
              <>
                <strong className="text-text-primary">Phí đăng bài theo cấu hình Backend</strong>
                <p className="mt-1">Backend sẽ kiểm tra và áp dụng phí khi tạo bài.</p>
              </>
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
                value={type === 'gift' ? 'Miễn phí' : isApiAccount ? 'Theo cấu hình Backend' : `${POST_LISTING_FEE} Credit`}
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
