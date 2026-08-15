import { useEffect, useState } from 'react';

/**
 * Copying the short link is the single most common thing anyone does here, so it gets a real
 * affordance and honest feedback — including when the clipboard API is unavailable, which it is
 * on any page not served over HTTPS or localhost.
 */
export function CopyButton({ value, label = 'Copy' }: { value: string; label?: string }) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle');

  useEffect(() => {
    if (state === 'idle') return;
    const timer = setTimeout(() => setState('idle'), 1800);
    return () => clearTimeout(timer);
  }, [state]);

  return (
    <button
      type="button"
      className={`btn btn--ghost copy ${state === 'copied' ? 'copy--done' : ''}`}
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value);
          setState('copied');
        } catch {
          setState('failed');
        }
      }}
      aria-label={`${label} ${value}`}
    >
      {state === 'copied' ? 'Copied' : state === 'failed' ? 'Press ⌘C' : label}
    </button>
  );
}
