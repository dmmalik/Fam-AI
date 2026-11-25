package ai.fam.app

import ai.fam.app.data.LeafCare
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LeafCareTest {
    @Test
    fun everyLeafClassHasCareSteps() {
        val ids = File("src/main/assets/leaf/classes.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }
        assertTrue(ids.size >= 38)
        for (id in ids) {
            val care = LeafCare.forClass(id, "en")
            assertTrue(id, care.summary.isNotBlank())
            assertTrue(id, care.immediateActions.isNotEmpty())
            assertTrue(id, care.whenToSeekHelp.isNotBlank())
        }
    }

    @Test
    fun healthyLeafSaysNoSprayIsNeeded() {
        val care = LeafCare.forClass("blueberry_healthy", "en")
        assertTrue(care.treatment.any { it.contains("No spray") })
    }

    @Test
    fun hindiCareIsNotEnglish() {
        val care = LeafCare.forClass("tomato_early_blight", "hi")
        assertTrue(care.summary.any { it.code > 127 })
        assertTrue(care.immediateActions.isNotEmpty())
    }
}
