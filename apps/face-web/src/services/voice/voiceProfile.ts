export type CompanionVoiceProfile = {
  name: 'Compagnon Nexus';
  language: 'fr-FR';
  tone: 'warm, calm, reassuring';
  speakingStyle: 'short, natural, friendly';
  rate: number;
  pitch: number;
  volume: number;
};

export const defaultCompanionVoiceProfile: CompanionVoiceProfile = {
  name: 'Compagnon Nexus',
  language: 'fr-FR',
  tone: 'warm, calm, reassuring',
  speakingStyle: 'short, natural, friendly',
  rate: 0.95,
  pitch: 1,
  volume: 1
};

export const selectPreferredVoice = (voices: SpeechSynthesisVoice[]): SpeechSynthesisVoice | null => {
  if (voices.length === 0) return null;
  const frenchVoices = voices.filter((voice) => voice.lang.toLowerCase().startsWith('fr'));
  if (frenchVoices.length > 0) {
    const frenchDefault = frenchVoices.find((voice) => voice.default);
    return frenchDefault ?? frenchVoices[0];
  }
  const browserDefault = voices.find((voice) => voice.default);
  return browserDefault ?? voices[0];
};

export const hasFrenchVoice = (voices: SpeechSynthesisVoice[]): boolean => voices.some((voice) => voice.lang.toLowerCase().startsWith('fr'));
