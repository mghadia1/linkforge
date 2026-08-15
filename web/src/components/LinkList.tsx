import type { Link } from '../api/types';

function created(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

export function LinkList({
  links,
  selected,
  onSelect,
  page,
  totalPages,
  totalElements,
  onPage,
}: {
  links: Link[];
  selected: string | null;
  onSelect: (code: string) => void;
  page: number;
  totalPages: number;
  totalElements: number;
  onPage: (page: number) => void;
}) {
  return (
    <section className="panel links" aria-labelledby="links-heading">
      <header className="links__head">
        <div>
          <p className="eyebrow">Your links</p>
          <h2 id="links-heading" className="links__title">
            {totalElements} forged
          </h2>
        </div>
      </header>

      {links.length === 0 ? (
        <p className="links__empty mono">
          nothing forged yet — paste a URL above to make your first short link
        </p>
      ) : (
        <ul className="links__list">
          {links.map((link) => {
            const isSelected = link.code === selected;
            return (
              <li key={link.code}>
                <button
                  type="button"
                  className={`links__row ${isSelected ? 'links__row--on' : ''}`}
                  onClick={() => onSelect(link.code)}
                  aria-current={isSelected ? 'true' : undefined}
                  // The row's visible text is three separate fragments; spelling the name out
                  // gives a screen reader one coherent label instead of a run-on.
                  aria-label={`Link ${link.code}, ${link.longUrl}`}
                >
                  <span className="links__code mono">{link.code}</span>
                  <span className="links__target" title={link.longUrl}>
                    {link.longUrl.replace(/^https?:\/\//, '')}
                  </span>
                  <span className="links__date mono">{created(link.createdAt)}</span>
                </button>
              </li>
            );
          })}
        </ul>
      )}

      {totalPages > 1 && (
        <footer className="links__pager">
          <button
            type="button"
            className="btn btn--ghost"
            onClick={() => onPage(page - 1)}
            disabled={page === 0}
          >
            Prev
          </button>
          <span className="mono links__pageinfo">
            {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            className="btn btn--ghost"
            onClick={() => onPage(page + 1)}
            disabled={page + 1 >= totalPages}
          >
            Next
          </button>
        </footer>
      )}
    </section>
  );
}
