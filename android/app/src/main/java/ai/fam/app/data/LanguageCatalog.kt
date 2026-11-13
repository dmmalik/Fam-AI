package ai.fam.app.data

import android.content.Context
import org.json.JSONArray

data class AppLanguage(
    val code: String,
    val nameEn: String,
    val autonym: String,
    val tts: List<String>,
    val builtin: Boolean,
)

object LanguageCatalog {
    private var cached: List<AppLanguage>? = null

    fun all(context: Context): List<AppLanguage> {
        cached?.let { return it }
        val json = context.applicationContext.assets.open("kb/languages.json").bufferedReader().use { it.readText() }
        val parsed = parse(json)
        cached = parsed
        return parsed
    }

    fun codes(context: Context): Set<String> = all(context).map { it.code }.toSet()

    fun byCode(context: Context, code: String): AppLanguage? = all(context).firstOrNull { it.code == code }

    fun ttsTags(context: Context, code: String): List<String> {
        return byCode(context, code)?.tts ?: listOf("en-IN", "en")
    }

    fun parse(json: String): List<AppLanguage> {
        val array = JSONArray(json)
        val out = ArrayList<AppLanguage>(array.length())
        val seen = HashSet<String>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val code = item.getString("code").trim()
            val name = item.getString("name_en").trim()
            val autonym = item.getString("autonym").trim()
            val tts = strings(item.getJSONArray("tts"))
            require(code.length in 2..8 && code.all { it.isLowerCase() } && seen.add(code)) { "bad language $code" }
            require(name.isNotEmpty() && autonym.isNotEmpty() && tts.isNotEmpty()) { "empty language $code" }
            out.add(
                AppLanguage(
                    code = code,
                    nameEn = name,
                    autonym = autonym,
                    tts = tts,
                    builtin = item.getBoolean("builtin"),
                ),
            )
        }
        val builtin = out.filter { it.builtin }.map { it.code }
        require(builtin == STORED_LANGUAGES) { "built-in languages changed" }
        return out
    }

    private fun strings(array: JSONArray): List<String> {
        val items = ArrayList<String>(array.length())
        for (index in 0 until array.length()) {
            val value = array.getString(index).trim()
            require(value.isNotEmpty()) { "blank speech tag" }
            items.add(value)
        }
        return items
    }
}

val STORED_LANGUAGES = listOf("en", "hi", "sw")
