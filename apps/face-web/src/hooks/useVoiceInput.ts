import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { defaultCompanionVoiceProfile, hasFrenchVoice, selectPreferredVoice } from '../services/voice/voiceProfile';
import { isWakePhrase, stripWakePhrasePrefix, wakeStateLabel, type WakeListeningState } from './useWakePhrase';

export type VoiceDiagnosticEvent = { at: string; type: string; detail: string };

type SpeechRecognitionLike = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  start: () => void;
  stop: () => void;
  onresult: ((event: { resultIndex: number; results: ArrayLike<ArrayLike<{ transcript: string } & { confidence?: number }> & { isFinal?: boolean }> }) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
  onstart: (() => void) | null;
  abort?: () => void;
};

type SpeechRecognitionCtor = new () => SpeechRecognitionLike;

const getSpeechRecognitionCtor = (): SpeechRecognitionCtor | null => {
  const browserWindow = window as Window & {
    SpeechRecognition?: SpeechRecognitionCtor;
    webkitSpeechRecognition?: SpeechRecognitionCtor;
  };
  return browserWindow.SpeechRecognition ?? browserWindow.webkitSpeechRecognition ?? null;
};

/**
 * Android recognizers can repeatedly emit the entire accumulated sentence.
 * Only forward new, word-separated material; ignore repeats and interim
 * revisions instead of treating a 200-character transcript as a new command.
 */
export const extractRecognitionIncrement = (previous: string, current: string): string => {
  const before = previous.trim();
  const after = current.trim();
  if (!after || after === before) return '';
  if (before && after.startsWith(before)) {
    const suffix = after.slice(before.length);
    return /^(?:[\s,;:.!?-]+|$)/.test(suffix) ? suffix.replace(/^[\s,;:.!?-]+/, '').trim() : '';
  }
  if (before && before.startsWith(after) && after.length >= 8) return '';
  let common = 0;
  while (common < Math.min(before.length, after.length) && before[common] === after[common]) common += 1;
  // A revision in the middle of a long cumulative result is not a new utterance.
  if (common >= 24 && common >= Math.min(before.length, after.length) * 0.6) return '';
  return after;
};

type UseVoiceInputParams = {
  onCommand: (text: string) => Promise<void>;
  onWake: () => void;
  requestCamera?: boolean;
  isCompanionSpeaking?: boolean;
};

