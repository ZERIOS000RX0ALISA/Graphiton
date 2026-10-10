package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "graphiton_preferences")

data class AppPreferences(
    val serverUrl: String = "http://10.197.250.50:1234",
    val defaultWidth: Int = 512,
    val defaultHeight: Int = 512,
    val defaultSteps: Int = 20,
    val defaultSampler: String = "euler",
    val defaultSeed: Long = 42L,
    val generationTimeoutSeconds: Int = 0, // 0 = Unlimited
    val themeMode: String = "SYSTEM",
    val warnLargeResolutions: Boolean = true,
    val lastDetectedModel: String? = null
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val DEFAULT_WIDTH = intPreferencesKey("default_width")
        val DEFAULT_HEIGHT = intPreferencesKey("default_height")
        val DEFAULT_STEPS = intPreferencesKey("default_steps")
        val DEFAULT_SAMPLER = stringPreferencesKey("default_sampler")
        val DEFAULT_SEED = longPreferencesKey("default_seed")
        val GENERATION_TIMEOUT = intPreferencesKey("generation_timeout")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val WARN_LARGE_RESOLUTIONS = booleanPreferencesKey("warn_large_resolutions")
        val LAST_DETECTED_MODEL = stringPreferencesKey("last_detected_model")
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            serverUrl = prefs[Keys.SERVER_URL] ?: "http://10.197.250.50:1234",
            defaultWidth = prefs[Keys.DEFAULT_WIDTH] ?: 512,
            defaultHeight = prefs[Keys.DEFAULT_HEIGHT] ?: 512,
            defaultSteps = prefs[Keys.DEFAULT_STEPS] ?: 20,
            defaultSampler = prefs[Keys.DEFAULT_SAMPLER] ?: "euler",
            defaultSeed = prefs[Keys.DEFAULT_SEED] ?: 42L,
            generationTimeoutSeconds = prefs[Keys.GENERATION_TIMEOUT] ?: 0,
            themeMode = prefs[Keys.THEME_MODE] ?: "SYSTEM",
            warnLargeResolutions = prefs[Keys.WARN_LARGE_RESOLUTIONS] ?: true,
            lastDetectedModel = prefs[Keys.LAST_DETECTED_MODEL]
        )
    }

    suspend fun updateServerUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = url
        }
    }

    suspend fun updateDefaults(
        width: Int,
        height: Int,
        steps: Int,
        sampler: String,
        seed: Long
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_WIDTH] = width
            prefs[Keys.DEFAULT_HEIGHT] = height
            prefs[Keys.DEFAULT_STEPS] = steps
            prefs[Keys.DEFAULT_SAMPLER] = sampler
            prefs[Keys.DEFAULT_SEED] = seed
        }
    }

    suspend fun updateGenerationTimeout(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GENERATION_TIMEOUT] = maxOf(0, seconds)
        }
    }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }

    suspend fun updateWarnLargeResolutions(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WARN_LARGE_RESOLUTIONS] = enabled
        }
    }

    suspend fun updateLastDetectedModel(model: String?) {
        context.dataStore.edit { prefs ->
            if (model != null) {
                prefs[Keys.LAST_DETECTED_MODEL] = model
            } else {
                prefs.remove(Keys.LAST_DETECTED_MODEL)
            }
        }
    }
}
