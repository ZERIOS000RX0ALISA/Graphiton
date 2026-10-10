package com.example.domain.model

import android.graphics.Bitmap
import android.net.Uri
import java.util.UUID

data class SelectedImage(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val order: Int = 0,
    val isPrimary: Boolean = false
)

enum class GenerationMode {
    TEXT_TO_IMAGE,
    IMAGE_TO_IMAGE
}

data class GenerationParams(
    val prompt: String,
    val negativePrompt: String = "",
    val width: Int = 512,
    val height: Int = 512,
    val steps: Int = 20,
    val seed: Long = 42L,
    val samplerName: String = "euler",
    val cfgScale: Float = 7.0f,
    val strength: Float = 0.75f,
    val initImageBase64: String? = null,
    val mode: GenerationMode = GenerationMode.TEXT_TO_IMAGE
)

data class GenerationResult(
    val id: Long = 0L,
    val prompt: String,
    val negativePrompt: String,
    val width: Int,
    val height: Int,
    val steps: Int,
    val seed: Long,
    val sampler: String,
    val strength: Float?,
    val mode: GenerationMode,
    val imagePath: String,
    val timestamp: Long,
    val durationMs: Long,
    val bitmap: Bitmap? = null
)

sealed interface GenerationState {
    data object Idle : GenerationState
    data object Connecting : GenerationState
    data object SendingRequest : GenerationState
    data class Generating(val elapsedSeconds: Int = 0, val isSlow: Boolean = false) : GenerationState
    data object ReceivingImage : GenerationState
    data class Success(val result: GenerationResult) : GenerationState
    data class Error(val message: String, val canRetry: Boolean = true) : GenerationState
}
