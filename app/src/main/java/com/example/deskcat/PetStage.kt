package com.example.deskcat

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deskcat.settings.PetImageResolver
import com.example.deskcat.settings.PetSettingsUiState
import kotlin.math.roundToInt

@Composable
fun PetStage(
    uiState: DesktopPetUiState,
    settingsState: PetSettingsUiState,
    aiAnimFrames: List<Bitmap>? = null,
    stageHeight: Dp,
    floatOffset: Float,
    bobAmplitude: Float,
    onDragPet: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val petScale = when (uiState.mood) {
        PetMood.Sleepy -> 0.96f
        PetMood.Chill -> 1f
        PetMood.Happy -> 1.03f
        PetMood.Excited -> 1.07f
        PetMood.Hungry -> 0.99f
    }
    val petRotation = when (uiState.mood) {
        PetMood.Sleepy -> -2f
        PetMood.Chill -> 0f
        PetMood.Happy -> 2f
        PetMood.Excited -> 4f
        PetMood.Hungry -> -1f
    }

    Card(
        modifier = modifier.height(stageHeight),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x55FFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            StageBackgroundGlow()

            Text(
                text = "拖动小猫到处跑",
                modifier = Modifier.align(Alignment.TopStart),
                color = Color(0x99000000),
                style = MaterialTheme.typography.labelMedium,
            )

            if (uiState.initialized) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                uiState.position.x.roundToInt(),
                                uiState.position.y.roundToInt(),
                            )
                        }
                        .requiredSize(192.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                onDragPet(dragAmount.x, dragAmount.y)
                            }
                        },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedContent(
                            targetState = uiState.speech,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(180)) togetherWith
                                    fadeOut(animationSpec = tween(120))
                            },
                            label = "speechBubble",
                        ) { speech ->
                            if (speech.isNotBlank()) {
                                StageSpeechBubble(text = speech)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .size(176.dp)
                                .offset(y = ((floatOffset - 0.5f) * bobAmplitude).dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            StagePetAvatar(
                                mood = uiState.mood,
                                settingsState = settingsState,
                                aiAnimFrames = aiAnimFrames,
                                scale = petScale,
                                rotation = petRotation,
                                phase = floatOffset,
                                modifier = Modifier.requiredSize(150.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.StageBackgroundGlow() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = Color(0x20FFFFFF),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.16f, size.height * 0.16f),
        )
        drawCircle(
            color = Color(0x22FFFFFF),
            radius = size.minDimension * 0.26f,
            center = Offset(size.width * 0.82f, size.height * 0.18f),
        )
        drawCircle(
            color = Color(0x14FFFFFF),
            radius = size.minDimension * 0.2f,
            center = Offset(size.width * 0.82f, size.height * 0.8f),
        )
    }
}

@Composable
fun StagePetAvatar(
    mood: PetMood,
    settingsState: PetSettingsUiState,
    aiAnimFrames: List<Bitmap>? = null,
    scale: Float,
    rotation: Float,
    phase: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val targetImageSizePx = with(LocalDensity.current) {
        (220f * settingsState.sizeScale).dp.toPx().roundToInt().coerceAtLeast(1)
    }
    val customBitmap = remember(settingsState.imageUri, targetImageSizePx) {
        PetImageResolver.decodeBitmap(context, settingsState.imageUri, targetImageSizePx)
    }

    var aiFrameIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(aiAnimFrames) {
        if (aiAnimFrames.isNullOrEmpty()) { aiFrameIndex = 0; return@LaunchedEffect }
        while (true) {
            kotlinx.coroutines.delay(125L)
            aiFrameIndex = (aiFrameIndex + 1) % aiAnimFrames.size
        }
    }
    val appliedScale = scale * settingsState.sizeScale
    val catFrame = when (mood) {
        PetMood.Chill -> R.drawable.cat5_re
        PetMood.Happy -> when {
            phase < 0.2f -> R.drawable.cat5_re
            phase < 0.4f -> R.drawable.cat6_re
            phase < 0.6f -> R.drawable.cat7_re
            phase < 0.8f -> R.drawable.cat6_re
            else -> R.drawable.cat5_re
        }
        PetMood.Sleepy -> when {
            phase < 0.5f -> R.drawable.cat6_re
            else -> R.drawable.cat7_re
        }
        PetMood.Excited -> when {
            phase < 0.25f -> R.drawable.cat5_re
            phase < 0.5f -> R.drawable.cat8_re
            phase < 0.75f -> R.drawable.cat9_re
            else -> R.drawable.cat5_re
        }
        PetMood.Hungry -> R.drawable.cat5_re
    }
    val wave = kotlin.math.sin(phase * kotlin.math.PI * 2.0).toFloat()
    val moodTilt = when (mood) {
        PetMood.Sleepy -> -4f
        PetMood.Chill -> 0f
        PetMood.Happy -> 0.8f
        PetMood.Excited -> 2f
        PetMood.Hungry -> -0.5f
    }
    val moodNudgeX = when (mood) {
        PetMood.Sleepy -> -0.8f
        PetMood.Chill -> 0f
        PetMood.Happy -> 0.8f
        PetMood.Excited -> 1.8f
        PetMood.Hungry -> -0.3f
    }
    val moodNudgeY = when (mood) {
        PetMood.Sleepy -> 2f
        PetMood.Chill -> 0f
        PetMood.Happy -> 1f
        PetMood.Excited -> -1.4f
        PetMood.Hungry -> 0.5f
    }

    Box(
        modifier = modifier.graphicsLayer {
            translationX = moodNudgeX + wave * when (mood) {
                PetMood.Excited -> 2.4f
                PetMood.Happy -> 1.2f
                PetMood.Sleepy -> 0.8f
                else -> 1f
            }
            translationY = moodNudgeY + wave * when (mood) {
                PetMood.Excited -> 1.8f
                PetMood.Happy -> 1f
                PetMood.Sleepy -> 1.2f
                else -> 0.9f
            }
            rotationZ = rotation + moodTilt + wave * when (mood) {
                PetMood.Excited -> 1.3f
                PetMood.Happy -> 0.7f
                PetMood.Sleepy -> 0.6f
                else -> 0.5f
            }
            scaleX = appliedScale
            scaleY = appliedScale
        },
        contentAlignment = Alignment.Center,
    ) {
        when {
            !aiAnimFrames.isNullOrEmpty() -> Image(
                bitmap = aiAnimFrames[aiFrameIndex].asImageBitmap(),
                contentDescription = "桌宠小猫",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            customBitmap != null -> Image(
                bitmap = customBitmap.asImageBitmap(),
                contentDescription = "桌宠小猫",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Image(
                painter = painterResource(id = catFrame),
                contentDescription = "桌宠小猫",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun StageSpeechBubble(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.width(220.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            color = Color(0xFF111111),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
