package com.example.ui.generate

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.GraphitonApplication
import com.example.domain.model.ConnectionStatus
import com.example.domain.model.GenerationMode
import com.example.domain.model.GenerationParams
import com.example.domain.model.GenerationResult
import com.example.domain.model.GenerationState
import com.example.domain.model.SelectedImage
import com.example.domain.model.ServerConnectionState
import com.example.domain.repository.GraphitonRepository
import com.example.utils.ImageUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

data class GenerateUiParams(
    val prompt: String = "a small red apple on a wooden table",
    val negativePrompt: String = "blurry, low quality, distorted",
    val width: Int = 512,
    val height: Int = 512,
    val steps: Int = 20,
    val seed: Long = 42L,
    val samplerName: String = "euler",
    val cfgScale: Float = 7.0f,
    val strength: Float = 0.75f,
    val mode: GenerationMode = GenerationMode.TEXT_TO_IMAGE,
    val selectedImages: List<SelectedImage> = emptyList(),
    val sourceImageUri: Uri? = null,
    val sourceImageBase64: String? = null,
    val currentResult: GenerationResult? = null,
    val showWarningDialog: Boolean = false,
    val showParametersDrawer: Boolean = false
)

class GenerateViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GraphitonApplication
    private val repository: GraphitonRepository = app.repository
    private val preferencesManager = app.preferencesManager

    val connectionState: StateFlow<ServerConnectionState> = repository.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ServerConnectionState()
        )

    private val _uiParams = MutableStateFlow(GenerateUiParams())
    val uiParams: StateFlow<GenerateUiParams> = _uiParams.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private var timerJob: Job? = null

    init {
        // Load default parameters from preferences
        viewModelScope.launch {
            val prefs = preferencesManager.preferencesFlow.first()
            _uiParams.value = _uiParams.value.copy(
                width = prefs.defaultWidth,
                height = prefs.defaultHeight,
                steps = prefs.defaultSteps,
                seed = prefs.defaultSeed,
                samplerName = prefs.defaultSampler
            )
            // Test connection on app start with stored server URL
            repository.testConnection(prefs.serverUrl)
        }
    }

    fun updatePrompt(value: String) {
        _uiParams.value = _uiParams.value.copy(prompt = value)
    }

    fun updateNegativePrompt(value: String) {
        _uiParams.value = _uiParams.value.copy(negativePrompt = value)
    }

    fun updateWidth(value: Int) {
        _uiParams.value = _uiParams.value.copy(width = value)
    }

    fun updateHeight(value: Int) {
        _uiParams.value = _uiParams.value.copy(height = value)
    }

    fun updateSteps(value: Int) {
        _uiParams.value = _uiParams.value.copy(steps = value)
    }

    fun updateSeed(value: Long) {
        _uiParams.value = _uiParams.value.copy(seed = value)
    }

    fun randomizeSeed() {
        val newSeed = Random.nextLong(1, 999999999L)
        _uiParams.value = _uiParams.value.copy(seed = newSeed)
    }

    fun updateSampler(sampler: String) {
        _uiParams.value = _uiParams.value.copy(samplerName = sampler)
    }

    fun updateStrength(strength: Float) {
        _uiParams.value = _uiParams.value.copy(strength = strength)
    }

    fun setPreset(width: Int, height: Int) {
        _uiParams.value = _uiParams.value.copy(width = width, height = height)
    }

    fun setMode(mode: GenerationMode) {
        _uiParams.value = _uiParams.value.copy(mode = mode)
    }

    fun addSelectedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val currentList = _uiParams.value.selectedImages.toMutableList()
        val existingUris = currentList.map { it.uri }.toSet()

        // Filter out duplicate URIs
        val newUris = uris.filter { it !in existingUris }
        if (newUris.isEmpty()) {
            viewModelScope.launch {
                _toastEvents.emit("Selected image(s) already added")
            }
            return
        }

        val startOrder = currentList.size
        val newItems = newUris.mapIndexed { index, uri ->
            SelectedImage(
                id = java.util.UUID.randomUUID().toString(),
                uri = uri,
                order = startOrder + index,
                isPrimary = (currentList.isEmpty() && index == 0)
            )
        }
        currentList.addAll(newItems)

        // Ensure there is a primary image
        if (currentList.none { it.isPrimary } && currentList.isNotEmpty()) {
            currentList[0] = currentList[0].copy(isPrimary = true)
        }

        val primaryItem = currentList.firstOrNull { it.isPrimary } ?: currentList.firstOrNull()

        _uiParams.value = _uiParams.value.copy(
            selectedImages = currentList,
            sourceImageUri = primaryItem?.uri
        )

        // If exactly one image is present, decode base64 for backward-compatible single-image img2img
        if (currentList.size == 1) {
            prepareBase64ForUri(currentList.first().uri)
        } else {
            // For multiple images, avoid keeping full base64 in RAM
            _uiParams.value = _uiParams.value.copy(sourceImageBase64 = null)
        }
    }

    fun removeSelectedImage(id: String) {
        val currentList = _uiParams.value.selectedImages.toMutableList()
        val removedItem = currentList.firstOrNull { it.id == id } ?: return
        currentList.removeAll { it.id == id }

        // Re-index remaining images
        val reindexed = currentList.mapIndexed { index, item ->
            item.copy(order = index)
        }

        // If the removed image was primary, designate the first remaining as primary
        val finalItems = if (reindexed.isNotEmpty() && (removedItem.isPrimary || reindexed.none { it.isPrimary })) {
            reindexed.mapIndexed { index, item -> item.copy(isPrimary = (index == 0)) }
        } else {
            reindexed
        }

        val primaryItem = finalItems.firstOrNull { it.isPrimary }

        _uiParams.value = _uiParams.value.copy(
            selectedImages = finalItems,
            sourceImageUri = primaryItem?.uri,
            sourceImageBase64 = null
        )

        if (finalItems.size == 1) {
            prepareBase64ForUri(finalItems.first().uri)
        }
    }

    fun setPrimaryImage(id: String) {
        val currentList = _uiParams.value.selectedImages
        if (currentList.none { it.id == id }) return

        val updated = currentList.map { item ->
            item.copy(isPrimary = (item.id == id))
        }
        val primaryItem = updated.firstOrNull { it.isPrimary }

        _uiParams.value = _uiParams.value.copy(
            selectedImages = updated,
            sourceImageUri = primaryItem?.uri
        )

        if (updated.size == 1 && primaryItem != null) {
            prepareBase64ForUri(primaryItem.uri)
        }
    }

    fun clearAllSelectedImages() {
        _uiParams.value = _uiParams.value.copy(
            selectedImages = emptyList(),
            sourceImageUri = null,
            sourceImageBase64 = null
        )
    }

    fun setSourceImage(uri: Uri) {
        addSelectedImages(listOf(uri))
    }

    fun clearSourceImage() {
        clearAllSelectedImages()
    }

    private fun prepareBase64ForUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val base64 = ImageUtils.uriToBase64(app, uri, maxDimension = 1024)
                val current = _uiParams.value
                if (current.selectedImages.size == 1 && current.selectedImages.first().uri == uri) {
                    _uiParams.value = current.copy(
                        sourceImageUri = uri,
                        sourceImageBase64 = base64
                    )
                }
            } catch (e: Exception) {
                _toastEvents.emit("Failed to load source image: ${e.message}")
            }
        }
    }

    fun toggleParametersDrawer(show: Boolean? = null) {
        _uiParams.value = _uiParams.value.copy(
            showParametersDrawer = show ?: !_uiParams.value.showParametersDrawer
        )
    }

    fun dismissWarningDialog() {
        _uiParams.value = _uiParams.value.copy(showWarningDialog = false)
    }

    fun onGenerateClicked() {
        val currentParams = _uiParams.value

        // Check image-to-image requirements and multi-image guard
        if (currentParams.mode == GenerationMode.IMAGE_TO_IMAGE) {
            if (currentParams.selectedImages.isEmpty()) {
                viewModelScope.launch {
                    _toastEvents.emit("Please select a source image for Image Edit")
                }
                return
            }
            if (currentParams.selectedImages.size > 1) {
                viewModelScope.launch {
                    _toastEvents.emit("Multiple images selected. Composition mode is not implemented yet.")
                }
                return
            }
        }

        val isLargeResolution = (currentParams.width > 512 || currentParams.height > 768) || (currentParams.width * currentParams.height > 512 * 768)

        if (isLargeResolution && !_uiParams.value.showWarningDialog) {
            _uiParams.value = _uiParams.value.copy(showWarningDialog = true)
            return
        }

        _uiParams.value = _uiParams.value.copy(showWarningDialog = false)
        startGeneration()
    }

    fun proceedWithLargeResolution() {
        _uiParams.value = _uiParams.value.copy(showWarningDialog = false)
        startGeneration()
    }

    private fun startGeneration() {
        val currentParams = _uiParams.value

        if (currentParams.prompt.isBlank()) {
            viewModelScope.launch {
                _toastEvents.emit("Please enter a prompt")
            }
            return
        }

        if (currentParams.mode == GenerationMode.IMAGE_TO_IMAGE) {
            if (currentParams.selectedImages.isEmpty()) {
                viewModelScope.launch {
                    _toastEvents.emit("Please select a source image for Image-to-Image edit")
                }
                return
            }
            if (currentParams.selectedImages.size > 1) {
                viewModelScope.launch {
                    _toastEvents.emit("Multiple images selected. Composition mode is not implemented yet.")
                }
                return
            }
        }

        viewModelScope.launch {
            var activeBase64 = currentParams.sourceImageBase64
            if (currentParams.mode == GenerationMode.IMAGE_TO_IMAGE && activeBase64 == null) {
                val singleImageUri = currentParams.selectedImages.firstOrNull()?.uri
                if (singleImageUri != null) {
                    try {
                        activeBase64 = ImageUtils.uriToBase64(app, singleImageUri, maxDimension = 1024)
                        _uiParams.value = _uiParams.value.copy(
                            sourceImageUri = singleImageUri,
                            sourceImageBase64 = activeBase64
                        )
                    } catch (e: Exception) {
                        _toastEvents.emit("Failed to process source image: ${e.message}")
                        return@launch
                    }
                } else {
                    _toastEvents.emit("Please select a source image for Image-to-Image edit")
                    return@launch
                }
            }
            _generationState.value = GenerationState.Connecting
            delay(150)
            _generationState.value = GenerationState.SendingRequest

            // Start a timer tracker to show progress and warn if generation takes longer than expected
            var elapsedSeconds = 0
            timerJob?.cancel()
            timerJob = launch {
                while (true) {
                    delay(1000)
                    elapsedSeconds++
                    val isSlow = elapsedSeconds > 40
                    if (_generationState.value is GenerationState.Generating || _generationState.value is GenerationState.SendingRequest) {
                        _generationState.value = GenerationState.Generating(
                            elapsedSeconds = elapsedSeconds,
                            isSlow = isSlow
                        )
                    }
                }
            }

            val params = GenerationParams(
                prompt = currentParams.prompt.trim(),
                negativePrompt = currentParams.negativePrompt.trim(),
                width = currentParams.width,
                height = currentParams.height,
                steps = currentParams.steps,
                seed = currentParams.seed,
                samplerName = currentParams.samplerName,
                cfgScale = currentParams.cfgScale,
                strength = currentParams.strength,
                initImageBase64 = activeBase64 ?: currentParams.sourceImageBase64,
                mode = currentParams.mode
            )

            val result = if (currentParams.mode == GenerationMode.IMAGE_TO_IMAGE) {
                repository.generateImageToImage(params)
            } else {
                repository.generateTextToImage(params)
            }

            timerJob?.cancel()

            result.onSuccess { genResult ->
                _generationState.value = GenerationState.ReceivingImage
                delay(200)
                _generationState.value = GenerationState.Success(genResult)
                _uiParams.value = _uiParams.value.copy(currentResult = genResult)
                _toastEvents.emit("Generation complete (${genResult.durationMs / 1000}s)")
            }.onFailure { error ->
                _generationState.value = GenerationState.Error(
                    message = error.localizedMessage ?: "Generation failed"
                )
                _toastEvents.emit("Error: ${error.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun saveCurrentImage() {
        val result = _uiParams.value.currentResult ?: return
        viewModelScope.launch {
            val saveResult = repository.saveImageToGallery(result.imagePath, result.prompt.take(30))
            saveResult.onSuccess { msg ->
                _toastEvents.emit(msg)
            }.onFailure { err ->
                _toastEvents.emit("Failed to save: ${err.message}")
            }
        }
    }

    fun reuseGeneration(result: GenerationResult) {
        _uiParams.value = _uiParams.value.copy(
            prompt = result.prompt,
            negativePrompt = result.negativePrompt,
            width = result.width,
            height = result.height,
            steps = result.steps,
            seed = result.seed,
            samplerName = result.sampler,
            strength = result.strength ?: 0.75f,
            mode = result.mode,
            currentResult = result
        )
    }

    fun reconnect() {
        viewModelScope.launch {
            val prefs = preferencesManager.preferencesFlow.first()
            repository.testConnection(prefs.serverUrl)
        }
    }
}
