/**
 * The mark: a struck anvil edge. Drawn inline so it inherits the ember token and needs no request.
 */
export function Wordmark({ compact = false }: { compact?: boolean }) {
  return (
    <span className="wordmark">
      <svg className="wordmark__glyph" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <path
          d="M3 15h7l3-6 3 4h5"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="square"
        />
        <path d="M3 19h18" fill="none" stroke="currentColor" strokeWidth="2" opacity="0.35" />
      </svg>
      <span className="wordmark__text">
        Link<span className="wordmark__accent">forge</span>
      </span>
      {!compact && <span className="wordmark__tag mono">v0.1</span>}
    </span>
  );
}
