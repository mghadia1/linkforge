import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ReferrerBars } from './ReferrerBars';
import { StatTiles } from './StatTiles';
import type { Stats } from '../api/types';

describe('ReferrerBars', () => {
  it('scales bars against the largest value, not the total', () => {
    const { container } = render(
      <ReferrerBars
        referrers={[
          { referrer: 'news.ycombinator.com', clicks: 40 },
          { referrer: 'reddit.com', clicks: 10 },
        ]}
      />,
    );

    const bars = container.querySelectorAll<HTMLElement>('.referrers__bar');
    expect(bars[0].style.width).toBe('100%');
    expect(bars[1].style.width).toBe('25%');
  });

  it('spells out what a missing referrer means', () => {
    render(<ReferrerBars referrers={[{ referrer: 'direct', clicks: 3 }]} />);
    expect(screen.getByText(/direct \/ no referrer/i)).toBeInTheDocument();
  });

  it('says so when there is nothing to show', () => {
    render(<ReferrerBars referrers={[]} />);
    expect(screen.getByText(/nothing recorded yet/i)).toBeInTheDocument();
  });
});

describe('StatTiles', () => {
  const stats: Stats = {
    code: '4C92',
    longUrl: 'https://example.com',
    totalClicks: 1234,
    uniqueVisitors: 56,
    firstClickAt: null,
    lastClickAt: null,
    windowDays: 30,
    clicksByDay: [],
    topReferrers: [],
  };

  it('formats large counts and marks the estimate as an estimate', () => {
    render(<StatTiles stats={stats} />);

    expect(screen.getByText('1,234')).toBeInTheDocument();
    expect(screen.getByText(/approx · by masked IP/i)).toBeInTheDocument();
  });

  it('shows a dash rather than a fake date when there are no clicks', () => {
    render(<StatTiles stats={stats} />);
    expect(screen.getAllByText('—')).toHaveLength(2);
  });
});