export const useVoiceInput = ({ onCommand, onWake, requestCamera = false, isCompanionSpeaking = false }: UseVoiceInputParams) => {
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const sessionEnabledRef = useRef(false);
  const fatalErrorRef = useRef(false);
  const recognitionRunningRef = useRef(false);
  const restartTimerRef = useRef<number | null>(null);
  const restartCountRef = useRef(0);
  const resultSeenSinceStartRef = useRef(false);
  const handledFinalResultsRef = useRef(new Set<string>());
  const lastFinalTranscriptRef = useRef('');
  const playbackPauseReasonsRef = useRef(new Set<'ack' | 'reply'>());
  const ackTimeoutRef = useRef<number | null>(null);

  const [isSessionActive, setSessionActive] = useState(false);
  const [mediaState, setMediaState] = useState({ micActive: false, cameraActive: false });
  const [transcript, setTranscript] = useState('');
  const [acknowledgementSpeaking, setAcknowledgementSpeaking] = useState(false);
  const [recognizerStatus, setRecognizerStatus] = useState<'inactive' | 'starting' | 'listening' | 'restarting' | 'paused'>('inactive');
  const [showLocalTranscript, setShowLocalTranscript] = useState(false);
  const [diagnosticsEnabled, setDiagnosticsEnabled] = useState(false);
  const diagnosticsEnabledRef = useRef(false);
  const [diagnosticEvents, setDiagnosticEvents] = useState<VoiceDiagnosticEvent[]>([]);
  const logDiagnostic = useCallback((type: string, detail = '') => {
    if (!diagnosticsEnabledRef.current) return;
    setDiagnosticEvents((prev) => [...prev.slice(-79), { at: new Date().toISOString(), type, detail }]);
  }, []);
  const setVoiceDiagnosticsEnabled = useCallback((enabled: boolean) => {
    diagnosticsEnabledRef.current = enabled;
    setDiagnosticsEnabled(enabled);
    setDiagnosticEvents([]);
  }, []);
  const clearVoiceDiagnostics = useCallback(() => setDiagnosticEvents([]), []);
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
    window.speechSynthesis.addEventListener('voiceschanged', refreshVoiceInfo);
    return () => {
      window.speechSynthesis.removeEventListener('voiceschanged', refreshVoiceInfo);
    };
  }, []);

  const clearRestartTimer = useCallback(() => {
    if (restartTimerRef.current) {
      window.clearTimeout(restartTimerRef.current);
      restartTimerRef.current = null;
    }
  }, []);

  const stopAndClearMediaStream = useCallback(() => {
    mediaStreamRef.current?.getTracks().forEach((track) => track.stop());
    mediaStreamRef.current = null;
    setMediaState({ micActive: false, cameraActive: false });
  }, []);

  const cleanupMediaSession = useCallback((options?: { setWakeInactive?: boolean }) => {
    logDiagnostic('session:stop');
    sessionEnabledRef.current = false;
    recognitionRunningRef.current = false;
    setSessionActive(false);
    setRecognizerStatus('inactive');
    setShowLocalTranscript(false);
    setTranscript('');
    setAcknowledgementSpeaking(false);
    if (ackTimeoutRef.current !== null) window.clearTimeout(ackTimeoutRef.current);
    ackTimeoutRef.current = null;
    playbackPauseReasonsRef.current.clear();
    lastFinalTranscriptRef.current = '';
    restartCountRef.current = 0;
    resultSeenSinceStartRef.current = false;
    handledFinalResultsRef.current.clear();
    clearRestartTimer();
    try {
      recognitionRef.current?.stop();
    } catch {
      // no-op: cleanup must remain idempotent and continue releasing media resources.
    } finally {
      stopAndClearMediaStream();
    }
    if (options?.setWakeInactive ?? true) {
      setWakeListeningState('inactive');
    }
  }, [clearRestartTimer, logDiagnostic, setWakeListeningState, stopAndClearMediaStream]);

  const scheduleRestart = useCallback(() => {
    clearRestartTimer();
    if (!sessionEnabledRef.current || fatalErrorRef.current || !recognitionRef.current || playbackPauseReasonsRef.current.size) return;
    if (restartCountRef.current >= 5) {
      setWakeListeningState('error');
      cleanupMediaSession({ setWakeInactive: false });
      setWakeListeningState('error');
      setListenerError('La reconnaissance vocale s’arrête sans transcrire de paroles. Vérifiez le service vocal Android ou essayez un autre moteur de reconnaissance.');
      logDiagnostic('recognition:stopped-no-results', 'limite de relances atteinte');
      return;
    }
    restartCountRef.current += 1;
    setRecognizerStatus('restarting');
    logDiagnostic('recognition:restart-scheduled', String(restartCountRef.current));
    restartTimerRef.current = window.setTimeout(() => {
      if (!sessionEnabledRef.current || fatalErrorRef.current || !recognitionRef.current) return;
      try {
        logDiagnostic('recognition:restart-attempt');
        recognitionRef.current.start();
      } catch {
        setWakeListeningState('error');
        setListenerError('Erreur micro');
        cleanupMediaSession({ setWakeInactive: false });
      }
    }, 550);
  }, [cleanupMediaSession, clearRestartTimer, logDiagnostic, setWakeListeningState]);

  const pauseForPlayback = useCallback((reason: 'ack' | 'reply') => {
    if (!sessionEnabledRef.current || playbackPauseReasonsRef.current.has(reason)) return;
    playbackPauseReasonsRef.current.add(reason);
    clearRestartTimer();
    setRecognizerStatus('paused');
    logDiagnostic('recognition:paused-for-speech', reason);
    if (recognitionRunningRef.current) {
      try {
        // abort discards any audio still buffered while Nexus starts talking.
        if (recognitionRef.current?.abort) recognitionRef.current.abort();
        else recognitionRef.current?.stop();
      } catch {
        // The browser may already have ended this recognition segment.
      }
      recognitionRunningRef.current = false;
    }
  }, [clearRestartTimer, logDiagnostic]);

  const resumeAfterPlayback = useCallback((reason: 'ack' | 'reply') => {
    if (!playbackPauseReasonsRef.current.delete(reason) || playbackPauseReasonsRef.current.size) return;
    if (!sessionEnabledRef.current || fatalErrorRef.current) return;
    restartCountRef.current = 0;
    logDiagnostic('recognition:resumed-after-speech', reason);
    scheduleRestart();
  }, [logDiagnostic, scheduleRestart]);

  useEffect(() => {
    if (isCompanionSpeaking) pauseForPlayback('reply');
    else resumeAfterPlayback('reply');
  }, [isCompanionSpeaking, pauseForPlayback, resumeAfterPlayback]);

  const speakAcknowledgement = useCallback(() => {
    if (!('speechSynthesis' in window)) return;
    const utterance = new SpeechSynthesisUtterance('Je t’écoute.');
    const voices = window.speechSynthesis.getVoices();
    const preferredVoice = selectPreferredVoice(voices);
    if (preferredVoice) utterance.voice = preferredVoice;
    utterance.lang = defaultCompanionVoiceProfile.language;
    utterance.rate = defaultCompanionVoiceProfile.rate;
    utterance.pitch = defaultCompanionVoiceProfile.pitch;
    utterance.volume = defaultCompanionVoiceProfile.volume;
    const finish = () => {
      if (ackTimeoutRef.current !== null) window.clearTimeout(ackTimeoutRef.current);
      ackTimeoutRef.current = null;
      setAcknowledgementSpeaking(false);
      resumeAfterPlayback('ack');
    };
    utterance.onstart = () => setAcknowledgementSpeaking(true);
    utterance.onend = finish;
    utterance.onerror = finish;
    pauseForPlayback('ack');
    ackTimeoutRef.current = window.setTimeout(finish, 4000);
    try {
      window.speechSynthesis.speak(utterance);
    } catch {
      finish();
    }
  }, [pauseForPlayback, resumeAfterPlayback]);

  const stopListeningSession = useCallback(() => {
    cleanupMediaSession();
  }, [cleanupMediaSession]);

  const handleFinalTranscript = useCallback(async (nextTranscript: string) => {
    if (!nextTranscript || !sessionEnabledRef.current || playbackPauseReasonsRef.current.size) return;

    const currentWakeState = wakeStateRef.current;
    logDiagnostic('wake:state', currentWakeState);

    if (currentWakeState === 'waiting_for_wake_phrase') {
      if (isWakePhrase(nextTranscript)) {
        logDiagnostic('wake:detected');
        setWakeListeningState('awake_listening_for_command');
        onWakeRef.current();
        speakAcknowledgement();
        return;
      }

      const strippedAtWake = stripWakePhrasePrefix(nextTranscript);
      if (strippedAtWake && strippedAtWake !== nextTranscript.trim()) {
        logDiagnostic('wake:command-prefixed');
        setWakeListeningState('processing_command');
        onWakeRef.current();
        try {
          await onCommandRef.current(strippedAtWake);
          if (sessionEnabledRef.current) setWakeListeningState('waiting_for_wake_phrase');
        } catch {
          setWakeListeningState('error');
          setListenerError('Erreur micro');
          return;
        }
      }
      // Do not retain private transcript text in diagnostics. This marker
      // distinguishes a recognition result from a matched wake phrase.
      logDiagnostic('wake:not-matched', 'caractères=' + nextTranscript.length);
      return;
    }

    if (currentWakeState === 'awake_listening_for_command') {
      const command = stripWakePhrasePrefix(nextTranscript) || nextTranscript;
      logDiagnostic('command:recognized');
      setWakeListeningState('processing_command');
      try {
        await onCommandRef.current(command);
        if (sessionEnabledRef.current) setWakeListeningState('waiting_for_wake_phrase');
      } catch {
        setWakeListeningState('error');
        setListenerError('Erreur micro');
      }
    }
  }, [logDiagnostic, setWakeListeningState, speakAcknowledgement]);

  const startListeningSession = useCallback(async () => {
    logDiagnostic('session:start-request');
    setListenerError(null);
    setTranscript('');
    fatalErrorRef.current = false;

    cleanupMediaSession();

    if (!speechRecognitionCtor) {
      setWakeListeningState('error');
      setListenerError('La reconnaissance vocale n’est pas disponible sur ce navigateur.');
      return;
    }

    if (navigator.mediaDevices?.getUserMedia) {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: requestCamera });
        logDiagnostic('permission:micro-granted');
        // SpeechRecognition acquires its own audio input. Keeping a second
        // getUserMedia microphone stream open can compete for capture on Android.
        // Permission is checked with a short-lived stream, then released before
        // starting recognition. A camera stream remains active only when requested.
        stream.getAudioTracks().forEach((track) => track.stop());
        if (requestCamera && stream.getVideoTracks().some((track) => track.readyState === 'live' && track.enabled)) {
          mediaStreamRef.current = stream;
        } else {
          stream.getVideoTracks().forEach((track) => track.stop());
          mediaStreamRef.current = null;
        }
        logDiagnostic('permission:micro-released-before-recognition');
        setMediaState({
          micActive: false,
          cameraActive: Boolean(mediaStreamRef.current)
        });
      } catch {
        logDiagnostic('permission:micro-failed');
        cleanupMediaSession({ setWakeInactive: false });
        setWakeListeningState('error');
        setListenerError('Autorisation micro/caméra refusée.');
        return;
      }
    }

    if (!recognitionRef.current) {
      recognitionRef.current = new speechRecognitionCtor();
      recognitionRef.current.lang = defaultCompanionVoiceProfile.language;
      recognitionRef.current.continuous = true;
      recognitionRef.current.interimResults = true;

      recognitionRef.current.onresult = (event) => {
        if (playbackPauseReasonsRef.current.size) return;
        // Some Android engines batch several results in a single callback.
        // Process every new final result, not just the first resultIndex.
        for (let index = event.resultIndex; index < event.results.length; index += 1) {
          const result = event.results[index];
          const nextTranscript = result?.[0]?.transcript?.trim() ?? '';
          if (!nextTranscript) continue;
          resultSeenSinceStartRef.current = true;
          restartCountRef.current = 0;
          setTranscript(nextTranscript.slice(-250));
          if (!result.isFinal) continue;
          const key = String(index) + ':' + nextTranscript;
          if (handledFinalResultsRef.current.has(key)) continue;
          handledFinalResultsRef.current.add(key);
          const increment = extractRecognitionIncrement(lastFinalTranscriptRef.current, nextTranscript);
          lastFinalTranscriptRef.current = nextTranscript;
          if (!increment) continue;
          if (increment.length > 180) {
            logDiagnostic('recognition:long-final-ignored', 'caractères=' + increment.length);
            continue;
          }
          logDiagnostic('recognition:final', 'caractères=' + increment.length);
          void handleFinalTranscript(increment);
        }
      };

      recognitionRef.current.onstart = () => {
        logDiagnostic('recognition:onstart');
        if (!sessionEnabledRef.current) return;
        recognitionRunningRef.current = true;
        if (playbackPauseReasonsRef.current.size) {
          setRecognizerStatus('paused');
          try { recognitionRef.current?.abort?.() ?? recognitionRef.current?.stop(); } catch { /* already stopped */ }
          return;
        }
        setRecognizerStatus('listening');
        resultSeenSinceStartRef.current = false;
        handledFinalResultsRef.current.clear();
        setListenerError(null);
        // A browser recognizer restart must not erase a successful wake.
        if (wakeStateRef.current === 'inactive' || wakeStateRef.current === 'error') {
          setWakeListeningState('waiting_for_wake_phrase');
        }
      };

      recognitionRef.current.onerror = (event) => {
        logDiagnostic('recognition:onerror', event.error);
        recognitionRunningRef.current = false;
        setRecognizerStatus('restarting');
        if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
          fatalErrorRef.current = true;
          cleanupMediaSession({ setWakeInactive: false });
          setWakeListeningState('error');
          setListenerError('Autorisation micro refusée.');
          return;
        }
        if (event.error === 'no-speech' || event.error === 'aborted') return;
        if (event.error === 'network' || event.error === 'audio-capture' || event.error === 'language-not-supported') {
          fatalErrorRef.current = true;
          cleanupMediaSession({ setWakeInactive: false });
          setWakeListeningState('error');
          setListenerError(event.error === 'network'
            ? 'Reconnaissance vocale indisponible : service réseau du navigateur. Essayez Chrome ou Samsung Internet directement.'
            : event.error === 'audio-capture'
              ? 'Aucune entrée audio accessible. Vérifiez les autorisations du microphone.'
              : 'Langue française non prise en charge par ce navigateur.');
          return;
        }
        setWakeListeningState('error');
        setListenerError('Erreur de reconnaissance vocale (' + event.error + ').');
      };

      recognitionRef.current.onend = () => {
        logDiagnostic('recognition:onend', resultSeenSinceStartRef.current ? 'résultat reçu' : 'aucun résultat');
        recognitionRunningRef.current = false;
        setRecognizerStatus(playbackPauseReasonsRef.current.size ? 'paused' : sessionEnabledRef.current ? 'restarting' : 'inactive');
        if (sessionEnabledRef.current && !fatalErrorRef.current && !playbackPauseReasonsRef.current.size) {
          scheduleRestart();
        }
      };
    }

    sessionEnabledRef.current = true;
    setSessionActive(true);
    setRecognizerStatus('starting');
    setWakeListeningState('inactive');
    try {
      logDiagnostic('recognition:start-attempt');
      recognitionRef.current.start();
    } catch {
      cleanupMediaSession({ setWakeInactive: false });
      setWakeListeningState('error');
      setListenerError('Erreur micro');
    }
  }, [cleanupMediaSession, handleFinalTranscript, logDiagnostic, requestCamera, scheduleRestart, setWakeListeningState, speechRecognitionCtor]);

  useEffect(() => () => {
    stopListeningSession();
  }, [stopListeningSession]);

  return {
    voiceInputAvailable,
    diagnosticsEnabled,
    diagnosticEvents,
    setVoiceDiagnosticsEnabled,
    clearVoiceDiagnostics,
    isSessionActive,
    mediaState,
    startListeningSession,
    stopListeningSession,
    transcript,
    acknowledgementSpeaking,
    recognizerStatus,
    showLocalTranscript,
    setShowLocalTranscript,
    listenerError,
    wakeState,
    wakeStatus: wakeStateLabel(wakeState),
    voiceProfile: defaultCompanionVoiceProfile,
    voiceProfileLabel: usesFrenchVoice ? 'Voix française détectée' : 'Voix par défaut du navigateur'
  };
};
