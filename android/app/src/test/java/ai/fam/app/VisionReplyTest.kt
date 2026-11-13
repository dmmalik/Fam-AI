package ai.fam.app

import ai.fam.app.data.DownloadGate
import ai.fam.app.data.LabelInfo
import ai.fam.app.data.PhoneModelFiles
import ai.fam.app.data.VisionReplyParser
import ai.fam.app.data.farmerSpeech
import ai.fam.app.data.shownPlantName
import ai.fam.app.data.chooseDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionReplyTest {
    @Test
    fun jsonNamesAKnownLabel() {
        val reply = VisionReplyParser.parse(
            """
            {"label_id":"tomato_early_blight","plant":"tomato","part":"leaf","disease":"early blight","confidence":0.82,"summary":"Brown rings on the leaf."}
            """.trimIndent(),
            labels(),
        )
        assertEquals("tomato_early_blight", reply?.labelId)
        assertEquals("tomato_early_blight", reply?.known(labels())?.id)
        assertEquals(0.82, reply?.confidence ?: 0.0, 0.0001)
        assertEquals("Tomato early blight", reply?.displayName(reply.known(labels())))
    }

    @Test
    fun fencedJsonAndAPercentStillParse() {
        val raw = """
            Here is the photo.
            ```json
            {"label_id":"pest_stem_borer","plant":"maize","part":"stem","disease":"stem borer","confidence":85,"summary":"A hole in the stem."}
            ```
        """.trimIndent()
        val reply = VisionReplyParser.parse(raw, labels())
        assertEquals("pest_stem_borer", reply?.labelId)
        assertEquals("stem", reply?.part)
        assertEquals(0.85, reply?.confidence ?: 0.0, 0.0001)
    }

    @Test
    fun displayNameMapsToTheLabelId() {
        val reply = VisionReplyParser.parse(
            """{"label_id":"Tomato early blight","plant":"tomato","part":"leaf","disease":"early blight","confidence":0.4,"summary":"Spots."}""",
            labels(),
        )
        assertEquals("tomato_early_blight", reply?.labelId)
    }

    @Test
    fun unknownPlantKeepsTheModelWords() {
        val reply = VisionReplyParser.parse(
            """{"label_id":"unknown","plant":"sorghum","part":"stem","disease":"charcoal rot","confidence":0.33,"summary":"The stem is dark inside."}""",
            labels(),
        )
        assertEquals("unknown", reply?.labelId)
        assertNull(reply?.known(labels()))
        assertEquals("sorghum — charcoal rot", reply?.displayName(null))
        assertEquals("The stem is dark inside.", reply?.summary)
    }

    @Test
    fun bambooOutsideTheListIsNamed() {
        val reply = VisionReplyParser.parse(
            """{"label_id":"bamboo","plant":"","part":"plant","disease":"unknown","confidence":0.7,"summary":"This is bamboo. The leaves look green.","immediate_actions":["Leave it if the leaves stay green."],"treatment":[],"prevention":[],"when_to_seek_help":"Ask someone local if the stems turn yellow."}""",
            labels(),
        )
        assertEquals("unknown", reply?.labelId)
        assertEquals("bamboo", reply?.displayName(null))
        assertEquals("", shownPlantName("unknown", "unknown"))
        val speech = farmerSpeech("bamboo", reply?.replyText("en"))
        assertTrue(speech.startsWith("bamboo"))
        assertTrue(speech.contains("leaves look green"))
        assertTrue(speech.contains("Leave it"))
    }

    @Test
    fun emptyUnknownAndProseAreUnusable() {
        assertNull(
            VisionReplyParser.parse(
                """{"label_id":"unknown","plant":"","part":"","disease":"","confidence":0.1,"summary":""}""",
                labels(),
            ),
        )
        assertNull(VisionReplyParser.parse("I am not sure about this leaf.", labels()))
    }

    @Test
    fun e4bFileWinsWhenBothArePresent() {
        assertEquals(
            PhoneModelFiles.E4B,
            PhoneModelFiles.pick(listOf(PhoneModelFiles.E2B, "notes.txt", PhoneModelFiles.E4B)),
        )
        assertEquals(PhoneModelFiles.E2B, PhoneModelFiles.pick(listOf(PhoneModelFiles.E2B)))
        assertEquals("gemma-4-e4b", PhoneModelFiles.modelId(PhoneModelFiles.E4B))
        assertEquals("gemma-4-e2b", PhoneModelFiles.modelId(PhoneModelFiles.E2B))
        assertNull(PhoneModelFiles.pick(emptyList()))
        assertNull(PhoneModelFiles.pick(listOf(PhoneModelFiles.E4B + PhoneModelFiles.PARTIAL_SUFFIX)))
        assertEquals(42, PhoneModelFiles.downloadPercent(42, 100))
        assertEquals(100, PhoneModelFiles.downloadPercent(3_700_000_000L, 3_650_000_000L))
        assertNull(PhoneModelFiles.downloadPercent(10, 0))
        assertEquals("0 MB", PhoneModelFiles.sizeText(0))
        assertEquals("120 MB", PhoneModelFiles.sizeText(120_000_000L))
        assertEquals(2_000_000L, PhoneModelFiles.speedBps(2_000_000L, 1_000_000_000L))
        assertNull(PhoneModelFiles.speedBps(1_000L, 500_000_000L))
        assertEquals("2.5 MB/s", PhoneModelFiles.speedText(2_500_000L))
        assertEquals("120 KB/s", PhoneModelFiles.speedText(120_000L))
        assertEquals("0 KB/s", PhoneModelFiles.speedText(0L))
        assertEquals("3.66 GB", PhoneModelFiles.sizeText(3_659_530_240L))
        assertEquals(3_659_530_240L, PhoneModelFiles.rangeTotal("bytes 0-15/3659530240"))
        assertNull(PhoneModelFiles.rangeTotal("bytes 0-15/*"))
        assertTrue(PhoneModelFiles.sameModel(PhoneModelFiles.E4B, 100L, PhoneModelFiles.E4B, 100L))
        assertTrue(!PhoneModelFiles.sameModel(PhoneModelFiles.E4B, 100L, PhoneModelFiles.E4B, 101L))
        assertTrue(!PhoneModelFiles.sameModel(null, 100L, PhoneModelFiles.E4B, 100L))
    }

    @Test
    fun downloadStaysOnWifiUntilTheUserAllowsMore() {
        assertEquals(DownloadGate.RUN, chooseDownload(true, metered = false, foreground = true, backgroundAllowed = false, mobileAllowed = false, dataSaver = false))
        assertEquals(DownloadGate.UNMETERED, chooseDownload(true, metered = true, foreground = true, backgroundAllowed = true, mobileAllowed = false, dataSaver = false))
        assertEquals(DownloadGate.BACKGROUND, chooseDownload(true, metered = false, foreground = false, backgroundAllowed = false, mobileAllowed = false, dataSaver = false))
        assertEquals(DownloadGate.SAVER, chooseDownload(true, metered = false, foreground = false, backgroundAllowed = true, mobileAllowed = true, dataSaver = true))
        assertEquals(DownloadGate.WAIT, chooseDownload(false, metered = false, foreground = true, backgroundAllowed = true, mobileAllowed = true, dataSaver = false))
        assertEquals(DownloadGate.RUN, chooseDownload(true, metered = true, foreground = false, backgroundAllowed = true, mobileAllowed = true, dataSaver = false))
    }

    @Test
    fun promptNamesTheStemAndALabel() {
        val prompt = VisionReplyParser.prompt(labels(), language = "hi", crop = "maize")
        assertTrue(prompt.contains("stem"))
        assertTrue(prompt.contains("pest_stem_borer"))
        assertTrue(prompt.contains("Hindi"))
        assertTrue(prompt.contains("maize"))
        assertTrue(prompt.contains("immediate_actions"))
        assertTrue(prompt.contains("when_to_seek_help"))
        assertTrue(prompt.contains("bamboo"))
        assertTrue(prompt.contains("Do not write the word unknown"))
        val tamil = VisionReplyParser.prompt(labels(), language = "ta", crop = null)
        assertTrue(tamil.contains("Tamil"))
        assertTrue(tamil.contains("in Tamil"))
    }

    @Test
    fun replyStepsComeFromTheModel() {
        val reply = VisionReplyParser.parse(
            """
            {"label_id":"unknown","plant":"sorghum","part":"stem","disease":"charcoal rot","confidence":0.4,"summary":"The stem is dark inside.","immediate_actions":["Cut out the dark stem.","Keep the field drier.","Ignore this third step."],"treatment":["Remove sick plants."],"prevention":["Rotate the crop."],"when_to_seek_help":"Ask a local officer if it spreads."}
            """.trimIndent(),
            labels(),
        )
        assertEquals(listOf("Cut out the dark stem.", "Keep the field drier."), reply?.immediate)
        assertEquals(listOf("Remove sick plants."), reply?.treatment)
        assertEquals(listOf("Rotate the crop."), reply?.prevention)
        val advice = reply?.replyText("en")
        assertEquals("The stem is dark inside.", advice?.summary)
        assertEquals("Ask a local officer if it spreads.", advice?.whenToSeekHelp)
    }

    private fun labels(): List<LabelInfo> {
        return listOf(
            LabelInfo("tomato_early_blight", "tomato", "disease", "Tomato early blight"),
            LabelInfo("pest_stem_borer", "maize", "pest", "Stem borer"),
            LabelInfo("tomato_healthy", "tomato", "disease", "Healthy tomato"),
        )
    }
}
