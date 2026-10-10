package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ModelInfoDto(
    @Json(name = "title") val title: String? = null,
    @Json(name = "model_name") val modelName: String? = null,
    @Json(name = "hash") val hash: String? = null,
    @Json(name = "filename") val filename: String? = null
)

@JsonClass(generateAdapter = true)
data class SamplerInfoDto(
    @Json(name = "name") val name: String,
    @Json(name = "aliases") val aliases: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class CapabilitiesDto(
    @Json(name = "supported_modes") val supportedModes: List<String>? = null,
    @Json(name = "features") val features: Map<String, Boolean>? = null,
    @Json(name = "default_width") val defaultWidth: Int? = null,
    @Json(name = "default_height") val defaultHeight: Int? = null,
    @Json(name = "default_steps") val defaultSteps: Int? = null,
    @Json(name = "max_width") val maxWidth: Int? = null,
    @Json(name = "max_height") val maxHeight: Int? = null
)

@JsonClass(generateAdapter = true)
data class Txt2ImgRequestDto(
    @Json(name = "prompt") val prompt: String,
    @Json(name = "negative_prompt") val negativePrompt: String? = null,
    @Json(name = "width") val width: Int = 512,
    @Json(name = "height") val height: Int = 512,
    @Json(name = "steps") val steps: Int = 20,
    @Json(name = "seed") val seed: Long = 42L,
    @Json(name = "sampler_name") val samplerName: String = "euler",
    @Json(name = "cfg_scale") val cfgScale: Float? = null
)

@JsonClass(generateAdapter = true)
data class Img2ImgRequestDto(
    @Json(name = "init_images") val initImages: List<String>,
    @Json(name = "prompt") val prompt: String,
    @Json(name = "negative_prompt") val negativePrompt: String? = null,
    @Json(name = "denoising_strength") val denoisingStrength: Float = 0.75f,
    @Json(name = "width") val width: Int = 512,
    @Json(name = "height") val height: Int = 512,
    @Json(name = "steps") val steps: Int = 20,
    @Json(name = "seed") val seed: Long = 42L,
    @Json(name = "sampler_name") val samplerName: String = "euler"
)

@JsonClass(generateAdapter = true)
data class GenerationResponseDto(
    @Json(name = "images") val images: List<String>? = null,
    @Json(name = "parameters") val parameters: Map<String, Any>? = null,
    @Json(name = "info") val info: String? = null
)
