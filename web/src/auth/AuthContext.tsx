import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { api, setUnauthorizedHandler } from '../api/client';

const STORAGE_KEY = 'linkforge.session';

interface Session {
  token: string;
  email: string;
  expiresAt: string;
}

interface AuthValue {
  session: Session | null;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (email: string, password: string) => Promise<void>;
  signOut: () => void;
}

const AuthContext = createContext<AuthValue | null>(null);

/**
 * The token lives in localStorage, which is the usual choice for a bearer token and the usual
 * criticism of one: anything that manages to run script on this origin can read it, where an
 * HttpOnly cookie could not. The honest trade is that a cookie would need CSRF protection the API
 * does not implement. Noted in the README rather than papered over.
 */
function readStoredSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Session;
    if (!parsed.token || !parsed.expiresAt) return null;
    // Drop a token that has already expired rather than letting the first request fail with it.
    if (new Date(parsed.expiresAt).getTime() <= Date.now()) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(readStoredSession);

  const signOut = useCallback(() => {
    setSession(null);
    localStorage.removeItem(STORAGE_KEY);
  }, []);

  // The server is the authority on whether a token is still good; if it says no, sign out.
  useEffect(() => {
    setUnauthorizedHandler(signOut);
  }, [signOut]);

  const start = useCallback((token: string, expiresAt: string, email: string) => {
    const next = { token, expiresAt, email };
    setSession(next);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  }, []);

  const value = useMemo<AuthValue>(
    () => ({
      session,
      signIn: async (email, password) => {
        const issued = await api.login(email, password);
        start(issued.token, issued.expiresAt, email);
      },
      signUp: async (email, password) => {
        const issued = await api.register(email, password);
        start(issued.token, issued.expiresAt, email);
      },
      signOut,
    }),
    [session, signOut, start],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
