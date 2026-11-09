import { useEffect, useRef } from 'react';
import { subscribeRealtime, type RealtimeUpdate } from '../services/realtime';

export function useRealtimeRefresh(
  kinds: ReadonlyArray<RealtimeUpdate['kind']>,
  refresh: (update: RealtimeUpdate) => void,
) {
  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;
  const kindKey = kinds.join('|');
  useEffect(
    () => subscribeRealtime((update) => {
      if (update.kind === 'READY' || kindKey.split('|').includes(update.kind)) {
        refreshRef.current(update);
      }
    }),
    [kindKey],
  );
}
