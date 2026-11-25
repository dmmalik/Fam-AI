package ai.fam.app.data

import java.io.File
import java.util.UUID

object ShareRules {
    const val PURPOSE = "model_training"
    const val CONSENT_VERSION = "2026-11-16"
    const val ORIGIN = "https://createmybills.tech"

    fun mobileOk(raw: String): Boolean = localMobile(raw).length == 10

    fun localMobile(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        val local = when {
            digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
            digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
            else -> digits
        }
        if (local.length != 10 || local[0] !in '6'..'9') return ""
        return local
    }

    fun shouldKeep(shareTraining: Boolean, keyPresent: Boolean): Boolean {
        return shareTraining && keyPresent
    }
}

class ShareFolder(private val root: File) {
    fun enqueue(jpeg: File, metaJson: String): File {
        root.mkdirs()
        val id = UUID.randomUUID().toString()
        val dest = File(root, "$id.jpg")
        jpeg.copyTo(dest, overwrite = true)
        File(root, "$id.json").writeText(metaJson)
        return dest
    }

    fun pending(): List<File> {
        return root.listFiles { file -> file.isFile && file.extension == "jpg" }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    fun metaFor(jpeg: File): File = File(root, jpeg.nameWithoutExtension + ".json")

    fun remove(jpeg: File) {
        jpeg.delete()
        metaFor(jpeg).delete()
    }

    fun clear() {
        root.listFiles()?.forEach { it.delete() }
    }
}

data class ShareDraft(
    val capturedAt: String,
    val language: String,
    val region: String,
    val modelId: String,
    val modelVersion: String,
    val backend: String,
    val leafLabel: String,
    val leafConfidence: Double,
    val leafNext: String,
    val careJson: String,
    val writtenJson: String,
)
