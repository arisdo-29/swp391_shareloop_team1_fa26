import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import { Alert, Button, EmptyState, Icon, LoadingState, RankBadge, Stat } from '../../components/ui';
import { ReputationBadge } from '../../components/ReputationBadge';
import { ReportDialog } from '../../components/ReportDialog';
import { ProfileItemsSection } from '../../components/profile/ProfileItemsSection';
import { userApi, type PublicProfileResponse } from '../../services/userApi';
import { toBackendUserId } from '../../services/requestApi';
import { resolveMediaUrl } from '../../services/mediaUrl';
import type { ApiError } from '../../services/http';
import { formatAsDescribedRate, formatTrustStars, getRankLabel } from '../../utils/reputation';

type LoadError = 'not-found' | 'failed' | null;

/**
 * Trang Hồ sơ công khai (route /users/:userId, cần đăng nhập). Chỉ hiện số liệu công khai, không có Credit hay
 * điểm thưởng. Không có nút Nhắn tin vì chat luôn gắn với một giao dịch (Request), không có tin nhắn tự do.
 */
export function UserProfile() {
  const { userId } = useParams();
  const navigate = useNavigate();
  const currentUser = useAppSelector(selectCurrentUser);
  const currentUserId = toBackendUserId(currentUser?.id);
  const profileId = Number(userId);
  const [profile, setProfile] = useState<PublicProfileResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<LoadError>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [reportOpen, setReportOpen] = useState(false);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    if (!Number.isSafeInteger(profileId) || profileId <= 0) {
      setLoading(false);
      setError('not-found');
      return;
    }
    let active = true;
    setLoading(true);
    setError(null);
    userApi
      .getPublicProfile(profileId)
      .then((response) => {
        if (active) setProfile(response);
      })
      .catch((caught: ApiError) => {
        // Backend trả 404 cho tài khoản không tồn tại, chưa xác thực, bị khoá, bị cấm hoặc là Admin.
        if (active) setError(caught.status === 404 ? 'not-found' : 'failed');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [profileId, reloadKey]);

  const goBack = () => navigate(-1);

  if (loading) {
    return (
      <div className="page-shell" role="status" aria-label="Đang tải hồ sơ">
        <LoadingState />
      </div>
    );
  }
  if (error === 'not-found') {
    return (
      <div className="page-shell">
        <EmptyState
          title="Không tìm thấy người dùng"
          text="Tài khoản này không tồn tại hoặc hiện không xem được."
          action={<Button onClick={goBack}>Quay lại</Button>}
        />
      </div>
    );
  }
  if (error || !profile) {
    return (
      <div className="page-shell space-y-4">
        <Alert tone="error">Không tải được hồ sơ. Vui lòng thử lại.</Alert>
        <Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
          Thử tải lại
        </Button>
      </div>
    );
  }

  const isOwnProfile = currentUserId === profile.id;
  return (
    <div className="page-shell space-y-8">
      <Button variant="ghost" onClick={goBack}>
        Quay lại
      </Button>
      <ProfileCard
        profile={profile}
        isOwnProfile={isOwnProfile}
        notice={notice}
        onReport={() => {
          setNotice('');
          setReportOpen(true);
        }}
      />
      <ProfileItemsSection userId={profile.id} tab="ACTIVE" />
      <ProfileItemsSection userId={profile.id} tab="DONE" />
      {reportOpen && !isOwnProfile ? (
        <ReportDialog
          target={{ targetType: 'USER', targetUserId: profile.id }}
          contextLabel={`Báo cáo người dùng ${profile.fullName}`}
          onClose={() => setReportOpen(false)}
          onSuccess={() => {
            setReportOpen(false);
            setNotice('Báo cáo đã được gửi đến quản trị viên để xem xét.');
          }}
        />
      ) : null}
    </div>
  );
}

function ProfileCard({
  profile,
  isOwnProfile,
  notice,
  onReport,
}: {
  profile: PublicProfileResponse;
  isOwnProfile: boolean;
  notice: string;
  onReport: () => void;
}) {
  const rankLabel = getRankLabel(profile.rank);
  return (
    <section aria-labelledby="profile-name" className="card space-y-6 p-5 sm:p-6">
      <div className="flex flex-wrap items-start gap-4 sm:gap-5">
        <ProfileAvatar profile={profile} />
        <div className="min-w-0 flex-1 space-y-2">
          <h1 id="profile-name" className="page-title break-words">
            {profile.fullName}
          </h1>
          <div className="flex flex-wrap items-center gap-2">
            <RankBadge rank={`Hạng ${rankLabel}`} />
            <ReputationBadge trustStars={profile.trustStars} />
          </div>
          {profile.district ? (
            <p className="text-sm text-text-secondary">Khu vực: {profile.district}</p>
          ) : null}
        </div>
        {/* Không có Nhắn tin: chat chỉ mở trong một giao dịch, không có tin nhắn tự do giữa hai người. */}
        {!isOwnProfile ? (
          <Button variant="ghost" onClick={onReport}>
            Báo cáo
          </Button>
        ) : null}
      </div>
      {notice ? (
        <div role="status">
          <Alert tone="success">{notice}</Alert>
        </div>
      ) : null}
      <div className="grid grid-cols-2 gap-4 border-t border-border pt-5 lg:grid-cols-4">
        <Stat label="Hạng thành viên" value={rankLabel} />
        <Stat label="Mức uy tín" value={`${formatTrustStars(profile.trustStars)} / 5 sao`} />
        <Stat label="Giao dịch hoàn tất" value={profile.completedTransactions.toLocaleString('vi-VN')} />
        {profile.asDescribedRate !== null ? (
          <Stat label="Đúng mô tả" value={formatAsDescribedRate(profile.asDescribedRate)} />
        ) : null}
      </div>
    </section>
  );
}

// Ảnh vuông bo góc 96px; không có ảnh hoặc ảnh lỗi thì hiện chữ viết tắt backend trả về.
function ProfileAvatar({ profile }: { profile: PublicProfileResponse }) {
  const src = resolveMediaUrl(profile.avatarUrl ?? undefined);
  const [failed, setFailed] = useState(false);
  if (src && !failed) {
    return (
      <img
        src={src}
        alt=""
        onError={() => setFailed(true)}
        className="size-24 shrink-0 rounded-lg object-cover"
      />
    );
  }
  return (
    <span
      aria-hidden="true"
      className="inline-flex size-24 shrink-0 items-center justify-center rounded-lg bg-primary text-2xl font-bold text-white"
    >
      {profile.avatarInitials || <Icon name="user" className="size-1/2" />}
    </span>
  );
}
