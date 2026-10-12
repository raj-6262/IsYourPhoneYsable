package com.example.phonecheck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    fun start() {
        if (_state.value is UiState.Scanning) return
        _state.value = UiState.Scanning("Starting…", 0f)
        viewModelScope.launch {
            val result = Scanner.run(getApplication()) { stage, progress ->
                _state.value = UiState.Scanning(stage, progress)
            }
            _state.value = UiState.Done(result)
        }
    }

    fun reset() {
        _state.value = UiState.Idle
    }
}
