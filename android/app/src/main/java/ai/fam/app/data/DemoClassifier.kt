package ai.fam.app.data

import kotlin.math.sqrt

data class LeafSample(
    val green: Double,
    val yellow: Double,
    val brown: Double,
    val orange: Double,
    val dark: Double,
    val white: Double,
    val lesionRatio: Double,
)

data class LabelInfo(
    val id: String,
    val crop: String,
    val kind: String,
    val displayName: String,
)

data class DemoPick(
    val label: LabelInfo,
    val confidence: Double,
    val severity: String,
    val lesionRatio: Double,
)

object DemoClassifier {
    const val BACKEND = "on-device demo"
    const val MODEL_ID = "demo-heuristic"
    const val ADVICE_SOURCE = "template"

    private val yellowIds = setOf(
        "tomato_yellow_leaf_curl",
        "tomato_mosaic_virus",
        "cassava_green_mottle",
        "cassava_mosaic",
        "tomato_spider_mite",
    )
    private val rustIds = setOf("maize_common_rust", "apple_cedar_rust")
    private val whiteIds = setOf("pest_whitefly", "tomato_leaf_mold")

    fun classify(sample: LeafSample, labels: List<LabelInfo>, crop: String?): DemoPick {
        require(labels.isNotEmpty()) { "no labels" }
        val scoped = if (crop.isNullOrBlank()) {
            labels
        } else {
            labels.filter { it.crop == crop }
        }
        val pool = if (scoped.isEmpty()) labels else scoped
        val lesionRatio = round4(sample.lesionRatio.coerceIn(0.0, 1.0))
        val colorSum = sample.green + sample.yellow + sample.brown + sample.orange + sample.dark
        if (colorSum == 0.0) {
            val healthy = pool.filter { it.id.endsWith("_healthy") }
            val label = (if (healthy.isEmpty()) pool else healthy).minBy { it.id }
            return DemoPick(label, 0.35, "low", 0.0)
        }
        val candidates = gate(sample, pool)
        var best = candidates.first()
        var bestDistance = Double.POSITIVE_INFINITY
        for (label in candidates) {
            val distance = distance(sample, profile(label.id))
            val closer = distance < bestDistance - 1e-9
            val tie = kotlin.math.abs(distance - bestDistance) <= 1e-9 && label.id < best.id
            if (closer || tie) {
                best = label
                bestDistance = distance
            }
        }
        val confidence = round4((0.95 - bestDistance).coerceIn(0.35, 0.96))
        val severity = Severity.of(best.id, best.kind, lesionRatio, confidence)
        return DemoPick(best, confidence, severity, lesionRatio)
    }

    private fun gate(sample: LeafSample, pool: List<LabelInfo>): List<LabelInfo> {
        val picked = when {
            sample.lesionRatio < 0.10 &&
                sample.green >= 0.70 &&
                sample.green >= sample.yellow &&
                sample.green >= sample.brown ->
                pool.filter { it.id.endsWith("_healthy") }
            sample.white in 0.08..0.45 &&
                sample.white >= sample.brown &&
                sample.lesionRatio < 0.30 ->
                pool.filter { it.id in whiteIds }
            sample.yellow >= 0.22 &&
                sample.yellow >= sample.brown &&
                sample.yellow >= sample.orange &&
                sample.yellow >= sample.dark ->
                pool.filter { it.id in yellowIds }
            sample.orange >= 0.15 &&
                sample.orange >= sample.brown &&
                sample.orange >= sample.dark ->
                pool.filter { it.id in rustIds }
            else -> pool
        }
        return if (picked.isEmpty()) pool else picked
    }

    private fun distance(sample: LeafSample, profile: Profile): Double {
        val white = if (sample.white in 0.08..0.45) sample.white else 0.0
        val green = sample.green - profile.green
        val yellow = sample.yellow - profile.yellow
        val brown = sample.brown - profile.brown
        val orange = sample.orange - profile.orange
        val dark = sample.dark - profile.dark
        val whiteDelta = white - profile.white
        return sqrt(
            green * green +
                yellow * yellow +
                brown * brown +
                orange * orange +
                dark * dark +
                whiteDelta * whiteDelta,
        )
    }

