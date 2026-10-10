package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Coil Transformation that non-destructively applies:
 * 1. Regional Blur/Privacy effect (FastBlur with blurMaskUri)
 * 2. Alpha visibility mask (PorterDuff.Mode.DST_IN with maskUri)
 */
class MaskTransformation(
    private val context: Context,
    private val maskUri: Uri? = null,
    private val isInverted: Boolean = false,
    private val isBlurEnabled: Boolean = false,
    private val blurRadius: Float = 0f,
    private val blurMaskUri: Uri? = null,
    private val isBlurMaskInverted: Boolean = false
) : Transformation {

    override val cacheKey: String
        get() {
            val maskMod = try { maskUri?.path?.let { File(it).lastModified() } ?: 0L } catch (_: Exception) { 0L }
            val blurMod = try { blurMaskUri?.path?.let { File(it).lastModified() } ?: 0L } catch (_: Exception) { 0L }
            return "mask_${maskUri}_${isInverted}_${maskMod}_blur_${isBlurEnabled}_${blurRadius.toInt()}_${blurMaskUri}_${isBlurMaskInverted}_$blurMod"
        }

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.IO) {
        try {
            var currentBitmap = input
            var intermediateBitmap: Bitmap? = null

            // 1. Apply Regional Blur effect if enabled
            if (isBlurEnabled && blurRadius > 0f && blurMaskUri != null) {
                var blurMaskRaw: Bitmap? = null
                var finalBlurMask: Bitmap? = null
                var blurred: Bitmap? = null
                var maskedBlur: Bitmap? = null
                try {
                    context.contentResolver.openInputStream(blurMaskUri)?.use { stream ->
                        blurMaskRaw = BitmapFactory.decodeStream(stream)
                    }
                    if (blurMaskRaw != null) {
                        finalBlurMask = if (isBlurMaskInverted) {
                            createInvertedMask(blurMaskRaw!!)
                        } else {
                            blurMaskRaw
                        }

                        blurred = FastBlur.blur(currentBitmap, blurRadius.toInt())

                        // Composite masked blur over currentBitmap
                        val blurComp = Bitmap.createBitmap(currentBitmap.width, currentBitmap.height, Bitmap.Config.ARGB_8888)
                        val bCanvas = Canvas(blurComp)
                        bCanvas.drawBitmap(currentBitmap, 0f, 0f, null)

                        maskedBlur = Bitmap.createBitmap(currentBitmap.width, currentBitmap.height, Bitmap.Config.ARGB_8888)
                        val mbCanvas = Canvas(maskedBlur)
                        mbCanvas.drawBitmap(blurred!!, 0f, 0f, null)

                        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                        }
                        val srcR = Rect(0, 0, finalBlurMask!!.width, finalBlurMask!!.height)
                        val dstR = RectF(0f, 0f, currentBitmap.width.toFloat(), currentBitmap.height.toFloat())
                        mbCanvas.drawBitmap(finalBlurMask!!, srcR, dstR, maskPaint)

                        bCanvas.drawBitmap(maskedBlur!!, 0f, 0f, null)
                        intermediateBitmap = blurComp
                        currentBitmap = blurComp
                    }
                } catch (_: Exception) {
                    // Fail gracefully, keep currentBitmap
                } finally {
                    maskedBlur?.recycle()
                    blurred?.recycle()
                    if (finalBlurMask != null && finalBlurMask != blurMaskRaw) {
                        finalBlurMask.recycle()
                    }
                    blurMaskRaw?.recycle()
                }
            }

            // 2. Apply Visibility Mask if present
            if (maskUri != null) {
                var maskBitmap: Bitmap? = null
                var finalMask: Bitmap? = null
                try {
                    context.contentResolver.openInputStream(maskUri)?.use { stream ->
                        maskBitmap = BitmapFactory.decodeStream(stream)
                    }

                    if (maskBitmap != null) {
                        finalMask = if (isInverted) {
                            createInvertedMask(maskBitmap!!)
                        } else {
                            maskBitmap
                        }

                        val output = Bitmap.createBitmap(currentBitmap.width, currentBitmap.height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(output)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        canvas.drawBitmap(currentBitmap, 0f, 0f, paint)

                        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                        }
                        val srcRect = Rect(0, 0, finalMask!!.width, finalMask!!.height)
                        val dstRect = RectF(0f, 0f, currentBitmap.width.toFloat(), currentBitmap.height.toFloat())
                        canvas.drawBitmap(finalMask!!, srcRect, dstRect, maskPaint)

                        // Recycle intermediate if created
                        intermediateBitmap?.recycle()
                        return@withContext output
                    }
                } catch (_: Exception) {
                    // Return currentBitmap if masking failed
                } finally {
                    if (finalMask != null && finalMask != maskBitmap) {
                        finalMask.recycle()
                    }
                    maskBitmap?.recycle()
                }
            }

            currentBitmap
        } catch (_: Exception) {
            input
        }
    }

    companion object {
        fun createInvertedMask(src: Bitmap): Bitmap {
            val inverted = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(src.width * src.height)
            src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
            for (i in pixels.indices) {
                val a = (pixels[i] ushr 24) and 0xFF
                val invA = 255 - a
                pixels[i] = (invA shl 24) or (0x00FFFFFF and pixels[i])
            }
            inverted.setPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
            return inverted
        }
    }
}
