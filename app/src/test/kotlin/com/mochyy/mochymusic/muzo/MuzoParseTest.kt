package com.mochyy.mochymusic.muzo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MuzoParseTest {

    @Test
    fun streamAnswerCarriesBothUrls() {
        val s = MuzoEndpoints.parseStreams("""{"losslessUrl":"https://x/a.flac","m4aUrl":"https://x/a.m4a"}""")!!
        assertEquals("https://x/a.flac", s.losslessUrl)
        assertEquals("https://x/a.m4a", s.m4aUrl)
    }

    @Test
    fun streamAnswerWithoutUrlsIsNothing() {
        assertNull(MuzoEndpoints.parseStreams("""{"videoId":"dQw4w9WgXcQ"}"""))
    }

    @Test
    fun queuePageReadsTracksAndContinuation() {
        val page = MuzoEndpoints.parseQueue(
            """{"tracks":[{"videoId":"dQw4w9WgXcQ","title":"T","artists":[{"name":"A"}],"duration":"3:32"}],"continuation":"c1"}""",
        )!!
        assertEquals("c1", page.continuation)
        val track = page.tracks.single()
        assertEquals("dQw4w9WgXcQ", track.id)
        assertEquals("T", track.title)
        assertEquals(listOf("A"), track.artists)
        assertEquals(212, track.durationSec)
    }

    @Test
    fun addAnswerSplitsTheThreeLists() {
        val r = MuzoEndpoints.parseAdd("""{"queued":["aaaaaaaaaaa"],"processing":["bbbbbbbbbbb"],"cached":["ccccccccccc"]}""")!!
        assertEquals(listOf("aaaaaaaaaaa"), r.queued)
        assertEquals(listOf("bbbbbbbbbbb"), r.processing)
        assertEquals(listOf("ccccccccccc"), r.cached)
    }

    @Test
    fun aRateLimitHoldsUntilMidnightUtc() {
        val day = 24L * 60 * 60 * 1000
        val now = 10 * day + 5_000
        MuzoEndpoints.hitLimit(now)
        assertTrue(MuzoEndpoints.limited(now + 1))
        assertFalse(MuzoEndpoints.limited(11 * day))
    }
}
