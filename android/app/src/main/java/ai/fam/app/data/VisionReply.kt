package ai.fam.app.data

import java.util.Locale

data class VisionReply(
    val labelId: String,
    val plant: String,
    val part: String,
    val disease: String,
    val confidence: Double,
    val summary: String,
    val immediate: List<String> = emptyList(),
    val treatment: List<String> = emptyList(),
    val prevention: List<String> = emptyList(),
    val whenToSeekHelp: String = "",
) {
    fun known(labels: List<LabelInfo>): LabelInfo? {
        if (labelId == UNKNOWN) return null
        return labels.firstOrNull { it.id == labelId }
    }

    fun displayName(known: LabelInfo?): String {
        if (known != null) return known.displayName
        val plantName = plainName(plant)
        val problem = plainName(disease)
        if (plantName.isNotEmpty() && problem.isNotEmpty()) return "$plantName — $problem"
        if (plantName.isNotEmpty()) return plantName
        if (problem.isNotEmpty()) return problem
        val sentence = summary.trim()
        if (sentence.isNotEmpty() && !sentence.equals(UNKNOWN, ignoreCase = true)) {
            return sentence.take(80)
        }
        return ""
    }

    fun replyText(language: String): AdviceText {
        return AdviceText(
            summary = summary.ifBlank { displayName(null) },
            immediateActions = immediate,
            treatment = treatment,
            prevention = prevention,
            whenToSeekHelp = whenToSeekHelp.ifBlank { VisionReplyParser.caution(language) },
        )
    }
}

object PhoneModelFiles {
    const val E4B = "gemma-4-E4B-it.litertlm"
    const val E2B = "gemma-4-E2B-it.litertlm"
    const val PARTIAL_SUFFIX = ".partial"
    const val E4B_URL =
        "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true"

    fun downloadPercent(done: Long, total: Long): Int? {
        if (done < 0L || total <= 0L) return null
        return ((done * 100L) / total).toInt().coerceIn(0, 100)
    }

    fun sizeText(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes / 1_000_000.0
        return if (mb >= 1_000.0) {
            String.format(Locale.US, "%.2f GB", mb / 1_000.0)
        } else {
            String.format(Locale.US, "%.0f MB", mb)
        }
    }

    fun speedBps(gained: Long, elapsedNanos: Long): Long? {
        if (gained < 0L || elapsedNanos < 1_000_000_000L) return null
        val perSecond = gained.toDouble() * 1_000_000_000.0 / elapsedNanos.toDouble()
        return perSecond.toLong().coerceAtLeast(0L)
    }

    fun speedText(bps: Long): String {
        if (bps <= 0L) return "0 KB/s"
        return if (bps < 1_000_000L) {
            String.format(Locale.US, "%.0f KB/s", bps / 1_000.0)
        } else {
            String.format(Locale.US, "%.1f MB/s", bps / 1_000_000.0)
        }
    }

    fun sameModel(savedName: String?, savedBytes: Long, name: String, bytes: Long): Boolean {
        return !savedName.isNullOrBlank() && savedName == name && savedBytes == bytes && bytes > 0L
    }

    fun rangeTotal(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val total = header.substringAfter('/', "").trim().toLongOrNull() ?: return null
        return total.takeIf { it > 0L }
    }

    fun pick(names: List<String>): String? {
        return names
            .filter { it.endsWith(".litertlm", ignoreCase = true) }
            .maxWithOrNull(compareBy<String> { rank(it) }.thenBy { it })
    }

    fun modelId(name: String): String {
        val lower = name.lowercase()
        return when {
            "e4b" in lower -> "gemma-4-e4b"
            "e2b" in lower -> "gemma-4-e2b"
            else -> "gemma-4"
        }
    }

    private fun rank(name: String): Int {
        val lower = name.lowercase()
        return when {
            "e4b" in lower -> 2
            "e2b" in lower -> 1
            else -> 0
        }
    }
}

object VisionReplyParser {
    const val MAX_OUTPUT_TOKENS = 384

