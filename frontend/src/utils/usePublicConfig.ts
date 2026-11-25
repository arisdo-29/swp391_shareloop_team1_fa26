import { useEffect, useState } from 'react';
import { DEFAULT_PUBLIC_CONFIG, publicConfigApi, type PublicConfig } from '../services/publicConfigApi';

/** Các con số nghiệp vụ công khai từ backend, có giá trị dự phòng ngay từ lần render đầu. */
export function usePublicConfig(): PublicConfig {
  const [config, setConfig] = useState<PublicConfig>(DEFAULT_PUBLIC_CONFIG);
  useEffect(() => {
    let active = true;
    void publicConfigApi.load().then((loaded) => {
      if (active) setConfig(loaded);
    });
    return () => {
      active = false;
    };
  }, []);
  return config;
}
