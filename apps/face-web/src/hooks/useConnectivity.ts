import { useEffect, useState } from 'react';

export type ConnectivityState = {
  isOnline: boolean;
  wasOffline: boolean;
};

const getInitialOnlineState = (): boolean => {
  if (typeof navigator === 'undefined' || typeof navigator.onLine !== 'boolean') {
    return true;
  }
  return navigator.onLine;
};

export const useConnectivity = (): ConnectivityState => {
  const [isOnline, setIsOnline] = useState<boolean>(getInitialOnlineState);
  const [wasOffline, setWasOffline] = useState<boolean>(() => !getInitialOnlineState());

  useEffect(() => {
    if (typeof window === 'undefined') return;

    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => {
      setIsOnline(false);
      setWasOffline(true);
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  return { isOnline, wasOffline };
};