    fun prompt(labels: List<LabelInfo>, language: String, crop: String?, notes: String = ""): String {
        val languageName = NAMES[language] ?: "English"
        val cropLine = if (crop.isNullOrBlank()) {
            "The grower did not name the crop."
        } else {
            "The grower says the crop is $crop. Prefer a label for that crop when it fits."
        }
        val list = labels.joinToString("\n") { "${it.id} | ${it.displayName} | ${it.crop} | ${it.kind}" }
        val noteBlock = if (notes.isBlank()) {
            ""
        } else {
            "Notes for the labels. They may be in English. Rewrite only what they say in $languageName. Do not add a product or a dose.\n$notes\n"
        }
        return """
            The photo may show a leaf, a stem, or a whole plant, including plants that are not in the list, such as bamboo or mango.
            $cropLine
            Choose label_id from this list only when the photo matches one. Otherwise set label_id to unknown.
            $list
            $noteBlock
            Always put the common name you see in plant, even when label_id is unknown. Bamboo stays bamboo.
            Do not write the word unknown in plant, disease, or summary. If it looks healthy, set disease to healthy.
            Return one JSON object and nothing else. Keys:
            label_id, plant, part, disease, confidence, summary, immediate_actions, treatment, prevention, when_to_seek_help.
            part is leaf, stem, or plant.
            confidence is a number from 0 to 1.
            immediate_actions, treatment, and prevention are arrays of at most two short steps.
            Write summary, the steps, and when_to_seek_help in $languageName, in short sentences a farmer can hear out loud.
            Do not name a chemical dose. Say this is a photo guess, not a lab test, inside when_to_seek_help.
        """.trimIndent()
    }

    fun caution(language: String): String = CAUTIONS[language] ?: CAUTIONS.getValue("en")

    private val NAMES = mapOf(
        "en" to "English",
        "as" to "Assamese",
        "bn" to "Bengali",
        "brx" to "Bodo",
        "doi" to "Dogri",
        "gu" to "Gujarati",
        "hi" to "Hindi",
        "kn" to "Kannada",
        "ks" to "Kashmiri",
        "kok" to "Konkani",
        "mai" to "Maithili",
        "ml" to "Malayalam",
        "mni" to "Manipuri",
        "mr" to "Marathi",
        "ne" to "Nepali",
        "or" to "Odia",
        "pa" to "Punjabi",
        "sa" to "Sanskrit",
        "sat" to "Santali",
        "sd" to "Sindhi",
        "ta" to "Tamil",
        "te" to "Telugu",
        "ur" to "Urdu",
        "sw" to "Swahili",
    )

    private val CAUTIONS = mapOf(
        "en" to "This is a phone model guess from one photo, not a lab test.",
        "hi" to "यह एक फ़ोटो पर फ़ोन मॉडल का अनुमान है, प्रयोगशाला जाँच नहीं।",
        "sw" to "Hii ni dhana ya modeli ya simu kutoka picha moja, si kipimo cha maabara.",
        "as" to "এইটো এখন ফটোৰ পৰা ফোন মডেলৰ অনুমান, পৰীক্ষাগাৰৰ পৰীক্ষা নহয়।",
        "bn" to "এটি একটি ছবি থেকে ফোন মডেলের অনুমান, পরীক্ষাগারের পরীক্ষা নয়।",
        "brx" to "बेयो मोनसे सावगारिनिफ्राय फोन मोडेलनि अनुमान, प्रयोगशालानि जांच नङा।",
        "doi" to "एह् इक फोटो ते फोन मॉडल दा अंदाजा ऐ, लैब दी जांच नेईं।",
        "gu" to "આ એક ફોટા પરથી ફોન મોડેલનો અંદાજ છે, પ્રયોગશાળાની તપાસ નથી.",
        "kn" to "ಇದು ಒಂದು ಫೋಟೋದಿಂದ ಫೋನ್ ಮಾದರಿಯ ಅಂದಾಜು, ಪ್ರಯೋಗಾಲಯ ಪರೀಕ್ಷೆ ಅಲ್ಲ.",
        "ks" to "یہ اکھ فوٹو پؠٹھ فون ماڈلُک اندازہ چھُ، لیبارٹری ہُند ٹیسٹ چھُ نَہ۔",
        "kok" to "ही एका फोटोवेल्यान फोन मॉडलाची अदमास आसा, प्रयोगशाळेची तपासणी न्हय.",
        "mai" to "ई एकटा फोटो सँ फोन मॉडल केर अनुमान अछि, प्रयोगशाला केर जाँच नहि।",
        "ml" to "ഇത് ഒരു ഫോട്ടോയിൽ നിന്നുള്ള ഫോൺ മോഡലിന്റെ ഊഹമാണ്, ലാബ് പരിശോധനയല്ല.",
        "mni" to "মসিগী ফোটো অমত্তগী ফোন মোদেলগী য়াইফদবা অমনি, ল্যাবগী পরীক্ষা নত্তে।",
        "mr" to "हा एका फोटोवरून फोन मॉडेलचा अंदाज आहे, प्रयोगशाळेची तपासणी नाही.",
        "ne" to "यो एउटा फोटोबाट फोन मोडेलको अनुमान हो, प्रयोगशालाको जाँच होइन।",
        "or" to "ଏହା ଗୋଟିଏ ଫଟୋରୁ ଫୋନ ମଡେଲର ଅନୁମାନ, ଲ୍ୟାବ୍ ପରୀକ୍ଷା ନୁହେଁ।",
        "pa" to "ਇਹ ਇੱਕ ਫੋਟੋ ਤੋਂ ਫੋਨ ਮਾਡਲ ਦਾ ਅੰਦਾਜ਼ਾ ਹੈ, ਲੈਬ ਦੀ ਜਾਂਚ ਨਹੀਂ।",
        "sa" to "एतत् एकस्मात् चित्राद् दूरभाषप्रतिमानस्य अनुमानम्, प्रयोगशालापरीक्षा न।",
        "sat" to "ᱱᱚᱣᱟ ᱫᱚ ᱢᱤᱫ ᱪᱤᱛᱟᱹᱨ ᱠᱷᱚᱱ ᱯᱷᱚᱱ ᱢᱚᱰᱮᱞ ᱨᱮᱭᱟᱜ ᱟᱸᱫᱟᱡᱽ, ᱞᱮᱵᱽ ᱯᱚᱨᱤᱠᱷᱟ ᱵᱟᱝ।",
        "sd" to "هي هڪ فوٽو تان فون ماڊل جو اندازو آهي، ليبارٽري جي جاچ ناهي.",
        "ta" to "இது ஒரு படத்திலிருந்து தொலைபேசி மாதிரியின் யூகம், ஆய்வகப் பரிசோதனை அல்ல.",
        "te" to "ఇది ఒక ఫోటో నుంచి ఫోన్ నమూనా అంచనా, ప్రయోగశాల పరీక్ష కాదు.",
        "ur" to "یہ ایک تصویر سے فون ماڈل کا اندازہ ہے، تجربہ گاہ کا ٹیسٹ نہیں۔",
    )

