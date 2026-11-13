package ai.fam.app

import ai.fam.app.data.DemoClassifier
import ai.fam.app.data.LabelInfo
import ai.fam.app.data.LeafSample
import ai.fam.app.data.PiAddress
import ai.fam.app.data.Severity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoLogicTest {
    @Test
    fun diseaseCutsMatchTheLeafAreaBands() {
        assertEquals("low", Severity.disease(0.0))
        assertEquals("low", Severity.disease(0.09))
        assertEquals("medium", Severity.disease(0.10))
        assertEquals("medium", Severity.disease(0.30))
        assertEquals("high", Severity.disease(0.31))
    }

    @Test
    fun pestCutsUseConfidence() {
        assertEquals("low", Severity.pest(0.59))
        assertEquals("medium", Severity.pest(0.60))
        assertEquals("medium", Severity.pest(0.849))
        assertEquals("high", Severity.pest(0.85))
        assertEquals("low", Severity.of("tomato_healthy", "disease", 0.9, 0.99))
        assertEquals("high", Severity.of("pest_aphids", "pest", 0.0, 0.90))
    }

    @Test
    fun greenLeafMapsToAHealthyClass() {
        val pick = DemoClassifier.classify(green(), labels(), crop = "tomato")
        assertTrue(pick.label.id.endsWith("_healthy"))
        assertEquals("tomato_healthy", pick.label.id)
        assertEquals("low", pick.severity)
        assertEquals("on-device demo", DemoClassifier.BACKEND)
    }

    @Test
    fun yellowLeafMapsToAVirusLabel() {
        val sample = LeafSample(
            green = 0.40,
            yellow = 0.42,
            brown = 0.08,
            orange = 0.05,
            dark = 0.05,
            white = 0.0,
            lesionRatio = 0.55,
        )
        val pick = DemoClassifier.classify(sample, labels(), crop = null)
        assertTrue(
            pick.label.id in setOf(
                "tomato_yellow_leaf_curl",
                "tomato_mosaic_virus",
                "cassava_green_mottle",
                "cassava_mosaic",
                "tomato_spider_mite",
            ),
        )
    }

    @Test
    fun rustColorOnMaizeMapsToCommonRust() {
        val sample = LeafSample(
            green = 0.55,
            yellow = 0.05,
            brown = 0.08,
            orange = 0.28,
            dark = 0.04,
            white = 0.0,
            lesionRatio = 0.40,
        )
        val pick = DemoClassifier.classify(sample, labels(), crop = "maize")
        assertEquals("maize_common_rust", pick.label.id)
        assertEquals("high", pick.severity)
    }

    @Test
    fun whiteSpecksMapToWhitefly() {
        val sample = LeafSample(
            green = 0.70,
            yellow = 0.08,
            brown = 0.08,
            orange = 0.04,
            dark = 0.10,
            white = 0.20,
            lesionRatio = 0.20,
        )
        val pick = DemoClassifier.classify(sample, labels(), crop = "tomato")
        assertEquals("pest_whitefly", pick.label.id)
    }

    @Test
    fun heavyBrownLesionIsNotHealthy() {
        val sample = LeafSample(
            green = 0.25,
            yellow = 0.05,
            brown = 0.45,
            orange = 0.05,
            dark = 0.20,
            white = 0.0,
            lesionRatio = 0.75,
        )
        val pick = DemoClassifier.classify(sample, labels(), crop = null)
        assertFalse(pick.label.id.endsWith("_healthy"))
        assertEquals("high", pick.severity)
    }

    @Test
    fun addressesStayOnTheLocalPi() {
        val named = PiAddress.parse("farm-ai.local")
        assertNotNull(named)
        assertEquals("farm-ai.local", named!!.requestHost())
        assertEquals(8080, named.port)
        val lan = PiAddress.parse("192.168.4.1:8080")
        assertNotNull(lan)
        assertEquals("farm-ai.local", lan!!.requestHost())
        assertEquals("192.168.4.1", lan.pinLiteral)
        assertNull(PiAddress.parse("https://example.com"))
        assertNull(PiAddress.parse("8.8.8.8"))
        assertNull(PiAddress.parse("http://user:secret@192.168.4.1"))
    }

    @Test
    fun bundledNotesMatchTheLabelContract() {
        val labels = labels()
        val treatments = File("src/main/assets/kb/treatments.json").readText()
        val regions = File("src/main/assets/kb/regions.json").readText()
        val ids = labels.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val crops = labels.map { it.crop }.toSet()
        for (label in labels) {
            assertTrue(treatments.contains("\"${label.id}\""))
            for (language in listOf("en", "hi", "sw")) {
                assertTrue(treatments.contains("\"$language\""))
            }
        }
        for (crop in listOf("tomato", "potato", "cassava", "maize", "apple")) {
            assertTrue(crops.contains(crop))
            assertTrue(ids.any { it.startsWith(crop + "_") && it.endsWith("_healthy") })
        }
        for (pest in listOf("pest_aphids", "pest_fall_armyworm", "pest_whitefly", "pest_stem_borer")) {
            assertTrue(ids.contains(pest))
        }
        for (region in listOf("indo_gangetic", "peninsular_india", "east_african_highlands")) {
            assertTrue(regions.contains("\"$region\""))
            assertTrue(regions.contains("\"sw\""))
        }
    }

    private fun green(): LeafSample {
        return LeafSample(
            green = 0.92,
            yellow = 0.03,
            brown = 0.02,
            orange = 0.02,
            dark = 0.01,
            white = 0.0,
            lesionRatio = 0.05,
        )
    }

    private fun labels(): List<LabelInfo> {
        val id = Regex(""""id"\s*:\s*"([^"]+)"""")
        val crop = Regex(""""crop"\s*:\s*"([^"]+)"""")
        val kind = Regex(""""kind"\s*:\s*"([^"]+)"""")
        val name = Regex(""""display_name"\s*:\s*"([^"]+)"""")
        return File("src/main/assets/kb/labels.json").readLines().mapNotNull { line ->
            val labelId = id.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
            LabelInfo(
                id = labelId,
                crop = crop.find(line)?.groupValues?.get(1) ?: return@mapNotNull null,
                kind = kind.find(line)?.groupValues?.get(1) ?: return@mapNotNull null,
                displayName = name.find(line)?.groupValues?.get(1) ?: return@mapNotNull null,
            )
        }
    }
}
