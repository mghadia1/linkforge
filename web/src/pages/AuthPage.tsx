import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Wordmark } from '../components/Wordmark';

type Mode = 'signin' | 'signup';

export function AuthPage() {
  const { signIn, signUp } = useAuth();
  const [mode, setMode] = useState<Mode>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await (mode === 'signin' ? signIn(email, password) : signUp(email, password));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not reach the server.');
      setBusy(false);
    }
  }

  return (
    <main className="gate">
      {/* Left: the pitch, set large. Right: the work. The asymmetry is the point — a centred card
          on an empty page is the shape every generated login screen has. */}
      <section className="gate__pitch">
        <Wordmark />
        <h1 className="gate__headline">
          Short links,
          <br />
          <em>hammered</em> into shape.
        </h1>
        <p className="gate__blurb">
          Paste a long URL, get a short code, and watch every click land — counted by day, by
          source, without slowing the redirect down.
        </p>
        <ul className="gate__marks">
          <li>
            <span className="mono">302</span> redirect, cached in Redis
          </li>
          <li>
            <span className="mono">async</span> click logging, never inline
          </li>
          <li>
            <span className="mono">JWT</span> auth, links private to you
          </li>
        </ul>
      </section>

      <section className="panel panel--ticked gate__card">
        <div className="gate__tabs" role="tablist" aria-label="Sign in or create an account">
          {(['signin', 'signup'] as Mode[]).map((value) => (
            <button
              key={value}
              type="button"
              role="tab"
              aria-selected={mode === value}
              className={`gate__tab ${mode === value ? 'gate__tab--on' : ''}`}
              onClick={() => {
                setMode(value);
                setError(null);
              }}
            >
              {value === 'signin' ? 'Sign in' : 'Create account'}
            </button>
          ))}
        </div>

        <form className="gate__form stagger" onSubmit={submit}>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              className="input"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
              autoComplete="email"
              placeholder="you@example.com"
            />
          </div>

          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              className="input"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              minLength={mode === 'signup' ? 8 : undefined}
              autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
              placeholder={mode === 'signup' ? 'at least 8 characters' : '••••••••'}
            />
          </div>

          {error && (
            <p className="notice notice--error" role="alert">
              {error}
            </p>
          )}

          <button type="submit" className="btn btn--hot gate__submit" disabled={busy}>
            {busy ? 'Working…' : mode === 'signin' ? 'Sign in' : 'Create account'}
          </button>

          <p className="gate__fine mono">
            {mode === 'signin'
              ? 'No account? Switch to Create account.'
              : 'Passwords are hashed with BCrypt. Nothing else is stored.'}
          </p>
        </form>
      </section>
    </main>
  );
}
