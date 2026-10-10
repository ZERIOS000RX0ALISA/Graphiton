package com.example.ui.composition

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CanvasBackground
import com.example.domain.model.CompositionLayer
import com.example.domain.model.CompositionState
import com.example.domain.model.ExportFormat
import com.example.domain.model.MaskEditorTarget
import com.example.domain.model.NormalizedCropRect
import com.example.utils.BackgroundRemovalEngine
import com.example.utils.BackgroundRemovalResult
import com.example.utils.CompositionExporter
import com.example.utils.DefaultBackgroundRemovalEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Fast, mutable transformation state for a single layer.
 * Changes to these properties are read inside graphicsLayer {} blocks,
 * allowing GPU-accelerated updates at 60/120Hz without recomposing the parent UI.
 */
class LayerTransformState(
    initialX: Float,
    initialY: Float,
    initialScale: Float,
    initialRotation: Float,
    initialOpacity: Float = 1.0f
) {
    var x by mutableFloatStateOf(initialX)
    var y by mutableFloatStateOf(initialY)
    var scale by mutableFloatStateOf(initialScale)
    var rotation by mutableFloatStateOf(initialRotation)
    var opacity by mutableFloatStateOf(initialOpacity)

    fun update(deltaX: Float, deltaY: Float, scaleMult: Float = 1f, rotDelta: Float = 0f) {
        x += deltaX
        y += deltaY
        scale = (scale * scaleMult).coerceIn(0.05f, 15f)
        val newRot = (rotation + rotDelta) % 360f
        rotation = if (newRot < 0) newRot + 360f else newRot
    }

    fun set(newX: Float, newY: Float, newScale: Float, newRotation: Float, newOpacity: Float = opacity) {
        x = newX
        y = newY
        scale = newScale
        rotation = newRotation
        opacity = newOpacity
    }
}

