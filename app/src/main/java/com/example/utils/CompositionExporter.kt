package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.content.FileProvider
import com.example.domain.model.CanvasBackground
import com.example.domain.model.CompositionState
import com.example.domain.model.ExportFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

object CompositionExporter {

    /**
     * Renders the complete composition onto an ARGB_8888 bitmap at logical resolution (1024x1024).
     * Reused across export, sharing, and img2img handoff to ensure zero code duplication.
     * Applies:
     * - Background (Transparent or Solid White)
     * - Layer order (Z-index ascending)
     * - Masks (using PorterDuff.Mode.DST_IN, with optional inversion)
     * - Non-destructive cropping (srcRect mapping)
     * - Layer opacity (paint alpha)
     * - Translation, rotation, and scaling transformations
     *
     * Callers must recycle the returned Bitmap when done.
     */
    suspend fun renderFinalComposition(
        context: Context,
        state: CompositionState,
        forceSolidBackground: Boolean = false,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Result<Bitmap> = withContext(dispatcher) {
        try {
            val width = state.canvasWidth
            val height = state.canvasHeight

            val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)

            // 1. Draw Background
            if (forceSolidBackground || state.background == CanvasBackground.WHITE) {
                canvas.drawColor(Color.WHITE)
            } else {
                canvas.drawColor(Color.TRANSPARENT)
            }

            // 2. Draw Layers in z-order
            val sortedLayers = state.layers
                .filter { it.isVisible }
                .sortedBy { it.zIndex }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            for (layer in sortedLayers) {
                drawLayerOntoCanvas(context, canvas, layer, paint, width, height)
            }

            Result.success(outputBitmap)
        } catch (e: OutOfMemoryError) {
            System.gc()
            Result.failure(Exception("Not enough memory to render composition at full resolution: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Renders the composition state into a final bitmap at logical resolution,
     * saves to internal files (for instant sharing via FileProvider) and to
     * MediaStore (Pictures/Graphiton) for user gallery visibility.
     */
    suspend fun exportComposition(
        context: Context,
        state: CompositionState,
        format: ExportFormat = ExportFormat.PNG,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Result<Pair<File, Uri?>> = withContext(dispatcher) {
        val renderResult = renderFinalComposition(
            context = context,
            state = state,
            forceSolidBackground = (format == ExportFormat.JPEG || state.background == CanvasBackground.WHITE),
            dispatcher = dispatcher
        )

        val outputBitmap = renderResult.getOrElse { return@withContext Result.failure(it) }

        try {
            // Save to internal app storage (for FileProvider sharing)
            val compDir = File(context.filesDir, "compositions").apply { if (!exists()) mkdirs() }
            val filename = "Graphiton_Comp_${System.currentTimeMillis()}.${format.extension}"
            val internalFile = File(compDir, filename)

            FileOutputStream(internalFile).use { out ->
                val compressFormat = if (format == ExportFormat.PNG) {
                    Bitmap.CompressFormat.PNG
                } else {
                    Bitmap.CompressFormat.JPEG
                }
                outputBitmap.compress(compressFormat, 95, out)
                out.flush()
            }

            // Save to MediaStore (Pictures/Graphiton)
            val mediaStoreUri = saveToMediaStore(context, internalFile, filename, format.mimeType)

            Result.success(Pair(internalFile, mediaStoreUri))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            outputBitmap.recycle()
        }
    }

    /**
     * Renders the final composition and prepares a single flattened PNG image
     * ready to be consumed as the source image by the existing img2img workflow.
     */
    suspend fun prepareCompositionForImg2img(
        context: Context,
        state: CompositionState,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Result<Pair<File, Uri>> = withContext(dispatcher) {
        val renderResult = renderFinalComposition(
            context = context,
            state = state,
            forceSolidBackground = (state.background == CanvasBackground.WHITE),
            dispatcher = dispatcher
        )

        val bitmap = renderResult.getOrElse { return@withContext Result.failure(it) }

        try {
            val compDir = File(context.filesDir, "compositions").apply { if (!exists()) mkdirs() }
            val filename = "comp_img2img_${System.currentTimeMillis()}.png"
            val file = File(compDir, filename)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            val uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (_: Exception) {
                Uri.fromFile(file)
            }

            Result.success(Pair(file, uri))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawLayerOntoCanvas(
        context: Context,
        canvas: Canvas,
        layer: com.example.domain.model.CompositionLayer,
        paint: Paint,
        canvasWidth: Int,
        canvasHeight: Int
    ) {
        var inputStream: InputStream? = null
        var decodedBitmap: Bitmap? = null
        try {
            inputStream = context.contentResolver.openInputStream(layer.uri)
                ?: return

            // Step 1: Decode bounds to calculate safe downsampling
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            inputStream.close()

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return

            // Calculate max useful dimension on the canvas
            val maxTargetDim = max(canvasWidth, canvasHeight) * max(layer.scale, 1.0f)
            val maxOrigDim = max(origWidth, origHeight)
            var sampleSize = 1
            if (maxOrigDim > maxTargetDim * 1.5f) {
                sampleSize = (maxOrigDim / (maxTargetDim * 1.5f)).toInt().coerceAtLeast(1)
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            inputStream = context.contentResolver.openInputStream(layer.uri)
                ?: return
            decodedBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream.close()

            if (decodedBitmap == null) return

            // Step 2a: Apply regional blur if enabled
            if (layer.isBlurEnabled && layer.blurRadius > 0f && layer.blurMaskUri != null) {
                try {
                    context.contentResolver.openInputStream(layer.blurMaskUri)?.use { blurMaskStream ->
                        val blurMaskRaw = BitmapFactory.decodeStream(blurMaskStream)
                        if (blurMaskRaw != null) {
                            val finalBlurMask = if (layer.isBlurMaskInverted) {
                                MaskTransformation.createInvertedMask(blurMaskRaw)
                            } else {
                                blurMaskRaw
                            }

                            val blurred = FastBlur.blur(decodedBitmap, layer.blurRadius.toInt())

                            val blurComp = Bitmap.createBitmap(decodedBitmap.width, decodedBitmap.height, Bitmap.Config.ARGB_8888)
                            val bCanvas = Canvas(blurComp)
                            bCanvas.drawBitmap(decodedBitmap, 0f, 0f, null)

                            val maskedBlur = Bitmap.createBitmap(decodedBitmap.width, decodedBitmap.height, Bitmap.Config.ARGB_8888)
                            val mbCanvas = Canvas(maskedBlur)
                            mbCanvas.drawBitmap(blurred, 0f, 0f, null)

                            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                            }
                            val srcR = Rect(0, 0, finalBlurMask.width, finalBlurMask.height)
                            val dstR = RectF(0f, 0f, decodedBitmap.width.toFloat(), decodedBitmap.height.toFloat())
                            mbCanvas.drawBitmap(finalBlurMask, srcR, dstR, maskPaint)

                            bCanvas.drawBitmap(maskedBlur, 0f, 0f, null)

                            maskedBlur.recycle()
                            blurred.recycle()
                            if (finalBlurMask !== blurMaskRaw) finalBlurMask.recycle()
                            blurMaskRaw.recycle()
                            decodedBitmap.recycle()
                            decodedBitmap = blurComp
                        }
                    }
                } catch (_: Exception) {
                    // Continue with unblurred layer
                }
            }

            // Step 2b: Apply visibility mask if present
            if (layer.maskUri != null) {
                try {
                    context.contentResolver.openInputStream(layer.maskUri)?.use { maskStream ->
                        val maskBitmap = BitmapFactory.decodeStream(maskStream)
                        if (maskBitmap != null) {
                            val maskedBitmap = Bitmap.createBitmap(decodedBitmap.width, decodedBitmap.height, Bitmap.Config.ARGB_8888)
                            val mCanvas = Canvas(maskedBitmap)
                            val mPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            mCanvas.drawBitmap(decodedBitmap, 0f, 0f, mPaint)

                            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                            }
                            val finalMask = if (layer.isMaskInverted) {
                                MaskTransformation.createInvertedMask(maskBitmap)
                            } else {
                                maskBitmap
                            }

                            val mSrc = Rect(0, 0, finalMask.width, finalMask.height)
                            val mDst = RectF(0f, 0f, decodedBitmap.width.toFloat(), decodedBitmap.height.toFloat())
                            mCanvas.drawBitmap(finalMask, mSrc, mDst, maskPaint)

                            if (finalMask !== maskBitmap) finalMask.recycle()
                            maskBitmap.recycle()
                            decodedBitmap.recycle()
                            decodedBitmap = maskedBitmap
                        }
                    }
                } catch (_: Exception) {
                    // Continue with unmasked layer if mask fails to load
                }
            }

            // Step 3: Source crop rectangle relative to decoded bitmap
            val crop = layer.crop
            val cropLeft = (decodedBitmap.width * crop.left.coerceIn(0f, 1f)).toInt().coerceIn(0, decodedBitmap.width - 1)
            val cropTop = (decodedBitmap.height * crop.top.coerceIn(0f, 1f)).toInt().coerceIn(0, decodedBitmap.height - 1)
            val cropRight = (decodedBitmap.width * crop.right.coerceIn(0f, 1f)).toInt().coerceIn(cropLeft + 1, decodedBitmap.width)
            val cropBottom = (decodedBitmap.height * crop.bottom.coerceIn(0f, 1f)).toInt().coerceIn(cropTop + 1, decodedBitmap.height)
            val srcRect = Rect(cropLeft, cropTop, cropRight, cropBottom)

            // Step 4: Visible destination dimensions on the composition canvas
            val visibleW = (crop.right - crop.left) * layer.intrinsicWidth * layer.scale
            val visibleH = (crop.bottom - crop.top) * layer.intrinsicHeight * layer.scale
            val dstRectF = RectF(-visibleW / 2f, -visibleH / 2f, visibleW / 2f, visibleH / 2f)

            // Step 5: Set layer opacity
            paint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255).toInt()

            // Step 6: Matrix transformation: center at (layer.x, layer.y) and rotate
            canvas.save()
            canvas.translate(layer.x, layer.y)
            canvas.rotate(layer.rotation)
            canvas.drawBitmap(decodedBitmap, srcRect, dstRectF, paint)
            canvas.restore()
        } catch (e: Exception) {
            // Silently skip failed individual layer to preserve rest of composition
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            decodedBitmap?.recycle()
        }
    }

    private fun saveToMediaStore(
        context: Context,
        file: File,
        filename: String,
        mimeType: String
    ): Uri? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Graphiton")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null

            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
                out.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (e: Exception) {
            null
        }
    }
}
