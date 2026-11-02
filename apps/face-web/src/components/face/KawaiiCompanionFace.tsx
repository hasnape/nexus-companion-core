import type { CompanionVisualState } from './companionVisualState';

export type KawaiiVariant = 'feminine' | 'masculine';

type KawaiiCompanionFaceProps = {
  variant: KawaiiVariant;
  expression?: string;
  visualState?: CompanionVisualState;
  preview?: boolean;
};

/**
 * Lightweight layered SVG avatar. Unlike a single bitmap, the eyes, brows,
 * mouth and hair are independently styleable for local, offline animations.
 * Both eyes live in one animated group to guarantee synchronized blinking.
 */
export function KawaiiCompanionFace({
  variant,
  expression = 'idle',
  visualState = 'idle',
  preview = false
}: KawaiiCompanionFaceProps) {
  const feminine = variant === 'feminine';
  const prefix = `nexus-avatar-${variant}-${preview ? 'preview' : 'live'}`;
  const color = feminine ? '#d29aff' : '#4fdcff';
  const hairTone = feminine ? '#7a42df' : '#126fe1';
  const isSpeaking = visualState === 'speaking' || expression === 'speaking';
  const isHappy = expression === 'happy';
  const isConcerned = expression === 'concerned' || visualState === 'error';
  return (
    <svg
      className={`kawaii-avatar kawaii-avatar--${variant} ${preview ? 'kawaii-avatar--preview' : ''} ${isSpeaking ? 'kawaii-avatar--speaking' : ''} ${isHappy ? 'kawaii-avatar--happy' : ''} ${isConcerned ? 'kawaii-avatar--concerned' : ''} visual-${visualState}`}
      viewBox="0 0 320 320"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <radialGradient id={`${prefix}-skin`} cx="35%" cy="25%" r="78%">
          <stop offset="0%" stopColor={feminine ? '#35478f' : '#20579c'} />
          <stop offset="55%" stopColor="#152b61" />
          <stop offset="100%" stopColor="#07183f" />
        </radialGradient>
        <linearGradient id={`${prefix}-hair`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor={feminine ? '#f3a7ff' : '#67e9ff'} />
          <stop offset="35%" stopColor={hairTone} />
          <stop offset="100%" stopColor="#133d99" />
        </linearGradient>
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
      <g className="kawaii-hair-back">
        {feminine ? (
          <>
            <ellipse cx="221" cy="61" rx="42" ry="32" fill="#18317f" stroke="#9868ff" strokeWidth="4" />
            <path d="M245 74 Q287 105 282 173 Q270 211 257 216" stroke="#6e3db5" strokeWidth="23" fill="none" strokeLinecap="round" />
            <path d="M76 91 Q31 135 45 194 Q49 216 66 228" stroke="#723ac0" strokeWidth="25" fill="none" strokeLinecap="round" />
          </>
        ) : (
          <path d="M68 116 L35 91 L79 83 L62 41 L107 61 L117 20 L149 53 L181 12 L191 58 L239 30 L231 69 L281 54 L257 93 L287 117 Q249 71 169 69 Q98 64 68 116Z" fill={`url(#${prefix}-hair)`} stroke="#35caff" strokeWidth="3" strokeLinejoin="round" />
        )}
      </g>
      <circle className="kawaii-head" cx="160" cy="166" r="115" fill={`url(#${prefix}-skin)`} stroke={color} strokeWidth="3" />
      <path d="M62 219 Q72 265 142 278" stroke="#2994e4" strokeWidth="3" opacity=".35" fill="none" />
      <path d="M241 104 Q277 166 244 234" stroke="#8bc9ff" strokeWidth="3" opacity=".25" fill="none" />
      {feminine ? (
        <g className="kawaii-hair-top">
          <path d="M56 124 Q43 72 100 45 Q161 7 233 58 Q251 81 241 103 Q214 79 194 65 Q149 96 101 94 Q82 119 56 124Z" fill={`url(#${prefix}-hair)`} stroke="#bf7cff" strokeWidth="3" />
          <path d="M64 106 Q117 36 207 47 Q166 71 124 84 Q100 93 64 106Z" fill="#10265b" opacity=".66" />
          <path d="M53 114 Q105 51 205 36" stroke="#eab7ff" strokeWidth="7" strokeLinecap="round" fill="none" />
          <path d="M86 92 Q118 56 171 57" stroke="#a1ecff" strokeWidth="3" opacity=".9" fill="none" />
          <path d="M232 79 Q259 103 253 143" fill="none" stroke="#965bff" strokeWidth="13" strokeLinecap="round" />
        </g>
      ) : (
        <g className="kawaii-hair-top">
          <path d="M55 106 Q91 58 117 67 L109 37 Q140 54 154 62 L183 19 L187 62 L242 33 L225 78 L274 67 L249 108 Q223 80 196 77 Q165 72 132 81 Q91 87 55 106Z" fill={`url(#${prefix}-hair)`} stroke="#36d8ff" strokeWidth="3" strokeLinejoin="round" />
          <path d="M75 96 Q115 63 143 78 Q183 59 228 53" stroke="#84efff" strokeWidth="5" strokeLinecap="round" fill="none" opacity=".86" />
          <path d="M143 72 Q168 42 181 31" stroke="#168be9" strokeWidth="7" strokeLinecap="round" />
        </g>
      )}
      <g className="kawaii-brows" fill="none" stroke={feminine ? '#c5a7ff' : '#54e6ff'} strokeLinecap="round" strokeWidth={feminine ? 5 : 7}>
        <path d="M82 121 Q109 107 135 124" />
        <path d="M187 124 Q214 107 240 121" />
      </g>
      <g className="kawaii-eye-pair">
        {([112, 209] as const).map((cx) => (
          <g key={cx}>
            <ellipse cx={cx} cy="168" rx="34" ry="43" fill="#071a42" opacity=".6" />
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
      <g className="kawaii-cheeks">
        <ellipse cx="72" cy="219" rx="21" ry="9" fill={feminine ? '#ff61b1' : '#22dfff'} opacity=".77" />
        <ellipse cx="247" cy="219" rx="21" ry="9" fill={feminine ? '#ff61b1' : '#22dfff'} opacity=".77" />
        {feminine ? (
          <g stroke="#ffc8ec" strokeWidth="3" strokeLinecap="round">
            <path d="M63 214 l-7 9 M73 213 l-7 11 M83 214 l-7 9 M238 214 l-7 9 M248 213 l-7 11 M258 214 l-7 9" />
          </g>
        ) : null}
      </g>
      <g className="kawaii-mouth">
        <path className="kawaii-mouth-smile" d="M124 244 Q160 279 196 244" stroke="#8fd3ff" strokeWidth="5" strokeLinecap="round" fill="none" />
        <g className="kawaii-mouth-open">
          <path d="M127 241 Q160 250 193 241 Q194 271 160 275 Q130 271 127 241Z" fill={`url(#${prefix}-mouth)`} stroke="#080e2d" strokeWidth="3" />
          <path d="M140 261 Q160 243 181 261 Q169 272 160 272 Q150 272 140 261Z" fill="#ff74c2" />
          <path d="M132 243 Q161 248 188 243" stroke="#f3ffff" strokeWidth="5" strokeLinecap="round" />
        </g>
      </g>
    </svg>
  );
}
