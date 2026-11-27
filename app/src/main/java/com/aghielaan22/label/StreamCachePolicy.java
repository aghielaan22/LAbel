package com.aghielaan22.label;

final class StreamCachePolicy {
    private static final long MAX_AGE_MS = 3_600_000L;
    private static final long RENEW_EARLY_MS = 60_000L;

    private StreamCachePolicy() {}

    static long validUntil(long now, String signedExpirySeconds) {
        long limit = now + MAX_AGE_MS;
        if (signedExpirySeconds == null) return limit;
        try {
            long expiry = Math.multiplyExact(Long.parseLong(signedExpirySeconds), 1000L);
            return Math.min(limit, Math.subtractExact(expiry, RENEW_EARLY_MS));
        } catch (ArithmeticException | NumberFormatException invalidExpiry) {
            return now;
        }
    }

    static String mediaKey(String song, String quality, String format, String revision, String resolvedUrl) {
        if (format == null || format.isEmpty() || revision == null || revision.isEmpty()) {
            return resolvedUrl;
        }
        return song + "|" + quality + "|" + format + "|" + revision;
    }
}