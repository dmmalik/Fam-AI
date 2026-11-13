package ai.fam.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max

data class PreparedPhoto(
    val jpeg: File,
    val thumb: File,
    val sha256: String,
    val sample: LeafSample,
)

object ImagePrep {
    const val LONG_SIDE = 768
    const val QUALITY = 80
    private const val THUMB_SIDE = 256
    private const val MAX_BYTES = 2_000_000

    fun stage(context: Context, uri: Uri): File {
        val dir = File(context.cacheDir, "capture").apply { mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("empty image")
        if (dest.length() == 0L) {
            dest.delete()
            throw IOException("empty image")
        }
        return dest
    }

    fun prepare(context: Context, source: File): PreparedPhoto {
        val orientation = readOrientation(source)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("could not read image")
        }
        val decode = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, decode)
            ?: throw IOException("could not read image")
        var working = decoded
        try {
            working = rotate(working, orientation)
            val fitted = scaleLongSide(working, LONG_SIDE)
            if (fitted !== working) working.recycle()
            working = fitted
            val sample = LeafSampler.sample(working)
            val jpegDir = File(context.cacheDir, "scaled").apply { mkdirs() }
            val thumbDir = File(context.filesDir, "thumbs").apply { mkdirs() }
            var quality = QUALITY
            var jpeg = writeJpeg(working, File(jpegDir, "tmp-${UUID.randomUUID()}.jpg"), quality)
            while (jpeg.file.length() > MAX_BYTES && quality > 40) {
                quality -= 10
                jpeg.file.delete()
                jpeg = writeJpeg(working, File(jpegDir, "tmp-${UUID.randomUUID()}.jpg"), quality)
            }
            val named = File(jpegDir, "${jpeg.sha256}.jpg")
            if (named.absolutePath != jpeg.file.absolutePath) {
                if (named.exists()) named.delete()
                if (!jpeg.file.renameTo(named)) {
                    jpeg.file.copyTo(named, overwrite = true)
                    jpeg.file.delete()
                }
            }
            val thumb = File(thumbDir, "${jpeg.sha256}.jpg")
            val thumbBitmap = scaleLongSide(working, THUMB_SIDE)
            try {
                FileOutputStream(thumb).use { out ->
                    if (!thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)) {
                        throw IOException("could not write thumbnail")
                    }
                }
            } finally {
                if (thumbBitmap !== working && !thumbBitmap.isRecycled) thumbBitmap.recycle()
            }
            return PreparedPhoto(named, thumb, jpeg.sha256, sample)
        } catch (oom: OutOfMemoryError) {
            throw IOException("image too large", oom)
        } finally {
            if (!working.isRecycled) working.recycle()
        }
    }

    private fun readOrientation(source: File): Int {
        return try {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var size = 1
        val longSide = max(width, height)
        while (longSide / (size * 2) >= LONG_SIDE) size *= 2
        return size
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleLongSide(bitmap: Bitmap, longSide: Int): Bitmap {
        val current = max(bitmap.width, bitmap.height).toFloat()
        if (current <= longSide) return bitmap
        val scale = longSide / current
        val width = max(1, (bitmap.width * scale).toInt())
        val height = max(1, (bitmap.height * scale).toInt())
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun writeJpeg(bitmap: Bitmap, dest: File, quality: Int): HashedFile {
        val digest = MessageDigest.getInstance("SHA-256")
        FileOutputStream(dest).use { file ->
            DigestOutputStream(file, digest).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    throw IOException("could not write jpeg")
                }
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        return HashedFile(dest, sha)
    }

    private data class HashedFile(val file: File, val sha256: String)
}

object LeafSampler {
    fun sample(bitmap: Bitmap): LeafSample {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var plant = 0
        var healthy = 0
        var yellow = 0
        var orange = 0
        var brown = 0
        var dark = 0
        var white = 0
        var seen = 0
        var y = 0
        while (y < height) {
            var x = 0
            var index = y * width
            while (x < width) {
                val color = pixels[index]
                val red = (color shr 16) and 0xff
                val green = (color shr 8) and 0xff
                val blue = color and 0xff
                val brightest = maxOf(red, green, blue)
                val darkest = minOf(red, green, blue)
                val saturation = (brightest - darkest).toFloat() / max(brightest, 1).toFloat()
                seen++
                if (brightest >= 220 && saturation < 0.12f) white++
                val isPlant = saturation >= 0.12f && brightest >= 35 && brightest <= 245
                if (isPlant) {
                    plant++
                    val isHealthy = green >= red + 8 && green >= blue + 8
                    if (isHealthy) {
                        healthy++
                    } else {
                        when (lesionBucket(red, green, blue, brightest)) {
                            YELLOW -> yellow++
                            ORANGE -> orange++
                            DARK -> dark++
                            else -> brown++
                        }
                    }
                }
                x += 2
                index += 2
            }
            y += 2
        }
        if (plant < 32 || seen == 0) {
            val whiteRatio = if (seen == 0) 0.0 else white.toDouble() / seen.toDouble()
            return LeafSample(0.0, 0.0, 0.0, 0.0, 0.0, whiteRatio, 0.0)
        }
        val total = plant.toDouble()
        return LeafSample(
            green = healthy / total,
            yellow = yellow / total,
            brown = brown / total,
            orange = orange / total,
            dark = dark / total,
            white = white.toDouble() / seen.toDouble(),
            lesionRatio = (plant - healthy).toDouble() / total,
        )
    }

    private fun lesionBucket(red: Int, green: Int, blue: Int, brightest: Int): Int {
        if (red >= 150 && green >= 130 && blue <= 130 && red + green > blue * 3) return YELLOW
        if (red >= 160 && green in 80..155 && blue <= 85 && red > green && green > blue) return ORANGE
        if (brightest < 95) return DARK
        return BROWN
    }

    private const val YELLOW = 1
    private const val ORANGE = 2
    private const val DARK = 3
    private const val BROWN = 4
}
