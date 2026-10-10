package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.GraphitonApplication
import com.example.data.local.AppPreferences
import com.example.domain.model.ServerConnectionState
import com.example.domain.repository.GraphitonRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrlInput: String = "http://10.197.250.50:1234",
    val defaultWidth: Int = 512,
    val defaultHeight: Int = 512,
    val defaultSteps: Int = 20,
    val defaultSampler: String = "euler",
    val defaultSeed: Long = 42L,
    val generationTimeoutInput: String = "0",
    val generationTimeoutError: String? = null,
    val themeMode: String = "SYSTEM",
    val warnLargeResolutions: Boolean = true,
    val isTestingConnection: Boolean = false,
    val showDebugInfo: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GraphitonApplication
    private val repository: GraphitonRepository = app.repository
    private val preferencesManager = app.preferencesManager

    val preferences: StateFlow<AppPreferences> = preferencesManager.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppPreferences()
        )

    val connectionState: StateFlow<ServerConnectionState> = repository.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ServerConnectionState()
        )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            preferencesManager.preferencesFlow.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    serverUrlInput = prefs.serverUrl,
                    defaultWidth = prefs.defaultWidth,
                    defaultHeight = prefs.defaultHeight,
                    defaultSteps = prefs.defaultSteps,
                    defaultSampler = prefs.defaultSampler,
                    defaultSeed = prefs.defaultSeed,
                    generationTimeoutInput = prefs.generationTimeoutSeconds.toString(),
                    themeMode = prefs.themeMode,
                    warnLargeResolutions = prefs.warnLargeResolutions
                )
            }
        }
    }

    fun updateServerUrlInput(value: String) {
        _uiState.value = _uiState.value.copy(serverUrlInput = value)
    }

    fun updateGenerationTimeoutInput(value: String) {
        // Enforce numeric only (integers >= 0). Reject negative signs, decimals, or letters.
        val cleaned = value.trim()
        if (cleaned.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                generationTimeoutInput = "",
                generationTimeoutError = "Timeout cannot be empty (enter 0 for unlimited)"
            )
            return
        }

        // Check if entire input consists of digits
        if (!cleaned.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(
                generationTimeoutError = "Please enter only positive whole numbers (seconds) or 0"
            )
            return
        }

        val seconds = cleaned.toIntOrNull()
        if (seconds == null || seconds < 0) {
            _uiState.value = _uiState.value.copy(
                generationTimeoutError = "Invalid number of seconds"
            )
            return
        }

        // Valid integer >= 0
        _uiState.value = _uiState.value.copy(
            generationTimeoutInput = cleaned,
            generationTimeoutError = null
        )

        viewModelScope.launch {
            preferencesManager.updateGenerationTimeout(seconds)
        }
    }

    fun testConnection() {
        val url = _uiState.value.serverUrlInput.trim()
        if (url.isBlank()) {
            viewModelScope.launch { _toastEvents.emit("Server URL cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingConnection = true)
            // Save configured URL
            preferencesManager.updateServerUrl(url)
            val resultState = repository.testConnection(url)
            _uiState.value = _uiState.value.copy(isTestingConnection = false)

            if (resultState.status == com.example.domain.model.ConnectionStatus.CONNECTED) {
                _toastEvents.emit("Connected! Model: ${resultState.detectedModel ?: "Unknown"}")
            } else {
                _toastEvents.emit("Connection test failed")
            }
        }
    }

    fun saveDefaults(
        width: Int,
        height: Int,
        steps: Int,
        sampler: String,
        seed: Long
    ) {
        viewModelScope.launch {
            preferencesManager.updateDefaults(width, height, steps, sampler, seed)
            _toastEvents.emit("Defaults saved")
        }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesManager.updateThemeMode(mode)
        }
    }

    fun toggleDebugInfo() {
        _uiState.value = _uiState.value.copy(showDebugInfo = !_uiState.value.showDebugInfo)
    }

    fun toggleWarnLargeResolutions(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.updateWarnLargeResolutions(enabled)
        }
    }
}
