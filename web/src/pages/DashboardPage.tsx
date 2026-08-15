import { useCallback, useEffect, useState } from 'react';
import { ApiError, api } from '../api/client';
import type { Link, Stats } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ClicksChart } from '../components/ClicksChart';
import { CopyButton } from '../components/CopyButton';
import { ForgeBar } from '../components/ForgeBar';
import { LinkList } from '../components/LinkList';
import { ReferrerBars } from '../components/ReferrerBars';
import { StatTiles } from '../components/StatTiles';
import { Wordmark } from '../components/Wordmark';

const WINDOWS = [7, 30, 90];

export function DashboardPage() {
  const { session, signOut } = useAuth();
  const token = session!.token;

  const [links, setLinks] = useState<Link[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);
  const [stats, setStats] = useState<Stats | null>(null);
  const [statsError, setStatsError] = useState<string | null>(null);
  // Bumped by the retry button to re-run the stats effect.
  const [reloadStats, setReloadStats] = useState(0);
  const [windowDays, setWindowDays] = useState(30);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadLinks = useCallback(
    async (nextPage: number) => {
      try {
        const found = await api.listLinks(token, nextPage);
        setLinks(found.links);
        setPage(found.page);
        setTotalPages(found.totalPages);
        setTotalElements(found.totalElements);
        // Keep a selection if it is still on this page; otherwise fall to the newest link.
        setSelected((current) =>
          current && found.links.some((link) => link.code === current)
            ? current
            : (found.links[0]?.code ?? null),
        );
        setError(null);
      } catch (e) {
        setError(e instanceof ApiError ? e.message : 'Could not reach the server.');
      } finally {
        setLoading(false);
      }
    },
    [token],
  );

  useEffect(() => {
    void loadLinks(0);
  }, [loadLinks]);

  useEffect(() => {
    if (!selected) {
      setStats(null);
      return;
    }
    let cancelled = false;
    setStatsError(null);
    api
      .stats(token, selected, windowDays)
      .then((next) => {
        // A slow response for a link the user has already navigated away from must not overwrite
        // the stats they are actually looking at.
        if (!cancelled) setStats(next);
      })
      .catch((e) => {
        if (cancelled) return;
        // Distinguishing "failed" from "still loading" matters: both used to clear stats to null,
        // and null renders as "loading analytics…", so a failed request left the panel claiming
        // to be loading forever with nothing on its way.
        setStats(null);
        setStatsError(e instanceof ApiError ? e.message : 'Could not reach the server.');
      });
    return () => {
      cancelled = true;
    };
  }, [token, selected, windowDays, reloadStats]);

  const selectedLink = links.find((link) => link.code === selected) ?? null;

  return (
    <div className="shell">
      <header className="topbar">
        <Wordmark />
        <div className="topbar__right">
          <span className="topbar__email mono">{session!.email}</span>
          <button type="button" className="btn btn--ghost" onClick={signOut}>
            Sign out
          </button>
        </div>
      </header>

      <main className="board stagger">
        <ForgeBar
          onForge={async (url) => {
            const link = await api.createLink(token, url);
            await loadLinks(0);
            setSelected(link.code);
            return link;
          }}
        />

        {error && (
          <p className="notice notice--error" role="alert">
            {error}
          </p>
        )}

        <div className="board__split">
          <LinkList
            links={links}
            selected={selected}
            onSelect={setSelected}
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            onPage={(next) => void loadLinks(next)}
          />

          <div className="detail">
            {loading ? (
              <p className="detail__idle mono">loading…</p>
            ) : !selectedLink ? (
              <p className="detail__idle mono">
                select a link to see where its clicks are coming from
              </p>
            ) : (
              <>
                <section className="panel detail__head">
                  <div className="detail__id">
                    <p className="eyebrow">Selected</p>
                    <a
                      className="detail__short mono"
                      href={selectedLink.shortUrl}
                      target="_blank"
                      rel="noreferrer"
                    >
                      {selectedLink.shortUrl.replace(/^https?:\/\//, '')}
                    </a>
                    <p className="detail__target mono" title={selectedLink.longUrl}>
                      → {selectedLink.longUrl}
                    </p>
                  </div>
                  <div className="detail__actions">
                    <CopyButton value={selectedLink.shortUrl} />
                    <div
                      className="windowpick"
                      role="group"
                      aria-label="Time window for the click chart"
                    >
                      {WINDOWS.map((days) => (
                        <button
                          key={days}
                          type="button"
                          className={`windowpick__btn ${windowDays === days ? 'windowpick__btn--on' : ''}`}
                          aria-pressed={windowDays === days}
                          onClick={() => setWindowDays(days)}
                        >
                          {days}d
                        </button>
                      ))}
                    </div>
                  </div>
                </section>

                {stats ? (
                  <>
                    <StatTiles stats={stats} />
                    <ClicksChart data={stats.clicksByDay} windowDays={stats.windowDays} />
                    <ReferrerBars referrers={stats.topReferrers} />
                  </>
                ) : statsError ? (
                  <div className="notice notice--error detail__failed" role="alert">
                    <span>{statsError}</span>
                    <button
                      type="button"
                      className="btn btn--ghost"
                      onClick={() => setReloadStats((n) => n + 1)}
                    >
                      Retry
                    </button>
                  </div>
                ) : (
                  <p className="detail__idle mono">loading analytics…</p>
                )}
              </>
            )}
          </div>
        </div>
      </main>

      <footer className="footswitch mono">
        <span>LinkForge — portfolio build, not a production service</span>
      </footer>
    </div>
  );
}
