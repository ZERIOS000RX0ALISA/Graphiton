package com.example.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.GraphitonApplication
import com.example.domain.model.GenerationResult
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

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GraphitonApplication
    private val repository: GraphitonRepository = app.repository

    val historyList: StateFlow<List<GenerationResult>> = repository.historyItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedItem = MutableStateFlow<GenerationResult?>(null)
    val selectedItem: StateFlow<GenerationResult?> = _selectedItem.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    fun selectItem(item: GenerationResult?) {
        _selectedItem.value = item
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
            if (_selectedItem.value?.id == id) {
                _selectedItem.value = null
            }
            _toastEvents.emit("Generation deleted")
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _selectedItem.value = null
            _toastEvents.emit("History cleared")
        }
    }

    fun saveToGallery(item: GenerationResult) {
        viewModelScope.launch {
            val result = repository.saveImageToGallery(item.imagePath, item.prompt.take(30))
            result.onSuccess { msg ->
                _toastEvents.emit(msg)
            }.onFailure { err ->
                _toastEvents.emit("Save failed: ${err.message}")
            }
        }
    }
}
