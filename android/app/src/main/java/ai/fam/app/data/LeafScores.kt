package ai.fam.app.data

data class LeafGuess(val id: String, val confidence: Float)

object LeafScores {
    fun top(logits: FloatArray, names: List<String>, k: Int = 3): List<LeafGuess> {
        if (logits.isEmpty() || names.size != logits.size) return emptyList()
        var peak = logits[0]
        for (value in logits) {
            if (value > peak) peak = value
        }
        val weights = DoubleArray(logits.size)
        var sum = 0.0
        for (index in logits.indices) {
            val weight = kotlin.math.exp((logits[index] - peak).toDouble())
            weights[index] = weight
            sum += weight
        }
        if (sum == 0.0) return emptyList()
        return logits.indices
            .sortedByDescending { logits[it] }
            .take(k.coerceAtMost(logits.size))
            .map { index -> LeafGuess(names[index], (weights[index] / sum).toFloat()) }
    }

    fun readable(id: String): String {
        return id.split('_')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
    }
}
