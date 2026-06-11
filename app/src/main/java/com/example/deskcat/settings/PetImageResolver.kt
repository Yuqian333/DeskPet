package com.example.deskcat.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.deskcat.config.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import kotlin.math.abs

object PetImageResolver {

    fun decodeBitmap(
        context: Context,
        uriString: String?,
        targetSizePx: Int = DEFAULT_TARGET_SIZE_PX,
    ): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        val uri = Uri.parse(uriString)
        return runCatching {
            decodeSampledBitmap(context, uri, targetSizePx)
        }.getOrNull()
    }

    suspend fun decodeAndRemoveBackground(context: Context, uriString: String?): Bitmap? {
        val original = decodeBitmap(context, uriString) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { removeBackground(context, original) }.getOrElse { original }
        }
    }

    suspend fun removeBackgroundFromBitmap(context: Context, bitmap: Bitmap): Bitmap {
        return withContext(Dispatchers.IO) {
            removeBackground(context, bitmap)
        }
    }

    fun hasMeaningfulTransparency(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return false
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return edgeTransparencyRatio(pixels, width, height) >= EDGE_TRANSPARENT_RATIO
    }

    private fun removeBackground(context: Context, bitmap: Bitmap): Bitmap {
        if (hasMeaningfulTransparency(bitmap) && !hasOpaqueLightEdges(bitmap)) return bitmap
        val viaApi = runCatching { removeBackgroundViaApi(context, bitmap) }.getOrNull()
        if (viaApi != null && hasMeaningfulTransparency(viaApi) && !hasOpaqueLightEdges(viaApi)) return viaApi
        return removeLightBackgroundLocally(viaApi ?: bitmap)
    }

    private fun removeBackgroundViaApi(context: Context, bitmap: Bitmap): Bitmap? {
        val apiKey = ApiConfig.removeBgApiKey(context)
        if (apiKey.isBlank()) return null
        val imageBytes = bitmapToPngBytes(bitmap)

        val boundary = "----FormBoundary${System.currentTimeMillis()}"
        val url = java.net.URL("https://api.remove.bg/v1.0/removebg")
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("X-Api-Key", apiKey)
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000

        return try {
            connection.outputStream.use { out ->
                val writer = out.bufferedWriter()
                writer.write("--$boundary\r\n")
                writer.write("Content-Disposition: form-data; name=\"image_file\"; filename=\"pet.png\"\r\n")
                writer.write("Content-Type: image/png\r\n\r\n")
                writer.flush()
                out.write(imageBytes)
                out.flush()
                writer.write("\r\n--$boundary\r\n")
                writer.write("Content-Disposition: form-data; name=\"size\"\r\n\r\n")
                writer.write("auto\r\n")
                writer.write("--$boundary--\r\n")
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) return null

            val resultBytes = connection.inputStream.use { it.readBytes() }
            BitmapFactory.decodeByteArray(resultBytes, 0, resultBytes.size)
        } finally {
            connection.disconnect()
        }
    }

    private fun removeLightBackgroundLocally(bitmap: Bitmap): Bitmap {
        val source = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val visited = BooleanArray(pixels.size)
        val queue = ArrayDeque<Int>()
        val background = estimateEdgeBackgroundColor(pixels, width, height)

        fun enqueue(index: Int) {
            if (index in pixels.indices && !visited[index]) {
                visited[index] = true
                queue.add(index)
            }
        }

        for (x in 0 until width) {
            enqueue(x)
            enqueue((height - 1) * width + x)
        }
        for (y in 0 until height) {
            enqueue(y * width)
            enqueue(y * width + width - 1)
        }

        while (!queue.isEmpty()) {
            val index = queue.removeFirst()
            val color = pixels[index]
            if (!looksLikeBackground(color, background)) continue

            pixels[index] = color and 0x00FFFFFF
            val x = index % width
            val y = index / width
            if (x > 0) enqueue(index - 1)
            if (x < width - 1) enqueue(index + 1)
            if (y > 0) enqueue(index - width)
            if (y < height - 1) enqueue(index + width)
        }

        softenLightEdgePixels(pixels, width, height, background)

        source.setPixels(pixels, 0, width, 0, 0, width, height)
        return source
    }

    private fun hasOpaqueLightEdges(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return false
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val edgeIndexes = edgeIndexes(width, height)
        if (edgeIndexes.isEmpty()) return false
        val opaqueLight = edgeIndexes.count { index ->
            val color = pixels[index]
            Color.alpha(color) >= 245 && brightness(color) >= 235
        }
        return opaqueLight.toFloat() / edgeIndexes.size.toFloat() >= 0.35f
    }

    private fun edgeTransparencyRatio(pixels: IntArray, width: Int, height: Int): Float {
        val edgeIndexes = edgeIndexes(width, height)
        if (edgeIndexes.isEmpty()) return 0f
        val transparent = edgeIndexes.count { Color.alpha(pixels[it]) < 245 }
        return transparent.toFloat() / edgeIndexes.size.toFloat()
    }

    private fun edgeIndexes(width: Int, height: Int): List<Int> {
        if (width <= 0 || height <= 0) return emptyList()
        return buildList {
            for (x in 0 until width) {
                add(x)
                add((height - 1) * width + x)
            }
            for (y in 1 until height - 1) {
                add(y * width)
                add(y * width + width - 1)
            }
        }
    }

    private fun estimateEdgeBackgroundColor(pixels: IntArray, width: Int, height: Int): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0L

        fun add(color: Int) {
            if (Color.alpha(color) < 245) return
            r += Color.red(color)
            g += Color.green(color)
            b += Color.blue(color)
            count++
        }

        for (x in 0 until width) {
            add(pixels[x])
            add(pixels[(height - 1) * width + x])
        }
        for (y in 0 until height) {
            add(pixels[y * width])
            add(pixels[y * width + width - 1])
        }

        if (count == 0L) return Color.WHITE
        return Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun looksLikeBackground(color: Int, background: Int): Boolean {
        if (Color.alpha(color) < 245) return true
        val value = brightness(color)
        val edgeDistance = colorDistance(color, background)
        val channelSpread = maxOf(Color.red(color), Color.green(color), Color.blue(color)) -
            minOf(Color.red(color), Color.green(color), Color.blue(color))
        return value >= 238 ||
            (value >= 205 && edgeDistance <= 96 && channelSpread <= 42) ||
            (value >= 190 && edgeDistance <= 54 && channelSpread <= 28)
    }

    private fun softenLightEdgePixels(pixels: IntArray, width: Int, height: Int, background: Int) {
        val original = pixels.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val color = original[index]
                if (Color.alpha(color) < 245) continue
                if (!hasTransparentNeighbor(original, width, index)) continue

                val value = brightness(color)
                val edgeDistance = colorDistance(color, background)
                val channelSpread = maxOf(Color.red(color), Color.green(color), Color.blue(color)) -
                    minOf(Color.red(color), Color.green(color), Color.blue(color))
                if (value >= 228 || (value >= 198 && edgeDistance <= 86 && channelSpread <= 38)) {
                    val alpha = ((255 - (value - 190).coerceIn(0, 65) * 3).coerceIn(36, 180))
                    pixels[index] = (alpha shl 24) or (color and 0x00FFFFFF)
                }
            }
        }
    }

    private fun hasTransparentNeighbor(pixels: IntArray, width: Int, index: Int): Boolean {
        return Color.alpha(pixels[index - 1]) < 245 ||
            Color.alpha(pixels[index + 1]) < 245 ||
            Color.alpha(pixels[index - width]) < 245 ||
            Color.alpha(pixels[index + width]) < 245
    }

    private fun brightness(color: Int): Int {
        return (Color.red(color) + Color.green(color) + Color.blue(color)) / 3
    }

    private fun colorDistance(first: Int, second: Int): Int {
        return abs(Color.red(first) - Color.red(second)) +
            abs(Color.green(first) - Color.green(second)) +
            abs(Color.blue(first) - Color.blue(second))
    }

    fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, targetSizePx: Int): Bitmap? {
        val safeTargetSize = targetSizePx.coerceAtLeast(1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                reqWidth = safeTargetSize,
                reqHeight = safeTargetSize,
            )
        }

        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private const val DEFAULT_TARGET_SIZE_PX = 512
    private const val EDGE_TRANSPARENT_RATIO = 0.72f
}
