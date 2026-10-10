package com.example.domain.repository

import com.example.domain.model.GenerationParams
import com.example.domain.model.GenerationResult
import com.example.domain.model.ModelInfo
import com.example.domain.model.SamplerInfo
import com.example.domain.model.ServerCapabilities
import com.example.domain.model.ServerConnectionState
import kotlinx.coroutines.flow.Flow

interface GraphitonRepository {
    val connectionState: Flow<ServerConnectionState>
    val historyItems: Flow<List<GenerationResult>>

    suspend fun testConnection(serverUrl: String): ServerConnectionState
    suspend fun getModels(): Result<List<ModelInfo>>
    suspend fun getSamplers(): Result<List<SamplerInfo>>
    suspend fun getCapabilities(): Result<ServerCapabilities>

    suspend fun generateTextToImage(params: GenerationParams): Result<GenerationResult>
    suspend fun generateImageToImage(params: GenerationParams): Result<GenerationResult>

    suspend fun deleteHistoryItem(id: Long): Result<Unit>
    suspend fun clearHistory(): Result<Unit>
    suspend fun saveImageToGallery(imagePath: String, title: String): Result<String>
}
