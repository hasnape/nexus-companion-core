export interface VoiceTranscriptEvent {
  transcript: string;
  isFinal: boolean;
}

export interface VoiceListenerAdapter {
  available: boolean;
  listening: boolean;
  start(onTranscript: (event: VoiceTranscriptEvent) => void, onError?: (message: string) => void): void;
  stop(): void;
}

interface SpeechRecognitionEventLike {
  results: ArrayLike<{
    isFinal: boolean;
    0: {
      transcript: string;
    };
  }>;
}

interface SpeechRecognitionLike {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onend: (() => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  start(): void;
  stop(): void;
}

type SpeechRecognitionCtor = new () => SpeechRecognitionLike;

export class BrowserVoiceListenerAdapter implements VoiceListenerAdapter {
  available: boolean;
  listening = false;
  private readonly ctor?: SpeechRecognitionCtor;
  private recognition?: SpeechRecognitionLike;

  constructor() {
    if (typeof window === 'undefined') {
      this.available = false;
      return;
    }
    const speechRecognition = (window as Window & { SpeechRecognition?: SpeechRecognitionCtor; webkitSpeechRecognition?: SpeechRecognitionCtor }).SpeechRecognition
      ?? (window as Window & { webkitSpeechRecognition?: SpeechRecognitionCtor }).webkitSpeechRecognition;
    this.ctor = speechRecognition;
    this.available = Boolean(speechRecognition);
  }

  start(onTranscript: (event: VoiceTranscriptEvent) => void, onError?: (message: string) => void): void {
    if (!this.available || !this.ctor || this.listening) return;
    this.recognition = new this.ctor();
    this.recognition.lang = 'fr-FR';
    this.recognition.continuous = false;
    this.recognition.interimResults = true;
    this.recognition.onresult = (event) => {
      const result = event.results[event.results.length - 1];
      onTranscript({ transcript: result[0].transcript.trim(), isFinal: result.isFinal });
    };
    this.recognition.onerror = (event) => {
      this.listening = false;
      onError?.(event.error);
    };
    this.recognition.onend = () => {
      this.listening = false;
    };
    this.listening = true;
    this.recognition.start();
  }

  stop(): void {
    if (!this.recognition || !this.listening) return;
    this.recognition.stop();
    this.listening = false;
  }
}

export class SilentVoiceListenerAdapter implements VoiceListenerAdapter {
  available = false;
  listening = false;
  start(): void {
    return;
  }
  stop(): void {
    return;
  }
}
