package ai.fam.app.data

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class KnowledgeBase(
    val labels: List<LabelInfo>,
    val treatments: Map<String, TreatmentBlock>,
    val regions: Map<String, RegionNote>,
    val modelVersion: String,
) {
    fun region(id: String): RegionNote {
        return regions[id] ?: regions.getValue("indo_gangetic")
    }

    fun advice(labelId: String, region: RegionNote, language: String): AdviceText {
        val block = treatments.getValue(labelId)
        val prevention = pickList(block.prevention, language).toMutableList()
        val irrigation = pickText(region.irrigation, language)
        if (irrigation !in prevention) {
            prevention.add(irrigation)
        }
        return AdviceText(
            summary = pickText(block.summary, language),
            immediateActions = pickList(block.immediateActions, language),
            treatment = pickList(block.treatment, language),
            prevention = prevention,
            whenToSeekHelp = pickText(block.whenToSeekHelp, language),
        )
    }

    fun regionName(id: String, language: String): String {
        val note = regions[id] ?: return id
        return pickText(note.names, language)
    }

    companion object {
        fun load(assets: AssetManager): KnowledgeBase {
            val labelsJson = assets.open("kb/labels.json").bufferedReader().use { it.readText() }
            val treatmentsJson = assets.open("kb/treatments.json").bufferedReader().use { it.readText() }
            val regionsJson = assets.open("kb/regions.json").bufferedReader().use { it.readText() }
            return parse(labelsJson, treatmentsJson, regionsJson)
        }

        fun parse(labelsJson: String, treatmentsJson: String, regionsJson: String): KnowledgeBase {
            val labels = parseLabels(labelsJson)
            val treatments = parseTreatments(treatmentsJson, labels)
            val regions = parseRegions(regionsJson)
            return KnowledgeBase(labels, treatments, regions, versionOf(labelsJson))
        }

        private fun parseLabels(json: String): List<LabelInfo> {
            val array = JSONArray(json)
            val labels = ArrayList<LabelInfo>(array.length())
            val seen = HashSet<String>()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val id = item.getString("id").trim()
                require(id.isNotEmpty() && seen.add(id)) { "duplicate label $id" }
                val kind = item.getString("kind").trim()
                require(kind == "disease" || kind == "pest") { "$id kind" }
                labels.add(
                    LabelInfo(
                        id = id,
                        crop = item.getString("crop").trim().lowercase(),
                        kind = kind,
                        displayName = item.getString("display_name").trim(),
                    ),
                )
            }
            require(labels.isNotEmpty()) { "labels.json is empty" }
            return labels
        }

        private fun parseTreatments(json: String, labels: List<LabelInfo>): Map<String, TreatmentBlock> {
            val obj = JSONObject(json)
            val out = LinkedHashMap<String, TreatmentBlock>()
            for (label in labels) {
                val block = obj.getJSONObject(label.id)
                out[label.id] = TreatmentBlock(
                    summary = texts(block.getJSONObject("summary")),
                    immediateActions = lists(block.getJSONObject("immediate_actions")),
                    treatment = lists(block.getJSONObject("treatment")),
                    prevention = lists(block.getJSONObject("prevention")),
                    whenToSeekHelp = texts(block.getJSONObject("when_to_seek_help")),
                )
            }
            return out
        }

        private fun parseRegions(json: String): Map<String, RegionNote> {
            val obj = JSONObject(json)
            val out = LinkedHashMap<String, RegionNote>()
            for (id in REGION_IDS) {
                val block = obj.getJSONObject(id)
                out[id] = RegionNote(
                    id = id,
                    names = texts(block.getJSONObject("names")),
                    season = texts(block.getJSONObject("season")),
                    irrigation = texts(block.getJSONObject("irrigation")),
                    inputAccess = texts(block.getJSONObject("input_access")),
                )
            }
            return out
        }

        private fun texts(obj: JSONObject): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            for (language in LANGUAGES) {
                val value = obj.getString(language).trim()
                require(value.isNotEmpty()) { "missing $language" }
                out[language] = value
            }
            return out
        }

        private fun lists(obj: JSONObject): Map<String, List<String>> {
            val out = LinkedHashMap<String, List<String>>()
            for (language in LANGUAGES) {
                val array = obj.getJSONArray(language)
                require(array.length() > 0) { "empty $language" }
                val items = ArrayList<String>(array.length())
                for (index in 0 until array.length()) {
                    val value = array.getString(index).trim()
                    require(value.isNotEmpty()) { "blank $language" }
                    items.add(value)
                }
                out[language] = items
            }
            return out
        }

        private fun versionOf(labelsJson: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(labelsJson.toByteArray(Charsets.UTF_8))
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}

data class TreatmentBlock(
    val summary: Map<String, String>,
    val immediateActions: Map<String, List<String>>,
    val treatment: Map<String, List<String>>,
    val prevention: Map<String, List<String>>,
    val whenToSeekHelp: Map<String, String>,
)

data class RegionNote(
    val id: String,
    val names: Map<String, String>,
    val season: Map<String, String>,
    val irrigation: Map<String, String>,
    val inputAccess: Map<String, String>,
)

val LANGUAGES = listOf("en", "hi", "sw")
val REGION_IDS = listOf("indo_gangetic", "peninsular_india", "east_african_highlands")
val CROP_ORDER = listOf("tomato", "potato", "cassava", "maize", "apple")

fun pickText(block: Map<String, String>, language: String): String {
    return block[language]?.takeIf { it.isNotBlank() } ?: block.getValue("en")
}

fun pickList(block: Map<String, List<String>>, language: String): List<String> {
    val chosen = block[language]?.takeIf { it.isNotEmpty() } ?: block.getValue("en")
    return chosen.toList()
}
