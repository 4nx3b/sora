package com.mochyy.mochymusic.ui.player

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class DriftRectTest {
    private val mini = Rect(8f, 8f, 56f, 56f)
    private val open = Rect(40f, 300f, 680f, 940f)

    @Test
    fun startsAtTheMiniPlayer() = assertEquals(mini, driftRect(mini, open, 0f, 1800f))

    @Test
    fun endsInTheSlot() = assertEquals(open, driftRect(mini, open, 1f, 1800f))

    @Test
    fun noTravelIsAStraightLerp() {
        val r = driftRect(mini, open, 0.5f, 0f)
        assertEquals(Rect(24f, 154f, 368f, 498f), r)
    }

    @Test
    fun clampsProgress() = assertEquals(open, driftRect(mini, open, 1.4f, 1800f))
}
