package com.example.deskcat.settings

enum class PetSizePreset {
    Small,
    Medium,
    Large,
}

data class PetSettingsUiState(
    val imageUri: String? = null,
    val sizeScale: Float = 1f,
    val sizePreset: PetSizePreset = PetSizePreset.Medium,
    val autoMoveEnabled: Boolean = true,
) {
    val useCustomImage: Boolean
        get() = !imageUri.isNullOrBlank()
}
