package com.example.data.api

import com.example.data.model.CapabilitiesDto
import com.example.data.model.GenerationResponseDto
import com.example.data.model.Img2ImgRequestDto
import com.example.data.model.ModelInfoDto
import com.example.data.model.SamplerInfoDto
import com.example.data.model.Txt2ImgRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface GraphitonApi {

    @GET("/sdapi/v1/sd-models")
    suspend fun getModels(): Response<List<ModelInfoDto>>

    @GET("/sdapi/v1/samplers")
    suspend fun getSamplers(): Response<List<SamplerInfoDto>>

    @GET("/sdcpp/v1/capabilities")
    suspend fun getCapabilities(): Response<CapabilitiesDto>

    @POST("/sdapi/v1/txt2img")
    suspend fun textToImage(@Body request: Txt2ImgRequestDto): Response<GenerationResponseDto>

    @POST("/sdapi/v1/img2img")
    suspend fun imageToImage(@Body request: Img2ImgRequestDto): Response<GenerationResponseDto>
}
