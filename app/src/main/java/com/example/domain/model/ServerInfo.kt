package com.example.domain.model

data class ModelInfo(
    val title: String,
    val modelName: String,
    val hash: String? = null
)

data class SamplerInfo(
    val name: String,
    val aliases: List<String> = emptyList()
)

data class ServerCapabilities(
    val supportedModes: List<String> = listOf("img_gen"),
    val features: Map<String, Boolean> = emptyMap(),
    val isInitImageSupported: Boolean = true,
    val isHiresSupported: Boolean = false,
    val isLoraSupported: Boolean = false,
    val isCancelSupported: Boolean = false
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class ServerConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val serverUrl: String = "",
    val detectedModel: String? = null,
    val models: List<ModelInfo> = emptyList(),
    val samplers: List<SamplerInfo> = emptyList(),
    val capabilities: ServerCapabilities? = null,
    val errorMessage: String? = null,
    val lastCheckedTimestamp: Long = 0L
)
