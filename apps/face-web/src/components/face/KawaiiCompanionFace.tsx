import type { CompanionVisualState } from './companionVisualState';

export type KawaiiVariant = 'feminine' | 'masculine';

type KawaiiCompanionFaceProps = {
  variant: KawaiiVariant;
  expression?: string;
  visualState?: CompanionVisualState;
  preview?: boolean;
  speechShape?: 'neutral' | 'round' | 'wide';
};

/**
 * Lightweight layered SVG avatar. Unlike a single bitmap, the eyes, brows,
 * mouth are independently styleable for local, offline animations.
 * Both eyes live in one animated group to guarantee synchronized blinking.
 */
export function KawaiiCompanionFace({
  variant,
  expression = 'idle',
  visualState = 'idle',
  preview = false,
  speechShape = 'neutral'
}: KawaiiCompanionFaceProps) {
  const feminine = variant === 'feminine';
  const prefix = `nexus-avatar-${variant}-${preview ? 'preview' : 'live'}`;
  const color = feminine ? '#d29aff' : '#4fdcff';
  const isSpeaking = visualState === 'speaking' || expression === 'speaking';
  const isHappy = expression === 'happy';
  const isConcerned = expression === 'concerned' || visualState === 'error';
  return (
    <svg
      className={`kawaii-avatar kawaii-avatar--${variant} ${preview ? 'kawaii-avatar--preview' : ''} ${isSpeaking ? 'kawaii-avatar--speaking' : ''} kawaii-avatar--mouth-${speechShape} ${isHappy ? 'kawaii-avatar--happy' : ''} ${isConcerned ? 'kawaii-avatar--concerned' : ''} visual-${visualState}`}
      viewBox="0 0 320 320"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <radialGradient id={`${prefix}-skin`} cx="38%" cy="24%" r="82%">
          <stop offset="0%" stopColor={feminine ? '#4756ac' : '#2674bb'} />
          <stop offset="54%" stopColor="#1b3978" />
          <stop offset="100%" stopColor="#081333" />
        </radialGradient>
        <radialGradient id={`${prefix}-iris`} cx="50%" cy="75%" r="72%">
          <stop offset="0%" stopColor="#4bfaff" />
          <stop offset="45%" stopColor="#1298e9" />
          <stop offset="78%" stopColor="#174da4" />
          <stop offset="100%" stopColor="#0a194c" />
        </radialGradient>
        <linearGradient id={`${prefix}-white`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="100%" stopColor="#acddff" />
        </linearGradient>
        <linearGradient id={`${prefix}-mouth`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#100c31" />
          <stop offset="100%" stopColor="#33114e" />
        </linearGradient>
      </defs>
      <circle className="kawaii-face-glow" cx="160" cy="166" r="119" fill="none" stroke={color} strokeWidth="7" opacity=".28" />
      <circle className="kawaii-head" cx="160" cy="166" r="115" fill={`url(#${prefix}-skin)`} stroke={color} strokeWidth="3" />
      <ellipse cx="116" cy="108" rx="61" ry="37" fill="#b6dfff" opacity=".065" transform="rotate(-24 116 108)" />
      <path d="M62 219 Q72 265 142 278" stroke="#2994e4" strokeWidth="3" opacity=".35" fill="none" />
      <path d="M241 104 Q277 166 244 234" stroke="#8bc9ff" strokeWidth="3" opacity=".25" fill="none" />
      <g className="kawaii-brows" fill="none" stroke={feminine ? '#c5a7ff' : '#54e6ff'} strokeLinecap="round" strokeWidth={feminine ? 5 : 7}>
        <path d="M84 124 Q109 113 134 124" />
        <path d="M188 124 Q213 113 238 124" />
      </g>
      <g className="kawaii-eye-pair">
        {([112, 209] as const).map((cx) => (
          <g key={cx}>
            <ellipse cx={cx} cy="168" rx="34" ry="43" fill="#071a42" opacity=".4" />
            <ellipse cx={cx} cy="166" rx="30" ry="41" fill={`url(#${prefix}-white)`} stroke="#204379" strokeWidth="2" />
            <ellipse cx={cx} cy="172" rx="20" ry="30" fill={`url(#${prefix}-iris)`} stroke="#193b80" strokeWidth="2" />
            <ellipse cx={cx} cy="170" rx="10" ry="18" fill="#09133f" />
            <circle cx={cx - 8} cy="153" r="8" fill="white" />
            <circle cx={cx + 9} cy="164" r="3" fill="#cbfbff" />
            <path d={`M${cx - 29} 136 Q${cx} 113 ${cx + 29} 136`} fill="none" stroke="#080e33" strokeWidth="6" strokeLinecap="round" />
            {feminine ? <path d={`M${cx + 21} 134 l11 -8 M${cx + 25} 140 l12 -3`} stroke="#0d123b" strokeWidth="4" strokeLinecap="round" /> : null}
          </g>
        ))}
      </g>
      <g className="kawaii-mouth">
        <path className="kawaii-mouth-smile" d="M132 245 Q160 260 188 245" stroke="#8fd3ff" strokeWidth="5" strokeLinecap="round" fill="none" />
        <g className="kawaii-mouth-open">
          <path d="M127 241 Q160 250 193 241 Q194 271 160 275 Q130 271 127 241Z" fill={`url(#${prefix}-mouth)`} stroke="#080e2d" strokeWidth="3" />
          <path d="M140 261 Q160 243 181 261 Q169 272 160 272 Q150 272 140 261Z" fill="#ff74c2" />
          <path d="M132 243 Q161 248 188 243" stroke="#f3ffff" strokeWidth="5" strokeLinecap="round" />
        </g>
      </g>
    </svg>
  );
}
