package com.mghadia.linkforge.cache;

import com.mghadia.linkforge.link.ResolvedLink;

/**
 * A lookaside cache for the redirect path.
 *
 * <p>No method here may throw. A cache is an optimisation, and an optimisation that can take the
 * service down is a liability: if Redis is unreachable, every call must degrade to
 * {@link Lookup.Unknown} and let the caller fall through to the database.
 */
public interface LinkCache {

    /**
     * Three outcomes, not two. Distinguishing "Redis says this code does not exist" from "Redis
     * has nothing to say" is what allows unknown codes to be answered without touching the
     * database — see {@link RedisLinkCache} for why that matters.
     */
    sealed interface Lookup {

        /** The code is cached and resolves to this link. */
        record Hit(ResolvedLink link) implements Lookup {
        }

        /** The code is cached as not existing. */
        record KnownMiss() implements Lookup {
        }

        /** Nothing cached, or the cache could not be reached. Ask the database. */
        record Unknown() implements Lookup {
        }
    }

    Lookup lookup(String code);

    void put(String code, ResolvedLink link);

    /** Remembers that a code does not exist, for a deliberately shorter time than a real entry. */
    void putMiss(String code);
}
