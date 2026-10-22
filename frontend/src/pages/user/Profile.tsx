import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { actions, selectCurrentUser, selectData } from '../../app/store';
import {
  Avatar,
  Alert,
  Button,
  Field,
  Icon,
  PageHeader,
  RankBadge,
  Select,
  Stat,
} from '../../components/ui';
import { formatCredit } from '../../utils/formatting';
import { fileToDataUrl } from '../../utils/files';
import { ChangePasswordForm } from '../../components/ChangePasswordForm';
import { OtpInput } from '../../components/OtpInput';
import { validEmail, validVietnamPhone } from '../../utils/passwordSecurity';
import { useWallet } from '../../app/useWallet';
import { userApi } from '../../services/userApi';
import { catalogApi } from '../../services/catalogApi';
import { mediaApi } from '../../services/mediaApi';
import { toAreaOptions, type AreaOption } from '../../services/mappers';
import type { ApiError } from '../../services/http';
import { toBackendUserId } from '../../services/requestApi';
import { resolveMediaUrl } from '../../services/mediaUrl';
import { reputationApi, type MyReputation } from '../../services/reputationApi';
import { createOtpValue } from '../../utils/otp';

const reputationRankLabel: Record<MyReputation['rank'], string> = {
  BRONZE: 'Đồng',
  SILVER: 'Bạc',
  GOLD: 'Vàng',
  DIAMOND: 'Kim cương',
};

function reputationErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if (apiError.code === 'USER_NOT_FOUND' || apiError.status === 404) {
    return 'Không tìm thấy thông tin uy tín của tài khoản.';
  }
  if (apiError.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (apiError.status === 0) return 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';
  return 'Không tải được thông tin uy tín. Vui lòng thử lại.';
}

