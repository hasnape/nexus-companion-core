import { useEffect } from 'react';

export const usePwaShell = () => {
  useEffect(() => {
    if (typeof window === 'undefined') return;

    if (!document.querySelector('link[rel="manifest"]')) {
      const manifestLink = document.createElement('link');
      manifestLink.setAttribute('rel', 'manifest');
      manifestLink.setAttribute('href', '/manifest.webmanifest');
      document.head.appendChild(manifestLink);
    }

    if (!('serviceWorker' in navigator)) return;
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // noop
    });
  }, []);
};
