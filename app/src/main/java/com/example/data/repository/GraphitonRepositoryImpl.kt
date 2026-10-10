package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.api.NetworkClient
import com.example.data.local.AppDatabase
import com.example.data.local.GenerationHistoryEntity
import com.example.data.local.PreferencesManager
import com.example.data.model.Img2ImgRequestDto
import com.example.data.model.Txt2ImgRequestDto
import com.example.domain.model.ConnectionStatus
import com.example.domain.model.GenerationMode
import com.example.domain.model.GenerationParams
import com.example.domain.model.GenerationResult
import com.example.domain.model.ModelInfo
import com.example.domain.model.SamplerInfo
import com.example.domain.model.ServerCapabilities
import com.example.domain.model.ServerConnectionState
import com.example.domain.repository.GraphitonRepository
import com.example.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class GraphitonRepositoryImpl(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val database: AppDatabase
) : GraphitonRepository {

    private val _connectionState = MutableStateFlow(ServerConnectionState())
    override val connectionState: Flow<ServerConnectionState> = _connectionState.asStateFlow()

    override val historyItems: Flow<List<GenerationResult>> =
        database.generationDao().getAllHistory().map { list ->
            list.map { entity ->
                GenerationResult(
                    id = entity.id,
                    prompt = entity.prompt,
                    negativePrompt = entity.negativePrompt,
                    width = entity.width,
                    height = entity.height,
                    steps = entity.steps,
                    seed = entity.seed,
                    sampler = entity.sampler,
                    strength = entity.strength,
                    mode = if (entity.mode == "IMAGE_TO_IMAGE") GenerationMode.IMAGE_TO_IMAGE else GenerationMode.TEXT_TO_IMAGE,
                    imagePath = entity.imagePath,
                    timestamp = entity.timestamp,
                    durationMs = entity.durationMs
                )
            }
        }

    override suspend fun testConnection(serverUrl: String): ServerConnectionState = withContext(Dispatchers.IO) {
        val normalizedUrl = NetworkClient.normalizeUrl(serverUrl)
        _connectionState.value = _connectionState.value.copy(
            status = ConnectionStatus.CONNECTING,
            serverUrl = normalizedUrl,
            errorMessage = null
        )

        try {
            val api = NetworkClient.getApi(normalizedUrl)

            // Step 1: Fetch Models
            val modelsResponse = api.getModels()
            if (!modelsResponse.isSuccessful) {
                val errorMsg = "HTTP ${modelsResponse.code()}: ${modelsResponse.message()}"
                val state = ServerConnectionState(
                    status = ConnectionStatus.ERROR,
                    serverUrl = normalizedUrl,
                    errorMessage = "Server responded with error: $errorMsg",
                    lastCheckedTimestamp = System.currentTimeMillis()
                )
                _connectionState.value = state
                return@withContext state
            }

            val modelDtos = modelsResponse.body() ?: emptyList()
            val domainModels = modelDtos.map {
                ModelInfo(
                    title = it.title ?: it.modelName ?: "Unknown model",
                    modelName = it.modelName ?: it.title ?: "Unknown",
                    hash = it.hash
                )
            }
            val primaryModel = domainModels.firstOrNull()?.modelName ?: domainModels.firstOrNull()?.title

            // Step 2: Fetch Samplers
            val samplers = try {
                val samplersResp = api.getSamplers()
                if (samplersResp.isSuccessful && samplersResp.body() != null) {
                    samplersResp.body()!!.map { SamplerInfo(name = it.name, aliases = it.aliases ?: emptyList()) }
                } else emptyList()
            } catch (e: Exception) {
                Log.w("GraphitonRepo", "Could not fetch samplers: ${e.message}")
                emptyList()
            }

            // Step 3: Fetch Capabilities
            val capabilities = try {
                val capResp = api.getCapabilities()
                if (capResp.isSuccessful && capResp.body() != null) {
                    val capDto = capResp.body()!!
                    val features = capDto.features ?: emptyMap()
                    ServerCapabilities(
                        supportedModes = capDto.supportedModes ?: listOf("img_gen"),
                        features = features,
                        isInitImageSupported = features["init_image"] ?: true,
                        isHiresSupported = features["hires"] ?: false,
                        isLoraSupported = features["lora"] ?: false,
                        isCancelSupported = features["cancel_generating"] ?: false
                    )
                } else {
                    ServerCapabilities()
                }
            } catch (e: Exception) {
                Log.w("GraphitonRepo", "Could not fetch capabilities: ${e.message}")
                ServerCapabilities()
            }

            // Save detected model in preferences
            preferencesManager.updateLastDetectedModel(primaryModel)

            val successState = ServerConnectionState(
                status = ConnectionStatus.CONNECTED,
                serverUrl = normalizedUrl,
                detectedModel = primaryModel,
                models = domainModels,
                samplers = samplers,
                capabilities = capabilities,
                errorMessage = null,
                lastCheckedTimestamp = System.currentTimeMillis()
            )
            _connectionState.value = successState
            successState
        } catch (e: Exception) {
            val friendlyError = when (e) {
                is ConnectException, is UnknownHostException -> {
                    "Unable to connect to Graphiton server.\nCheck that:\n• The PC is powered on\n• stable-diffusion.cpp server is running\n• Phone is on the same local Wi-Fi\n• Server URL is correct"
                }
                is SocketTimeoutException -> {
                    "Connection timed out. The server may be busy or unreachable."
                }
                else -> {
                    "Connection failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
                }
            }

            val errorState = ServerConnectionState(
                status = ConnectionStatus.ERROR,
                serverUrl = normalizedUrl,
                errorMessage = friendlyError,
                lastCheckedTimestamp = System.currentTimeMillis()
            )
            _connectionState.value = errorState
            errorState
        }
    }

    override suspend fun getModels(): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val currentUrl = preferencesManager.preferencesFlow.first().serverUrl
            val api = NetworkClient.getApi(currentUrl)
            val response = api.getModels()
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!.map {
                    ModelInfo(
                        title = it.title ?: it.modelName ?: "Model",
                        modelName = it.modelName ?: it.title ?: "Model",
                        hash = it.hash
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSamplers(): Result<List<SamplerInfo>> = withContext(Dispatchers.IO) {
        try {
            val currentUrl = preferencesManager.preferencesFlow.first().serverUrl
            val api = NetworkClient.getApi(currentUrl)
            val response = api.getSamplers()
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!.map {
                    SamplerInfo(name = it.name, aliases = it.aliases ?: emptyList())
                }
                Result.success(list)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCapabilities(): Result<ServerCapabilities> = withContext(Dispatchers.IO) {
        try {
            val currentUrl = preferencesManager.preferencesFlow.first().serverUrl
            val api = NetworkClient.getApi(currentUrl)
            val response = api.getCapabilities()
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                val feats = dto.features ?: emptyMap()
                val caps = ServerCapabilities(
                    supportedModes = dto.supportedModes ?: listOf("img_gen"),
                    features = feats,
                    isInitImageSupported = feats["init_image"] ?: true,
                    isHiresSupported = feats["hires"] ?: false,
                    isLoraSupported = feats["lora"] ?: false,
                    isCancelSupported = feats["cancel_generating"] ?: false
                )
                Result.success(caps)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateTextToImage(params: GenerationParams): Result<GenerationResult> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var configuredTimeoutSeconds = 0
            try {
                val prefs = preferencesManager.preferencesFlow.first()
                val currentUrl = prefs.serverUrl
                configuredTimeoutSeconds = prefs.generationTimeoutSeconds
                val api = NetworkClient.getGenerationApi(currentUrl, configuredTimeoutSeconds)

                val requestDto = Txt2ImgRequestDto(
                    prompt = params.prompt,
                    negativePrompt = params.negativePrompt.ifBlank { null },
                    width = params.width,
                    height = params.height,
                    steps = params.steps,
                    seed = params.seed,
                    samplerName = params.samplerName,
                    cfgScale = if (params.cfgScale > 0) params.cfgScale else null
                )

                val response = api.textToImage(requestDto)
                val duration = System.currentTimeMillis() - startTime

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Server error HTTP ${response.code()}: ${response.message()}")
                    )
                }

                val body = response.body()
                val rawImages = body?.images
                if (rawImages.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("The server response did not contain any generated image."))
                }

                val (filePath, bitmap) = ImageUtils.saveBase64ImageToInternalStorage(context, rawImages.first())

                // Insert into Room database
                val entity = GenerationHistoryEntity(
                    prompt = params.prompt,
                    negativePrompt = params.negativePrompt,
                    width = params.width,
                    height = params.height,
                    steps = params.steps,
                    seed = params.seed,
                    sampler = params.samplerName,
                    strength = null,
                    mode = "TEXT_TO_IMAGE",
                    imagePath = filePath,
                    timestamp = System.currentTimeMillis(),
                    durationMs = duration
                )
                val newId = database.generationDao().insert(entity)

                Result.success(
                    GenerationResult(
                        id = newId,
                        prompt = params.prompt,
                        negativePrompt = params.negativePrompt,
                        width = params.width,
                        height = params.height,
                        steps = params.steps,
                        seed = params.seed,
                        sampler = params.samplerName,
                        strength = null,
                        mode = GenerationMode.TEXT_TO_IMAGE,
                        imagePath = filePath,
                        timestamp = entity.timestamp,
                        durationMs = duration,
                        bitmap = bitmap
                    )
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val readableMessage = when (e) {
                    is SocketTimeoutException -> {
                        if (configuredTimeoutSeconds > 0) {
                            "Generation request timed out (${configuredTimeoutSeconds}s). The PC may still be processing the image."
                        } else {
                            "Connection timed out while contacting Graphiton server. Check that the PC is reachable."
                        }
                    }
                    is ConnectException, is UnknownHostException -> "Cannot reach Graphiton server. Please verify local Wi-Fi and server status."
                    else -> e.localizedMessage ?: "Unknown generation error"
                }
                Result.failure(Exception(readableMessage, e))
            }
        }

    override suspend fun generateImageToImage(params: GenerationParams): Result<GenerationResult> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var configuredTimeoutSeconds = 0
            try {
                val prefs = preferencesManager.preferencesFlow.first()
                val currentUrl = prefs.serverUrl
                configuredTimeoutSeconds = prefs.generationTimeoutSeconds
                val api = NetworkClient.getGenerationApi(currentUrl, configuredTimeoutSeconds)

                val initImage = params.initImageBase64
                    ?: return@withContext Result.failure(Exception("No source image provided for image-to-image editing."))

                val requestDto = Img2ImgRequestDto(
                    initImages = listOf(initImage),
                    prompt = params.prompt,
                    negativePrompt = params.negativePrompt.ifBlank { null },
                    denoisingStrength = params.strength,
                    width = params.width,
                    height = params.height,
                    steps = params.steps,
                    seed = params.seed,
                    samplerName = params.samplerName
                )

                val response = api.imageToImage(requestDto)
                val duration = System.currentTimeMillis() - startTime

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Server error HTTP ${response.code()}: ${response.message()}")
                    )
                }

                val body = response.body()
                val rawImages = body?.images
                if (rawImages.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("Server returned an empty image list."))
                }

                val (filePath, bitmap) = ImageUtils.saveBase64ImageToInternalStorage(context, rawImages.first())

                val entity = GenerationHistoryEntity(
                    prompt = params.prompt,
                    negativePrompt = params.negativePrompt,
                    width = params.width,
                    height = params.height,
                    steps = params.steps,
                    seed = params.seed,
                    sampler = params.samplerName,
                    strength = params.strength,
                    mode = "IMAGE_TO_IMAGE",
                    imagePath = filePath,
                    timestamp = System.currentTimeMillis(),
                    durationMs = duration
                )
                val newId = database.generationDao().insert(entity)

                Result.success(
                    GenerationResult(
                        id = newId,
                        prompt = params.prompt,
                        negativePrompt = params.negativePrompt,
                        width = params.width,
                        height = params.height,
                        steps = params.steps,
                        seed = params.seed,
                        sampler = params.samplerName,
                        strength = params.strength,
                        mode = GenerationMode.IMAGE_TO_IMAGE,
                        imagePath = filePath,
                        timestamp = entity.timestamp,
                        durationMs = duration,
                        bitmap = bitmap
                    )
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val readable = when (e) {
                    is SocketTimeoutException -> {
                        if (configuredTimeoutSeconds > 0) {
                            "Generation request timed out (${configuredTimeoutSeconds}s). The PC may still be processing the image."
                        } else {
                            "Connection timed out while contacting Graphiton server. Check that the PC is reachable."
                        }
                    }
                    is ConnectException, is UnknownHostException -> "Cannot connect to server. Check local network."
                    else -> e.localizedMessage ?: "Unknown img2img error"
                }
                Result.failure(Exception(readable, e))
            }
        }

    override suspend fun deleteHistoryItem(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val item = database.generationDao().getById(id)
            if (item != null) {
                val file = File(item.imagePath)
                if (file.exists()) file.delete()
                database.generationDao().deleteById(id)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearHistory(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.generationDao().clearAll()
            val dir = File(context.filesDir, "generations")
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveImageToGallery(imagePath: String, title: String): Result<String> {
        return ImageUtils.saveImageToGallery(context, imagePath, title)
    }
}
