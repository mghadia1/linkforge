import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { DailyClicks } from '../api/types';

/**
 * Clicks per day — change over time, one series, so an area chart with no legend: the panel
 * heading names the series, and a legend box for a single line is noise.
 *
 * The mark colour is #e35c12 rather than the brighter ember used for buttons. Chart marks and UI
 * accents answer to different checks — this step sits inside the OKLCH lightness band for a dark
 * surface and clears the chroma floor, verified with the palette validator rather than by eye.
 */
const MARK = '#e35c12';

function shortDay(day: string): string {
  const [, month, date] = day.split('-');
  return `${month}/${date}`;
}

function fullDay(day: string): string {
  return new Date(`${day}T00:00:00Z`).toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  });
}

export function ClicksChart({ data, windowDays }: { data: DailyClicks[]; windowDays: number }) {
  const total = data.reduce((sum, day) => sum + day.clicks, 0);
  const busiest = data.reduce((max, day) => (day.clicks > max.clicks ? day : max), data[0]);
  // Every day is zero-filled by the API, so an empty chart is genuinely "no clicks", not "no data".
  const hasClicks = total > 0;

  return (
    <section className="panel panel--ticked chart-card" aria-labelledby="clicks-chart-heading">
      <header className="chart-card__head">
        <div>
          <p className="eyebrow">Clicks per day</p>
          <h3 id="clicks-chart-heading" className="chart-card__title">
            Last {windowDays} days
          </h3>
        </div>
        {hasClicks && (
          <p className="chart-card__peak mono">
            peak {busiest.clicks} on {shortDay(busiest.day)}
          </p>
        )}
      </header>

      <div className="chart-card__plot">
        <ResponsiveContainer width="100%" height={230}>
          {/* The right margin holds the last date label, which clips at narrow widths without it. */}
          <AreaChart data={data} margin={{ top: 8, right: 22, bottom: 0, left: -18 }}>
            <defs>
              <linearGradient id="ember-fill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={MARK} stopOpacity={0.42} />
                <stop offset="100%" stopColor={MARK} stopOpacity={0.02} />
              </linearGradient>
            </defs>
            {/* Grid and axes are recessive: they orient the eye, they are not the content. */}
            <CartesianGrid stroke="rgba(255,233,214,0.07)" vertical={false} />
            <XAxis
              dataKey="day"
              tickFormatter={shortDay}
              tick={{ fill: '#9a8b7d', fontSize: 10.5, fontFamily: 'IBM Plex Mono, monospace' }}
              tickLine={false}
              axisLine={{ stroke: 'rgba(255,233,214,0.16)' }}
              minTickGap={22}
            />
            <YAxis
              allowDecimals={false}
              width={46}
              tick={{ fill: '#9a8b7d', fontSize: 10.5, fontFamily: 'IBM Plex Mono, monospace' }}
              tickLine={false}
              axisLine={false}
            />
            <Tooltip
              cursor={{ stroke: '#ff8a3d', strokeWidth: 1, strokeDasharray: '3 3' }}
              content={({ active, payload }) => {
                if (!active || !payload?.length) return null;
                const point = payload[0].payload as DailyClicks;
                return (
                  <div className="tooltip">
                    <p className="tooltip__day">{fullDay(point.day)}</p>
                    <p className="tooltip__value mono">
                      {point.clicks} {point.clicks === 1 ? 'click' : 'clicks'}
                    </p>
                  </div>
                );
              }}
            />
            <Area
              // Linear, not a smoothed curve. Spline interpolation between daily counts bows the
              // line above days that were actually zero, drawing traffic that never happened.
              // These are discrete daily totals and the line has to pass through them.
              type="linear"
              dataKey="clicks"
              stroke={MARK}
              strokeWidth={2}
              fill="url(#ember-fill)"
              activeDot={{ r: 4, fill: MARK, stroke: '#100d0b', strokeWidth: 2 }}
              isAnimationActive={false}
            />
          </AreaChart>
        </ResponsiveContainer>

        {!hasClicks && (
          <p className="chart-card__empty mono">no clicks in this window yet</p>
        )}
      </div>

      {/* The same numbers in a form a screen reader can read in order. */}
      <table className="sr-only">
        <caption>Clicks per day over the last {windowDays} days</caption>
        <thead>
          <tr>
            <th scope="col">Day</th>
            <th scope="col">Clicks</th>
          </tr>
        </thead>
        <tbody>
          {data.map((day) => (
            <tr key={day.day}>
              <th scope="row">{fullDay(day.day)}</th>
              <td>{day.clicks}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
