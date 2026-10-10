package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "generation_history")
data class GenerationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val prompt: String,
    @ColumnInfo(name = "negative_prompt")
    val negativePrompt: String,
    val width: Int,
    val height: Int,
    val steps: Int,
    val seed: Long,
    val sampler: String,
    val strength: Float?,
    val mode: String, // "TEXT_TO_IMAGE" or "IMAGE_TO_IMAGE"
    @ColumnInfo(name = "image_path")
    val imagePath: String,
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L
)
