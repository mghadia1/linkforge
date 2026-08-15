package com.mghadia.linkforge.link;

/**
 * The only two fields the redirect path needs: where to send the visitor, and which link to
 * attribute the click to.
 *
 * <p>It exists separately from {@link Link} because this is what gets cached. Caching the entity
 * would mean caching {@code createdAt} and any column added later, and would tie the cache's
 * serialized form to the persistence model.
 */
public record ResolvedLink(long id, String longUrl) {

    public static ResolvedLink of(Link link) {
        return new ResolvedLink(link.getId(), link.getLongUrl());
    }
}
