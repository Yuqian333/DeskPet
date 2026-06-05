package com.example.deskcat.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.deskcat.ai.AiAnimState
import com.example.deskcat.ai.DoubaoAnimGenerator
import com.example.deskcat.pack.PetPackLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PetSettingsViewModel(
    private val repository: PetPreferencesRepository,
) : ViewModel() {
    val uiState: StateFlow<PetSettingsUiState> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PetSettingsUiState(),
    )

    private val _analyzing = MutableStateFlow(false)
    val analyzing: StateFlow<Boolean> = _analyzing.asStateFlow()

    private val _generatingAnim = MutableStateFlow(false)
    val generatingAnim: StateFlow<Boolean> = _generatingAnim.asStateFlow()

    private val _aiAnimFrames = MutableStateFlow<List<Bitmap>?>(null)
    val aiAnimFrames: StateFlow<List<Bitmap>?> = _aiAnimFrames.asStateFlow()

    fun setImageUri(uri: String?) {
        viewModelScope.launch {
            repository.setImageUri(uri)
        }
    }

    fun analyzeAndSetImage(context: Context, uri: String?) {
        if (uri.isNullOrBlank()) {
            viewModelScope.launch { repository.setImageUri(null) }
            return
        }
        viewModelScope.launch {
            _analyzing.value = true
            try {
                val bitmap = PetImageResolver.decodeAndRemoveBackground(context, uri)
                val (style, label) = if (bitmap != null) {
                    PetStyleAnalyzer.analyze(bitmap)
                } else {
                    PetStyle.Default to null
                }
                repository.setImageUri(uri)
                repository.setPetStyle(style, label)
            } finally {
                _analyzing.value = false
            }
        }
    }

    fun setSizeScale(scale: Float) {
        viewModelScope.launch {
            repository.setSizeScale(scale)
        }
    }

    fun setSizePreset(preset: PetSizePreset) {
        viewModelScope.launch {
            repository.setSizePreset(preset)
        }
    }

    fun setAutoMoveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoMoveEnabled(enabled)
        }
    }

    fun importPetPackFromZip(context: Context, zipUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = PetPackLoader.importFromZip(context, zipUri)
            if (dir != null) {
                repository.setPetPackDir(dir)
                repository.setImageUri(null)
            }
        }
    }

    fun importPetPackFromFiles(context: Context, bodyUri: Uri, pawUpUri: Uri, pawDownUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = PetPackLoader.importFromFiles(context, bodyUri, pawUpUri, pawDownUri)
            if (dir != null) {
                repository.setPetPackDir(dir)
                repository.setImageUri(null)
            }
        }
    }

    fun clearPetPack(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            PetPackLoader.clearPack(context)
            repository.setPetPackDir(null)
        }
    }

    fun generateAiAnimation(context: Context) {
        val uri = uiState.value.imageUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _generatingAnim.value = true
            try {
                val bitmap = PetImageResolver.decodeAndRemoveBackground(context, uri)
                    ?: PetImageResolver.decodeBitmap(context, uri)
                if (bitmap != null) {
                    val frames = DoubaoAnimGenerator.generate(bitmap)
                    val result = if (frames.isNotEmpty()) frames else null
                    _aiAnimFrames.value = result
                    AiAnimState.update(result)
                }
            } finally {
                _generatingAnim.value = false
            }
        }
    }

    fun clearAiAnimation() {
        _aiAnimFrames.value = null
        AiAnimState.update(null)
    }

    class Factory(
        private val repository: PetPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PetSettingsViewModel::class.java)) {
                return PetSettingsViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
