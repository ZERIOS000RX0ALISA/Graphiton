package com.example

import com.example.data.model.CapabilitiesDto
import com.example.data.model.GenerationResponseDto
import com.example.data.model.ModelInfoDto
import com.example.data.model.SamplerInfoDto
import com.example.data.model.Txt2ImgRequestDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GraphitonApiTest {

    private lateinit var moshi: Moshi

    @Before
    fun setUp() {
        moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Test
    fun `test sd-models response parsing`() {
        val json = """
            [
                {
                    "title": "meinahentai_v5Final",
                    "model_name": "meinahentai_v5Final",
                    "hash": "abcdef123456"
                }
            ]
        """.trimIndent()

        val type = Types.newParameterizedType(List::class.java, ModelInfoDto::class.java)
        val adapter = moshi.adapter<List<ModelInfoDto>>(type)
        val models = adapter.fromJson(json)

        assertNotNull(models)
        assertEquals(1, models!!.size)
        assertEquals("meinahentai_v5Final", models[0].modelName)
        assertEquals("abcdef123456", models[0].hash)
    }

    @Test
    fun `test samplers response parsing`() {
        val json = """
            [
                {"name": "euler", "aliases": ["Euler"]},
                {"name": "euler_a", "aliases": ["Euler a"]}
            ]
        """.trimIndent()

        val type = Types.newParameterizedType(List::class.java, SamplerInfoDto::class.java)
        val adapter = moshi.adapter<List<SamplerInfoDto>>(type)
        val samplers = adapter.fromJson(json)

        assertNotNull(samplers)
        assertEquals(2, samplers!!.size)
        assertEquals("euler", samplers[0].name)
    }

    @Test
    fun `test capabilities response parsing`() {
        val json = """
            {
                "supported_modes": ["img_gen"],
                "features": {
                    "cache": true,
                    "cancel_generating": false,
                    "cancel_queued": true,
                    "control_image": true,
                    "hires": true,
                    "init_image": true,
                    "lora": true,
                    "mask_image": true,
                    "ref_images": true,
                    "vae_tiling": true
                }
            }
        """.trimIndent()

        val adapter = moshi.adapter(CapabilitiesDto::class.java)
        val caps = adapter.fromJson(json)

        assertNotNull(caps)
        assertEquals(listOf("img_gen"), caps!!.supportedModes)
        assertTrue(caps.features?.get("init_image") == true)
        assertEquals(false, caps.features?.get("cancel_generating"))
    }

    @Test
    fun `test txt2img request serialization`() {
        val request = Txt2ImgRequestDto(
            prompt = "a small red apple on a wooden table",
            negativePrompt = "blurry",
            width = 512,
            height = 512,
            steps = 20,
            seed = 42L,
            samplerName = "euler"
        )

        val adapter = moshi.adapter(Txt2ImgRequestDto::class.java)
        val json = adapter.toJson(request)

        assertTrue(json.contains("\"prompt\":\"a small red apple on a wooden table\""))
        assertTrue(json.contains("\"width\":512"))
        assertTrue(json.contains("\"sampler_name\":\"euler\""))
    }

    @Test
    fun `test generation response parsing`() {
        val json = """
            {
                "images": ["iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="],
                "parameters": {"steps": 20},
                "info": "{\"seed\": 42}"
            }
        """.trimIndent()

        val adapter = moshi.adapter(GenerationResponseDto::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertNotNull(response!!.images)
        assertEquals(1, response.images!!.size)
    }

    @Test
    fun `test default generation timeout preference is zero for unlimited`() {
        val prefs = com.example.data.local.AppPreferences()
        assertEquals(0, prefs.generationTimeoutSeconds)
    }

    @Test
    fun `test NetworkClient generation api initialization`() {
        val apiUnlimited = com.example.data.api.NetworkClient.getGenerationApi("http://10.197.250.50:1234", 0)
        assertNotNull(apiUnlimited)

        val apiCustom = com.example.data.api.NetworkClient.getGenerationApi("http://10.197.250.50:1234", 600)
        assertNotNull(apiCustom)

        val standardApi = com.example.data.api.NetworkClient.getApi("http://10.197.250.50:1234")
        assertNotNull(standardApi)
    }
}
