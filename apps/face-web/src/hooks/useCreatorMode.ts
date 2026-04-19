import { useState } from 'react';

const CREATOR_CODE = '0410';
const REQUIRED_TAP_COUNT = 5;
const TAP_WINDOW_MS = 2000;

type CreatorModeOptions = {
  now?: () => number;
};

type CreatorModeState = {
  creatorMode: boolean;
  creatorCodeDialogOpen: boolean;
  creatorCodeInput: string;
  creatorCodeError: string | null;
  registerFaceTap: () => void;
  setCreatorCodeInput: (value: string) => void;
  submitCreatorCode: () => void;
  closeCreatorCodeDialog: () => void;
  exitCreatorMode: () => void;
};

export const trimTapHistory = (tapHistory: number[], nextTap: number): number[] =>
  [...tapHistory, nextTap].filter((timestamp) => nextTap - timestamp <= TAP_WINDOW_MS);

export const isCreatorCodeValid = (code: string): boolean => code.trim() === CREATOR_CODE;

export function useCreatorMode(options?: CreatorModeOptions): CreatorModeState {
  const now = options?.now ?? (() => Date.now());
  const [creatorMode, setCreatorMode] = useState(false);
  const [creatorCodeDialogOpen, setCreatorCodeDialogOpen] = useState(false);
  const [creatorCodeInput, setCreatorCodeInput] = useState('');
  const [creatorCodeError, setCreatorCodeError] = useState<string | null>(null);
  const [, setRecentTapTimestamps] = useState<number[]>([]);

  const registerFaceTap = () => {
    const tapTimestamp = now();
    setRecentTapTimestamps((previous) => {
      const updated = trimTapHistory(previous, tapTimestamp);
      if (updated.length >= REQUIRED_TAP_COUNT) {
        setCreatorCodeDialogOpen(true);
        setCreatorCodeInput('');
        setCreatorCodeError(null);
        return [];
      }
      return updated;
    });
  };

  const submitCreatorCode = () => {
    if (isCreatorCodeValid(creatorCodeInput)) {
      setCreatorMode(true);
      setCreatorCodeDialogOpen(false);
      setCreatorCodeInput('');
      setCreatorCodeError(null);
      setRecentTapTimestamps([]);
      return;
    }

    setCreatorCodeError('Code incorrect.');
  };

  const closeCreatorCodeDialog = () => {
    setCreatorCodeDialogOpen(false);
    setCreatorCodeInput('');
    setCreatorCodeError(null);
  };

  const exitCreatorMode = () => {
    setCreatorMode(false);
    setCreatorCodeDialogOpen(false);
    setCreatorCodeInput('');
    setCreatorCodeError(null);
    setRecentTapTimestamps([]);
  };

  return {
    creatorMode,
    creatorCodeDialogOpen,
    creatorCodeInput,
    creatorCodeError,
    registerFaceTap,
    setCreatorCodeInput,
    submitCreatorCode,
    closeCreatorCodeDialog,
    exitCreatorMode
  };
}

export const creatorModeConstants = {
  REQUIRED_TAP_COUNT,
  TAP_WINDOW_MS,
  CREATOR_CODE
};