function profileLoadErrorMessage(error: unknown, source: 'profile' | 'areas') {
  const apiError = error as Partial<ApiError>;
  if (apiError.code === 'AUTH_ACCOUNT_LOCKED') {
    return 'Tài khoản đã bị khoá. Vui lòng liên hệ quản trị viên.';
  }
  if (apiError.code === 'USER_NOT_FOUND') return 'Không tìm thấy hồ sơ tài khoản.';
  if (apiError.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (apiError.status === 403) return 'Tài khoản không có quyền truy cập hồ sơ.';
  if (apiError.status === 404 && source === 'profile') return 'Không tìm thấy hồ sơ tài khoản.';
  if (apiError.status === 0) return 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';
  if (source === 'areas') return 'Không tải được danh sách khu vực mặc định. Vui lòng thử lại.';
  if (apiError.status && apiError.status >= 500) {
    return 'Máy chủ chưa thể tải hồ sơ lúc này. Vui lòng thử lại sau.';
  }
  return 'Không tải được hồ sơ. Vui lòng thử lại.';
}

function profileSaveErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  const messages: Record<string, string> = {
    VALIDATION_FAILED: 'Thông tin hồ sơ chưa hợp lệ. Vui lòng kiểm tra lại.',
    USER_AREA_INVALID: 'Khu vực đã chọn không tồn tại hoặc đã ngừng hoạt động.',
    MEDIA_NOT_FOUND: 'Không tìm thấy ảnh đại diện đã chọn.',
    MEDIA_NOT_OWNER: 'Bạn không có quyền sử dụng ảnh đại diện này.',
    MEDIA_NOT_UPLOADED: 'Ảnh đại diện này đã được sử dụng hoặc không còn hợp lệ.',
    USER_NOT_FOUND: 'Không tìm thấy hồ sơ tài khoản.',
    AUTH_ACCOUNT_LOCKED: 'Tài khoản đã bị khoá. Vui lòng liên hệ quản trị viên.',
  };
  if (apiError.code && messages[apiError.code]) return messages[apiError.code];
  if (apiError.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (apiError.status === 0) return 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';
  if (apiError.status && apiError.status >= 500) {
    return 'Máy chủ chưa thể cập nhật hồ sơ lúc này. Vui lòng thử lại sau.';
  }
  return 'Không cập nhật được hồ sơ. Vui lòng thử lại.';
}

const PHONE_PATTERN = /^(0|\+84)\d{9}$/;
const OTP_PATTERN = /^\d{6}$/;

function formatCountdown(seconds: number) {
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes}:${String(remainingSeconds).padStart(2, '0')}`;
}

function maskEmail(email: string) {
  const [localPart, domain] = email.split('@');
  if (!localPart || !domain) return email;
  const visibleLength = Math.min(2, localPart.length);
  return `${localPart.slice(0, visibleLength)}${'*'.repeat(Math.max(3, localPart.length - visibleLength))}@${domain}`;
}

function phoneChangeErrorMessage(error: ApiError) {
  const code = error.code?.toUpperCase();
  const remainingAttempts = error.details?.remainingAttempts;
  const retryAfterDays = error.details?.retryAfterDays;
  if (code?.includes('PHONE_ALREADY') || code?.includes('PHONE_EXISTS')) {
    return 'Số điện thoại này đã được sử dụng.';
  }
  if (code === 'USER_PHONE_SAME_AS_CURRENT') {
    return 'Số điện thoại mới phải khác số hiện tại.';
  }
  if (code === 'USER_PHONE_CHANGE_COOLDOWN') {
    return typeof retryAfterDays === 'number'
      ? `Bạn có thể đổi số điện thoại sau ${retryAfterDays} ngày nữa.`
      : 'Bạn chưa thể đổi số điện thoại do chưa hết thời gian chờ 30 ngày.';
  }
  if (code === 'USER_PHONE_CHANGE_ACTIVE_DELIVERY') {
    return 'Không thể đổi số điện thoại khi đang có giao dịch chưa hoàn tất.';
  }
  if (code?.includes('PHONE_INVALID') || (code?.includes('VALIDATION') && error.field === 'newPhone')) {
    return 'Số điện thoại không hợp lệ. Vui lòng nhập dạng 0xxxxxxxxx hoặc +84xxxxxxxxx.';
  }
  if (code?.includes('OTP_EXPIRED')) {
    return 'Mã OTP đã hết hạn. Vui lòng gửi lại mã mới.';
  }
  if (code?.includes('OTP_TOO_MANY') || code?.includes('OTP_ATTEMPT') || code === 'AUTH_OTP_MAX_ATTEMPTS') {
    return 'Bạn đã nhập sai OTP quá nhiều lần. Vui lòng gửi lại mã mới.';
  }
  if (code?.includes('OTP_RESEND') || code?.includes('OTP_COOLDOWN') || error.status === 429) {
    return 'Bạn gửi mã quá nhanh. Vui lòng chờ hết thời gian đếm ngược.';
  }
  if (code?.includes('OTP_INVALID') || (code?.includes('VALIDATION') && error.field === 'otp')) {
    return typeof remainingAttempts === 'number'
      ? `Mã OTP không đúng. Bạn còn ${remainingAttempts} lần thử.`
      : 'Mã OTP không đúng. Vui lòng kiểm tra lại.';
  }
  if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (error.status === 403) return 'Tài khoản không được phép đổi số điện thoại.';
  if (error.status === 400 || error.status === 422) {
    return 'Thông tin đổi số điện thoại không hợp lệ. Vui lòng kiểm tra và thử lại.';
  }
  return 'Không thể đổi số điện thoại lúc này. Vui lòng thử lại.';
}

export function Profile() {
  const user = useAppSelector(selectCurrentUser)!;
  const isAdmin = user.role === 'admin';
  const data = useAppSelector(selectData);
  const dispatch = useAppDispatch();
  const { wallet } = useWallet();
  const isApiAccount = toBackendUserId(user.id) !== null;
  const [tab, setTab] = useState<'profile' | 'credit' | 'reputation' | 'password'>('profile');
  const [avatarUrl, setAvatarUrl] = useState(user.avatarUrl);
  const [name, setName] = useState(user.name);
  const [email, setEmail] = useState(user.email);
  const [phone, setPhone] = useState(user.phone);
  const [district, setDistrict] = useState(user.district);
  const [apiAreaId, setApiAreaId] = useState('');
  const [apiAvatarMediaId, setApiAvatarMediaId] = useState<number | null>(null);
  const [apiAreas, setApiAreas] = useState<AreaOption[]>([]);
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [profileLoading, setProfileLoading] = useState(isApiAccount);
  const [profileSaving, setProfileSaving] = useState(false);
  const [profileReloadKey, setProfileReloadKey] = useState(0);
  const [profileLoadError, setProfileLoadError] = useState('');
  const [profileSaveError, setProfileSaveError] = useState('');
  const [profileNotice, setProfileNotice] = useState('');
  const [phoneChangeOpen, setPhoneChangeOpen] = useState(false);
  const [newPhone, setNewPhone] = useState('');
  const [phoneOtp, setPhoneOtp] = useState(() => createOtpValue());
  const [phoneOtpEmail, setPhoneOtpEmail] = useState('');
  const [phoneOtpExpiresIn, setPhoneOtpExpiresIn] = useState<number | null>(null);
  const [phoneResendCooldown, setPhoneResendCooldown] = useState(0);
  const [phoneChangeSending, setPhoneChangeSending] = useState(false);
  const [phoneChangeConfirming, setPhoneChangeConfirming] = useState(false);
  const [phoneChangeError, setPhoneChangeError] = useState('');
  const [phoneChangeNotice, setPhoneChangeNotice] = useState('');
  const [reputation, setReputation] = useState<MyReputation | null>(null);
  const [reputationPage, setReputationPage] = useState(0);
  const [reputationLoading, setReputationLoading] = useState(false);
  const [reputationError, setReputationError] = useState('');
  const [reputationReloadKey, setReputationReloadKey] = useState(0);
  const history = data.creditHistory.filter((h) => h.userId === user.id);
  const topups = data.topups.filter((t) => t.userId === user.id);
  const districts = data.districts
    .filter((entry) => entry.status === 'active')
    .map((entry) => entry.name);

  useEffect(() => {
    if (!isApiAccount) return;
    let active = true;
    setProfileLoading(true);
    setProfileLoadError('');
    Promise.allSettled([userApi.getMe(), catalogApi.areas()])
      .then(([profileResult, areasResult]) => {
        if (!active) return;
        const options = areasResult.status === 'fulfilled' ? toAreaOptions(areasResult.value) : [];
        if (areasResult.status === 'fulfilled') setApiAreas(options);
        if (profileResult.status === 'rejected') {
          setProfileLoadError(profileLoadErrorMessage(profileResult.reason, 'profile'));
          return;
        }
        const profile = profileResult.value;
        const resolvedAvatarUrl = profile.avatarUrl ? resolveMediaUrl(profile.avatarUrl) : undefined;
        const districtLabel = options.find((area) => area.id === profile.areaId)?.label ?? '';
        setName(profile.fullName);
        setEmail(profile.email);
        setPhone(profile.phone ?? '');
        setApiAreaId(profile.areaId ? String(profile.areaId) : '');
        setApiAvatarMediaId(profile.avatarMediaId);
        setAvatarUrl(resolvedAvatarUrl);
        dispatch(actions.updateProfile({
          userId: user.id,
          name: profile.fullName,
          email: profile.email,
          phone: profile.phone ?? '',
          district: districtLabel,
          avatarUrl: resolvedAvatarUrl,
        }));
        if (areasResult.status === 'rejected') {
          setProfileLoadError(profileLoadErrorMessage(areasResult.reason, 'areas'));
        }
      })
      .finally(() => {
        if (active) setProfileLoading(false);
      });
    return () => {
      active = false;
    };
  }, [dispatch, isApiAccount, profileReloadKey, user.id]);

  useEffect(() => {
    if (phoneOtpExpiresIn === null || phoneOtpExpiresIn <= 0) return;
    const timer = window.setInterval(() => {
      setPhoneOtpExpiresIn((seconds) => seconds === null ? null : Math.max(0, seconds - 1));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [phoneOtpExpiresIn]);

  useEffect(() => {
    if (phoneResendCooldown <= 0) return;
    const timer = window.setInterval(() => {
      setPhoneResendCooldown((seconds) => Math.max(0, seconds - 1));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [phoneResendCooldown]);

  useEffect(() => {
    if (!isApiAccount || tab !== 'reputation') return;
    let active = true;
    setReputationLoading(true);
    setReputationError('');
    reputationApi.getMyReputation(reputationPage, 20)
      .then((response) => {
        if (active) setReputation(response);
      })
      .catch((error) => {
        if (active) setReputationError(reputationErrorMessage(error));
      })
      .finally(() => {
        if (active) setReputationLoading(false);
      });
    return () => {
      active = false;
    };
  }, [isApiAccount, reputationPage, reputationReloadKey, tab]);

  const closePhoneChange = () => {
    setPhoneChangeOpen(false);
    setNewPhone('');
    setPhoneOtp(createOtpValue());
    setPhoneOtpEmail('');
    setPhoneOtpExpiresIn(null);
    setPhoneResendCooldown(0);
    setPhoneChangeError('');
    setPhoneChangeNotice('');
  };

  const sendPhoneOtp = async () => {
    if (!isApiAccount || phoneChangeSending || phoneChangeConfirming) return;
    const normalizedPhone = newPhone.trim();
    setPhoneChangeError('');
    setPhoneChangeNotice('');
    if (!normalizedPhone) {
      setPhoneChangeError('Vui lòng nhập số điện thoại mới.');
      return;
    }
    if (!PHONE_PATTERN.test(normalizedPhone)) {
      setPhoneChangeError('Số điện thoại không hợp lệ. Vui lòng nhập dạng 0xxxxxxxxx hoặc +84xxxxxxxxx.');
      return;
    }
    if (normalizedPhone === phone) {
      setPhoneChangeError('Số điện thoại mới phải khác số hiện tại.');
      return;
    }

    setPhoneChangeSending(true);
    try {
      const response = await userApi.sendChangePhoneOtp({ newPhone: normalizedPhone });
      if (
        !response.email?.trim()
        || !Number.isFinite(response.otpExpiresInSeconds)
        || response.otpExpiresInSeconds <= 0
        || !Number.isFinite(response.resendCooldownSeconds)
        || response.resendCooldownSeconds < 0
      ) {
        setPhoneChangeError('Phản hồi gửi OTP không hợp lệ. Vui lòng thử lại.');
        return;
      }
      setNewPhone(normalizedPhone);
      setPhoneOtp(createOtpValue());
      setPhoneOtpEmail(response.email);
      setPhoneOtpExpiresIn(Math.floor(response.otpExpiresInSeconds));
      setPhoneResendCooldown(Math.floor(response.resendCooldownSeconds));
      setPhoneChangeNotice('Mã OTP đã được gửi qua email.');
    } catch (error) {
      const apiError = error as ApiError;
      const retryAfterSeconds = apiError.details?.retryAfterSeconds;
      if (typeof retryAfterSeconds === 'number' && retryAfterSeconds > 0) {
        setPhoneResendCooldown(Math.floor(retryAfterSeconds));
      }
      setPhoneChangeError(phoneChangeErrorMessage(apiError));
    } finally {
      setPhoneChangeSending(false);
    }
  };

  const confirmPhoneChange = async () => {
    if (!isApiAccount || phoneChangeSending || phoneChangeConfirming) return;
    const normalizedPhone = newPhone.trim();
    setPhoneChangeError('');
    setPhoneChangeNotice('');
    if (!PHONE_PATTERN.test(normalizedPhone)) {
      setPhoneChangeError('Số điện thoại không hợp lệ. Vui lòng nhập dạng 0xxxxxxxxx hoặc +84xxxxxxxxx.');
      return;
    }
    if (!phoneOtpEmail) {
      setPhoneChangeError('Vui lòng gửi mã OTP trước khi xác nhận.');
      return;
    }
    if (phoneOtpExpiresIn === 0) {
      setPhoneChangeError('Mã OTP đã hết hạn. Vui lòng gửi lại mã mới.');
      return;
    }
    const otpCode = phoneOtp.join('');
    if (!OTP_PATTERN.test(otpCode)) {
      setPhoneChangeError('Mã OTP phải gồm đúng 6 chữ số.');
      return;
    }

    setPhoneChangeConfirming(true);
    try {
      const profile = await userApi.changePhone({ newPhone: normalizedPhone, otp: otpCode });
      const currentUserId = toBackendUserId(user.id);
      if (
        !Number.isSafeInteger(profile.id)
        || profile.id <= 0
        || profile.id !== currentUserId
        || !profile.phone
      ) {
        setPhoneChangeError('Phản hồi đổi số điện thoại không hợp lệ. Vui lòng tải lại hồ sơ.');
        return;
      }
      const districtLabel = apiAreas.find((area) => area.id === profile.areaId)?.label ?? '';
      const nextAvatarUrl = profile.avatarUrl ? resolveMediaUrl(profile.avatarUrl) : undefined;
      setName(profile.fullName);
      setEmail(profile.email);
      setPhone(profile.phone);
      setApiAreaId(profile.areaId ? String(profile.areaId) : '');
      setApiAvatarMediaId(profile.avatarMediaId);
      setAvatarUrl(nextAvatarUrl);
      dispatch(actions.updateProfile({
        userId: user.id,
        name: profile.fullName,
        email: profile.email,
        phone: profile.phone,
        district: districtLabel,
        avatarUrl: nextAvatarUrl,
      }));
      closePhoneChange();
      setProfileNotice('Đổi số điện thoại thành công.');
    } catch (error) {
      setPhoneChangeError(phoneChangeErrorMessage(error as ApiError));
    } finally {
      setPhoneChangeConfirming(false);
    }
  };

  const save = async () => {
    setProfileSaveError('');
    setProfileNotice('');
    if (!name.trim()) {
      setProfileSaveError('Vui lòng nhập họ và tên.');
      return;
    }
    if (!isApiAccount && !validEmail(email)) {
      setProfileSaveError('Email không đúng định dạng.');
      return;
    }
    if (!isApiAccount && !validVietnamPhone(phone)) {
      setProfileSaveError('Số điện thoại Việt Nam không hợp lệ.');
      return;
    }
    if (isApiAccount) {
      setProfileSaving(true);
      try {
        const uploaded = avatarFile ? await mediaApi.upload(avatarFile) : null;
        const parsedAreaId = Number(apiAreaId);
        const profile = await userApi.updateMe({
          fullName: name.trim(),
          areaId: Number.isSafeInteger(parsedAreaId) && parsedAreaId > 0 ? parsedAreaId : null,
          avatarMediaId: uploaded?.mediaId ?? apiAvatarMediaId,
        });
        const districtLabel = apiAreas.find((area) => area.id === profile.areaId)?.label ?? '';
        const nextAvatarUrl = resolveMediaUrl(profile.avatarUrl) || undefined;
        setAvatarUrl(nextAvatarUrl);
        setApiAvatarMediaId(profile.avatarMediaId);
        setAvatarFile(null);
        dispatch(actions.updateProfile({
          userId: user.id,
          name: profile.fullName,
          email: profile.email,
          phone: profile.phone ?? '',
          district: districtLabel,
          avatarUrl: nextAvatarUrl,
        }));
        setProfileNotice('Cập nhật hồ sơ thành công.');
      } catch (error) {
        setProfileSaveError(profileSaveErrorMessage(error));
      } finally {
        setProfileSaving(false);
      }
      return;
    }
    dispatch(actions.updateProfile({ userId: user.id, name: name.trim(), email: email.trim().toLowerCase(), phone: phone.trim(), district, avatarUrl }));
    setProfileNotice('Cập nhật hồ sơ thành công.');
  };
  return (
    <div className="page-shell">
      <PageHeader
        title="Tài khoản của bạn"
      />
      <div className="grid gap-7 lg:grid-cols-[240px_1fr]">
        <aside>
          <div className="flex items-center gap-3 pb-5">
            <Avatar
              user={{
                ...user,
                name,
                avatarInitials: name.trim().split(/\s+/).map((part) => part[0]).slice(-2).join('').toUpperCase(),
                avatarUrl,
              }}
              size="lg"
            />
            <div className="min-w-0">
              <h2 className="truncate font-bold">{name}</h2>
              <div className="mt-1">
                <RankBadge rank={isAdmin ? 'Quản trị hệ thống' : user.rank} />
              </div>
            </div>
          </div>
          <nav className="space-y-1 border-t border-border pt-4">
            {(
              [
                ['profile', 'Hồ sơ', 'user'],
                ['credit', 'Credit', 'wallet'],
                ['reputation', 'Uy tín & Hạng', 'star'],
              ] as const
            ).map(([id, label, icon]) =>
              id === 'credit' && !isAdmin ? (
                <Link
                  key={id}
                  to="/credit"
                  className="flex w-full items-center gap-3 rounded-md px-3 py-2.5 text-left text-sm font-semibold text-text-secondary hover:bg-surface-low"
                >
                  <Icon name={icon} className="size-5" />
                  {label}
                </Link>
              ) : id === 'credit' ? null : isAdmin && id === 'reputation' ? null : (
                <button
                  key={id}
                  onClick={() => setTab(id)}
                  className={`flex w-full items-center gap-3 rounded-md px-3 py-2.5 text-left text-sm font-semibold ${tab === id ? 'bg-primary-faint text-primary' : 'text-text-secondary hover:bg-surface-low'}`}
                >
                  <Icon name={icon} className="size-5" />
                  {label}
                </button>
              ),
            )}
            <Link to="/change-password" className="flex w-full items-center gap-3 rounded-md px-3 py-2.5 text-left text-sm font-semibold text-text-secondary hover:bg-surface-low">
              <Icon name="shield" className="size-5" />
              Đổi mật khẩu
            </Link>
          </nav>
        </aside>
        <main className="min-w-0">
          {tab === 'profile' ? (
            <section className="rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-6">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="section-title">Thông tin cá nhân</h2>
                  <p className="mt-1 text-sm text-text-muted">
                    Thông tin dùng để liên hệ trong giao dịch.
                  </p>
                </div>
                <label
                  className="grid size-11 cursor-pointer place-items-center rounded-md bg-primary-soft text-primary"
                  title="Đổi ảnh đại diện"
                >
                  <Icon name="image" className="size-5" />
                  <input
                    className="hidden"
                    type="file"
                    accept="image/*"
                    onChange={async (event) => {
                      const file = event.target.files?.[0];
                      if (file) {
                        setAvatarFile(file);
                        setAvatarUrl(await fileToDataUrl(file));
                      }
                    }}
                  />
                </label>
              </div>
              <div className="mt-6 grid gap-4 sm:grid-cols-2">
                <Field label="Họ và tên" value={name} onChange={(e) => setName(e.target.value)} />
                {user.username.trim().toLowerCase() !== email.trim().toLowerCase() ? (
                  <Field label="Tên đăng nhập" value={user.username} readOnly className="bg-surface-low" />
                ) : null}
                <Field
                  label="Email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  readOnly={isApiAccount}
                  className={isApiAccount ? 'bg-surface-low' : undefined}
                />
                <div>
                  <Field
                    label="Số điện thoại"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    readOnly={isApiAccount}
                    className={isApiAccount ? 'bg-surface-low' : undefined}
                  />
                  {isApiAccount ? (
                    <Button
                      type="button"
                      className="mt-2"
                      size="sm"
                      variant="outline"
                      onClick={() => {
                        setPhoneChangeOpen(true);
                        setNewPhone('');
                        setPhoneOtp(createOtpValue());
                        setPhoneOtpEmail('');
                        setPhoneOtpExpiresIn(null);
                        setPhoneResendCooldown(0);
                        setPhoneChangeError('');
                        setPhoneChangeNotice('');
                        setProfileNotice('');
                      }}
                      disabled={profileLoading || phoneChangeSending || phoneChangeConfirming}
                    >
                      Đổi số điện thoại
                    </Button>
                  ) : null}
                </div>
                <Select
                  label="Khu vực mặc định"
                  value={isApiAccount ? apiAreaId : district}
                  onChange={(e) => isApiAccount ? setApiAreaId(e.target.value) : setDistrict(e.target.value)}
                >
                  {isApiAccount ? <option value="">Chưa chọn khu vực</option> : null}
                  {(isApiAccount ? apiAreas : districts).map((entry) =>
                    typeof entry === 'string' ? (
                      <option key={entry}>{entry}</option>
                    ) : (
                      <option key={entry.id} value={entry.id}>{entry.label}</option>
                    ),
                  )}
                </Select>
              </div>
              {isApiAccount && phoneChangeOpen ? (
                <div className="mt-5 rounded-lg border border-border bg-surface-low p-4">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <h3 className="font-semibold text-text">Đổi số điện thoại</h3>
                      <p className="mt-1 text-sm text-text-muted">
                        Mã xác nhận sẽ được gửi đến email của tài khoản.
                      </p>
                    </div>
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      onClick={closePhoneChange}
                      disabled={phoneChangeSending || phoneChangeConfirming}
                    >
                      Hủy
                    </Button>
                  </div>
                  <div className="mt-4 max-w-md space-y-4">
                    <Field
                      label="Số điện thoại mới"
                      value={newPhone}
                      inputMode="tel"
                      placeholder="0901234567"
                      onChange={(event) => {
                        setNewPhone(event.target.value.replace(/[^\d+]/g, ''));
                        setPhoneChangeError('');
                      }}
                      readOnly={Boolean(phoneOtpEmail)}
                      className={phoneOtpEmail ? 'bg-white' : undefined}
                    />
                    {!phoneOtpEmail ? (
                      <Button
                        type="button"
                        onClick={() => void sendPhoneOtp()}
                        disabled={phoneChangeSending || phoneChangeConfirming || !newPhone.trim()}
                      >
                        {phoneChangeSending ? 'Đang gửi...' : 'Gửi mã OTP'}
                      </Button>
                    ) : (
                      <>
                        <Alert>
                          Mã OTP đã được gửi đến {maskEmail(phoneOtpEmail)}.
                          {phoneOtpExpiresIn !== null
                            ? ` Mã hết hạn sau ${formatCountdown(phoneOtpExpiresIn)}.`
                            : ''}
                        </Alert>
                        <OtpInput
                          value={phoneOtp}
                          disabled={phoneChangeSending || phoneChangeConfirming}
                          invalid={phoneChangeError.startsWith('Mã OTP')}
                          onChange={(value) => {
                            setPhoneOtp(value);
                            setPhoneChangeError('');
                          }}
                        />
                        <div className="flex flex-wrap gap-3">
                          <Button
                            type="button"
                            onClick={() => void confirmPhoneChange()}
                            disabled={
                              phoneChangeConfirming
                              || phoneChangeSending
                              || phoneOtp.join('').length !== 6
                              || phoneOtpExpiresIn === 0
                            }
                          >
                            {phoneChangeConfirming ? 'Đang xác nhận...' : 'Xác nhận đổi số điện thoại'}
                          </Button>
                          <Button
                            type="button"
                            variant="outline"
                            onClick={() => void sendPhoneOtp()}
                            disabled={phoneResendCooldown > 0 || phoneChangeSending || phoneChangeConfirming}
                          >
                            {phoneChangeSending
                              ? 'Đang gửi...'
                              : phoneResendCooldown > 0
                                ? `Gửi lại sau ${formatCountdown(phoneResendCooldown)}`
                                : 'Gửi lại OTP'}
                          </Button>
                        </div>
                      </>
                    )}
                    {phoneChangeError ? <Alert tone="error">{phoneChangeError}</Alert> : null}
                    {phoneChangeNotice ? <Alert tone="success">{phoneChangeNotice}</Alert> : null}
                  </div>
                </div>
              ) : null}
              {profileLoading ? <div className="mt-5"><Alert>Đang tải hồ sơ...</Alert></div> : null}
              {profileLoadError ? <div className="mt-5"><Alert tone="error">{profileLoadError}</Alert></div> : null}
              {profileSaveError ? <div className="mt-5"><Alert tone="error">{profileSaveError}</Alert></div> : null}
              {profileLoadError && isApiAccount && !profileSaving ? (
                <Button className="mt-3" size="sm" variant="outline" onClick={() => setProfileReloadKey((key) => key + 1)}>
                  Tải lại hồ sơ
                </Button>
              ) : null}
              {profileNotice ? <div className="mt-5"><Alert tone="success">{profileNotice}</Alert></div> : null}
              <Button className="mt-6" onClick={() => void save()} disabled={profileLoading || profileSaving}>
                {profileSaving ? 'Đang lưu...' : 'Lưu thay đổi'}
              </Button>
            </section>
          ) : null}
          {tab === 'credit' ? (
            <div className="space-y-6">
              <section className="rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-6">
                <div className="grid gap-6 sm:grid-cols-3">
                  <Stat label="Tổng Credit" value={isApiAccount ? (wallet ? formatCredit(wallet.creditBalance) : '--') : formatCredit(user.totalCredit)} />
                  <Stat
                    label="Khả dụng"
                    value={isApiAccount ? (wallet ? formatCredit(wallet.available) : '--') : formatCredit(user.availableCredit)}
                    detail="Có thể dùng cho giao dịch"
                  />
                  <Stat
                    label="Đang giữ"
                    value={isApiAccount ? (wallet ? formatCredit(wallet.heldCredit) : '--') : formatCredit(user.holdCredit)}
                    detail="Chưa được chi tiêu"
                  />
                </div>
                {isApiAccount || import.meta.env.DEV ? <div className="mt-6 border-t border-border pt-5">
                  {isApiAccount ? (
                    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <h2 className="section-title">Nạp Credit</h2>
                        <p className="mt-1 text-sm text-text-muted">
                          Tạo đơn nạp và theo dõi trạng thái thanh toán tại trang Credit.
                        </p>
                      </div>
                      <Link to="/credit">
                        <Button>Đi đến trang Credit</Button>
                      </Link>
                    </div>
                  ) : (
                    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <h2 className="section-title">Nạp Credit thử</h2>
                        <p className="mt-1 text-sm text-text-muted">
                          Thanh toán thử — Không sử dụng tiền thật. Tính năng chỉ khả dụng khi được hệ thống bật.
                        </p>
                      </div>
                      <Link to="/credit">
                        <Button>Đi đến trang Credit</Button>
                      </Link>
                    </div>
                  )}
                </div> : null}
              </section>
              <section className="rounded-xl bg-white ring-1 ring-border/80">
                <div className="border-b border-border px-5 py-4">
                  <h2 className="section-title">Lịch sử Credit</h2>
                </div>
                {[
                  ...topups.map((t) => ({
                    id: t.id,
                    note: t.method === 'Thanh toán thử' ? `Nạp thử ${t.amount} Credit` : `Nạp ${t.amount} Credit`,
                    amount: t.status === 'completed' ? t.amount : 0,
                    date: t.createdAt,
                    status: t.status,
                  })),
                  ...history.map((h) => ({
                    id: h.id,
                    note: h.note,
                    amount: h.amount,
                    date: h.createdAt,
                    status: 'record',
                  })),
                ].map((row) => (
                  <div
                    key={row.id}
                    className="grid grid-cols-[1fr_auto] gap-4 border-b border-border/70 px-5 py-4 last:border-0"
                  >
                    <div>
                      <p className="text-sm font-medium">{row.note}</p>
                      <p className="mt-1 text-xs text-text-muted">
                        {new Date(row.date).toLocaleDateString('vi-VN')} ·{' '}
                        {row.status === 'pending' ? 'Chờ xác nhận' : 'Đã ghi nhận'}
                      </p>
                    </div>
                    <strong
                      className={`text-sm tabular-nums ${row.amount < 0 ? 'text-error' : row.amount > 0 ? 'text-success' : 'text-warning'}`}
                    >
                      {row.amount > 0 ? '+' : ''}
                      {row.amount || 'Đang chờ'}
                    </strong>
                  </div>
                ))}
              </section>
            </div>
          ) : null}
          {tab === 'password' ? <ChangePasswordForm user={user} /> : null}
          {tab === 'reputation' && isApiAccount ? (
            <div className="space-y-6">
              {reputationLoading && !reputation ? <Alert>Đang tải thông tin uy tín...</Alert> : null}
              {reputationError ? (
                <div className="space-y-3">
                  <Alert tone="error">{reputationError}</Alert>
                  <Button size="sm" variant="outline" onClick={() => setReputationReloadKey((key) => key + 1)}>
                    Thử lại
                  </Button>
                </div>
              ) : null}
              {reputation ? (
                <>
                  <section className="grid gap-5 rounded-xl bg-white p-6 ring-1 ring-border/80 sm:grid-cols-4">
                    <Stat label="Tổng điểm" value={reputation.totalPoints} detail="Điểm hoạt động" />
                    <Stat label="Hạng thành viên" value={reputationRankLabel[reputation.rank]} detail="Dựa trên điểm uy tín" />
                    <Stat label="Sao uy tín" value={`${reputation.trustStars}/5`} detail="Mức tin cậy hiện tại" />
                    <Stat
                      label="Tỉ lệ đúng mô tả"
                      value={reputation.accuracyRate === null ? 'Chưa có' : `${Math.round(reputation.accuracyRate * 100)}%`}
                      detail="Từ giao dịch được đánh giá"
                    />
                  </section>
                  <section className="rounded-xl bg-white ring-1 ring-border/80">
                    <div className="border-b border-border px-5 py-4">
                      <h2 className="section-title">Lịch sử uy tín</h2>
                      <p className="mt-1 text-xs text-text-muted">{reputation.history.totalElements} lần thay đổi điểm</p>
                    </div>
                    {reputation.history.content.length ? reputation.history.content.map((entry, index) => (
                      <div
                        key={`${entry.createdAt}-${index}`}
                        className="grid grid-cols-[1fr_auto] gap-4 border-b border-border/70 px-5 py-4 last:border-0"
                      >
                        <div>
                          <p className="text-sm font-medium">{entry.reason}</p>
                          <p className="mt-1 text-xs text-text-muted">{new Date(entry.createdAt).toLocaleString('vi-VN')}</p>
                        </div>
                        <strong className={`text-sm tabular-nums ${entry.delta < 0 ? 'text-error' : 'text-success'}`}>
                          {entry.delta > 0 ? '+' : ''}{entry.delta} điểm
                        </strong>
                      </div>
                    )) : (
                      <div className="p-5">
                        <p className="text-sm text-text-muted">Chưa có thay đổi điểm uy tín.</p>
                      </div>
                    )}
                    {reputation.history.totalPages > 1 ? (
                      <div className="flex items-center justify-between border-t border-border px-5 py-4">
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={reputationLoading || reputation.history.number <= 0}
                          onClick={() => setReputationPage((page) => Math.max(0, page - 1))}
                        >
                          Trang trước
                        </Button>
                        <span className="text-xs text-text-muted">
                          Trang {reputation.history.number + 1}/{reputation.history.totalPages}
                        </span>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={reputationLoading || reputation.history.number + 1 >= reputation.history.totalPages}
                          onClick={() => setReputationPage((page) => page + 1)}
                        >
                          Trang sau
                        </Button>
                      </div>
                    ) : null}
                  </section>
                </>
              ) : null}
            </div>
          ) : tab === 'reputation' ? (
            <div className="space-y-6">
              <section className="grid gap-5 rounded-xl bg-white p-6 ring-1 ring-border/80 sm:grid-cols-3">
                <Stat label="Reward Points" value={user.rewardPoints} detail="Điểm hoạt động" />
                <Stat label="Hạng thành viên" value={user.rank} detail="Dựa trên điểm và lịch sử" />
                <Stat
                  label="Reputation Stars"
                  value={user.reputationStars.toFixed(1)}
                  detail="Đánh giá từ cộng đồng"
                />
              </section>
              <section className="rounded-xl bg-white p-6 ring-1 ring-border/80">
                <h2 className="section-title">Lịch sử uy tín</h2>
                <div className="mt-4 space-y-3">
                  {[
                    'Hoàn tất giao dịch đúng lịch',
                    'Phản hồi tích cực từ thành viên',
                    'Xác nhận bàn giao đầy đủ',
                  ].map((text, i) => (
                    <div
                      key={text}
                      className="flex items-center justify-between rounded-md bg-background p-3 text-sm"
                    >
                      <span>{text}</span>
                      <span className="font-semibold text-success">+{[5, 3, 2][i]} điểm</span>
                    </div>
                  ))}
                </div>
              </section>
            </div>
          ) : null}
        </main>
      </div>
    </div>
  );
}
