package com.example.data.api

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    private const val TAG = "GraphitonNetwork"
    private const val DEFAULT_BASE_URL = "http://10.197.250.50:1234/"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * Sanitized logging interceptor that logs request metadata and timing
     * without logging full Base64 image payloads to protect memory and user privacy.
     */
    private val sanitizedLoggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "--> [${request.method}] ${request.url}")

        try {
            val response: Response = chain.proceed(request)
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "<-- [${response.code}] ${request.url} in ${duration}ms")
            response
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.w(TAG, "<-- FAILED [${request.method}] ${request.url} after ${duration}ms: ${e.message}")
            throw e
        }
    }

    /**
     * Base/Standard client for fast metadata operations (models, samplers, capabilities, connection tests).
     * Maintains reasonable finite timeouts so connection errors and server unreachability are detected promptly.
     */
    private val standardOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(sanitizedLoggingInterceptor)
            .build()
    }

    private val standardApiCache = mutableMapOf<String, GraphitonApi>()
    private val generationApiCache = mutableMapOf<String, GraphitonApi>()

    /**
     * Returns API instance with standard finite timeouts for quick operations.
     */
    fun getApi(baseUrl: String): GraphitonApi {
        val sanitizedUrl = normalizeUrl(baseUrl)
        synchronized(standardApiCache) {
            return standardApiCache.getOrPut(sanitizedUrl) {
                Retrofit.Builder()
                    .baseUrl(sanitizedUrl)
                    .client(standardOkHttpClient)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()
                    .create(GraphitonApi::class.java)
            }
        }
    }

    /**
     * Returns an API instance configured specifically for image generation (txt2img & img2img).
     *
     * @param timeoutSeconds Configured generation timeout.
     * When 0 or negative, UNLIMITED timeout is configured:
     * readTimeout(0) and callTimeout(0) explicitly disable finite timeouts in OkHttp,
     * allowing CPU generations that take 10+ or 20+ minutes to complete successfully.
     */
    fun getGenerationApi(baseUrl: String, timeoutSeconds: Int): GraphitonApi {
        val sanitizedUrl = normalizeUrl(baseUrl)
        val cacheKey = "$sanitizedUrl|timeout=$timeoutSeconds"

        synchronized(generationApiCache) {
            return generationApiCache.getOrPut(cacheKey) {
                val clientBuilder = standardOkHttpClient.newBuilder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS) // Generous write timeout for large init_images
                    .retryOnConnectionFailure(false) // Avoid re-triggering expensive generations on transient connection resets

                if (timeoutSeconds <= 0) {
                    // Explicitly unlimited in OkHttp (0 = no timeout)
                    clientBuilder.readTimeout(0, TimeUnit.MILLISECONDS)
                    clientBuilder.callTimeout(0, TimeUnit.MILLISECONDS)
                } else {
                    clientBuilder.readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                    clientBuilder.callTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                }

                Retrofit.Builder()
                    .baseUrl(sanitizedUrl)
                    .client(clientBuilder.build())
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()
                    .create(GraphitonApi::class.java)
            }
        }
    }

    fun normalizeUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) {
            url = "$url/"
        }
        return url
    }
}
