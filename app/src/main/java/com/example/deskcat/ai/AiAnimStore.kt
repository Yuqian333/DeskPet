package com.example.deskcat.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

object AiAnimStore {
    private const val ROOT_DIR_NAME = "ai_anim"
    private const val LEGACY_CURRENT_DIR_NAME = "current"
    private const val SETS_DIR_NAME = "sets"
    private const val FRAME_PREFIX = "frame_"
    private const val FRAME_SUFFIX = ".png"
    private const val META_FILE_NAME = "meta.json"

    data class StoredAnimation(
        val dirPath: String,
        val title: String,
        val frameCount: Int,
        val updatedAtMillis: Long,
        val coverPath: String?,
        val editable: Boolean,
        val legacy: Boolean,
    )

    private fun setsDir(context: Context): File {
        return File(context.filesDir, "$ROOT_DIR_NAME/$SETS_DIR_NAME")
    }

    private fun legacyCurrentDir(context: Context): File {
        return File(context.filesDir, "$ROOT_DIR_NAME/$LEGACY_CURRENT_DIR_NAME")
    }

    fun saveCurrent(context: Context, frames: List<Bitmap>): String? {
        if (frames.isEmpty()) return null
        val root = setsDir(context)
        if (!root.mkdirs() && !root.exists()) return null
        val createdAt = System.currentTimeMillis()
        val dir = File(root, "anim_$createdAt")
        if (!dir.mkdirs() && !dir.exists()) return null

        frames.forEachIndexed { index, bitmap ->
            val file = File(dir, "$FRAME_PREFIX${index + 1}$FRAME_SUFFIX")
            val saved = runCatching {
                file.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            }.getOrDefault(false)
            if (!saved) {
                deleteDir(dir)
                return null
            }
        }
        writeMeta(dir, title = formatTitle(createdAt), createdAtMillis = createdAt)
        return dir.absolutePath
    }

    fun load(dirPath: String?): List<Bitmap> {
        if (dirPath.isNullOrBlank()) return emptyList()
        val dir = File(dirPath)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file ->
            file.isFile && file.name.startsWith(FRAME_PREFIX) && file.name.endsWith(FRAME_SUFFIX)
        }
            ?.sortedBy { it.nameWithoutExtension.removePrefix(FRAME_PREFIX).toIntOrNull() ?: Int.MAX_VALUE }
            ?.mapNotNull { BitmapFactory.decodeFile(it.absolutePath) }
            .orEmpty()
    }

    fun list(context: Context): List<StoredAnimation> {
        val root = setsDir(context)
        val setItems = root.listFiles { file -> file.isDirectory }
            .orEmpty()
            .mapNotNull { dir ->
                val frameCount = frameFiles(dir).size
                if (frameCount <= 0) return@mapNotNull null
                val meta = readMeta(dir)
                val createdAt = meta?.optLong("createdAtMillis")?.takeIf { it > 0L } ?: dir.lastModified()
                StoredAnimation(
                    dirPath = dir.absolutePath,
                    title = meta?.optString("title")?.takeIf { it.isNotBlank() } ?: formatTitle(createdAt),
                    frameCount = frameCount,
                    updatedAtMillis = createdAt,
                    coverPath = frameFiles(dir).firstOrNull()?.absolutePath,
                    editable = true,
                    legacy = false,
                )
            }
        val legacyDir = legacyCurrentDir(context)
        val legacyFrameCount = frameFiles(legacyDir).size
        val legacyItem = if (legacyFrameCount > 0) {
            StoredAnimation(
                dirPath = legacyDir.absolutePath,
                title = "AI 动画 旧版本",
                frameCount = legacyFrameCount,
                updatedAtMillis = legacyDir.lastModified(),
                coverPath = frameFiles(legacyDir).firstOrNull()?.absolutePath,
                editable = false,
                legacy = true,
            )
        } else {
            null
        }
        return (setItems + listOfNotNull(legacyItem)).sortedByDescending { it.updatedAtMillis }
    }

    fun rename(context: Context, dirPath: String, title: String): Boolean {
        val dir = File(dirPath)
        if (!isEditableSetDir(context, dir)) return false
        val normalized = title.trim()
        if (normalized.isBlank()) return false
        val meta = readMeta(dir)
        val createdAt = meta?.optLong("createdAtMillis")?.takeIf { it > 0L } ?: dir.lastModified()
        return writeMeta(dir, normalized, createdAt)
    }

    fun delete(context: Context, dirPath: String): Boolean {
        val dir = File(dirPath)
        if (!isEditableSetDir(context, dir)) return false
        deleteDir(dir)
        return !dir.exists()
    }

    private fun frameFiles(dir: File): List<File> {
        return dir.listFiles { file ->
            file.isFile && file.name.startsWith(FRAME_PREFIX) && file.name.endsWith(FRAME_SUFFIX)
        }
            ?.sortedBy { it.nameWithoutExtension.removePrefix(FRAME_PREFIX).toIntOrNull() ?: Int.MAX_VALUE }
            .orEmpty()
    }

    private fun deleteDir(dir: File) {
        dir.walkBottomUp().forEach { file -> runCatching { file.delete() } }
    }

    private fun readMeta(dir: File): JSONObject? {
        val file = File(dir, META_FILE_NAME)
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    private fun writeMeta(dir: File, title: String, createdAtMillis: Long): Boolean {
        val file = File(dir, META_FILE_NAME)
        val json = JSONObject().apply {
            put("title", title)
            put("createdAtMillis", createdAtMillis)
        }
        return runCatching {
            file.writeText(json.toString(), Charsets.UTF_8)
            true
        }.getOrDefault(false)
    }

    private fun isEditableSetDir(context: Context, dir: File): Boolean {
        val root = setsDir(context).canonicalFile
        val target = runCatching { dir.canonicalFile }.getOrNull() ?: return false
        return target.isDirectory && target.parentFile == root
    }

    private fun formatTitle(updatedAtMillis: Long): String {
        val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return "AI 动画 ${formatter.format(Date(updatedAtMillis))}"
    }
}
