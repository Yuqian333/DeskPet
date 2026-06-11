package com.example.deskcat.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.deskcat.ai.AiAnimState
import com.example.deskcat.ai.AiAnimStore
import com.example.deskcat.ai.DoubaoAnimGenerator
import com.example.deskcat.pack.PetPackLoader
import com.example.deskcat.pet.PetStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private val _aiAnimMessage = MutableStateFlow<String?>(null)
    val aiAnimMessage: StateFlow<String?> = _aiAnimMessage.asStateFlow()

    private val _storedAiAnimations = MutableStateFlow<List<AiAnimStore.StoredAnimation>>(emptyList())
    val storedAiAnimations: StateFlow<List<AiAnimStore.StoredAnimation>> = _storedAiAnimations.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.settingsFlow.collect { settings ->
                PetStateRepository.setPetName(settings.petName)
            }
        }
    }

    fun setImageUri(uri: String?) {
        viewModelScope.launch {
            repository.setImageUri(uri)
        }
    }

    fun clearCustomImage(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            clearAiAnimationInternal(context)
            repository.setImageUri(null)
        }
    }

    fun setPetName(name: String) {
        viewModelScope.launch {
            repository.setPetName(name)
        }
    }

    fun analyzeAndSetImage(context: Context, uri: String?) {
        if (uri.isNullOrBlank()) {
            clearCustomImage(context)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _analyzing.value = true
            try {
                clearAiAnimationInternal(context)
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

    fun setAiPrompt(prompt: String) {
        viewModelScope.launch {
            repository.setAiPrompt(prompt)
        }
    }

    fun importPetPackFromZip(context: Context, zipUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = PetPackLoader.importFromZip(context, zipUri)
            if (dir != null) {
                clearAiAnimationInternal(context)
                repository.setPetPackDir(dir)
                repository.setImageUri(null)
            }
        }
    }

    fun importPetPackFromFiles(context: Context, bodyUri: Uri, pawUpUri: Uri, pawDownUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = PetPackLoader.importFromFiles(context, bodyUri, pawUpUri, pawDownUri)
            if (dir != null) {
                clearAiAnimationInternal(context)
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
        val uri = uiState.value.imageUri
        if (uri.isNullOrBlank()) {
            _aiAnimMessage.value = "请先上传一张宠物图片，再生成 AI 动画"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _generatingAnim.value = true
            _aiAnimFrames.value = null
            AiAnimState.update(null)
            repository.setAiAnimDir(null)
            _aiAnimMessage.value = "正在准备图片"
            try {
                val bitmap = PetImageResolver.decodeBitmap(context, uri)
                if (bitmap != null) {
                    _aiAnimMessage.value = "正在调用豆包生成透明动画帧"
                    when (
                        val result = DoubaoAnimGenerator.generate(
                            context = context,
                            sourceBitmap = bitmap,
                            petName = uiState.value.petName,
                            customPrompt = uiState.value.aiPrompt,
                        )
                    ) {
                        is DoubaoAnimGenerator.GenerateResult.Success -> {
                            val savedDir = AiAnimStore.saveCurrent(context, result.frames)
                            repository.setAiAnimDir(savedDir)
                            refreshStoredAiAnimations(context)
                            _aiAnimFrames.value = result.frames
                            _aiAnimMessage.value = result.warningMessage
                                ?: if (savedDir == null) {
                                    "已生成 ${result.frames.size} 帧动画，但本地保存失败"
                                } else {
                                    "已生成 ${result.frames.size} 帧透明动画"
                                }
                            AiAnimState.update(result.frames)
                        }
                        is DoubaoAnimGenerator.GenerateResult.Failure -> {
                            _aiAnimFrames.value = null
                            _aiAnimMessage.value = result.message
                            AiAnimState.update(null)
                        }
                    }
                } else {
                    _aiAnimMessage.value = "图片读取失败，请重新选择图片"
                }
            } finally {
                _generatingAnim.value = false
            }
        }
    }

    fun restoreAiAnimation(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            refreshStoredAiAnimations(context)
            val dir = repository.settingsFlow.first().aiAnimDir
            val frames = AiAnimStore.load(dir)
            if (frames.isEmpty()) {
                _aiAnimFrames.value = null
                AiAnimState.update(null)
                if (!dir.isNullOrBlank()) repository.setAiAnimDir(null)
            } else {
                _aiAnimFrames.value = frames
                _aiAnimMessage.value = "已恢复 ${frames.size} 帧 AI 动画"
                AiAnimState.update(frames)
            }
        }
    }

    fun clearAiAnimation(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            clearAiAnimationInternal(context)
        }
    }

    fun refreshStoredAiAnimations(context: Context) {
        _storedAiAnimations.value = AiAnimStore.list(context)
    }

    fun selectAiAnimation(context: Context, dirPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val frames = AiAnimStore.load(dirPath)
            if (frames.isEmpty()) {
                _aiAnimMessage.value = "动画帧读取失败，请重新生成"
                refreshStoredAiAnimations(context)
                return@launch
            }
            repository.setAiAnimDir(dirPath)
            _aiAnimFrames.value = frames
            _aiAnimMessage.value = "已切换 ${frames.size} 帧 AI 动画"
            AiAnimState.update(frames)
            refreshStoredAiAnimations(context)
        }
    }

    fun renameAiAnimation(context: Context, dirPath: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (AiAnimStore.rename(context, dirPath, title)) {
                _aiAnimMessage.value = "已重命名动画"
            } else {
                _aiAnimMessage.value = "重命名失败，请换一个名称"
            }
            refreshStoredAiAnimations(context)
        }
    }

    fun deleteAiAnimation(context: Context, dirPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentDir = repository.settingsFlow.first().aiAnimDir
            if (!AiAnimStore.delete(context, dirPath)) {
                _aiAnimMessage.value = "删除失败，旧版本动画不可删除"
                refreshStoredAiAnimations(context)
                return@launch
            }
            if (currentDir == dirPath) {
                repository.setAiAnimDir(null)
                _aiAnimFrames.value = null
                AiAnimState.update(null)
                _aiAnimMessage.value = "已删除当前动画，已回到默认显示"
            } else {
                _aiAnimMessage.value = "已删除动画"
            }
            refreshStoredAiAnimations(context)
        }
    }

    private suspend fun clearAiAnimationInternal(context: Context) {
        repository.setAiAnimDir(null)
        _aiAnimFrames.value = null
        _aiAnimMessage.value = null
        AiAnimState.update(null)
        refreshStoredAiAnimations(context)
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
