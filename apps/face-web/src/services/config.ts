export const config = {
  companionName: import.meta.env.VITE_COMPANION_NAME ?? 'Nexus Companion',
  enableTTS: import.meta.env.VITE_ENABLE_TTS !== 'false',
  enableCamera: import.meta.env.VITE_ENABLE_CAMERA === 'true',
  devSimulatedPresence: import.meta.env.VITE_DEV_SIMULATED_PRESENCE !== 'false'
};
