package com.example.utils

import android.content.Context
import android.net.Uri

sealed class BackgroundRemovalResult {
    data class Success(val maskUri: Uri) : BackgroundRemovalResult()
    data class Unavailable(val message: String) : BackgroundRemovalResult()
    data class Error(val message: String, val throwable: Throwable? = null) : BackgroundRemovalResult()
}

/**
 * Pluggable abstraction for on-device background removal / segmentation.
 * Implementations process an image and return an alpha mask Uri.
 */
interface BackgroundRemovalEngine {
    val name: String
    fun isAvailable(): Boolean
    suspend fun removeBackground(
        context: Context,
        imageUri: Uri,
        layerId: String
    ): BackgroundRemovalResult
}

/**
 * Default implementation using the local on-device ML Kit selfie/subject segmentation engine.
 */
class DefaultBackgroundRemovalEngine : BackgroundRemovalEngine {

    private val localEngine = LocalMlKitBackgroundRemovalEngine()

    override val name: String = localEngine.name

    override fun isAvailable(): Boolean = localEngine.isAvailable()

    override suspend fun removeBackground(
        context: Context,
        imageUri: Uri,
        layerId: String
    ): BackgroundRemovalResult {
        return localEngine.removeBackground(context, imageUri, layerId)
    }
}
