package com.example.deskcat

import androidx.compose.ui.geometry.Offset

enum class PetMood {
    Sleepy,
    Chill,
    Happy,
    Excited,
    Hungry,
}

data class DesktopPetUiState(
    val position: Offset = Offset.Zero,
    val bounds: Offset = Offset.Zero,
    val mood: PetMood = PetMood.Chill,
    val speech: String = "你好，我是你的桌宠小猫。",
    val hunger: Int = 35,
    val happiness: Int = 70,
    val energy: Int = 80,
    val coins: Int = 1000,
    val petCount: Int = 0,
    val initialized: Boolean = false,
)