    fun parse(raw: String, labels: List<LabelInfo>): VisionReply? {
        val parsed = jsonObject(raw) ?: return null
        val fields = parsed.fields
        val lists = parsed.lists
        val byId = HashMap<String, String>()
        val byName = HashMap<String, String>()
        for (label in labels) {
            byId[label.id.lowercase()] = label.id
            byName[label.displayName.lowercase()] = label.id
        }
        val rawId = fields["label_id"].orEmpty().ifBlank { fields["labelid"].orEmpty() }.trim()
        val labelId = when {
            rawId.isEmpty() || rawId.equals(UNKNOWN, ignoreCase = true) -> UNKNOWN
            rawId.lowercase() in byId -> byId.getValue(rawId.lowercase())
            rawId.lowercase() in byName -> byName.getValue(rawId.lowercase())
            else -> UNKNOWN
        }
        var plant = fields["plant"].orEmpty().trim()
        if (plainName(plant).isEmpty() && labelId == UNKNOWN && plainName(rawId).isNotEmpty()) {
            plant = rawId.trim()
        }
        val part = fields["part"].orEmpty().trim()
        val disease = fields["disease"].orEmpty().trim()
        val summary = fields["summary"].orEmpty().trim()
        if (labelId == UNKNOWN && plant.isEmpty() && disease.isEmpty() && summary.isEmpty()) {
            return null
        }
        return VisionReply(
            labelId = labelId,
            plant = plant,
            part = part,
            disease = disease,
            confidence = confidenceOf(fields["confidence"]),
            summary = summary,
            immediate = steps("immediate_actions", fields, lists),
            treatment = steps("treatment", fields, lists),
            prevention = steps("prevention", fields, lists),
            whenToSeekHelp = fields["when_to_seek_help"].orEmpty().trim(),
        )
    }

    private fun steps(key: String, fields: Map<String, String>, lists: Map<String, List<String>>): List<String> {
        val fromList = lists[key].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        if (fromList.isNotEmpty()) return fromList.take(2)
        val one = fields[key].orEmpty().trim()
        return if (one.isEmpty()) emptyList() else listOf(one)
    }

    private fun confidenceOf(raw: String?): Double {
        val number = raw?.trim()?.toDoubleOrNull() ?: return 0.5
        val scaled = if (number > 1.0) number / 100.0 else number
        return scaled.coerceIn(0.0, 1.0)
    }
}

private const val UNKNOWN = "unknown"

private val BLANK_NAMES = setOf("unknown", "none", "n/a", "na", "null", "unidentified", "healthy", "no disease")

internal fun plainName(raw: String): String {
    val text = raw.trim()
    if (text.isEmpty() || text.lowercase() in BLANK_NAMES) return ""
    return text
}

fun shownPlantName(displayName: String, label: String): String {
    return plainName(displayName).ifEmpty { plainName(label) }
}

fun speechForFarmer(name: String, script: String): String {
    val who = name.trim()
    if (who.isEmpty()) return script
    return "$who. $script"
}

