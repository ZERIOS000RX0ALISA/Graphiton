package com.example.domain.model

import android.net.Uri
import java.util.UUID

enum class CanvasBackground {
    TRANSPARENT,
    WHITE
}

enum class ExportFormat(val extension: String, val mimeType: String) {
    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg")
}

data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val isFull: Boolean
        get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f

    val widthRatio: Float get() = (right - left).coerceIn(0.01f, 1f)
    val heightRatio: Float get() = (bottom - top).coerceIn(0.01f, 1f)

    companion object {
        val FULL = NormalizedCropRect(0f, 0f, 1f, 1f)
    }
}

data class CompositionLayer(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val x: Float = 512f,
    val y: Float = 512f,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f,
    val crop: NormalizedCropRect = NormalizedCropRect.FULL,
    val maskUri: Uri? = null,
    val isMaskInverted: Boolean = false,
    val isBlurEnabled: Boolean = false,
    val blurRadius: Float = 20f,
    val blurMaskUri: Uri? = null,
    val isBlurMaskInverted: Boolean = false,
    val zIndex: Int = 0,
    val isVisible: Boolean = true,
    val intrinsicWidth: Int = 512,
    val intrinsicHeight: Int = 512
) {
    val hasMask: Boolean get() = maskUri != null
    val hasBlurMask: Boolean get() = blurMaskUri != null
}

enum class MaskEditorTarget {
    VISIBILITY,
    BLUR
}

data class CompositionState(
    val canvasWidth: Int = 1024,
    val canvasHeight: Int = 1024,
    val background: CanvasBackground = CanvasBackground.TRANSPARENT,
    val layers: List<CompositionLayer> = emptyList(),
    val selectedLayerId: String? = null,
    val isCropping: Boolean = false,
    val cropDraft: NormalizedCropRect? = null,
    val isMasking: Boolean = false,
    val maskEditorTarget: MaskEditorTarget = MaskEditorTarget.VISIBILITY,
    val isProcessingBackgroundRemoval: Boolean = false,
    val isExporting: Boolean = false,
    val isPreparingImg2img: Boolean = false,
    val lastExportedUri: Uri? = null,
    val lastExportedFilePath: String? = null
) {
    val selectedLayer: CompositionLayer?
        get() = layers.find { it.id == selectedLayerId }
}