class CompositionViewModel @JvmOverloads constructor(
    application: Application,
    private val backgroundRemovalEngine: BackgroundRemovalEngine = DefaultBackgroundRemovalEngine(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(CompositionState())
    val state: StateFlow<CompositionState> = _state.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    // Map of layerId -> LayerTransformState for instantaneous 60fps gesture response
    private val _layerTransforms = ConcurrentHashMap<String, LayerTransformState>()

    fun getTransform(layerId: String): LayerTransformState {
        return _layerTransforms.getOrPut(layerId) {
            val existing = _state.value.layers.firstOrNull { it.id == layerId }
            LayerTransformState(
                initialX = existing?.x ?: (_state.value.canvasWidth / 2f),
                initialY = existing?.y ?: (_state.value.canvasHeight / 2f),
                initialScale = existing?.scale ?: 1f,
                initialRotation = existing?.rotation ?: 0f,
                initialOpacity = existing?.opacity ?: 1.0f
            )
        }
    }

    /**
     * Adds one or multiple images as new composition layers.
     * Decodes only bounds for aspect ratio and places each layer at canvas center.
     */
    fun addLayers(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            val newLayers = withContext(ioDispatcher) {
                val context = getApplication<Application>()
                var nextZ = (_state.value.layers.maxOfOrNull { it.zIndex } ?: -1) + 1

                uris.map { uri ->
                    var w = 512
                    var h = 512
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeStream(inputStream, null, options)
                            if (options.outWidth > 0 && options.outHeight > 0) {
                                w = options.outWidth
                                h = options.outHeight
                            }
                        }
                    } catch (_: Exception) {
                        // Keep default 512x512 if stream cannot be opened
                    }

                    // Fit initial size to around ~512 max dimension on 1024x1024 canvas
                    val maxDim = max(w, h).toFloat()
                    val initialScale = if (maxDim > 0) (512f / maxDim).coerceAtMost(1.0f) else 1.0f

                    CompositionLayer(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        x = _state.value.canvasWidth / 2f,
                        y = _state.value.canvasHeight / 2f,
                        scale = initialScale,
                        rotation = 0f,
                        opacity = 1.0f,
                        crop = NormalizedCropRect.FULL,
                        zIndex = nextZ++,
                        isVisible = true,
                        intrinsicWidth = w,
                        intrinsicHeight = h
                    )
                }
            }

            if (newLayers.isNotEmpty()) {
                _state.update { current ->
                    val updatedLayers = current.layers + newLayers
                    current.copy(
                        layers = updatedLayers,
                        selectedLayerId = newLayers.last().id,
                        isCropping = false,
                        cropDraft = null
                    )
                }
            }
        }
    }

    /**
     * Selects a layer by its ID.
     */
    fun selectLayer(layerId: String?) {
        _state.update {
            it.copy(
                selectedLayerId = layerId,
                isCropping = if (layerId == null || layerId != it.selectedLayerId) false else it.isCropping,
                cropDraft = if (layerId == null || layerId != it.selectedLayerId) null else it.cropDraft
            )
        }
    }

    /**
     * Called on high-frequency pointer move events during active gestures.
     * Updates the LayerTransformState directly without allocating new objects
     * or emitting to _state, providing seamless 60/120Hz gesture response.
     */
    fun updateSelectedTransformRealtime(
        deltaX: Float,
        deltaY: Float,
        scaleMultiplier: Float = 1.0f,
        rotationDelta: Float = 0.0f
    ) {
        val targetId = _state.value.selectedLayerId ?: return
        val transform = getTransform(targetId)
        transform.update(deltaX, deltaY, scaleMultiplier, rotationDelta)
    }

    /**
     * Called when a gesture finishes (finger lifted).
     * Synchronizes the active layer's final transformation back to _state.
     */
    fun commitSelectedTransform() {
        val targetId = _state.value.selectedLayerId ?: return
        val transform = _layerTransforms[targetId] ?: return
        _state.update { current ->
            val updated = current.layers.map { layer ->
                if (layer.id == targetId) {
                    layer.copy(
                        x = transform.x,
                        y = transform.y,
                        scale = transform.scale,
                        rotation = transform.rotation,
                        opacity = transform.opacity
                    )
                } else layer
            }
            current.copy(layers = updated)
        }
    }

    /**
     * Synchronizes all layer transforms back to _state.
     */
    private fun commitAllTransforms() {
        _state.update { current ->
            val updated = current.layers.map { layer ->
                val transform = _layerTransforms[layer.id]
                if (transform != null) {
                    layer.copy(
                        x = transform.x,
                        y = transform.y,
                        scale = transform.scale,
                        rotation = transform.rotation,
                        opacity = transform.opacity
                    )
                } else layer
            }
            current.copy(layers = updated)
        }
    }

    /**
     * Updates transformation for the selected layer and immediately commits to state.
     */
    fun updateSelectedTransform(
        deltaX: Float,
        deltaY: Float,
        scaleMultiplier: Float = 1.0f,
        rotationDelta: Float = 0.0f
    ) {
        updateSelectedTransformRealtime(deltaX, deltaY, scaleMultiplier, rotationDelta)
        commitSelectedTransform()
    }

    // ==================== STAGE 2: OPACITY CONTROLS ====================

    /**
     * Updates opacity for the selected layer in real-time during slider drag.
     */
    fun updateSelectedOpacityRealtime(opacity: Float) {
        val targetId = _state.value.selectedLayerId ?: return
        val clamped = opacity.coerceIn(0f, 1f)
        getTransform(targetId).opacity = clamped
    }

    /**
     * Sets opacity for the selected layer and commits to state.
     */
    fun setSelectedOpacity(opacity: Float) {
        val targetId = _state.value.selectedLayerId ?: return
        val clamped = opacity.coerceIn(0f, 1f)
        getTransform(targetId).opacity = clamped
        _state.update { current ->
            current.copy(
                layers = current.layers.map {
                    if (it.id == targetId) it.copy(opacity = clamped) else it
                }
            )
        }
    }

    /**
     * Resets opacity for the selected layer to 100% (1.0f).
     */
    fun resetSelectedOpacity() {
        setSelectedOpacity(1.0f)
    }

    // ==================== STAGE 2: ROTATION CONTROLS ====================

    /**
     * Real-time rotation update during slider drag.
     */
    fun updateSelectedRotationRealtime(degrees: Float) {
        val targetId = _state.value.selectedLayerId ?: return
        var norm = degrees % 360f
        if (norm < 0) norm += 360f
        getTransform(targetId).rotation = norm
    }

    /**
     * Sets rotation to a specific angle in degrees and commits to state.
     */
    fun setSelectedRotation(degrees: Float) {
        val targetId = _state.value.selectedLayerId ?: return
        var norm = degrees % 360f
        if (norm < 0) norm += 360f
        getTransform(targetId).rotation = norm
        _state.update { current ->
            current.copy(
                layers = current.layers.map {
                    if (it.id == targetId) it.copy(rotation = norm) else it
                }
            )
        }
    }

    /**
     * Quick rotation by delta (e.g. -90 degrees or +90 degrees).
     */
    fun rotateSelectedBy(deltaDegrees: Float) {
        val targetId = _state.value.selectedLayerId ?: return
        val transform = getTransform(targetId)
        var newRot = (transform.rotation + deltaDegrees) % 360f
        if (newRot < 0) newRot += 360f
        transform.rotation = newRot
        _state.update { current ->
            current.copy(
                layers = current.layers.map {
                    if (it.id == targetId) it.copy(rotation = newRot) else it
                }
            )
        }
    }

    /**
     * Resets rotation for the selected layer to 0 degrees.
     */
    fun resetSelectedRotation() {
        setSelectedRotation(0f)
    }

    // ==================== STAGE 2: ALIGNMENT CONTROLS ====================

    enum class AlignmentType {
        LEFT,
        CENTER_HORIZONTAL,
        RIGHT,
        TOP,
        CENTER_VERTICAL,
        BOTTOM
    }

    /**
     * Aligns the selected layer relative to the 1024x1024 Composition canvas,
     * accounting for crop dimensions, scale, and rotation.
     */
    fun alignSelected(alignment: AlignmentType) {
        val selected = _state.value.selectedLayer ?: return
        val transform = getTransform(selected.id)
        val canvasW = _state.value.canvasWidth.toFloat()
        val canvasH = _state.value.canvasHeight.toFloat()

        // Visible dimensions taking crop and scale into account
        val visibleW = (selected.crop.right - selected.crop.left) * selected.intrinsicWidth * transform.scale
        val visibleH = (selected.crop.bottom - selected.crop.top) * selected.intrinsicHeight * transform.scale

        // Bounding box half-extents accounting for rotation angle
        val rad = Math.toRadians(transform.rotation.toDouble())
        val cosVal = kotlin.math.abs(kotlin.math.cos(rad)).toFloat()
        val sinVal = kotlin.math.abs(kotlin.math.sin(rad)).toFloat()
        val halfExtentX = (visibleW * cosVal + visibleH * sinVal) / 2f
        val halfExtentY = (visibleW * sinVal + visibleH * cosVal) / 2f

        when (alignment) {
            AlignmentType.LEFT -> transform.x = halfExtentX
            AlignmentType.CENTER_HORIZONTAL -> transform.x = canvasW / 2f
            AlignmentType.RIGHT -> transform.x = canvasW - halfExtentX
            AlignmentType.TOP -> transform.y = halfExtentY
            AlignmentType.CENTER_VERTICAL -> transform.y = canvasH / 2f
            AlignmentType.BOTTOM -> transform.y = canvasH - halfExtentY
        }
        commitSelectedTransform()
    }

    // ==================== STAGE 2: CROP CONTROLS ====================

    /**
     * Enters Crop mode for the selected layer.
     */
    fun startCropping() {
        val selected = _state.value.selectedLayer ?: return
        _state.update {
            it.copy(
                isCropping = true,
                cropDraft = selected.crop
            )
        }
    }

    /**
     * Updates draft crop rectangle during user dragging/resizing.
     */
    fun updateCropDraft(draft: NormalizedCropRect) {
        _state.update { it.copy(cropDraft = draft) }
    }

    /**
     * Confirms crop operation and applies the draft crop to the selected layer.
     */
    fun applyCrop() {
        val draft = _state.value.cropDraft ?: return
        val targetId = _state.value.selectedLayerId ?: return
        _state.update { current ->
            current.copy(
                isCropping = false,
                cropDraft = null,
                layers = current.layers.map {
                    if (it.id == targetId) it.copy(crop = draft) else it
                }
            )
        }
    }

    /**
     * Cancels Crop mode and discards changes to the draft crop.
     */
    fun cancelCrop() {
        _state.update {
            it.copy(
                isCropping = false,
                cropDraft = null
            )
        }
    }

    /**
     * Resets crop for the selected layer to the full original image (0, 0, 1, 1).
     */
    fun resetSelectedCrop() {
        val targetId = _state.value.selectedLayerId ?: return
        _state.update { current ->
            current.copy(
                cropDraft = if (current.isCropping) NormalizedCropRect.FULL else current.cropDraft,
                layers = current.layers.map {
                    if (it.id == targetId) it.copy(crop = NormalizedCropRect.FULL) else it
                }
            )
        }
    }

    fun setBackground(background: CanvasBackground) {
        _state.update { it.copy(background = background) }
    }

    /**
     * Moves the selected layer one step forward in z-order.
     */
    fun moveForward() {
        val selectedId = _state.value.selectedLayerId ?: return
        _state.update { current ->
            val sorted = current.layers.sortedBy { it.zIndex }.toMutableList()
            val index = sorted.indexOfFirst { it.id == selectedId }
            if (index != -1 && index < sorted.size - 1) {
                // Swap zIndex with next layer
                val currentLayer = sorted[index]
                val nextLayer = sorted[index + 1]
                val oldZ = currentLayer.zIndex
                sorted[index] = currentLayer.copy(zIndex = nextLayer.zIndex)
                sorted[index + 1] = nextLayer.copy(zIndex = oldZ)
            }
            current.copy(layers = sorted)
        }
    }

    /**
     * Moves the selected layer one step backward in z-order.
     */
    fun moveBackward() {
        val selectedId = _state.value.selectedLayerId ?: return
        _state.update { current ->
            val sorted = current.layers.sortedBy { it.zIndex }.toMutableList()
            val index = sorted.indexOfFirst { it.id == selectedId }
            if (index > 0) {
                val currentLayer = sorted[index]
                val prevLayer = sorted[index - 1]
                val oldZ = currentLayer.zIndex
                sorted[index] = currentLayer.copy(zIndex = prevLayer.zIndex)
                sorted[index - 1] = prevLayer.copy(zIndex = oldZ)
            }
            current.copy(layers = sorted)
        }
    }

    /**
     * Brings the selected layer to the very front.
     */
    fun bringToFront() {
        val selectedId = _state.value.selectedLayerId ?: return
        _state.update { current ->
            val maxZ = current.layers.maxOfOrNull { it.zIndex } ?: 0
            val updated = current.layers.map { layer ->
                if (layer.id == selectedId) layer.copy(zIndex = maxZ + 1) else layer
            }
            current.copy(layers = updated)
        }
    }

    /**
     * Sends the selected layer to the very back.
     */
    fun sendToBack() {
        val selectedId = _state.value.selectedLayerId ?: return
        _state.update { current ->
            val minZ = current.layers.minOfOrNull { it.zIndex } ?: 0
            val updated = current.layers.map { layer ->
                if (layer.id == selectedId) layer.copy(zIndex = minZ - 1) else layer
            }
            current.copy(layers = updated)
        }
    }

    /**
     * Deletes a layer by ID.
     */
    fun deleteLayer(layerId: String) {
        _layerTransforms.remove(layerId)
        _state.update { current ->
            val remaining = current.layers.filter { it.id != layerId }
            val newSelectedId = if (current.selectedLayerId == layerId) {
                remaining.maxByOrNull { it.zIndex }?.id
            } else {
                current.selectedLayerId
            }
            current.copy(
                layers = remaining,
                selectedLayerId = newSelectedId,
                isCropping = if (current.selectedLayerId == layerId) false else current.isCropping,
                cropDraft = if (current.selectedLayerId == layerId) null else current.cropDraft
            )
        }
    }

    fun deleteSelectedLayer() {
        val selectedId = _state.value.selectedLayerId ?: return
        deleteLayer(selectedId)
    }

    fun clearAll() {
        _layerTransforms.clear()
        _state.update {
            it.copy(
                layers = emptyList(),
                selectedLayerId = null,
                isCropping = false,
                cropDraft = null,
                lastExportedUri = null,
                lastExportedFilePath = null
            )
        }
    }

    /**
     * Exports the composition to a final bitmap file and MediaStore.
     */
    fun exportComposition(
        format: ExportFormat = ExportFormat.PNG,
        onExportComplete: (File) -> Unit = {}
    ) {
        commitAllTransforms()
        if (_state.value.layers.isEmpty()) {
            viewModelScope.launch {
                _toastEvents.emit("Cannot export an empty composition.")
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isExporting = true) }
            val context = getApplication<Application>()
            val result = CompositionExporter.exportComposition(context, _state.value, format)

            _state.update { it.copy(isExporting = false) }

            result.fold(
                onSuccess = { (file, mediaStoreUri) ->
                    _state.update {
                        it.copy(
                            lastExportedFilePath = file.absolutePath,
                            lastExportedUri = mediaStoreUri
                        )
                    }
                    _toastEvents.emit("Saved to Pictures/Graphiton (${file.name})")
                    onExportComplete(file)
                },
                onFailure = { error ->
                    _toastEvents.emit("Export failed: ${error.message}")
                }
            )
        }
    }

    // ==========================================
    // STAGE 3: MASKS, BLUR & ADVANCED FEATURES
    // ==========================================

    fun openMaskEditor(
        layerId: String? = _state.value.selectedLayerId,
        target: MaskEditorTarget = MaskEditorTarget.VISIBILITY
    ) {
        if (layerId == null) return
        commitAllTransforms()
        _state.update {
            it.copy(
                selectedLayerId = layerId,
                isMasking = true,
                maskEditorTarget = target
            )
        }
    }

    fun openBlurMaskEditor(layerId: String? = _state.value.selectedLayerId) {
        openMaskEditor(layerId, MaskEditorTarget.BLUR)
    }

    fun closeMaskEditor() {
        _state.update { it.copy(isMasking = false) }
    }

    fun setLayerBlurEnabled(layerId: String, enabled: Boolean) {
        _state.update { current ->
            val updated = current.layers.map { layer ->
                if (layer.id == layerId) layer.copy(isBlurEnabled = enabled) else layer
            }
            current.copy(layers = updated)
        }
    }

    fun setLayerBlurRadius(layerId: String, radius: Float) {
        _state.update { current ->
            val updated = current.layers.map { layer ->
                if (layer.id == layerId) layer.copy(blurRadius = radius.coerceIn(1f, 50f)) else layer
            }
            current.copy(layers = updated)
        }
    }

    fun applyMaskToSelectedLayer(maskUri: Uri, isInverted: Boolean = false) {
        val selectedId = _state.value.selectedLayerId ?: return
        val target = _state.value.maskEditorTarget
        _state.update { current ->
            val updated = current.layers.map { layer ->
                if (layer.id == selectedId) {
                    if (target == MaskEditorTarget.BLUR) {
                        layer.copy(
                            blurMaskUri = maskUri,
                            isBlurMaskInverted = isInverted,
                            isBlurEnabled = true
                        )
                    } else {
                        layer.copy(maskUri = maskUri, isMaskInverted = isInverted)
                    }
                } else layer
            }
            current.copy(layers = updated, isMasking = false)
        }
        viewModelScope.launch {
            if (target == MaskEditorTarget.BLUR) {
                _toastEvents.emit("Blur region applied to layer")
            } else {
                _toastEvents.emit("Mask applied to layer")
            }
        }
    }

    fun clearSelectedLayerMask() {
        val selectedId = _state.value.selectedLayerId ?: return
        _state.update { current ->
            val updated = current.layers.map { layer ->
                if (layer.id == selectedId) {
                    layer.copy(maskUri = null, isMaskInverted = false)
                } else layer
            }
            current.copy(layers = updated)
        }
        viewModelScope.launch {
            _toastEvents.emit("Mask cleared")
        }
    }

    fun invertSelectedLayerMask() {
        val selectedId = _state.value.selectedLayerId ?: return
        _state.update { current ->
            val updated = current.layers.map { layer ->
                if (layer.id == selectedId && layer.hasMask) {
                    layer.copy(isMaskInverted = !layer.isMaskInverted)
                } else layer
            }
            current.copy(layers = updated)
        }
    }

    fun clearSelectedLayerBlurMask() {
        val selectedId = _state.value.selectedLayerId ?: return
        _state.update { current ->
            val updated = current.layers.map { layer ->
                if (layer.id == selectedId) {
                    layer.copy(blurMaskUri = null, isBlurMaskInverted = false)
                } else layer
            }
            current.copy(layers = updated)
        }
        viewModelScope.launch {
            _toastEvents.emit("Blur region cleared")
        }
    }

    fun invertSelectedLayerBlurMask() {
        val selectedId = _state.value.selectedLayerId ?: return
        _state.update { current ->
            val updated = current.layers.map { layer ->
                if (layer.id == selectedId && layer.hasBlurMask) {
                    layer.copy(isBlurMaskInverted = !layer.isBlurMaskInverted)
                } else layer
            }
            current.copy(layers = updated)
        }
    }

    fun resetSelectedLayerBlur() {
        val selectedId = _state.value.selectedLayerId ?: return
        _state.update { current ->
            val updated = current.layers.map { layer ->
                if (layer.id == selectedId) {
                    layer.copy(
                        isBlurEnabled = false,
                        blurRadius = 20f,
                        blurMaskUri = null,
                        isBlurMaskInverted = false
                    )
                } else layer
            }
            current.copy(layers = updated)
        }
        viewModelScope.launch {
            _toastEvents.emit("Blur reset")
        }
    }

    /**
     * Attempts on-device background removal for the currently selected layer.
     * Uses pluggable BackgroundRemovalEngine. If no local segmentation engine is installed,
     * honestly informs the user without pretending or faking.
     */
    fun removeBackgroundForSelectedLayer() {
        val selected = _state.value.selectedLayer ?: return
        viewModelScope.launch {
            _state.update { it.copy(isProcessingBackgroundRemoval = true) }
            val context = getApplication<Application>()
            val result = withContext(ioDispatcher) {
                backgroundRemovalEngine.removeBackground(context, selected.uri, selected.id)
            }
            _state.update { it.copy(isProcessingBackgroundRemoval = false) }

            when (result) {
                is BackgroundRemovalResult.Success -> {
                    // Non-destructively attach mask to layer
                    applyMaskToSelectedLayer(result.maskUri, false)
                    _toastEvents.emit("Background removed successfully")
                }
                is BackgroundRemovalResult.Unavailable -> {
                    _toastEvents.emit(result.message)
                }
                is BackgroundRemovalResult.Error -> {
                    _toastEvents.emit("Background removal failed: ${result.message}")
                }
            }
        }
    }

    /**
     * Renders the complete, flattened composition into a single 1024x1024 PNG image
     * off the main thread and passes the resulting Uri to the caller for img2img handoff.
     */
    fun prepareCompositionForImg2img(onReady: (Uri) -> Unit) {
        commitAllTransforms()
        if (_state.value.layers.isEmpty()) {
            viewModelScope.launch {
                _toastEvents.emit("Cannot send an empty composition to img2img.")
            }
            return
        }

        viewModelScope.launch(ioDispatcher) {
            _state.update { it.copy(isPreparingImg2img = true) }
            val context = getApplication<Application>()
            val result = CompositionExporter.prepareCompositionForImg2img(context, _state.value, ioDispatcher)

            _state.update { it.copy(isPreparingImg2img = false) }

            result.fold(
                onSuccess = { (_, uri) ->
                    withContext(Dispatchers.Main) {
                        onReady(uri)
                    }
                },
                onFailure = { error ->
                    _toastEvents.emit("Failed to prepare composition: ${error.message}")
                }
            )
        }
    }
}
