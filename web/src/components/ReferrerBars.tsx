import type { ReferrerClicks } from '../api/types';

/**
 * Where the clicks came from — magnitude across a handful of names, so horizontal bars with the
 * labels read left to right rather than a pie nobody can compare slices in.
 *
 * Every bar is the same colour on purpose. These are one series measured across categories, and
 * colouring by rank would mean the colours change meaning the moment the ranking does. Length is
 * the encoding; colour is identity, and there is only one identity here.
 */
export function ReferrerBars({ referrers }: { referrers: ReferrerClicks[] }) {
  const max = referrers.reduce((peak, row) => Math.max(peak, row.clicks), 0);

  return (
    <section className="panel panel--ticked referrers" aria-labelledby="referrers-heading">
      <header className="referrers__head">
        <p className="eyebrow">Sources</p>
        <h3 id="referrers-heading" className="referrers__title">
          Top referrers
        </h3>
      </header>

      {referrers.length === 0 ? (
        <p className="referrers__empty mono">nothing recorded yet</p>
      ) : (
        <ol className="referrers__list">
          {referrers.map((row) => (
            <li key={row.referrer} className="referrers__row">
              <div className="referrers__meta">
                <span className="referrers__name" title={row.referrer}>
                  {row.referrer === 'direct' ? 'direct / no referrer' : row.referrer}
                </span>
                <span className="referrers__count mono">{row.clicks.toLocaleString()}</span>
              </div>
              <div className="referrers__track">
                <div
                  className="referrers__bar"
                  style={{ width: `${max === 0 ? 0 : (row.clicks / max) * 100}%` }}
                />
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
