package com.mghadia.linkforge.shortcode;

/**
 * Encodes a non-negative long as a base62 string.
 *
 * <p>Why base62 of a database sequence rather than a random code: a sequence value is unique by
 * construction, so the write path needs no collision check and no retry loop. The cost is that
 * codes are sequential, so they are guessable and they leak how many links exist. That is
 * acceptable while links are public-by-design; it would not be if links were private.
 */
public final class Base62Codec {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Base62Codec() {
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative, got " + value);
        }
        if (value == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        long remaining = value;
        while (remaining > 0) {
            sb.append(ALPHABET.charAt((int) (remaining % BASE)));
            remaining /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decode(String code) {
        long value = 0;
        for (int i = 0; i < code.length(); i++) {
            int digit = ALPHABET.indexOf(code.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("not a base62 string: " + code);
            }
            value = value * BASE + digit;
        }
        return value;
    }
}
