package ai.fam.app

import ai.fam.app.data.LeafScores
import org.junit.Assert.assertEquals
import org.junit.Test

class LeafScoresTest {
    @Test
    fun topClassIsTheLargestLogit() {
        val names = listOf("apple_scab", "tomato_healthy", "maize_common_rust")
        val ranked = LeafScores.top(floatArrayOf(1f, 4f, 0f), names)
        assertEquals("tomato_healthy", ranked.first().id)
        assertEquals(3, ranked.size)
        assertEquals(1f, ranked.sumOf { it.confidence.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun readableNameSplitsTheClassId() {
        assertEquals("Tomato Early Blight", LeafScores.readable("tomato_early_blight"))
    }
}
