package ai.fam.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File

data class PackNote(
    val summary: String,
    val immediateActions: List<String>,
    val treatment: List<String>,
    val prevention: List<String>,
    val whenToSeekHelp: String,
    val caution: String,
)

data class PackRegion(
    val names: String,
    val season: String,
    val irrigation: String,
    val inputAccess: String,
)

data class LanguagePack(
    val code: String,
    val version: Int,
    val treatments: Map<String, PackNote>,
    val regions: Map<String, PackRegion>,
) {
    companion object {
        fun parse(raw: String, kb: KnowledgeBase, expected: String): LanguagePack? {
            if (!expected.matches(Regex("^[a-z]{2,8}$")) || expected in STORED_LANGUAGES) return null
            return try {
                val obj = JSONObject(raw)
                val code = obj.getString("code").trim()
                if (code != expected || obj.getInt("version") != 1) return null
                val treatments = obj.getJSONObject("treatments")
                val regions = obj.getJSONObject("regions")
                val notes = LinkedHashMap<String, PackNote>()
                var same = true
                for (label in kb.labels) {
                    val block = treatments.getJSONObject(label.id)
                    val english = kb.treatments[label.id] ?: return null
                    val note = PackNote(
                        summary = text(block, "summary"),
                        immediateActions = items(block, "immediate_actions", english.immediateActions.getValue("en").size),
                        treatment = items(block, "treatment", english.treatment.getValue("en").size),
                        prevention = items(block, "prevention", english.prevention.getValue("en").size),
                        whenToSeekHelp = text(block, "when_to_seek_help"),
                        caution = text(block, "caution"),
                    )
                    if (note.summary != english.summary.getValue("en")) same = false
                    notes[label.id] = note
                }
                if (same || treatments.length() != kb.labels.size) return null
                val regionNotes = LinkedHashMap<String, PackRegion>()
                for (id in REGION_IDS) {
                    val block = regions.getJSONObject(id)
                    regionNotes[id] = PackRegion(
                        names = text(block, "names"),
                        season = text(block, "season"),
                        irrigation = text(block, "irrigation"),
                        inputAccess = text(block, "input_access"),
                    )
                }
                LanguagePack(code, 1, notes, regionNotes)
            } catch (_: Exception) {
                null
            }
        }

        private fun text(block: JSONObject, key: String): String {
            val value = block.getString(key).trim()
            if (value.isEmpty()) throw IllegalArgumentException(key)
            return value
        }

        private fun items(block: JSONObject, key: String, expected: Int): List<String> {
            val array = block.getJSONArray(key)
            if (array.length() != expected) throw IllegalArgumentException(key)
            val out = ArrayList<String>(array.length())
            for (index in 0 until array.length()) {
                val value = array.getString(index).trim()
                if (value.isEmpty()) throw IllegalArgumentException(key)
                out.add(value)
            }
            return out
        }
    }
}

class LanguagePackStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "language-packs").apply { mkdirs() }

    fun file(code: String): File? {
        if (!code.matches(Regex("^[a-z]{2,8}$"))) return null
        return File(root, "$code.json")
    }

    fun read(code: String, kb: KnowledgeBase): LanguagePack? {
        val stored = file(code) ?: return null
        if (!stored.isFile) return null
        val parsed = LanguagePack.parse(stored.readText(), kb, code) ?: return null
        return parsed
    }

    fun save(code: String, raw: String, kb: KnowledgeBase): LanguagePack? {
        val parsed = LanguagePack.parse(raw, kb, code) ?: return null
        val dest = file(code) ?: return null
        val partial = File(dest.absolutePath + ".partial")
        partial.writeText(raw)
        if (dest.exists()) dest.delete()
        if (!partial.renameTo(dest)) return null
        return parsed
    }

    fun remove(code: String) {
        file(code)?.delete()
    }

    fun installedCodes(): Set<String> {
        return root.listFiles().orEmpty().mapNotNull { file ->
            if (!file.isFile || file.extension != "json") return@mapNotNull null
            file.nameWithoutExtension.takeIf { it.matches(Regex("^[a-z]{2,8}$")) }
        }.toSet()
    }
}
