package ai.fam.app.data

import org.json.JSONArray
import org.json.JSONObject

data class AdviceText(
    val summary: String,
    val immediateActions: List<String>,
    val treatment: List<String>,
    val prevention: List<String>,
    val whenToSeekHelp: String,
) {
    fun useful(): Boolean {
        return summary.isNotBlank() ||
            immediateActions.isNotEmpty() ||
            treatment.isNotEmpty() ||
            prevention.isNotEmpty() ||
            whenToSeekHelp.isNotBlank()
    }

    fun toJson(): String {
        return JSONObject()
            .put("summary", summary)
            .put("immediate_actions", JSONArray(immediateActions))
            .put("treatment", JSONArray(treatment))
            .put("prevention", JSONArray(prevention))
            .put("when_to_seek_help", whenToSeekHelp)
            .toString()
    }

    companion object {
        fun fromJson(raw: String): AdviceText? {
            if (raw.isBlank()) return null
            return try {
                val obj = JSONObject(raw)
                AdviceText(
                    summary = obj.getString("summary"),
                    immediateActions = strings(obj.getJSONArray("immediate_actions")),
                    treatment = strings(obj.getJSONArray("treatment")),
                    prevention = strings(obj.getJSONArray("prevention")),
                    whenToSeekHelp = obj.getString("when_to_seek_help"),
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun strings(array: JSONArray): List<String> {
            val items = ArrayList<String>(array.length())
            for (index in 0 until array.length()) {
                items.add(array.getString(index))
            }
            return items
        }
    }
}
