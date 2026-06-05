package com.example.deskcat.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.deskcat.DesktopPetUiState
import com.example.deskcat.PetMood
import com.example.deskcat.R
import com.example.deskcat.StageBackgroundGlow
import com.example.deskcat.StagePetAvatar
import com.example.deskcat.settings.PetSettingsUiState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

data class CoinGameResult(
    val caughtCoins: Int,
    val earnedCoins: Int,
)

@Composable
fun CoinGameResultDialog(
    result: CoinGameResult,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFCF7)),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.icon_coin),
                    contentDescription = "金币",
                    modifier = Modifier.size(86.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "本局接到 ${result.caughtCoins} 枚金币",
                    color = Color(0xFF111111),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "实际获得 ${result.earnedCoins} 金币",
                    color = Color(0xFF666666),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss) {
                    Text("返回主界面")
                }
            }
        }
    }
}

@Composable
fun CoinCatchGame(
    settingsState: PetSettingsUiState,
    uiState: DesktopPetUiState,
    onFinish: (Int) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var timeLeftMillis by remember { mutableStateOf(20_000L) }
    var caughtCoins by remember { mutableStateOf(0) }
    var playerX by remember { mutableStateOf(0f) }
    var coinX by remember { mutableStateOf(0f) }
    var coinY by remember { mutableStateOf(0f) }
    var gameStarted by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xEEFFFFFF)),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "接金币",
                        color = Color(0xFF111111),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = if (uiState.energy <= 0) "精力不足，本局奖励减半" else "拖动小猫接住金币",
                        color = Color(0xFF666666),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onExit) {
                    Text("退出")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "剩余 ${((timeLeftMillis + 999) / 1000).coerceAtLeast(0)} 秒",
                    color = Color(0xFF333333),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "接到 $caughtCoins",
                    color = Color(0xFF333333),
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .background(Color(0x55FFFFFF), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            playerX = (playerX + dragAmount.x).coerceIn(70f, size.width - 70f)
                        }
                    },
            ) {
                val density = LocalDensity.current
                val gameWidthPx = with(density) { maxWidth.toPx() }
                val gameHeightPx = with(density) { maxHeight.toPx() }
                val coinSize = 52.dp
                val petSize = 116.dp
                val coinSizePx = with(density) { coinSize.toPx() }
                val petSizePx = with(density) { petSize.toPx() }

                LaunchedEffect(gameWidthPx, gameHeightPx) {
                    if (gameWidthPx <= 0f || gameHeightPx <= 0f || gameStarted) return@LaunchedEffect
                    gameStarted = true
                    playerX = gameWidthPx / 2f
                    coinX = Random.nextFloat() * (gameWidthPx - coinSizePx) + coinSizePx / 2f
                    coinY = -coinSizePx
                    var remaining = 20_000L
                    var lastFrame = withFrameNanos { it }
                    while (remaining > 0L) {
                        val now = withFrameNanos { it }
                        val deltaMillis = ((now - lastFrame) / 1_000_000L).coerceAtLeast(1L)
                        lastFrame = now
                        remaining = (remaining - deltaMillis).coerceAtLeast(0L)
                        timeLeftMillis = remaining

                        val speed = 0.32f + caughtCoins * 0.012f
                        coinY += deltaMillis * speed
                        val catcherY = gameHeightPx - petSizePx * 0.62f
                        val caught = coinY + coinSizePx >= catcherY && abs(coinX - playerX) <= petSizePx * 0.56f
                        if (caught) {
                            caughtCoins += 1
                            coinX = Random.nextFloat() * (gameWidthPx - coinSizePx) + coinSizePx / 2f
                            coinY = -coinSizePx
                        } else if (coinY > gameHeightPx) {
                            coinX = Random.nextFloat() * (gameWidthPx - coinSizePx) + coinSizePx / 2f
                            coinY = -coinSizePx
                        }
                    }
                    onFinish(caughtCoins)
                }

                StageBackgroundGlow()
                Image(
                    painter = painterResource(id = R.drawable.icon_coin),
                    contentDescription = "掉落金币",
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (coinX - coinSizePx / 2f).roundToInt(),
                                coinY.roundToInt(),
                            )
                        }
                        .size(coinSize),
                )
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (playerX - petSizePx / 2f).roundToInt(),
                                (gameHeightPx - petSizePx - 16f).roundToInt(),
                            )
                        }
                        .size(petSize),
                    contentAlignment = Alignment.Center,
                ) {
                    StagePetAvatar(
                        mood = PetMood.Excited,
                        settingsState = settingsState,
                        scale = 1f,
                        rotation = 0f,
                        phase = 0.75f,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
