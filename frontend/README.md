# SHARELOOP Frontend

Giao diện React + TypeScript kết nối backend ShareLoop qua các service trong `src/services/`. Tìm đồ theo quận/huyện, đăng bài, ví, giao dịch, chat và các chức năng quản trị dùng API tương ứng.

## Stack

React, TypeScript, Vite, Tailwind CSS, Redux Toolkit, React Redux, React Router DOM, Be Vietnam Pro, and Material Symbols Outlined.

## Run

```bash
npm install
npm run dev
npm run build
```

## Deploy to Vercel

The repository includes `vercel.json` with the SPA rewrite required by React Router. Vercel uses Node.js 24, runs `npm run build`, and publishes `dist`.

### From Git

1. Push this repository to GitHub, GitLab, or Bitbucket.
2. In Vercel, select **Add New Project** and import the repository.
3. Chọn framework **Vite**, cấu hình `VITE_API_BASE_URL` là địa chỉ backend trong Project Settings → Environment Variables, sau đó build và deploy. Giá trị mặc định trong code là `https://shareloop-api.onrender.com`.

Every push to the production branch creates a production deployment. Other branches and pull requests receive preview deployments.

### From the Vercel CLI

```bash
npx vercel
npx vercel --prod
```

Do not commit the generated `.vercel` directory; it contains the local link to the Vercel project.

## Dữ liệu và phiên đăng nhập

Ứng dụng khởi tạo dữ liệu nghiệp vụ rỗng và tải từ API. Không đọc hoặc ghi cơ sở dữ liệu mẫu, số dư hay giao dịch vào localStorage. Token vẫn được quản lý trong HTTP client; khi tải lại trang, ứng dụng khôi phục người dùng qua API hồ sơ.

Một số nhánh reducer và màn hình cũ chưa được gỡ hoàn toàn. Xem [báo cáo rà soát](docs/mieu-r1-r3-review.md) để biết các phần còn thiếu API. Dữ liệu mẫu trong `src/mocks/database.ts` hiện chỉ phục vụ kiểm chứng domain cũ, không được tải vào ứng dụng.

## Kiểm tra

`npm run lint`, `npm run build`, `npm run test:api`.

Bộ kiểm tra API dùng adapter HTTP trong bộ nhớ, không gọi staging và không thay cho kiểm thử trình duyệt với hai tài khoản thật.

## Main Routes

- `/`, `/browse`, `/product/:id`, `/ai`
- `/login`, `/register`
- `/post`, `/activities`, `/requests/:requestId`, `/messages`, `/profile`
- `/admin` and nested moderation, users, transactions, finance, settings, audit logs

## Credit Rules

1 Credit = 1,000 VND. `totalCredit = availableCredit + holdCredit`. Opening pages, modals, or conversations never charges Credit. Credit moves only through explicit transaction or wallet actions.
