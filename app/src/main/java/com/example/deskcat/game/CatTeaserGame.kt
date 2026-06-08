package com.example.deskcat.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.deskcat.R
import kotlinx.coroutines.delay
import kotlin.math.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.*
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Job
import com.example.deskcat.pet.PetStateRepository
import androidx.compose.runtime.collectAsState
@Composable
fun CatTeaserGame(
    onReward: () -> Unit,
    onExit: () -> Unit,

) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()

    ) {

        //val uiState by PetStateRepository.uiState.collectAsState()
        val scope = rememberCoroutineScope()


        val infiniteTransition = rememberInfiniteTransition(
            label = "teaser"
        )

        val teaserRotation by infiniteTransition.animateFloat(
            initialValue = -12f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 400
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rotation"
        )
        var gameFinished by remember { mutableStateOf(false) }
        val maxWidth = constraints.maxWidth.toFloat()
        val maxHeight = constraints.maxHeight.toFloat()

        var teaserPos by remember {
            mutableStateOf(
                Offset(
                    maxWidth * 0.7f,
                    maxHeight * 0.3f
                )
            )
        }

        var catPos by remember {
            mutableStateOf(
                Offset(
                    maxWidth * 0.3f,
                    maxHeight * 0.6f
                )
            )
        }

        var catFacingRight by remember {
            mutableStateOf(true)
        }
        var catchCount by remember {
            mutableIntStateOf(0)
        }

        var floatingText by remember {
            mutableStateOf<String?>(null)
        }
        var floatingTextOffset by remember { mutableStateOf(Offset.Zero) }
        val textAlpha = remember {
            Animatable(0f)
        }
        var gameStarted by remember {
            mutableStateOf(false)
        }
        var message by remember {
            mutableStateOf("移动逗猫棒开始游戏")
        }

        var showRewardDialog by remember {
            mutableStateOf(false)
        }
        var floatingJob by remember {
            mutableStateOf<Job?>(null)
        }
    // 小猫AI追逐
        LaunchedEffect(Unit) {
            while (!gameFinished) {    // 游戏结束后不再循环

                if (!gameStarted) {
                    delay(16)
                    continue
                }

                val dx = teaserPos.x - catPos.x
                val dy = teaserPos.y - catPos.y
                catFacingRight = dx >= 0
                val distance = sqrt(dx * dx + dy * dy)


                if (distance > 5f) {
                    val speed = 6f + catchCount * 1.2f
                    catPos = Offset(
                        (catPos.x + dx / distance * speed).coerceIn(0f, maxWidth - 120f),
                        (catPos.y + dy / distance * speed).coerceIn(120f, maxHeight - 120f)
                    )
                }

                if (distance < 60f) {
                    catchCount++



                    floatingTextOffset = catPos // 从小猫当前位置开始
                    floatingJob?.cancel()
                    floatingJob = scope.launch {

                        floatingText = "抓到啦！✕$catchCount"
                        floatingTextOffset = catPos

                        textAlpha.snapTo(1f)

                        val duration = 1200
                        val steps = 24

                        repeat(steps) { i ->
                            textAlpha.snapTo(1f - i / steps.toFloat())

                            floatingTextOffset =
                                floatingTextOffset.copy(
                                    y = floatingTextOffset.y - 60f / steps
                                )

                            delay((duration / steps).toLong())
                        }

                        floatingText = null
                    }
                    teaserPos = Offset(
                        (50..(maxWidth - 240f).toInt()).random().toFloat(),
                        (150..(maxHeight - 220f).toInt()).random().toFloat()
                    )

                    if (catchCount >= 5) {

                        gameFinished = true

                        message = "小猫玩累啦~,让它休息会儿吧"

                        // 修改宠物属性

                        PetStateRepository.finishTeaserGame() // ✅ 确保修改了 StateFlow
                        delay(800L)

                        showRewardDialog = true

                        onReward()
                    }

                    delay(500)
                }

                delay(16)
            }
        }



        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F5EE))
                .pointerInput(Unit) {

                    detectDragGestures { change, dragAmount ->

                        change.consume()

                        if (!gameStarted) {
                            gameStarted = true
                            message = "拖动逗猫棒吸引小猫"
                        }
                        teaserPos = Offset(
                            (teaserPos.x + dragAmount.x)
                                .coerceIn(0f, maxWidth - 240f),

                            (teaserPos.y + dragAmount.y)
                                .coerceIn(120f, maxHeight - 220f)
                        )
                    }

                }
        ) {

            floatingText?.let { text ->

                Text(
                    text = text,
                    color = Color(0xFF595656).copy(alpha = textAlpha.value),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .offset { IntOffset(floatingTextOffset.x.toInt(), floatingTextOffset.y.toInt()) }
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = message
                )

                Spacer(Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { catchCount / 5f },
                    modifier = Modifier.width(180.dp)
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "$catchCount / 5",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(
                        top = 12.dp,
                        end = 8.dp
                    )
                    .size(40.dp)
                    .background(
                        Color.White.copy(alpha = 0.85f),
                        CircleShape
                    )
            ) {
                Text(
                    text = "✕",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
            }

            // 小猫
            Image(
                painter = painterResource(R.drawable.cat_open),
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset(catPos.x.toInt(), catPos.y.toInt()) }
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = if (catFacingRight) 1f else -1f
                    }
            )

            // 逗猫棒
            Image(
                painter = painterResource(R.drawable.game_teaser_wand),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            teaserPos.x.toInt(),
                            teaserPos.y.toInt()
                        )
                    }
                    .size(100.dp)
                    .graphicsLayer {
                        rotationZ = teaserRotation
                    }
            )
        }

        if (showRewardDialog) {

            AlertDialog(
                onDismissRequest = {},

                title = {
                    Text(
                        text = "游戏完成！",
                        fontWeight = FontWeight.Bold
                    )
                },

                text = {
                    Column {

                        val state = PetStateRepository.uiState.collectAsState()

                        Text("😊 当前开心值：${state.value.happiness}")
                        Text("⚡ 当前精力值：${state.value.energy}")
                        Text("🍖 当前饱腹值：${state.value.hunger}")
                    }
                },

                confirmButton = {

                    Button(
                        onClick = {
                            showRewardDialog = false
                            onExit()                   // 返回主页面
                        }
                    ) {
                        Text("确定")
                    }
                }
            )
        }
}
}
