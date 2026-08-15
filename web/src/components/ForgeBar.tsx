import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../api/client';
import type { Link } from '../api/types';
import { CopyButton } from './CopyButton';

/**
 * The forge. One input, one button, and the result appears in place with a heat flash rather than
 * in a toast that has vanished by the time anyone looks for it.
 */
export function ForgeBar({ onForge }: { onForge: (url: string) => Promise<Link> }) {
  const [url, setUrl] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [forged, setForged] = useState<Link | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!url.trim() || busy) return;
    setBusy(true);
    setError(null);
    try {
      const link = await onForge(url.trim());
      setForged(link);
      setUrl('');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not reach the server.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel panel--ticked forge">
      <div className="forge__glow" aria-hidden="true" />
      <form className="forge__form" onSubmit={submit}>
        <div className="field forge__field">
          <label htmlFor="forge-url">Long URL</label>
          <input
            id="forge-url"
            className="input forge__input"
            type="url"
            inputMode="url"
            placeholder="https://example.com/a/very/long/path?with=query"
            value={url}
            onChange={(event) => setUrl(event.target.value)}
            // The server validates and is the authority; this only saves a round trip.
            required
            autoComplete="off"
            spellCheck={false}
          />
        </div>
        <button type="submit" className="btn btn--hot forge__submit" disabled={busy || !url.trim()}>
          {busy ? 'Forging…' : 'Forge link'}
        </button>
      </form>

      {error && (
        <p className="notice notice--error forge__error" role="alert">
          {error}
        </p>
      )}

      {forged && (
        <div className="forge__result" key={forged.code} role="status">
          <span className="eyebrow">Ready</span>
          <a className="forge__short mono" href={forged.shortUrl} target="_blank" rel="noreferrer">
            {forged.shortUrl.replace(/^https?:\/\//, '')}
          </a>
          <span className="forge__target mono" title={forged.longUrl}>
            → {forged.longUrl}
          </span>
          <CopyButton value={forged.shortUrl} />
        </div>
      )}
    </section>
  );
}
