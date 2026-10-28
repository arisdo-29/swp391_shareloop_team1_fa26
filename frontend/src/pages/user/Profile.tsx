import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { actions, selectCurrentUser } from '../../app/store';
import {
  Avatar,
  Alert,
  Button,
  Field,
  PageHeader,
  RankBadge,
  Select,
} from '../../components/ui';
import { fileToDataUrl } from '../../utils/files';
import { OtpInput } from '../../components/OtpInput';
import { userApi } from '../../services/userApi';
import { catalogApi } from '../../services/catalogApi';
import { mediaApi } from '../../services/mediaApi';
import { toAreaOptions, type AreaOption } from '../../services/mappers';
import type { ApiError } from '../../services/http';
import { toBackendUserId } from '../../services/requestApi';
import { resolveMediaUrl } from '../../services/mediaUrl';
import {
  reputationApi,
  summarizePointHistory,
  type MyReputation,
  type PointHistorySummary,
} from '../../services/reputationApi';
import { createOtpValue } from '../../utils/otp';
import { getRankLabel } from '../../utils/reputation';

type ProfileTab = 'profile' | 'reputation';

// Chỉ nhận tab hợp lệ từ URL, giá trị lạ thì về tab Hồ sơ.
function tabFromSearch(value: string | null): ProfileTab {
  return value === 'reputation' ? 'reputation' : 'profile';
}

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
  const dispatch = useAppDispatch();
  const [searchParams, setSearchParams] = useSearchParams();
  // Tab lấy từ URL để thông báo có thể trỏ thẳng tới /profile?tab=reputation.
  const tab: ProfileTab = isAdmin ? 'profile' : tabFromSearch(searchParams.get('tab'));
  const changeTab = (nextTab: ProfileTab) => {
    setSearchParams(nextTab === 'profile' ? {} : { tab: nextTab }, { replace: true });
  };
  const [avatarUrl, setAvatarUrl] = useState(user.avatarUrl);
  const [name, setName] = useState(user.name);
  const [email, setEmail] = useState(user.email);
  const [phone, setPhone] = useState(user.phone);
  const [apiAreaId, setApiAreaId] = useState('');
  const [apiAvatarMediaId, setApiAvatarMediaId] = useState<number | null>(null);
  const [apiAreas, setApiAreas] = useState<AreaOption[]>([]);
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
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
  const [pointSummary, setPointSummary] = useState<PointHistorySummary | null>(null);

  useEffect(() => {
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
  }, [dispatch, profileReloadKey, user.id]);

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

  // Tổng điểm được cộng, bị trừ trên toàn bộ lịch sử; tải lại cùng lúc với thông tin uy tín.
  useEffect(() => {
    if (tab !== 'reputation' || isAdmin) return undefined;
    let active = true;
    reputationApi
      .listAllPointHistory()
      .then((entries) => {
        if (active) setPointSummary(summarizePointHistory(entries));
      })
      .catch(() => {
        if (active) setPointSummary(null);
      });
    return () => {
      active = false;
    };
  }, [tab, isAdmin, reputationReloadKey]);

  // Gọi ngay khi mở trang để cột trái có hạng thật, tab Uy tín dùng lại cùng kết quả. Admin không có uy tín.
  useEffect(() => {
    if (isAdmin) return;
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
  }, [isAdmin, reputationPage, reputationReloadKey]);

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
    if (phoneChangeSending || phoneChangeConfirming) return;
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
    if (phoneChangeSending || phoneChangeConfirming) return;
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
    if (profileSaving) return;
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
  };

  return (
    <div className="page-shell">
      <PageHeader title="Tài khoản" />
      <div className="grid items-start gap-6 lg:grid-cols-[240px_minmax(0,1fr)]">
        <aside className="rounded-lg bg-white p-3 ring-1 ring-border/80">
          <div className="flex items-center gap-3 px-2 pb-4 pt-2">
            <Avatar
              user={{
                ...user,
                name,
                avatarInitials: name.trim().split(/\s+/).map((part) => part[0]).slice(-2).join('').toUpperCase(),
                avatarUrl,
              }}
            />
            <div className="min-w-0">
              <h2 className="truncate text-sm font-bold">{name}</h2>
              <p className="truncate text-xs text-text-muted">{email}</p>
              <div className="mt-1">
                {isAdmin ? <RankBadge rank="Quản trị hệ thống" /> : null}
                {!isAdmin && reputation ? (
                  <RankBadge rank={`Hạng ${getRankLabel(reputation.rank)}`} />
                ) : null}
              </div>
            </div>
          </div>
          <nav aria-label="Mục tài khoản" className="space-y-1 border-t border-border pt-2">
            {(
              [
                ['profile', 'Hồ sơ'],
                ['credit', 'Ví Credit'],
                ['reputation', 'Uy tín & Hạng'],
              ] as const
            ).map(([id, label]) =>
              id === 'credit' && !isAdmin ? (
                <Link
                  key={id}
                  to="/credit"
                  className="flex w-full items-center min-h-11 rounded-md px-3 py-2.5 text-left text-sm font-semibold transition text-text-secondary hover:bg-surface-low"
                >
                  {label}
                </Link>
              ) : id === 'credit' ? null : isAdmin && id === 'reputation' ? null : (
                <button
                  key={id}
                  onClick={() => changeTab(id)}
                  className={`flex w-full items-center min-h-11 rounded-md px-3 py-2.5 text-left text-sm font-semibold transition ${tab === id ? 'bg-primary-faint text-primary' : 'text-text-secondary hover:bg-surface-low'}`}
                >
                  {label}
                </button>
              ),
            )}
            <Link to="/change-password" className="flex w-full items-center min-h-11 rounded-md px-3 py-2.5 text-left text-sm font-semibold transition text-text-secondary hover:bg-surface-low">
              Đổi mật khẩu
            </Link>
          </nav>
        </aside>
        <main className="min-w-0">
          {tab === 'profile' ? (
            <section className="rounded-lg bg-white p-5 ring-1 ring-border/80 sm:p-6">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h2 className="section-title">Thông tin cá nhân</h2>
                  <p className="mt-1 text-sm text-text-muted">
                    Thông tin dùng để liên hệ trong giao dịch.
                  </p>
                </div>
                <label className="inline-flex min-h-11 cursor-pointer items-center rounded-md border border-border bg-white px-3 text-sm font-semibold text-text-secondary transition hover:border-primary/50 hover:bg-primary-faint hover:text-primary">
                  Đổi ảnh đại diện
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
                  readOnly
                  className="bg-surface-low"
                />
                <div>
                  <Field
                    label="Số điện thoại"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    readOnly
                    className="bg-surface-low"
                  />
                  <Button
                      type="button"
                      className="mt-2"
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
                </div>
                <Select
                  label="Khu vực mặc định"
                  value={apiAreaId}
                  onChange={(e) => setApiAreaId(e.target.value)}
                >
                  <option value="">Chưa chọn khu vực</option>
                  {apiAreas.map((entry) => (
                    <option key={entry.id} value={entry.id}>{entry.label}</option>
                  ))}
                </Select>
              </div>
              {phoneChangeOpen ? (
                <div className="mt-5 rounded-lg border border-border bg-surface-low p-4">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <h3 className="font-semibold text-text-primary">Đổi số điện thoại</h3>
                      <p className="mt-1 text-sm text-text-muted">
                        Mã xác nhận sẽ được gửi đến email của tài khoản.
                      </p>
                    </div>
                    <Button
                      type="button"
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
              {profileLoadError && !profileSaving ? (
                <Button className="mt-3" variant="outline" onClick={() => setProfileReloadKey((key) => key + 1)}>
                  Tải lại hồ sơ
                </Button>
              ) : null}
              {profileNotice ? <div className="mt-5"><Alert tone="success">{profileNotice}</Alert></div> : null}
              <Button className="mt-6" onClick={() => void save()} disabled={profileLoading || profileSaving}>
                {profileSaving ? 'Đang lưu...' : 'Lưu thay đổi'}
              </Button>
            </section>
          ) : null}
          {tab === 'reputation' ? (
            <div className="space-y-6">
              {reputationLoading && !reputation ? <Alert>Đang tải thông tin uy tín...</Alert> : null}
              {reputationError ? (
                <div className="space-y-3">
                  <Alert tone="error">{reputationError}</Alert>
                  <Button variant="outline" onClick={() => setReputationReloadKey((key) => key + 1)}>
                    Thử lại
                  </Button>
                </div>
              ) : null}
              {reputation ? (
                <>
                  <section aria-label="Tóm tắt uy tín" className="grid gap-3 md:grid-cols-[2fr_1fr_1fr]">
                    <div className="rounded-lg bg-white p-5 ring-1 ring-border/80">
                      <p className="text-xs font-semibold text-text-secondary">Hạng thành viên</p>
                      <p className="mt-2 flex items-baseline gap-2.5">
                        <strong className="text-2xl">{getRankLabel(reputation.rank)}</strong>
                        <span className="text-sm text-text-muted">{reputation.totalPoints} điểm uy tín</span>
                      </p>
                      <p className="mt-2 text-sm leading-6 text-text-secondary">Điểm tăng khi bạn hoàn tất giao dịch và quyết định hạng.</p>
                    </div>
                    <div className="rounded-lg bg-white p-5 ring-1 ring-border/80">
                      <p className="text-xs font-semibold text-text-secondary">Sao uy tín</p>
                      <p className="mt-2 text-xl font-bold">{reputation.trustStars} / 5</p>
                      <p className="mt-1 text-xs leading-5 text-text-muted">Giảm khi bị báo cáo đúng.</p>
                    </div>
                    <div className="rounded-lg bg-white p-5 ring-1 ring-border/80">
                      <p className="text-xs font-semibold text-text-secondary">Đúng mô tả</p>
                      <p className="mt-2 text-xl font-bold">
                        {reputation.accuracyRate === null ? (
                          <span className="block text-sm font-semibold leading-6">Chưa đủ giao dịch để tính</span>
                        ) : (
                          `${Math.round(reputation.accuracyRate * 100)}%`
                        )}
                      </p>
                      <p className="mt-1 text-xs leading-5 text-text-muted">Từ đánh giá của người nhận.</p>
                    </div>
                  </section>
                  <section className="overflow-hidden rounded-lg bg-white ring-1 ring-border/80">
                    <div className="border-b border-border px-5 py-4">
                      <h2 className="section-title">Lịch sử uy tín</h2>
                      <p className="mt-1 text-xs text-text-muted">{reputation.history.totalElements} lần thay đổi điểm</p>
                    </div>
                    <PointSummary summary={pointSummary} />
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
                      <div className="flex items-center justify-between gap-3 border-t border-border px-5 py-4">
                        <Button
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
          ) : null}
        </main>
      </div>
    </div>
  );
}

function PointSummary({ summary }: { summary: PointHistorySummary | null }) {
  if (!summary) return null;
  const cells: Array<[string, string, string]> = [
    ['Đã được cộng', `+${summary.gained}`, 'text-success'],
    ['Bị trừ', summary.lost ? `-${summary.lost}` : '0', 'text-error'],
    ['Số lần thay đổi', String(summary.changes), 'text-text-primary'],
    ['Không được cộng', String(summary.notCounted), 'text-text-secondary'],
  ];
  return (
    <dl className="grid grid-cols-2 gap-2.5 border-b border-border p-4 sm:grid-cols-4">
      {cells.map(([label, value, tone]) => (
        <div key={label} className="rounded-md bg-surface-low px-4 py-3">
          <dt className="text-xs font-medium text-text-muted">{label}</dt>
          <dd className={`mt-1 text-lg font-bold tabular-nums ${tone}`}>{value}</dd>
        </div>
      ))}
    </dl>
  );
}
