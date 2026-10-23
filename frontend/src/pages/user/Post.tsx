import { type FormEvent, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import { Alert, Button, Field, Icon, ItemImage, PageHeader, Select, TextArea } from '../../components/ui';
import { CONDITIONS } from '../../constants/domain';
import { conditionLabel } from '../../utils/formatting';
import type { ItemCondition, ItemType } from '../../types/domain';
import { fileToDataUrl } from '../../utils/files';
import { catalogApi, type CategoryAttributeResponse } from '../../services/catalogApi';
import { toAreaOptions, toCategoryIdByName, toCategoryNames, type AreaOption } from '../../services/mappers';
import { itemApi, type ApiCondition, type OperatingStatus } from '../../services/itemApi';
import { mediaApi } from '../../services/mediaApi';
import type { ApiError } from '../../services/http';
import { toBackendUserId } from '../../services/requestApi';
import { useWallet } from '../../app/useWallet';

type FormErrors = Partial<Record<'type' | 'title' | 'category' | 'condition' | 'district' | 'description' | 'tradeFor' | 'brand' | 'defectNote' | 'images' | 'defectImages' | 'operatingStatus', string>>;
type MediaUploadState = 'idle' | 'uploading' | 'error';

const SWAP_POST_SUCCESS =
  'Đăng bài trao đổi thành công. Bài đăng đã được gửi để xét duyệt.';
const GIVE_POST_SUCCESS =
  'Đăng bài cho tặng thành công. Bài đăng đã được gửi để xét duyệt.';
const PENDING_REVIEW_NOTICE = 'Bài đăng đang chờ quản trị viên duyệt.';
const WALLET_INSUFFICIENT_BALANCE = 'WALLET_INSUFFICIENT_BALANCE';
const INSUFFICIENT_CREDIT_MESSAGE = 'Số dư Credit khả dụng không đủ để đăng bài.';
const BACKEND_SESSION_REQUIRED = 'Vui lòng đăng nhập bằng tài khoản thật để đăng bài.';
const SKIP_MEDIA_UPLOAD_FOR_ITEM_TEST =
  import.meta.env.DEV && import.meta.env.VITE_POST_SKIP_MEDIA_UPLOAD === 'true';
const ITEM_IMAGES_MIN = 3;
const ITEM_IMAGES_MAX = 8;
// Giá trị mặc định của Backend; Backend vẫn quyết định mức phí thực tế khi tạo bài.
const POST_FEE_DISPLAY = 5;

const CONDITION_TO_API: Record<ItemCondition, ApiCondition> = {
  NEW: 'NEW',
  LIKE_NEW: 'LIKE_NEW',
  GOOD: 'GOOD',
  DEFECTIVE: 'DEFECTIVE',
  new: 'NEW',
  good: 'GOOD',
  used: 'DEFECTIVE',
};

export function Post() {
  const user = useAppSelector(selectCurrentUser)!;
  const { isApiAccount, wallet, loading: walletLoading, error: walletError, refresh: refreshWallet } = useWallet();
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
  const availableCredit = isApiAccount ? wallet?.available ?? null : user.availableCredit;
  const operatingStatusRequired = categoryAttributes.some(
    (attribute) =>
      attribute.required &&
      attribute.dataType === 'SELECT' &&
      ['NORMAL', 'PARTIAL', 'NOT_WORKING'].every((option) => attribute.options.includes(option)),
  );

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
    const categoryId = categoryIds.get(category);
    if (!isApiAccount || !categoryId) {
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
  }, [category, categoryIds, isApiAccount]);

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

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setErrorCode('');
    if (!validate()) return;
    if (!isApiAccount && user.reputationStars <= 0) {
      setError('Uy tín của bạn đang ở mức 0 nên hiện không thể đăng bài mới.');
      return;
    }
    if (!currentUserId) {
      setError(BACKEND_SESSION_REQUIRED);
      return;
    }
    if (type === 'trade' && isApiAccount && availableCredit === null) {
      setError(walletLoading ? 'Đang tải số dư Credit. Vui lòng chờ trong giây lát.' : walletError || 'Chưa thể xác minh số dư Credit.');
      return;
    }
    if (type === 'trade' && availableCredit !== null && availableCredit < POST_FEE_DISPLAY) {
      setErrorCode(WALLET_INSUFFICIENT_BALANCE);
      setError(INSUFFICIENT_CREDIT_MESSAGE);
      setConfirmOpen(false);
      return;
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
    if (type === 'trade' && isApiAccount && availableCredit === null) {
      setError(walletLoading ? 'Đang tải số dư Credit. Vui lòng chờ trong giây lát.' : walletError || 'Chưa thể xác minh số dư Credit.');
      setConfirmOpen(false);
      return;
    }
    if (type === 'trade' && availableCredit !== null && availableCredit < POST_FEE_DISPLAY) {
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
        : apiError.message || 'Không đăng được bài. Vui lòng kiểm tra thông tin và thử lại.');
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
            </span>
            {errorCode === WALLET_INSUFFICIENT_BALANCE ? (
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
          {isApiAccount && category && (attributesLoading || Boolean(attributesError) || categoryAttributes.length > 0) ? (
            <div className="rounded-lg bg-surface-low p-4 text-sm">
              <p className="font-semibold text-text-primary">Thông tin theo danh mục</p>
              {attributesLoading ? <p className="mt-2 text-text-muted">Đang tải thuộc tính...</p> : null}
              {attributesError ? <p className="mt-2 text-error">{attributesError}</p> : null}
              {!attributesLoading && !attributesError && categoryAttributes.length ? (
                <div className="mt-2 flex flex-wrap gap-2">
                  {categoryAttributes.map((attribute) => <span key={attribute.id} className="rounded-full bg-white px-3 py-1 text-xs ring-1 ring-border">{attribute.name}{attribute.required ? ' · bắt buộc' : ''}</span>)}
                </div>
              ) : null}
              {categoryAttributes.length ? <p className="mt-2 text-xs text-text-muted">Hãy nêu các thông tin trên trong phần mô tả để người xem dễ đánh giá món đồ.</p> : null}
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
          {type === 'trade' ? (
            <Field
              label="Bạn muốn đổi lấy gì?"
              value={tradeFor}
              onChange={(e) => setTradeFor(e.target.value)}
              placeholder="Ví dụ: Sách thiếu nhi hoặc cây để bàn"
              error={errors.tradeFor}
            />
          ) : null}
          <Field
            label="Thương hiệu (không bắt buộc)"
            value={brand}
            onChange={(event) => setBrand(event.target.value)}
            error={errors.brand}
          />
          {condition === 'DEFECTIVE' ? (
            <>
              <TextArea
                label="Mô tả lỗi"
                value={defectNote}
                onChange={(event) => setDefectNote(event.target.value)}
                error={errors.defectNote}
              />
              <label className="block rounded-lg border border-dashed border-border p-4 text-sm">
                <span className="font-semibold">Ảnh lỗi</span>
                <span className="mt-1 block text-xs text-text-muted">Chọn ít nhất một ảnh thể hiện rõ lỗi.</span>
                <input
                  className="mt-3 block w-full text-xs"
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
              </label>
            </>
          ) : null}
          {operatingStatusRequired ? (
            <div>
              <Select
                label="Trạng thái hoạt động"
                value={operatingStatus}
                onChange={(event) => setOperatingStatus(event.target.value as OperatingStatus | '')}
              >
                <option value="">Chọn trạng thái hoạt động</option>
                <option value="NORMAL">Hoạt động bình thường</option>
                <option value="PARTIAL">Hoạt động một phần</option>
                <option value="NOT_WORKING">Không hoạt động</option>
              </Select>
              {errors.operatingStatus ? <span className="mt-1 block text-xs text-error">{errors.operatingStatus}</span> : null}
            </div>
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
                if (files.some((file) => !file.type.startsWith('image/'))) {
                  setErrors((current) => ({ ...current, images: 'Vui lòng chỉ chọn tệp hình ảnh.' }));
                  setImageFiles([]);
                  setImages([]);
                  setUploadedImageUrls(new Map());
                  return;
                } else if (!SKIP_MEDIA_UPLOAD_FOR_ITEM_TEST && files.length < ITEM_IMAGES_MIN) {
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
                  <ItemImage
                    src={image}
                    alt={`Ảnh món đồ ${index + 1}`}
                    className="aspect-square rounded-md object-cover"
                  />
                  <div className="absolute bottom-1 left-1 right-1 flex justify-between gap-1">
                    <button type="button" className="rounded bg-white px-2 text-xs" aria-label={`Đưa ảnh ${index + 1} về trước`} disabled={index === 0 || submitting || mediaUploading} onClick={() => moveImage(index, -1)}>←</button>
                    <button type="button" className="rounded bg-white px-2 text-xs" aria-label={`Đưa ảnh ${index + 1} ra sau`} disabled={index === images.length - 1 || submitting || mediaUploading} onClick={() => moveImage(index, 1)}>→</button>
                  </div>
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
                        images: !SKIP_MEDIA_UPLOAD_FOR_ITEM_TEST && remainingCount < ITEM_IMAGES_MIN
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
          <div className="space-y-1 rounded-lg bg-white p-4 text-xs leading-5 text-text-secondary ring-1 ring-border/80">
            <p className="whitespace-nowrap text-text-primary"><strong>Cho tặng:</strong> miễn phí</p>
            <p className="whitespace-nowrap text-text-primary"><strong>Trao đổi:</strong> phí đăng bài {POST_FEE_DISPLAY} Credit</p>
          </div>
          <Button className="w-full" size="lg" disabled={submitting || mediaUploading || catalogLoading}>
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
                ? 'Bài cho tặng được đăng miễn phí.'
                : 'Phí đăng bài sẽ được trừ một lần sau khi bạn xác nhận.'}
            </p>
            <div className="mt-5 space-y-3 rounded-lg bg-surface-low p-4 text-sm">
              <SummaryRow
                label="Phí đăng bài"
                value={type === 'gift' ? 'Miễn phí' : `${POST_FEE_DISPLAY} Credit`}
              />
              <SummaryRow label="Số dư hiện tại" value={availableCredit === null ? '--' : `${availableCredit} Credit`} />
              <SummaryRow
                label="Số dư sau khi đăng"
                value={availableCredit === null ? '--' : `${type === 'gift' ? availableCredit : availableCredit - POST_FEE_DISPLAY} Credit`}
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
