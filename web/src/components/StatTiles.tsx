import type { Stats } from '../api/types';

/**
 * Four headline numbers. A single value has no shape to plot, so it gets a tile, not a chart.
 */
function when(iso: string | null): string {
  if (!iso) return '—';
  const then = new Date(iso).getTime();
  const seconds = Math.max(0, Math.round((Date.now() - then) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.round(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.round(seconds / 3600)}h ago`;
  return `${Math.round(seconds / 86400)}d ago`;
}

export function StatTiles({ stats }: { stats: Stats }) {
  const tiles = [
    { label: 'Total clicks', value: stats.totalClicks.toLocaleString(), note: 'all time' },
    {
      label: 'Unique visitors',
      value: stats.uniqueVisitors.toLocaleString(),
      // The API anonymises addresses before storing them, so this genuinely is an estimate. Saying
      // so on the tile is cheaper than someone quoting it as exact.
      note: 'approx · by masked IP',
    },
    { label: 'First click', value: when(stats.firstClickAt), note: 'since created' },
    { label: 'Last click', value: when(stats.lastClickAt), note: 'most recent' },
  ];

  return (
    <ul className="tiles">
      {tiles.map((tile) => (
        <li key={tile.label} className="panel tile">
          <p className="eyebrow">{tile.label}</p>
          <p className="tile__value mono">{tile.value}</p>
          <p className="tile__note mono">{tile.note}</p>
        </li>
      ))}
    </ul>
  );
}
