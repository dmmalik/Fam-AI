package ai.fam.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream

data class LeafRead(val top: LeafGuess, val others: List<LeafGuess>)

data class LeafStatus(val present: Boolean, val classes: Int)

class LeafModel(context: Context) {
    private val app = context.applicationContext
    private val names: List<String> = readNames()
    private var interpreter: Interpreter? = null

    fun status(): LeafStatus {
        val present = try {
            app.assets.open(MODEL).close()
            names.isNotEmpty()
        } catch (_: Exception) {
            false
        }
        return LeafStatus(present, names.size)
    }

    fun predict(jpeg: File): LeafRead? {
        val engine = openInterpreter() ?: return null
        val decoded = BitmapFactory.decodeFile(jpeg.absolutePath) ?: return null
        val crop = centerCrop(decoded)
        if (crop !== decoded) decoded.recycle()
        return try {
            val shape = engine.getInputTensor(0).shape()
            val channelsFirst = shape.size == 4 && shape[1] == 3
            val input = pixels(crop, channelsFirst)
            val logits = Array(1) { FloatArray(names.size) }
            synchronized(engine) {
                engine.run(input, logits)
            }
            val ranked = LeafScores.top(logits[0], names, 3)
            if (ranked.isEmpty()) {
                null
            } else {
                android.util.Log.i("FamAI", "leaf ${ranked.first().id} ${ranked.first().confidence}")
                LeafRead(ranked.first(), ranked.drop(1))
            }
        } finally {
            if (!crop.isRecycled) crop.recycle()
        }
    }

    private fun readNames(): List<String> {
        return try {
            app.assets.open(CLASSES).bufferedReader().use { reader ->
                reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun openInterpreter(): Interpreter? {
        interpreter?.let { return it }
        if (names.isEmpty()) return null
        return try {
            val options = Interpreter.Options().setNumThreads(4).setUseXNNPACK(false)
            Interpreter(unpackedModel(), options).also { interpreter = it }
        } catch (exc: Exception) {
            android.util.Log.e("FamAI", "leaf model open failed", exc)
            null
        }
    }

    private fun unpackedModel(): File {
        val dest = File(app.filesDir, MODEL)
        dest.parentFile?.mkdirs()
        val expected = try {
            app.assets.openFd(MODEL).use { it.declaredLength }
        } catch (_: Exception) {
            -1L
        }
        if (!dest.exists() || expected <= 0L || dest.length() != expected) {
            app.assets.open(MODEL).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
        }
        return dest
    }

    private fun centerCrop(source: Bitmap): Bitmap {
        val minSide = minOf(source.width, source.height).coerceAtLeast(1)
        val scale = RESIZE.toFloat() / minSide.toFloat()
        val width = (source.width * scale).toInt().coerceAtLeast(SIZE)
        val height = (source.height * scale).toInt().coerceAtLeast(SIZE)
        val scaled = if (width == source.width && height == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, width, height, true)
        }
        val x = ((scaled.width - SIZE) / 2).coerceAtLeast(0)
        val y = ((scaled.height - SIZE) / 2).coerceAtLeast(0)
        val cropWidth = minOf(SIZE, scaled.width - x)
        val cropHeight = minOf(SIZE, scaled.height - y)
        val cropped = Bitmap.createBitmap(scaled, x, y, cropWidth, cropHeight)
        if (scaled !== source && scaled !== cropped && !scaled.isRecycled) scaled.recycle()
        if (cropped.width == SIZE && cropped.height == SIZE) return cropped
        val fitted = Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true)
        if (fitted !== cropped) cropped.recycle()
        return fitted
    }

    private fun pixels(bitmap: Bitmap, channelsFirst: Boolean): Any {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        if (channelsFirst) {
            val input = Array(1) { Array(3) { Array(height) { FloatArray(width) } } }
            var index = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = pixels[index++]
                    input[0][0][y][x] = norm(pixel, 0)
                    input[0][1][y][x] = norm(pixel, 1)
                    input[0][2][y][x] = norm(pixel, 2)
                }
            }
            return input
        }
        val input = Array(1) { Array(height) { Array(width) { FloatArray(3) } } }
        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[index++]
                input[0][y][x][0] = norm(pixel, 0)
                input[0][y][x][1] = norm(pixel, 1)
                input[0][y][x][2] = norm(pixel, 2)
            }
        }
        return input
    }

    private fun norm(pixel: Int, channel: Int): Float {
        val value = when (channel) {
            0 -> (pixel shr 16) and 0xff
            1 -> (pixel shr 8) and 0xff
            else -> pixel and 0xff
        } / 255f
        return (value - MEAN[channel]) / STD[channel]
    }

    companion object {
        const val MODEL = "leaf/efficientnetv2_s_plant.tflite"
        const val CLASSES = "leaf/classes.txt"
        const val SIZE = 384
        private const val RESIZE = 416
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        fun status(context: Context): LeafStatus = LeafModel(context).status()
    }
}
