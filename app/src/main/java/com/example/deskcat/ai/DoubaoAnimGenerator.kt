package com.example.deskcat.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.deskcat.config.ApiConfig
import com.example.deskcat.settings.DEFAULT_PET_NAME
import com.example.deskcat.settings.PetImageResolver
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * 调用豆包图片生成接口，把用户上传的宠物图片转成简洁线稿透明桌宠帧。
 */
object DoubaoAnimGenerator {
    private const val TAG = "DoubaoAnimGenerator"
    private const val OUTPUT_IMAGE_SIZE = "2K"
    private const val GENERATED_FRAME_COUNT = 3
    private const val SOURCE_MAX_SIZE = 1024

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    sealed class GenerateResult {
        data class Success(
            val frames: List<Bitmap>,
            val warningMessage: String? = null,
        ) : GenerateResult()

        data class Failure(val message: String) : GenerateResult()
    }

    val DEFAULT_PROMPT: String = defaultPrompt(DEFAULT_PET_NAME)

    fun defaultPrompt(petName: String): String {
        val subject = petName.trim().ifBlank { DEFAULT_PET_NAME }
        return """
请参考输入图片中的主体特征，为“$subject”生成原创桌宠动画关键帧。
核心要求：
- 只参考主体的轮廓、姿态、比例、标志性花纹或结构特征，不复制原图背景、光影、文字、水印、Logo 或平台标识。
- 将主体转换为可爱的极简黑白线稿风格，线条清晰、圆润、干净，适合手机桌面悬浮展示。
- 背景必须透明；如果无法透明，则使用纯白背景，后续会自动抠图。
- 主体居中，占画面主要区域，不要过小，不要添加复杂背景、边框、编号、签名、额外角色或装饰元素。
- 三张关键帧必须保持同一个主体、同一画风、同一构图和同一比例，只改变小幅动作。
- 动作幅度要小，适合组成循环动画：第 1 帧 -> 第 2 帧 -> 第 3 帧 -> 第 2 帧 -> 第 1 帧。
- 输出 PNG 图像，不要生成文字。
""".trimIndent()
    }

    suspend fun generate(
        context: Context,
        sourceBitmap: Bitmap,
        petName: String,
        customPrompt: String? = null,
    ): GenerateResult {
        val appContext = context.applicationContext
        val config = ApiConfig.doubaoImage(appContext)
        if (!config.isConfigured) {
            return GenerateResult.Failure("未配置豆包图片生成 API Key，请检查 local.properties。")
        }

        val sourceImage = encodeSourceImage(sourceBitmap)
        val generatedFrames = mutableListOf<Bitmap>()
        val transparencyWarnings = mutableListOf<Int>()

        for (index in 1..GENERATED_FRAME_COUNT) {
            val result = requestFrame(
                config = config,
                prompt = singleFramePrompt(index, petName, customPrompt),
                sourceImage = sourceImage,
            )
            when (result) {
                is GenerateResult.Success -> {
                    val frame = result.frames.firstOrNull()
                    if (frame == null) {
                        Log.e(TAG, "第 $index 帧生成成功但没有可用图片。")
                        break
                    }
                    val transparentFrame = PetImageResolver.removeBackgroundFromBitmap(appContext, frame)
                    if (!PetImageResolver.hasMeaningfulTransparency(transparentFrame)) {
                        transparencyWarnings += index
                    }
                    generatedFrames += transparentFrame
                }
                is GenerateResult.Failure -> {
                    Log.e(TAG, "第 $index 帧生成失败：${result.message}")
                    return if (generatedFrames.isEmpty()) {
                        result
                    } else {
                        val warning = "仅生成 ${generatedFrames.size} 张关键帧；第 $index 帧失败：${result.message}"
                        GenerateResult.Success(buildPingPongFrames(generatedFrames), warning)
                    }
                }
            }
        }

        if (generatedFrames.isEmpty()) {
            return GenerateResult.Failure("豆包未返回可解析的图片。")
        }

        val warning = if (transparencyWarnings.isEmpty()) {
            null
        } else {
            "已生成图片，但第 ${transparencyWarnings.joinToString("、")} 帧透明背景处理可能不完整。"
        }
        return GenerateResult.Success(buildPingPongFrames(generatedFrames), warning)
    }

