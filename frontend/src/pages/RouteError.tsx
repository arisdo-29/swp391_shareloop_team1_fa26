import { Link, isRouteErrorResponse, useRouteError } from 'react-router-dom';
import { Button } from '../components/ui';

// Sau mỗi lần deploy, tab đang mở có thể còn trỏ tới file JS cũ đã bị xoá: tải lại trang là hết.
function isStaleChunkError(error: unknown) {
  const message = error instanceof Error ? error.message : String(error ?? '');
  return /dynamically imported module|Importing a module script failed|Loading chunk|error loading dynamically/i.test(
    message,
  );
}

/** Màn hình thay cho trang lỗi mặc định của React Router khi một màn bị lỗi lúc hiển thị. */
export function RouteError() {
  const error = useRouteError();
  const staleChunk = isStaleChunkError(error);
  const notFound = isRouteErrorResponse(error) && error.status === 404;

  return (
    <div className="page-shell grid min-h-[420px] place-items-center px-4 text-center">
      <div className="max-w-md">
        <h1 className="text-2xl font-bold">
          {notFound
            ? 'Không tìm thấy trang này'
            : staleChunk
              ? 'ShareLoop vừa được cập nhật'
              : 'Màn hình này đang gặp lỗi'}
        </h1>
        <p className="mt-3 text-sm leading-6 text-text-secondary">
          {staleChunk
            ? 'Bấm Tải lại trang để dùng phiên bản mới nhất. Dữ liệu của bạn không bị mất.'
            : 'Bạn thử tải lại trang. Nếu vẫn lỗi, quay về trang chủ và báo cho nhóm hỗ trợ kèm thao tác vừa làm.'}
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-2">
          <Button onClick={() => window.location.reload()}>Tải lại trang</Button>
          <Link to="/">
            <Button variant="outline">Về trang chủ</Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