    private fun profile(id: String): Profile = profiles[id] ?: neutral

    private fun round4(value: Double): Double = kotlin.math.round(value * 10000.0) / 10000.0

    private data class Profile(
        val green: Double,
        val yellow: Double,
        val brown: Double,
        val orange: Double,
        val dark: Double,
        val white: Double = 0.0,
    )

    private val neutral = Profile(0.2, 0.2, 0.2, 0.2, 0.2, 0.0)

    private val profiles = mapOf(
        "apple_scab" to Profile(0.62, 0.05, 0.22, 0.04, 0.07),
        "apple_black_rot" to Profile(0.40, 0.04, 0.28, 0.06, 0.22),
        "apple_cedar_rust" to Profile(0.55, 0.08, 0.08, 0.24, 0.05),
        "apple_healthy" to Profile(0.90, 0.04, 0.03, 0.01, 0.02),
        "cassava_bacterial_blight" to Profile(0.45, 0.08, 0.25, 0.05, 0.17),
        "cassava_brown_streak" to Profile(0.42, 0.18, 0.28, 0.06, 0.06),
        "cassava_green_mottle" to Profile(0.55, 0.28, 0.08, 0.04, 0.05),
        "cassava_mosaic" to Profile(0.48, 0.32, 0.08, 0.05, 0.07),
        "cassava_healthy" to Profile(0.86, 0.08, 0.03, 0.01, 0.02),
        "maize_gray_leaf_spot" to Profile(0.58, 0.06, 0.14, 0.04, 0.18),
        "maize_common_rust" to Profile(0.58, 0.08, 0.08, 0.22, 0.04),
        "maize_northern_leaf_blight" to Profile(0.40, 0.08, 0.30, 0.08, 0.14),
        "maize_healthy" to Profile(0.82, 0.12, 0.03, 0.01, 0.02),
        "potato_early_blight" to Profile(0.60, 0.08, 0.22, 0.05, 0.05),
        "potato_late_blight" to Profile(0.38, 0.05, 0.22, 0.05, 0.30),
        "potato_healthy" to Profile(0.88, 0.05, 0.04, 0.01, 0.02),
        "tomato_bacterial_spot" to Profile(0.62, 0.06, 0.16, 0.04, 0.12),
        "tomato_early_blight" to Profile(0.55, 0.10, 0.25, 0.05, 0.05),
        "tomato_late_blight" to Profile(0.35, 0.06, 0.24, 0.05, 0.30),
        "tomato_leaf_mold" to Profile(0.70, 0.10, 0.08, 0.04, 0.08, white = 0.16),
        "tomato_septoria_leaf_spot" to Profile(0.60, 0.06, 0.12, 0.03, 0.19),
        "tomato_spider_mite" to Profile(0.50, 0.28, 0.10, 0.06, 0.06),
        "tomato_target_spot" to Profile(0.52, 0.08, 0.22, 0.06, 0.12),
        "tomato_yellow_leaf_curl" to Profile(0.40, 0.42, 0.08, 0.05, 0.05),
        "tomato_mosaic_virus" to Profile(0.48, 0.30, 0.08, 0.06, 0.08),
        "tomato_healthy" to Profile(0.90, 0.04, 0.03, 0.01, 0.02),
        "pest_aphids" to Profile(0.62, 0.16, 0.06, 0.04, 0.12),
        "pest_fall_armyworm" to Profile(0.42, 0.08, 0.28, 0.06, 0.16),
        "pest_whitefly" to Profile(0.70, 0.08, 0.08, 0.04, 0.10, white = 0.20),
        "pest_stem_borer" to Profile(0.48, 0.10, 0.22, 0.05, 0.15),
    )
}
