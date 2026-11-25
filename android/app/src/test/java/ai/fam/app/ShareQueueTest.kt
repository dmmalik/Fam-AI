package ai.fam.app

import ai.fam.app.data.ShareFolder
import ai.fam.app.data.ShareRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShareQueueTest {
    @Test
    fun noConsentMeansNothingIsQueued() {
        val root = folder()
        val jpeg = sample(root)
        assertFalse(ShareRules.shouldKeep(shareTraining = false, keyPresent = true))
        val queue = ShareFolder(File(root, "queue"))
        if (ShareRules.shouldKeep(shareTraining = false, keyPresent = true)) {
            queue.enqueue(jpeg, "{}")
        }
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun consentWhileOfflineKeepsThePhoto() {
        val root = folder()
        val jpeg = sample(root)
        val queue = ShareFolder(File(root, "queue"))
        assertTrue(ShareRules.shouldKeep(shareTraining = true, keyPresent = true))
        queue.enqueue(jpeg, """{"share_purpose":"model_training"}""")
        assertEquals(1, queue.pending().size)
        assertTrue(queue.metaFor(queue.pending().first()).readText().contains("model_training"))
    }

    @Test
    fun withdrawnConsentDeletesTheQueue() {
        val root = folder()
        val jpeg = sample(root)
        val queue = ShareFolder(File(root, "queue"))
        queue.enqueue(jpeg, "{}")
        queue.clear()
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun mobileNeedsTenIndianDigits() {
        assertTrue(ShareRules.mobileOk("9876543210"))
        assertTrue(ShareRules.mobileOk("919876543210"))
        assertTrue(ShareRules.mobileOk("09876543210"))
        assertFalse(ShareRules.mobileOk("12345"))
        assertFalse(ShareRules.mobileOk("5876543210"))
    }

    private fun folder(): File = File("build/share-queue-test-${System.nanoTime()}").apply { mkdirs() }

    private fun sample(root: File): File {
        val file = File(root, "leaf.jpg")
        file.writeBytes(byteArrayOf(1, 2, 3))
        return file
    }
}
