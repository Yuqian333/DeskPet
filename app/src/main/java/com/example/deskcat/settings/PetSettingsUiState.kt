package com.example.deskcat.settings

enum class PetSizePreset {
    Small,
    Medium,
    Large,
}

enum class PetStyle {
    Cat,    // 慵懒：缓慢摇摆，低频弹跳
    Dog,    // 活泼：快速弹跳，大幅摇摆
    Rabbit, // 温柔：轻柔浮动，小幅旋转
    Default // 默认：与原始行为一致
}

data class PetAnimParams(
    val bounceMagnitude: Float,
    val swingMagnitude: Float,
    val speedMultiplier: Float,
    val tiltRange: Float,
)

fun PetStyle.toAnimParams(): PetAnimParams = when (this) {
    PetStyle.Cat -> PetAnimParams(bounceMagnitude = 0.7f, swingMagnitude = 0.6f, speedMultiplier = 0.8f, tiltRange = 1.5f)
    PetStyle.Dog -> PetAnimParams(bounceMagnitude = 1.5f, swingMagnitude = 1.6f, speedMultiplier = 1.4f, tiltRange = 3.5f)
    PetStyle.Rabbit -> PetAnimParams(bounceMagnitude = 0.9f, swingMagnitude = 0.5f, speedMultiplier = 1.0f, tiltRange = 0.8f)
    PetStyle.Default -> PetAnimParams(bounceMagnitude = 1.0f, swingMagnitude = 1.0f, speedMultiplier = 1.0f, tiltRange = 2.0f)
}

data class PetSettingsUiState(
    val imageUri: String? = null,
    val sizeScale: Float = 1f,
    val sizePreset: PetSizePreset = PetSizePreset.Medium,
    val autoMoveEnabled: Boolean = true,
    val petStyle: PetStyle = PetStyle.Default,
    val detectedLabel: String? = null,
    val petPackDir: String? = null,
) {
    val useCustomImage: Boolean
        get() = !imageUri.isNullOrBlank()
    val usePetPack: Boolean
        get() = !petPackDir.isNullOrBlank()
}
