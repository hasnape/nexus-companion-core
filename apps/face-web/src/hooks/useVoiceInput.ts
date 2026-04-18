import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { defaultCompanionVoiceProfile, hasFrenchVoice, selectPreferredVoice } from '../services/voice/voiceProfile';
import { isWakePhrase, wakeStateLabel, type WakeListeningState } from './useWakePhrase';

type SpeechRecognitionLike = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  start: () => void;
  stop: () => void;
  onresult: ((event: { resultIndex: number; results: ArrayLike<ArrayLike<{ transcript: string } & { confidence?: number }> & { isFinal?: boolean }> }) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
};

type SpeechRecognitionCtor = new () => SpeechRecognitionLike;

const getSpeechRecognitionCtor = (): SpeechRecognitionCtor | null => {
  const browserWindow = window as Window & {
    SpeechRecognition?: SpeechRecognitionCtor;
    webkitSpeechRecognition?: SpeechRecognitionCtor;
  };
  return browserWindow.SpeechRecognition ?? browserWindow.webkitSpeechRecognition ?? null;
};

type UseVoiceInputParams = {
  onCommand: (text: string) => Promise<void>;
  onWake: () => void;
};

export const useVoiceInput = ({ onCommand, onWake }: UseVoiceInputParams) => {
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const sessionEnabledRef = useRef(false);
  const fatalErrorRef = useRef(false);
  const restartTimerRef = useRef<number | null>(null);
  const restartCountRef = useRef(0);

  const [isSessionActive, setSessionActive] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [listenerError, setListenerError] = useState<string | null>(null);
  const [wakeState, setWakeState] = useState<WakeListeningState>('inactive');
  const wakeStateRef = useRef<WakeListeningState>('inactive');
  const onCommandRef = useRef(onCommand);
  const onWakeRef = useRef(onWake);
  const [usesFrenchVoice, setUsesFrenchVoice] = useState(false);

  const speechRecognitionCtor = useMemo(() => getSpeechRecognitionCtor(), []);
  const voiceInputAvailable = Boolean(speechRecognitionCtor);

  const setWakeListeningState = useCallback((next: WakeListeningState) => {
    wakeStateRef.current = next;
    setWakeState(next);
  }, []);

  const speakAcknowledgement = useCallback(() => {
    if (!('speechSynthesis' in window)) return;
    const utterance = new SpeechSynthesisUtterance('Je suis là.');
    const voices = window.speechSynthesis.getVoices();
    const preferredVoice = selectPreferredVoice(voices);
    if (preferredVoice) utterance.voice = preferredVoice;
    utterance.lang = defaultCompanionVoiceProfile.language;
    utterance.rate = defaultCompanionVoiceProfile.rate;
    utterance.pitch = defaultCompanionVoiceProfile.pitch;
    utterance.volume = defaultCompanionVoiceProfile.volume;
    window.speechSynthesis.speak(utterance);
  }, []);


  useEffect(() => {
    wakeStateRef.current = wakeState;
  }, [wakeState]);

  useEffect(() => {
    onCommandRef.current = onCommand;
  }, [onCommand]);

  useEffect(() => {
    onWakeRef.current = onWake;
  }, [onWake]);

  useEffect(() => {
    if (!('speechSynthesis' in window)) return;
    const refreshVoiceInfo = () => {
      const voices = window.speechSynthesis.getVoices();
      setUsesFrenchVoice(hasFrenchVoice(voices));
    };
    refreshVoiceInfo();
    window.speechSynthesis.onvoiceschanged = refreshVoiceInfo;
    return () => {
      window.speechSynthesis.onvoiceschanged = null;
    };
  }, []);

  const clearRestartTimer = () => {
    if (restartTimerRef.current) {
      window.clearTimeout(restartTimerRef.current);
      restartTimerRef.current = null;
    }
  };

  const scheduleRestart = useCallback(() => {
    clearRestartTimer();
    if (!sessionEnabledRef.current || fatalErrorRef.current || !recognitionRef.current) return;
    if (restartCountRef.current >= 5) {
      setWakeListeningState('error');
      setListenerError('Erreur micro');
      sessionEnabledRef.current = false;
      setSessionActive(false);
      return;
    }
    restartCountRef.current += 1;
    restartTimerRef.current = window.setTimeout(() => {
      if (!sessionEnabledRef.current || fatalErrorRef.current || !recognitionRef.current) return;
      try {
        setListenerError(null);
        setWakeListeningState('waiting_for_wake_phrase');
        recognitionRef.current.start();
      } catch {
        setWakeListeningState('error');
        setListenerError('Erreur micro');
      }
    }, 550);
  }, [setWakeListeningState]);

  const stopListeningSession = useCallback(() => {
    sessionEnabledRef.current = false;
    setSessionActive(false);
    setWakeListeningState('inactive');
    restartCountRef.current = 0;
    clearRestartTimer();
    recognitionRef.current?.stop();
  }, [setWakeListeningState]);

  const handleFinalTranscript = useCallback(async (nextTranscript: string) => {
    if (!nextTranscript || !sessionEnabledRef.current) return;

    const currentWakeState = wakeStateRef.current;

    if (currentWakeState === 'waiting_for_wake_phrase' && isWakePhrase(nextTranscript)) {
      setWakeListeningState('awake_listening_for_command');
      onWakeRef.current();
      speakAcknowledgement();
      return;
    }

    if (currentWakeState === 'awake_listening_for_command') {
      setWakeListeningState('processing_command');
      try {
        await onCommandRef.current(nextTranscript);
        if (sessionEnabledRef.current) setWakeListeningState('waiting_for_wake_phrase');
      } catch {
        setWakeListeningState('error');
        setListenerError('Erreur micro');
      }
    }
  }, [setWakeListeningState, speakAcknowledgement]);

  const startListeningSession = useCallback(() => {
    setListenerError(null);
    setTranscript('');
    fatalErrorRef.current = false;
    restartCountRef.current = 0;

    if (!speechRecognitionCtor) {
      setWakeListeningState('error');
      setListenerError('La reconnaissance vocale n’est pas disponible sur ce navigateur.');
      return;
    }

    if (!recognitionRef.current) {
      recognitionRef.current = new speechRecognitionCtor();
      recognitionRef.current.lang = defaultCompanionVoiceProfile.language;
      recognitionRef.current.continuous = true;
      recognitionRef.current.interimResults = true;

      recognitionRef.current.onresult = (event) => {
        const result = event.results[event.resultIndex];
        const nextTranscript = result?.[0]?.transcript?.trim() ?? '';
        setTranscript(nextTranscript);
        const isFinal = Boolean((result as { isFinal?: boolean } | undefined)?.isFinal);
        if (isFinal) {
          void handleFinalTranscript(nextTranscript);
        }
      };

      recognitionRef.current.onerror = (event) => {
        if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
          fatalErrorRef.current = true;
          sessionEnabledRef.current = false;
          setSessionActive(false);
          setWakeListeningState('error');
          setListenerError('Autorisation micro refusée.');
          return;
        }
        setWakeListeningState('error');
        setListenerError('Erreur micro');
      };

      recognitionRef.current.onend = () => {
        if (sessionEnabledRef.current && !fatalErrorRef.current) {
          scheduleRestart();
        }
      };
    }

    sessionEnabledRef.current = true;
    setSessionActive(true);
    setWakeListeningState('waiting_for_wake_phrase');
    try {
      recognitionRef.current.start();
    } catch {
      setWakeListeningState('error');
      setListenerError('Erreur micro');
    }
  }, [handleFinalTranscript, scheduleRestart, setWakeListeningState, speechRecognitionCtor]);

  useEffect(() => () => {
    stopListeningSession();
  }, [stopListeningSession]);

  return {
    voiceInputAvailable,
    isSessionActive,
    startListeningSession,
    stopListeningSession,
    transcript,
    listenerError,
    wakeState,
    wakeStatus: wakeStateLabel(wakeState),
    voiceProfile: defaultCompanionVoiceProfile,
    voiceProfileLabel: usesFrenchVoice ? 'Voix française détectée' : 'Voix par défaut du navigateur'
  };
};
