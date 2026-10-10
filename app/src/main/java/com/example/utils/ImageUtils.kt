package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageUtils {

    /**
     * Decodes a Base64 string from stable-diffusion.cpp response and saves it
     * directly to internal app storage. Returns the local file path and Bitmap.
     */
    suspend fun saveBase64ImageToInternalStorage(
        context: Context,
        base64Data: String
    ): Pair<String, Bitmap?> = withContext(Dispatchers.IO) {
        val cleanBase64 = if (base64Data.contains(",")) {
            base64Data.substringAfter(",")
        } else {
            base64Data
        }

        val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        val imagesDir = File(context.filesDir, "generations").apply { if (!exists()) mkdirs() }
        val fileName = "gen_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.png"
        val targetFile = File(imagesDir, fileName)

        FileOutputStream(targetFile).use { out ->
            out.write(decodedBytes)
            out.flush()
        }

        // Decode bitmap with inSampleSize if too large to protect phone RAM
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)

        Pair(targetFile.absolutePath, bitmap)
    }

    /**
     * Reads a Uri from PhotoPicker / Document picker and converts to Base64
     * for POST /sdapi/v1/img2img with optional dimension capping to protect server & client memory.
     */
    suspend fun uriToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1024
    ): String = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(inputStream, null, boundsOptions)
        inputStream?.close()

        var sampleSize = 1
        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        val maxDim = maxOf(origWidth, origHeight)
        if (maxDim > maxDimension) {
            sampleSize = maxDim / maxDimension
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            ?: throw IllegalArgumentException("Could not decode selected image")
        inputStream?.close()

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        bitmap.recycle()

        Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Saves an image file into the public MediaStore Gallery in Pictures/Graphiton
     */
    suspend fun saveImageToGallery(
        context: Context,
        sourceFilePath: String,
        title: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourceFilePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Image file does not exist"))
            }

            val filename = "Graphiton_${System.currentTimeMillis()}.png"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Graphiton")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
                out.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Result.success("Saved to Pictures/Graphiton")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
