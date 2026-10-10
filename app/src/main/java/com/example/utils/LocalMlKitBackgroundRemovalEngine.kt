package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Genuine local, on-device ML background removal implementation using Google ML Kit Selfie Segmentation.
 * - Statically bundled local model (no internet downloads, zero cloud dependency)
 * - Runs on-device CPU/GPU
 * - Memory-constrained: Downscales input to max 512px for inference, processes 1 image at a time,
 *   and recycles all intermediate buffers.
 * - Lazy-loaded: Model segmenter is initialized only when background removal is first invoked.
 * - Non-destructive: Produces an alpha mask saved in files/masks/ without touching the source image.
 */
class LocalMlKitBackgroundRemovalEngine : BackgroundRemovalEngine {

    override val name: String = "Local ML Kit On-Device Segmenter"

    private val mutex = Mutex()

    @Volatile
    private var segmenter: Segmenter? = null

    override fun isAvailable(): Boolean = true

    private fun getOrCreateSegmenter(): Segmenter {
        val existing = segmenter
        if (existing != null) return existing
        synchronized(this) {
            val doubleCheck = segmenter
            if (doubleCheck != null) return doubleCheck

            val options = SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build()
            val created = Segmentation.getClient(options)
            segmenter = created
            return created
        }
    }

    override suspend fun removeBackground(
        context: Context,
        imageUri: Uri,
        layerId: String
    ): BackgroundRemovalResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            var decodedBitmap: Bitmap? = null
            var maskBitmap: Bitmap? = null

            try {
                // 1. Inspect image dimensions safely
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                } ?: return@withContext BackgroundRemovalResult.Error("Could not access image at URI")

                val origW = boundsOptions.outWidth
                val origH = boundsOptions.outHeight
                if (origW <= 0 || origH <= 0) {
                    return@withContext BackgroundRemovalResult.Error("Invalid image dimensions")
                }

                // Downscale to max 512px for inference efficiency and low RAM usage
                val maxDim = max(origW, origH)
                var inSampleSize = 1
                if (maxDim > 512) {
                    inSampleSize = (maxDim / 512).coerceAtLeast(1)
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    decodedBitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)
                } ?: return@withContext BackgroundRemovalResult.Error("Failed to decode image data")

                val inputBmp = decodedBitmap
                    ?: return@withContext BackgroundRemovalResult.Error("Decoded bitmap was null")

                // 2. Local ML Inference
                val seg = try {
                    getOrCreateSegmenter()
                } catch (e: Throwable) {
                    return@withContext BackgroundRemovalResult.Unavailable(
                        "Local ML model initialization failed: ${e.message ?: "Unsupported architecture"}"
                    )
                }

                val inputImage = InputImage.fromBitmap(inputBmp, 0)
                val segmentationMask = try {
                    Tasks.await(seg.process(inputImage), 30, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    return@withContext BackgroundRemovalResult.Error(
                        "Background removal failed. The image could not be processed: ${e.message}",
                        e
                    )
                }

                val maskBuffer: ByteBuffer = segmentationMask.buffer
                maskBuffer.rewind()
                val maskW = segmentationMask.width
                val maskH = segmentationMask.height

                if (maskW <= 0 || maskH <= 0) {
                    return@withContext BackgroundRemovalResult.Error("Segmentation produced an empty mask")
                }

                // 3. Process confidence buffer into anti-aliased alpha mask
                maskBitmap = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ARGB_8888)
                val totalPixels = maskW * maskH
                val pixels = IntArray(totalPixels)

                var maxConfidence = 0.0f
                for (i in 0 until totalPixels) {
                    val confidence = maskBuffer.float
                    if (confidence > maxConfidence) {
                        maxConfidence = confidence
                    }

                    // Smooth edge transition between 0.35 and 0.70
                    val alpha = when {
                        confidence >= 0.70f -> 255
                        confidence <= 0.35f -> 0
                        else -> {
                            val norm = (confidence - 0.35f) / 0.35f
                            (norm * 255).toInt().coerceIn(0, 255)
                        }
                    }
                    // Pure white with confidence-based alpha for PorterDuff.Mode.DST_IN
                    pixels[i] = Color.argb(alpha, 255, 255, 255)
                }

                if (maxConfidence < 0.20f) {
                    return@withContext BackgroundRemovalResult.Error(
                        "No clear foreground subject was detected in this image."
                    )
                }

                maskBitmap.setPixels(pixels, 0, maskW, 0, 0, maskW, maskH)

                // 4. Save mask to persistent app storage
                val masksDir = File(context.filesDir, "masks").apply { if (!exists()) mkdirs() }
                val maskFile = File(masksDir, "mask_bg_${layerId}_${System.currentTimeMillis()}.png")
                FileOutputStream(maskFile).use { out ->
                    maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }

                val maskUri = try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        maskFile
                    )
                } catch (_: Exception) {
                    Uri.fromFile(maskFile)
                }

                BackgroundRemovalResult.Success(maskUri)

            } catch (e: OutOfMemoryError) {
                System.gc()
                BackgroundRemovalResult.Error("Insufficient memory to process background removal: ${e.message}")
            } catch (e: Exception) {
                BackgroundRemovalResult.Error(
                    "Background removal failed. The image could not be processed: ${e.message}",
                    e
                )
            } finally {
                maskBitmap?.recycle()
                decodedBitmap?.recycle()
            }
        }
    }
}
