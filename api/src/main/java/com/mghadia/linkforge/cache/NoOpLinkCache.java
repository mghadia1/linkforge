package com.mghadia.linkforge.cache;

import com.mghadia.linkforge.link.ResolvedLink;

/**
 * Used when {@code linkforge.cache.enabled} is false.
 *
 * <p>This exists so the cache can be turned off without changing the code path around it: the
 * before/after latency measurement in the README compares the same {@code LinkService} against
 * the same database, with only this substitution in between. A benchmark that compared two
 * different code paths would not be measuring the cache.
 */
public class NoOpLinkCache implements LinkCache {

    @Override
    public Lookup lookup(String code) {
        return new Lookup.Unknown();
    }

    @Override
    public void put(String code, ResolvedLink link) {
    }

    @Override
    public void putMiss(String code) {
    }
}
