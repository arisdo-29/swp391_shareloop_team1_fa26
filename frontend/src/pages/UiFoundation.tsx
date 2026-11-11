import { useState } from 'react';
import { Badge, Button, Card, Field, PageHeader, Select } from '../components/ui';
import { Dialog } from '../components/Dialog';

// Component preview available only in the development router.
export function UiFoundation() {
  const [dialogOpen, setDialogOpen] = useState(false);
  return (
    <div className="page-shell space-y-6">
      <PageHeader
        title="Nền tảng giao diện"
        description="Xanh rừng, xanh lime và nền kem. Chọn giao diện trên thanh điều hướng để xem các trạng thái."
      />
      <Card className="space-y-5 p-6 sm:p-8">
        <Badge tone="success">Cho đồ thêm một vòng đời mới</Badge>
        <h2 className="section-title">Một khởi đầu mới cho món đồ cũ.</h2>
        <p className="max-w-2xl text-sm leading-7 text-text-secondary">
          Kiểm tra kiểu chữ tiếng Việt: Đồ dùng, trao tặng, cộng đồng, Nguyễn, Trường, Ắ Ằ Ẳ Ẵ Ặ Ế Ề
          Ể Ễ Ệ Ớ Ờ Ở Ỡ Ợ.
        </p>
        <div className="flex flex-wrap gap-3">
          <Button onClick={() => setDialogOpen(true)}>Mở hộp thoại</Button>
          <Button variant="outline">Nút phụ</Button>
          <Button variant="ghost">Nút đơn giản</Button>
          <Button variant="danger">Thao tác cần chú ý</Button>
          <Button disabled>Chưa khả dụng</Button>
        </div>
      </Card>
      <div className="grid gap-6 md:grid-cols-2">
        <Card className="space-y-5 p-6">
          <h2 className="section-title">Trường nhập liệu</h2>
          <Field
            label="Tên hiển thị"
            placeholder="Nhập tên của bạn"
            hint="Kiểu chữ hỗ trợ đầy đủ dấu tiếng Việt."
          />
          <Select label="Lựa chọn">
            <option>Chọn một mục</option>
            <option>Mục thứ hai</option>
          </Select>
          <Field label="Trạng thái lỗi" error="Vui lòng nhập thông tin." />
          <Field label="Trạng thái khóa" disabled placeholder="Chưa khả dụng" />
        </Card>
        <Card className="space-y-5 p-6">
          <h2 className="section-title">Bề mặt và trạng thái</h2>
          <div className="flex flex-wrap gap-2">
            <Badge>Trung tính</Badge>
            <Badge tone="success">Thành công</Badge>
            <Badge tone="warning">Cần chú ý</Badge>
            <Badge tone="error">Có lỗi</Badge>
            <Badge tone="info">Thông tin</Badge>
          </div>
          <div className="rounded-md bg-surface-container p-5 text-sm text-text-secondary">
            Bề mặt phụ phân tách các nhóm nội dung.
          </div>
          <div className="rounded-md bg-highlight p-5 text-sm font-semibold text-on-highlight">
            Điểm nhấn lime
          </div>
        </Card>
      </div>
      {dialogOpen && (
        <Dialog title="Hộp thoại dùng chung" onClose={() => setDialogOpen(false)}>
          <p className="text-sm leading-6 text-text-secondary">
            Kiểm tra hiển thị trên điện thoại, bàn phím và chế độ tối.
          </p>
          <div className="mt-6 flex justify-end">
            <Button onClick={() => setDialogOpen(false)}>Đóng hộp thoại</Button>
          </div>
        </Dialog>
      )}
    </div>
  );
}