    private fun requestFrame(
        config: ApiConfig.DoubaoImageConfig,
        prompt: String,
        sourceImage: String,
    ): GenerateResult {
        val body = buildRequestJson(config, prompt, sourceImage).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(config.generationUrl)
            .post(body)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = "豆包图片生成请求失败：HTTP ${response.code} ${extractErrorMessage(responseText)}".trim()
                    return GenerateResult.Failure(message)
                }

                val frames = parseGeneratedImages(responseText)
                if (frames.isEmpty()) {
                    GenerateResult.Failure("豆包响应中没有可解析图片：${summarizeResponse(responseText)}")
                } else {
                    GenerateResult.Success(frames)
                }
            }
        } catch (e: Exception) {
            val message = "豆包图片生成异常：${e.message ?: e::class.java.simpleName}"
            Log.e(TAG, message, e)
            GenerateResult.Failure(message)
        }
    }

    private fun buildRequestJson(
        config: ApiConfig.DoubaoImageConfig,
        prompt: String,
        sourceImage: String,
    ): String {
        return JSONObject().apply {
            put("model", config.model)
            put("prompt", prompt)
            put("size", OUTPUT_IMAGE_SIZE)
            put("response_format", "b64_json")
            put("output_format", "png")
            put("watermark", false)
            put(config.imageInputField, sourceImage)
        }.toString()
    }

    private fun basePrompt(petName: String, customPrompt: String?): String {
        val extra = customPrompt?.trim().orEmpty()
        return if (extra.isBlank()) {
            defaultPrompt(petName)
        } else {
            "${defaultPrompt(petName)}\n用户补充要求：$extra"
        }
    }

    private fun singleFramePrompt(index: Int, petName: String, customPrompt: String?): String {
        val subject = petName.trim().ifBlank { DEFAULT_PET_NAME }
        val action = when (index) {
            1 -> """
第 1 帧：
$subject 保持自然起始姿态，身体稳定，表情放松。动作幅度最小，适合作为循环动画起点。
""".trimIndent()

            2 -> """
第 2 帧：
在第 1 帧基础上做轻微过渡动作，例如前肢微抬、身体轻微上浮、尾部或边缘小幅摆动。主体位置、大小和轮廓尽量一致，改动面积不要超过 10%。
""".trimIndent()

            else -> """
第 3 帧：
动作到达最高点，但仍然保持小幅变化，例如前肢抬到最高、身体轻微上浮或表情更开心。适合随后回放到第 2 帧和第 1 帧形成流畅循环。
""".trimIndent()
        }
        return "${basePrompt(petName, customPrompt)}\n当前只生成一张图：\n$action"
    }

    private fun encodeSourceImage(sourceBitmap: Bitmap): String {
        val prepared = resizeForRequest(sourceBitmap)
        val bytes = PetImageResolver.bitmapToPngBytes(prepared)
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/png;base64,$base64"
    }

    private fun resizeForRequest(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= SOURCE_MAX_SIZE) return bitmap
        val scale = SOURCE_MAX_SIZE.toFloat() / maxSide.toFloat()
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun parseGeneratedImages(responseJson: String): List<Bitmap> {
        val root = JSONObject(responseJson)
        val candidates = mutableListOf<GeneratedImage>()

        root.optJSONArray("data")?.collectImages(candidates)
        root.optJSONArray("images")?.collectImages(candidates)
        root.optJSONArray("result")?.collectImages(candidates)
        root.optJSONArray("results")?.collectImages(candidates)
        if (candidates.isEmpty()) collectImagesRecursive(root, candidates)

        return candidates
            .sortedBy { it.order }
            .distinctBy { it.value }
            .mapNotNull { decodeBitmapCandidate(it.value) }
    }

    private fun JSONArray.collectImages(output: MutableList<GeneratedImage>) {
        for (i in 0 until length()) {
            when (val value = opt(i)) {
                is JSONObject -> collectImageCandidate(value, i)?.let(output::add)
                is String -> if (looksLikeImageValue("url", value)) {
                    output.add(GeneratedImage(order = i, value = value))
                }
            }
        }
    }

    private fun collectImageCandidate(json: JSONObject, order: Int): GeneratedImage? {
        val value = json.optString("url").ifBlank {
            json.optString("b64_json").ifBlank {
                imageValue(json.opt("image_url")).ifBlank {
                    imageValue(json.opt("image"))
                }
            }
        }
        if (value.isBlank()) return null
        return GeneratedImage(order = order, value = value)
    }

    private fun imageValue(value: Any?): String {
        return when (value) {
            is JSONObject -> value.optString("url").ifBlank { value.optString("b64_json") }
            is String -> value
            else -> ""
        }
    }

    private fun collectImagesRecursive(value: Any?, output: MutableList<GeneratedImage>) {
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = value.opt(key)
                    if (child is String && looksLikeImageValue(key, child)) {
                        output.add(GeneratedImage(order = output.size, value = child))
                    } else {
                        collectImagesRecursive(child, output)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) collectImagesRecursive(value.opt(i), output)
            }
        }
    }

    private fun looksLikeImageValue(key: String, value: String): Boolean {
        val lowerKey = key.lowercase()
        val lowerValue = value.take(64).lowercase()
        if (lowerValue.startsWith("data:image/")) return true
        if (lowerValue.startsWith("http://") || lowerValue.startsWith("https://")) {
            return lowerKey.contains("url") ||
                lowerKey.contains("image") ||
                Regex("\\.(png|jpg|jpeg|webp)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(value)
        }
        if (lowerKey.contains("b64") || lowerKey.contains("base64")) return value.length > 200
        return false
    }

    private fun decodeBitmapCandidate(value: String): Bitmap? {
        return when {
            value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true) -> downloadBitmap(value)
            value.startsWith("data:image", ignoreCase = true) -> {
                val base64 = value.substringAfter(',', missingDelimiterValue = "")
                decodeBase64Bitmap(base64)
            }
            else -> decodeBase64Bitmap(value)
        }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.e(TAG, "下载生成图失败：${e.message}", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun decodeBase64Bitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "解析生成图 Base64 失败：${e.message}", e)
            null
        }
    }

    private fun buildPingPongFrames(frames: List<Bitmap>): List<Bitmap> {
        return when (frames.size) {
            1 -> listOf(frames[0])
            2 -> listOf(frames[0], frames[1], frames[0])
            else -> listOf(frames[0], frames[1], frames[2], frames[1], frames[0])
        }
    }

    private fun extractErrorMessage(errorText: String): String {
        if (errorText.isBlank()) return ""
        return runCatching {
            val root = JSONObject(errorText)
            val error = root.opt("error")
            when (error) {
                is JSONObject -> error.optString("message").ifBlank { error.toString() }
                is String -> error
                else -> root.optString("message").ifBlank { errorText.take(300) }
            }
        }.getOrDefault(errorText.take(300))
    }

    private fun summarizeResponse(responseText: String): String {
        return runCatching {
            val parsed = JSONTokener(responseText).nextValue()
            if (parsed is JSONObject) summarizeJsonObject(parsed) else responseText.take(300)
        }.getOrDefault(responseText.take(300))
    }

    private fun summarizeJsonObject(json: JSONObject): String {
        json.optJSONObject("error")?.let { error ->
            return error.optString("message").ifBlank { error.toString() }.take(300)
        }
        json.optString("message").takeIf { it.isNotBlank() }?.let { return it.take(300) }
        val keys = mutableListOf<String>()
        val iterator = json.keys()
        while (iterator.hasNext() && keys.size < 8) keys += iterator.next()
        return "响应字段：${keys.joinToString(", ")}"
    }

    private data class GeneratedImage(
        val order: Int,
        val value: String,
    )
}
