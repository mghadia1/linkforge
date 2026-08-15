import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import type { Link, PagedLinks, Stats } from '../api/types';

const link: Link = {
  code: '4C92',
  shortUrl: 'http://localhost:8080/4C92',
  longUrl: 'https://example.com/one',
  createdAt: '2026-08-15T02:30:51Z',
};

const page: PagedLinks = { links: [link], page: 0, size: 20, totalElements: 1, totalPages: 1 };

const stats: Stats = {
  code: '4C92',
  longUrl: 'https://example.com/one',
  totalClicks: 12,
  uniqueVisitors: 4,
  firstClickAt: '2026-08-15T02:00:00Z',
  lastClickAt: '2026-08-15T02:30:00Z',
  windowDays: 30,
  clicksByDay: [{ day: '2026-08-15', clicks: 12 }],
  topReferrers: [{ referrer: 'direct', clicks: 12 }],
};

const listLinks = vi.fn();
const statsCall = vi.fn();

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return {
    ...actual,
    api: {
      listLinks: (...args: unknown[]) => listLinks(...args),
      stats: (...args: unknown[]) => statsCall(...args),
      createLink: vi.fn(),
      register: vi.fn(),
      login: vi.fn(),
    },
  };
});

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    session: { token: 't', email: 'demo@example.com', expiresAt: '2099-01-01T00:00:00Z' },
    signOut: vi.fn(),
    signIn: vi.fn(),
    signUp: vi.fn(),
  }),
  AuthProvider: ({ children }: { children: React.ReactNode }) => children,
}));

const { DashboardPage } = await import('./DashboardPage');

beforeEach(() => {
  listLinks.mockReset().mockResolvedValue(page);
  statsCall.mockReset();
});

describe('DashboardPage analytics panel', () => {
  it('renders the stats once they arrive', async () => {
    statsCall.mockResolvedValue(stats);
    render(<DashboardPage />);

    // Scoped to the stat tile: the chart's screen-reader table also contains this number.
    const tile = (await screen.findByText('Total clicks')).closest('li')!;
    expect(within(tile).getByText('12')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /top referrers/i })).toBeInTheDocument();
  });

  /**
   * Regression: a failed stats request used to clear stats to null, and null renders the loading
   * message — so the panel claimed to be loading forever with nothing on its way.
   */
  it('shows an error instead of loading forever when stats fail', async () => {
    statsCall.mockRejectedValue(new ApiError(500, 'the server exploded'));
    render(<DashboardPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('the server exploded');
    expect(screen.queryByText(/loading analytics/i)).not.toBeInTheDocument();
  });

  it('can retry after a failure', async () => {
    statsCall.mockRejectedValueOnce(new ApiError(500, 'the server exploded'));
    statsCall.mockResolvedValue(stats);
    render(<DashboardPage />);

    await userEvent.click(await screen.findByRole('button', { name: /retry/i }));

    await waitFor(() => expect(screen.getByText('Total clicks')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
