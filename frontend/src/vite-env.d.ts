/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_DEMO_PAYMENT?: string;
  readonly VITE_POST_SKIP_MEDIA_UPLOAD?: string;
  readonly VITE_ENABLE_MOCK_TOPUP?: string;
}