fun farmerSpeech(title: String, advice: AdviceText?): String {
    val parts = ArrayList<String>()
    val name = plainName(title)
    if (name.isNotEmpty()) parts.add(name)
    if (advice != null) {
        val summary = advice.summary.trim()
        if (summary.isNotEmpty() && !summary.equals(name, ignoreCase = true)) parts.add(summary)
        advice.immediateActions.forEach { line -> if (line.isNotBlank()) parts.add(line.trim()) }
        advice.treatment.forEach { line -> if (line.isNotBlank()) parts.add(line.trim()) }
        advice.prevention.forEach { line -> if (line.isNotBlank()) parts.add(line.trim()) }
        val help = advice.whenToSeekHelp.trim()
        if (help.isNotEmpty()) parts.add(help)
    }
    return parts.joinToString(". ")
}

private fun jsonObject(raw: String): ParsedObject? {
    val start = raw.indexOf('{')
    if (start < 0) return null
    return try {
        val parser = JsonParser(raw, start)
        val value = parser.readValue()
        parser.skipSpace()
        if (value !is ParsedObject) null else value
    } catch (_: Exception) {
        null
    }
}

private class ParsedObject(
    val fields: Map<String, String>,
    val lists: Map<String, List<String>>,
)

private class ParsedList(val items: List<String>)

private class JsonParser(private val raw: String, start: Int) {
    private var index = start

    fun skipSpace() {
        while (index < raw.length && raw[index].isWhitespace()) index++
    }

    fun readValue(): Any? {
        skipSpace()
        if (index >= raw.length) throw IllegalArgumentException("end")
        return when (raw[index]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> readLiteral("true", "true")
            'f' -> readLiteral("false", "false")
            'n' -> readLiteral("null", "")
            else -> readNumber()
        }
    }

    private fun readObject(): ParsedObject {
        expect('{')
        val fields = LinkedHashMap<String, String>()
        val lists = LinkedHashMap<String, List<String>>()
        skipSpace()
        if (peek('}')) {
            index++
            return ParsedObject(fields, lists)
        }
        while (index < raw.length) {
            skipSpace()
            val key = readString().lowercase()
            skipSpace()
            expect(':')
            when (val value = readValue()) {
                is String -> fields[key] = value
                is ParsedList -> lists[key] = value.items
            }
            skipSpace()
            if (peek('}')) {
                index++
                return ParsedObject(fields, lists)
            }
            expect(',')
        }
        throw IllegalArgumentException("object")
    }

    private fun readArray(): ParsedList {
        expect('[')
        val items = ArrayList<String>()
        skipSpace()
        if (peek(']')) {
            index++
            return ParsedList(items)
        }
        while (index < raw.length) {
            val value = readValue()
            if (value is String && value.isNotBlank()) items.add(value.trim())
            skipSpace()
            if (peek(']')) {
                index++
                return ParsedList(items)
            }
            expect(',')
        }
        throw IllegalArgumentException("array")
    }

    private fun readString(): String {
        expect('"')
        val out = StringBuilder()
        while (index < raw.length) {
            val char = raw[index]
            index++
            when {
                char == '\\' -> out.append(escape())
                char == '"' -> return out.toString()
                else -> out.append(char)
            }
        }
        throw IllegalArgumentException("string")
    }

    private fun escape(): Char {
        if (index >= raw.length) throw IllegalArgumentException("escape")
        val char = raw[index]
        index++
        return when (char) {
            '"', '\\', '/' -> char
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (index + 4 > raw.length) throw IllegalArgumentException("unicode")
                val hex = raw.substring(index, index + 4)
                index += 4
                hex.toInt(16).toChar()
            }
            else -> char
        }
    }

    private fun readNumber(): String {
        val start = index
        if (peek('-')) index++
        while (index < raw.length && raw[index].isDigit()) index++
        if (peek('.')) {
            index++
            while (index < raw.length && raw[index].isDigit()) index++
        }
        if (index < raw.length && (raw[index] == 'e' || raw[index] == 'E')) {
            index++
            if (index < raw.length && (raw[index] == '+' || raw[index] == '-')) index++
            while (index < raw.length && raw[index].isDigit()) index++
        }
        if (index == start) throw IllegalArgumentException("number")
        return raw.substring(start, index)
    }

    private fun readLiteral(token: String, value: String): String {
        if (!raw.startsWith(token, index)) throw IllegalArgumentException(token)
        index += token.length
        return value
    }

    private fun expect(char: Char) {
        skipSpace()
        if (index >= raw.length || raw[index] != char) throw IllegalArgumentException("$char")
        index++
    }

    private fun peek(char: Char): Boolean {
        return index < raw.length && raw[index] == char
    }
}
