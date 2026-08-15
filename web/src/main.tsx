import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

// Fonts are bundled, not fetched from a CDN: the app works offline, nothing leaks a request to a
// third party on load, and there is no flash of fallback type while a stylesheet resolves.
import '@fontsource-variable/bricolage-grotesque';
import '@fontsource/ibm-plex-mono/400.css';
import '@fontsource/ibm-plex-mono/500.css';

import './styles/global.css';
import './styles/app.css';
import { App } from './App';
import { AuthProvider } from './auth/AuthContext';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </StrictMode>,
);
