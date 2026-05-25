package com.example.deskcat.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

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

    private fun decodeSampledBitmap(context: Context, uri: Uri, targetSizePx: Int): Bitmap? {
        val safeTargetSize = targetSizePx.coerceAtLeast(1)
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
}
