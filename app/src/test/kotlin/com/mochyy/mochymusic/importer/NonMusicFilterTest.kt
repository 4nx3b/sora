package com.mochyy.mochymusic.importer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NonMusicFilterTest {
    private val minutes = 60_000L

    @Test
    fun `an amr voice note is not a song`() {
        assertFalse(NonMusicFilter.isEligible(2 * minutes, "Voice 001.amr", "/storage/emulated/0/Notes/Voice 001.amr"))
    }

    @Test
    fun `a 3gp call recording is not a song`() {
        assertFalse(NonMusicFilter.isEligible(4 * minutes, "Call 0123.3gp", "/storage/emulated/0/Misc/Call 0123.3gp"))
    }

    @Test
    fun `a name without an extension falls back to the amr mime type`() {
        assertFalse(NonMusicFilter.isEligible(3 * minutes, "Memo", null, mimeType = "audio/amr-wb"))
    }

    @Test
    fun `an mp3 in the music folder is a song`() {
        assertTrue(NonMusicFilter.isEligible(3 * minutes, "song.mp3", "/storage/emulated/0/Music/song.mp3"))
    }

    @Test
    fun `a flac is a song whatever the extension's case`() {
        assertTrue(NonMusicFilter.isEligible(3 * minutes, "song.FLAC", null))
    }

    @Test
    fun `a clip under 30 seconds is not a song`() {
        assertFalse(NonMusicFilter.isEligible(10_000L, "jingle.mp3", "/storage/emulated/0/Music/jingle.mp3"))
    }

    @Test
    fun `an unreadable duration is not mistaken for a short clip`() {
        assertTrue(NonMusicFilter.isEligible(0L, "song.mp3", "/storage/emulated/0/Music/song.mp3"))
    }

    @Test
    fun `wav is still left out`() {
        assertFalse(NonMusicFilter.isEligible(3 * minutes, "x.wav", null))
    }

    @Test
    fun `anything in a recordings folder is left out`() {
        assertFalse(NonMusicFilter.isEligible(3 * minutes, "a.m4a", "/storage/emulated/0/Recordings/a.m4a"))
    }
}
