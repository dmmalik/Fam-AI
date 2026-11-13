package ai.fam.app.data

object Severity {
    fun of(labelId: String, kind: String, lesionRatio: Double, confidence: Double): String {
        if (labelId.endsWith("_healthy")) return "low"
        return if (kind == "pest") pest(confidence) else disease(lesionRatio)
    }

    fun disease(lesionRatio: Double): String {
        val ratio = lesionRatio.toDouble()
        if (ratio < 0.10) return "low"
        if (ratio <= 0.30) return "medium"
        return "high"
    }

    fun pest(confidence: Double): String {
        val score = confidence.toDouble()
        if (score < 0.60) return "low"
        if (score < 0.85) return "medium"
        return "high"
    }
}
