import { Link } from 'react-router-dom';
import { Button } from '../components/ui';

export function NotFound() {
  return (
    <div className="page-shell grid min-h-[520px] place-items-center text-center">
      <div>
        <p className="text-7xl font-extrabold leading-none text-primary-fixed">404</p>
        <h1 className="mt-4 text-3xl font-bold">Không tìm thấy trang này</h1>
        <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-text-secondary">
          Có thể bài đăng đã được gỡ, đã có người nhận, hoặc đường dẫn bị gõ sai.
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-2">
          <Link to="/browse">
            <Button>Tìm món khác</Button>
          </Link>
          <Link to="/">
            <Button variant="outline">Về trang chủ</Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
