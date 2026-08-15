import { Suspense, lazy } from 'react';
import { useAuth } from './auth/AuthContext';
import { AuthPage } from './pages/AuthPage';

/**
 * The dashboard is loaded on demand. It pulls in the charting library, which is by far the largest
 * dependency here, and a signed-out visitor has no use for it — so the sign-in screen ships
 * without it and paints sooner.
 */
const DashboardPage = lazy(() =>
  import('./pages/DashboardPage').then((module) => ({ default: module.DashboardPage })),
);

export function App() {
  const { session } = useAuth();
  if (!session) return <AuthPage />;
  return (
    <Suspense fallback={<p className="detail__idle mono boot">warming the forge…</p>}>
      <DashboardPage />
    </Suspense>
  );
}
