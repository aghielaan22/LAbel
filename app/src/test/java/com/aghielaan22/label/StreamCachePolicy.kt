package com.aghielaan22.label

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StreamCachePolicyTest {
    @Test fun signedExpiryIsBoundedAndRefreshedEarly() {
        assertEquals(60_000L, StreamCachePolicy.validUntil(0, "120"))
        assertEquals(3_600_000L, StreamCachePolicy.validUntil(0, null))
        assertEquals(3_600_000L, StreamCachePolicy.validUntil(0, "999999"))
        assertEquals(100L, StreamCachePolicy.validUntil(100, "invalid"))
        assertEquals(100L, StreamCachePolicy.validUntil(100, Long.MAX_VALUE.toString()))
    }

    @Test fun refreshedLinksReuseOnlyMatchingRepresentations() {
        fun key(quality: String = "High", format: String? = "251", rev: String? = "1", url: String = "A") =
            StreamCachePolicy.mediaKey("song", quality, format, rev, url)
        assertEquals(key(), key(url = "B"))
        assertNotEquals(key(), key(quality = "Standard"))
        assertNotEquals(key(), key(format = "140"))
        assertNotEquals(key(), key(rev = "2"))
        assertEquals("B", key(format = null, url = "B"))
        assertEquals("B", key(rev = "", url = "B"))
    }
}