import { useCallback, useEffect, useState } from 'react';

export const useFullscreenMode = () => {
  const [isFullscreen, setIsFullscreen] = useState<boolean>(() => Boolean(document.fullscreenElement));

  useEffect(() => {
    const onChange = () => setIsFullscreen(Boolean(document.fullscreenElement));
    document.addEventListener('fullscreenchange', onChange);
    return () => document.removeEventListener('fullscreenchange', onChange);
  }, []);

  const enterFullscreen = useCallback(async (element: HTMLElement | null): Promise<boolean> => {
    if (!element || typeof element.requestFullscreen !== 'function') return false;
    try {
      await element.requestFullscreen();
      return true;
    } catch {
      return false;
    }
  }, []);

  const exitFullscreen = useCallback(async (): Promise<boolean> => {
    if (!document.fullscreenElement || typeof document.exitFullscreen !== 'function') return false;
    try {
      await document.exitFullscreen();
      return true;
    } catch {
      return false;
    }
  }, []);

  return { isFullscreen, enterFullscreen, exitFullscreen };
};
