package com.example.deskcat

import android.R.attr.alpha
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.deskcat.game.CoinCatchGame
import com.example.deskcat.game.CoinGameResult
import com.example.deskcat.game.CoinGameResultDialog
import com.example.deskcat.game.SlotMachineGame
import com.example.deskcat.pet.FoodItem
import com.example.deskcat.pet.MiniGameItem
import com.example.deskcat.pet.PetCatalog
import com.example.deskcat.settings.PetSettingsUiState
import com.example.deskcat.settings.PetSettingsViewModel
import com.example.deskcat.settings.PetSizePreset
import com.example.deskcat.settings.PetStyle
import com.example.deskcat.weather.WeatherViewModel
import kotlin.math.roundToInt

private enum class DeskCatScreenMode {
    Main,
    CoinGame,
    SlotMachine,
}

@Composable
fun BakingScreen(
    bakingViewModel: BakingViewModel = viewModel(),
    settingsViewModel: PetSettingsViewModel,
    weatherViewModel: WeatherViewModel,
    overlayGranted: Boolean,
    overlayRunning: Boolean,
    onPickCustomImage: (((android.net.Uri?) -> Unit) -> Unit),
    onImportPetPack: (((android.net.Uri?) -> Unit) -> Unit),
    onOpenOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onRequestDeviceLocation: (((Double?, Double?) -> Unit) -> Unit),
) {
    val uiState by bakingViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val weatherState by weatherViewModel.uiState.collectAsState()
    val analyzing by settingsViewModel.analyzing.collectAsState()
    val generatingAnim by settingsViewModel.generatingAnim.collectAsState()
    val aiAnimFrames by settingsViewModel.aiAnimFrames.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var settingsExpanded by remember { mutableStateOf(false) }
    var screenMode by remember { mutableStateOf(DeskCatScreenMode.Main) }
    var showFoodMenu by remember { mutableStateOf(false) }
    var showPlayMenu by remember { mutableStateOf(false) }
    var coinGameResult by remember { mutableStateOf<CoinGameResult?>(null) }

    val floatTransition = rememberInfiniteTransition(label = "petFloat")
    val floatOffset by floatTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "floatOffset",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF7F4EE),
                        Color(0xFFEDE7DD),
                        Color(0xFFDCD5CA),
                    ),
                ),
            ),
    ) {
        val compact = maxWidth < 360.dp || maxHeight < 700.dp
        val pagePadding = if (compact) 14.dp else 18.dp
        val cardSpacing = if (compact) 10.dp else 14.dp
        val bobAmplitude = if (compact) 4f else 6f
        val stageHeight = if (compact) 250.dp else (maxHeight * 0.56f).coerceAtLeast(300.dp)
        val density = LocalDensity.current
        val stageWidthPx = with(density) { (maxWidth - pagePadding * 2 - 24.dp).toPx().coerceAtLeast(0f) }
        val stageHeightPx = with(density) { (stageHeight - 24.dp).toPx().coerceAtLeast(0f) }

        LaunchedEffect(stageWidthPx, stageHeightPx) {
            bakingViewModel.onStageReady(stageWidthPx, stageHeightPx)
        }

        val requestDeviceWeather = {
            onRequestDeviceLocation { latitude, longitude ->
                if (latitude != null && longitude != null) {
                    weatherViewModel.refreshDeviceWeather(latitude, longitude, speak = true)
                } else {
                    weatherViewModel.refreshManualWeather(speak = true)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(pagePadding),
            verticalArrangement = Arrangement.spacedBy(cardSpacing),
        ) {
            if (screenMode == DeskCatScreenMode.CoinGame) {
                CoinCatchGame(
                    settingsState = settingsState,
                    uiState = uiState,
                    onFinish = { caughtCoins ->
                        val earnedCoins = bakingViewModel.finishCoinGame(caughtCoins)
                        coinGameResult = CoinGameResult(caughtCoins, earnedCoins)
                        screenMode = DeskCatScreenMode.Main
                    },
                    onExit = {
                        bakingViewModel.setSpeech("这局先暂停，下次继续接金币。")
                        screenMode = DeskCatScreenMode.Main
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (screenMode == DeskCatScreenMode.SlotMachine) {
                SlotMachineGame(
                    onWinFood = { food ->
                        bakingViewModel.buyFood(food, free = true)
                        bakingViewModel.setSpeech("三连！获得了${food.name}！")
                    },
                    onExit = {
                        bakingViewModel.setSpeech("下次再来试试手气吧。")
                        screenMode = DeskCatScreenMode.Main
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                HeaderCard(
                    uiState = uiState,
                    settingsState = settingsState,
                    overlayGranted = overlayGranted,
                    overlayRunning = overlayRunning,
                    onToggleSettings = { settingsExpanded = !settingsExpanded },
                    onStartOverlay = onStartOverlay,
                    onStopOverlay = onStopOverlay,
                    modifier = Modifier.fillMaxWidth(),
                )

                WeatherCard(
                    weatherState = weatherState,
                    onAskWeather = { weatherViewModel.refreshManualWeather(speak = true) },
                    onUseDeviceLocation = requestDeviceWeather,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (settingsExpanded) {
                    PetSettingsPanel(
                        settingsState = settingsState,
                        analyzing = analyzing,
                        generatingAnim = generatingAnim,
                        hasAiAnim = aiAnimFrames != null,
                        onPickCustomImage = {
                            onPickCustomImage { uri ->
                                settingsViewModel.analyzeAndSetImage(context, uri?.toString())
                            }
                        },
                        onResetImage = { settingsViewModel.setImageUri(null) },
                        onImportPetPack = {
                            onImportPetPack { uri ->
                                if (uri != null) settingsViewModel.importPetPackFromZip(context, uri)
                            }
                        },
                        onClearPetPack = { settingsViewModel.clearPetPack(context) },
                        onGenerateAiAnim = { settingsViewModel.generateAiAnimation(context) },
                        onClearAiAnim = { settingsViewModel.clearAiAnimation() },
                        onSelectPreset = { preset, scale ->
                            settingsViewModel.setSizePreset(preset)
                            settingsViewModel.setSizeScale(scale)
                        },
                        onScaleChange = settingsViewModel::setSizeScale,
                        onAutoMoveChange = settingsViewModel::setAutoMoveEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        WeatherSettingsSection(
                            weatherState = weatherState,
                            onCityChange = weatherViewModel::updateCityInput,
                            onSaveCity = { weatherViewModel.refreshManualWeather(speak = true) },
                            onUseDeviceLocation = requestDeviceWeather,
                        )
                    }
                }

                PetStage(
                    uiState = uiState,
                    settingsState = settingsState,
                    aiAnimFrames = aiAnimFrames,
                    stageHeight = stageHeight,
                    floatOffset = floatOffset,
                    bobAmplitude = bobAmplitude,
                    onDragPet = bakingViewModel::dragPet,
                    modifier = Modifier.fillMaxWidth(),
                )

                DesktopActionPanel(
                    onPet = bakingViewModel::pet,
                    onFeed = { showFoodMenu = true },
                    onPlay = { showPlayMenu = true },
                    onRest = bakingViewModel::rest,
                    onReset = bakingViewModel::resetPosition,
                )
            }
        }

        if (showFoodMenu) {
            FoodMenuDialog(
                coins = uiState.coins,
                onBuyFood = { food -> bakingViewModel.buyFood(food) },
                onDismiss = { showFoodMenu = false },
            )
        }
        if (showPlayMenu) {
            PlayMenuDialog(
                onSelectGame = { game ->
                    when (game.id) {
                        PetCatalog.COIN_CATCH_GAME_ID -> {
                            showPlayMenu = false
                            screenMode = DeskCatScreenMode.CoinGame
                        }
                        PetCatalog.SLOT_MACHINE_GAME_ID -> {
                            showPlayMenu = false
                            screenMode = DeskCatScreenMode.SlotMachine
                        }
                        else -> {
                            bakingViewModel.setSpeech("${game.name}还在开发中，先玩接金币吧。")
                        }
                    }
                },
                onDismiss = { showPlayMenu = false },
            )
        }
        coinGameResult?.let { result ->
            CoinGameResultDialog(
                result = result,
                onDismiss = { coinGameResult = null },
            )
        }
    }
}

@Composable
private fun PetSettingsPanel(
    settingsState: PetSettingsUiState,
    analyzing: Boolean,
    generatingAnim: Boolean,
    hasAiAnim: Boolean,
    onPickCustomImage: () -> Unit,
    onResetImage: () -> Unit,
    onImportPetPack: () -> Unit,
    onClearPetPack: () -> Unit,
    onGenerateAiAnim: () -> Unit,
    onClearAiAnim: () -> Unit,
    onSelectPreset: (PetSizePreset, Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onAutoMoveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    weatherContent: @Composable ColumnScope.() -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xEEFFFFFF)),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "桌宠设置", color = Color(0xFF111111), fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))

            // 资源包区域
            Text(text = "动画资源包", color = Color(0xFF111111), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (settingsState.usePetPack) "已加载资源包（body + paw_up + paw_down）" else "未加载资源包，使用内置动画",
                color = Color(0xFF444444),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(
                    onClick = onImportPetPack,
                    label = { Text("导入资源包 (.zip)") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFF1E7D7), labelColor = Color(0xFF111111)),
                )
                if (settingsState.usePetPack) {
                    AssistChip(
                        onClick = onClearPetPack,
                        label = { Text("清除") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFE8E2D8), labelColor = Color(0xFF111111)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 单图模式
            Text(text = "单张图片模式", color = Color(0xFF111111), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            val statusText = when {
                analyzing -> "正在识别宠物并处理图片..."
                settingsState.useCustomImage && settingsState.detectedLabel != null -> {
                    val styleName = when (settingsState.petStyle) {
                        PetStyle.Cat -> "猫咪（慵懒风格）"
                        PetStyle.Dog -> "狗狗（活泼风格）"
                        PetStyle.Rabbit -> "兔子（温柔风格）"
                        PetStyle.Default -> "未知宠物（默认风格）"
                    }
                    "已识别：${settingsState.detectedLabel} → $styleName"
                }
                settingsState.useCustomImage -> "当前使用自定义图片"
                else -> "当前使用内置动画图片"
            }
            Text(
                text = statusText,
                color = if (analyzing) Color(0xFF888888) else Color(0xFF444444),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(
                    onClick = { if (!analyzing) onPickCustomImage() },
                    label = { Text(if (analyzing) "识别中..." else "选择图片") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFF1E7D7), labelColor = Color(0xFF111111)),
                )
                AssistChip(
                    onClick = { if (!analyzing) onResetImage() },
                    label = { Text("恢复默认") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFE8E2D8), labelColor = Color(0xFF111111)),
                )
            }

            // AI 动画生成区域
            if (settingsState.useCustomImage) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "AI 动画生成", color = Color(0xFF111111), fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        generatingAnim -> "正在调用豆包 AI 生成动画帧..."
                        hasAiAnim -> "已生成 AI 动画，正在预览播放"
                        else -> "基于当前图片，用豆包 AI 生成逐帧动画"
                    },
                    color = if (generatingAnim) Color(0xFF888888) else Color(0xFF444444),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssistChip(
                        onClick = { if (!generatingAnim && !analyzing) onGenerateAiAnim() },
                        label = { Text(if (generatingAnim) "生成中..." else "生成动画") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFF1E7D7), labelColor = Color(0xFF111111)),
                    )
                    if (hasAiAnim) {
                        AssistChip(
                            onClick = onClearAiAnim,
                            label = { Text("清除动画") },
                            colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFE8E2D8), labelColor = Color(0xFF111111)),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "桌宠大小", color = Color(0xFF111111), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(
                    onClick = { onSelectPreset(PetSizePreset.Small, 0.85f) },
                    label = { Text("小") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (settingsState.sizePreset == PetSizePreset.Small) Color(0xFFF7F4EE) else Color(0xFFF1E7D7),
                        labelColor = Color(0xFF111111),
                    ),
                )
                AssistChip(
                    onClick = { onSelectPreset(PetSizePreset.Medium, 1f) },
                    label = { Text("中") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (settingsState.sizePreset == PetSizePreset.Medium) Color(0xFFF7F4EE) else Color(0xFFF1E7D7),
                        labelColor = Color(0xFF111111),
                    ),
                )
                AssistChip(
                    onClick = { onSelectPreset(PetSizePreset.Large, 1.2f) },
                    label = { Text("大") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (settingsState.sizePreset == PetSizePreset.Large) Color(0xFFF7F4EE) else Color(0xFFF1E7D7),
                        labelColor = Color(0xFF111111),
                    ),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "缩放 ${(settingsState.sizeScale * 100).roundToInt()}%",
                color = Color(0xFF444444),
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(value = settingsState.sizeScale, onValueChange = onScaleChange, valueRange = 0.8f..1.4f)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = "自动移动彩蛋", color = Color(0xFF111111), fontWeight = FontWeight.Medium)
                    Text(
                        text = "3 秒不拖动时，小猫会轻微自己动一下。",
                        color = Color(0xFF666666),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = settingsState.autoMoveEnabled, onCheckedChange = onAutoMoveChange)
            }
            weatherContent()
        }
    }
}

@Composable
private fun HeaderCard(
    uiState: DesktopPetUiState,
    settingsState: PetSettingsUiState,
    overlayGranted: Boolean,
    overlayRunning: Boolean,
    onToggleSettings: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xEEFFFFFF)),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "桌宠喵",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    MoodBadge(mood = uiState.mood)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = onToggleSettings,
                        label = { Text("设置") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (settingsState.useCustomImage) Color(0xFFF1E7D7) else Color(0xFFE8E2D8),
                            labelColor = Color(0xFF111111),
                        ),
                    )
                    AssistChip(
                        onClick = if (overlayRunning) onStopOverlay else onStartOverlay,
                        label = { Text(if (overlayRunning) "关闭" else "开启") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFF7F4EE), labelColor = Color(0xFF111111)),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "拖动我到处走走，摸摸、喂食、玩耍、休息都会影响我的状态。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF444444),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "互动 ${uiState.petCount} 次", color = Color(0xFF666666), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(12.dp))
            CoinRow(coins = uiState.coins)
            Spacer(modifier = Modifier.height(10.dp))
            StatRow(label = "饱腹", value = uiState.hunger)
            StatRow(label = "开心", value = uiState.happiness)
            StatRow(label = "精力", value = uiState.energy)
        }
    }
}

@Composable
private fun CoinRow(coins: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x22F0C24B), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.icon_coin),
                contentDescription = "金币",
                modifier = Modifier.size(30.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "金币", color = Color(0xFF333333), fontWeight = FontWeight.SemiBold)
        }
        Text(text = coins.toString(), color = Color(0xFF111111), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatRow(label: String, value: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, color = Color(0xFF333333), fontSize = 13.sp)
            Text(text = "$value%", color = Color(0xFF333333), fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(Color(0x33111111), RoundedCornerShape(999.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value / 100f)
                    .height(10.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF1B1B1B),
                                Color(0xFFF2F2F2)
                            )
                        ),
                        RoundedCornerShape(999.dp),
                    ),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun MoodBadge(mood: PetMood) {
    val (text, background) = when (mood) {
        PetMood.Sleepy -> "想睡觉" to Color(0xFFD7D7D7)
        PetMood.Chill -> "很放松" to Color(0xFFEAEAEA)
        PetMood.Happy -> "心情好" to Color(0xFFF6F6F6)
        PetMood.Excited -> "很兴奋" to Color(0xFFFFFFFF)
        PetMood.Hungry -> "有点饿" to Color(0xFFD1CDC5)
    }
    Surface(color = background, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = Color(0xFF111111),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}


@Composable
private fun PlayMenuDialog(
    onSelectGame: (MiniGameItem) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        MenuDialogCard(title = "选择小游戏", subtitle = "玩耍获得金币，再去给小猫买好吃的！") {
            PetCatalog.miniGames.forEach { game ->
                GameMenuCard(game = game, onClick = { onSelectGame(game) })
                Spacer(modifier = Modifier.height(10.dp))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("关闭")
            }
        }
    }
}

@Composable
private fun FoodMenuDialog(
    coins: Int,
    onBuyFood: (FoodItem) -> Boolean,
    onDismiss: () -> Unit,
) {
    var message by remember { mutableStateOf("当前金币：$coins") }
    Dialog(onDismissRequest = onDismiss) {
        MenuDialogCard(title = "选择食物", subtitle = message) {
            PetCatalog.foods.forEach { food ->
                FoodMenuCard(
                    food = food,
                    canAfford = coins >= food.price,
                    onClick = {
                        val success = onBuyFood(food)
                        message = if (success) "已购买 ${food.name}" else "金币不够，先玩一局吧"
                        if (success) onDismiss()
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("关闭")
            }
        }
    }
}

@Composable
private fun MenuDialogCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFCF7)),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp),
    ) {
        Column(modifier = Modifier
            .padding(18.dp)
            .verticalScroll(rememberScrollState())) {
            Text(text = title, color = Color(0xFF111111), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, color = Color(0xFF666666), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun GameMenuCard(game: MiniGameItem, onClick: () -> Unit) {
    MenuItemCard(
        iconRes = game.iconRes,
        title = game.name,
        description = game.description,
        trailingText = if (game.enabled) "开始" else "待开发",
        enabled = true,
        locked = !game.enabled,
        onClick = onClick,
    )
}

@Composable
private fun FoodMenuCard(food: FoodItem, canAfford: Boolean, onClick: () -> Unit) {
    MenuItemCard(
        iconRes = food.iconRes,
        title = food.name,
        description = food.description,
        trailingText = "${food.price} 金币",
        enabled = true,
        locked = !canAfford,
        onClick = onClick,
    )
}

@Composable
private fun MenuItemCard(
    iconRes: Int,
    title: String,
    description: String,
    trailingText: String,
    enabled: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val contentAlpha = if (locked) 0.58f else 1f
    Card(
        colors = CardDefaults.cardColors(containerColor = if (locked) Color(0xFFF0EEE9) else Color(0xFFFFFFFF)),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(66.dp).graphicsLayer { alpha = contentAlpha },
                )
                if (locked) {
                    Image(
                        painter = painterResource(id = R.drawable.badge_locked),
                        contentDescription = "锁定",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color(0xFF111111), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = description, color = Color(0xFF666666), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = trailingText,
                color = if (locked) Color(0xFF777777) else Color(0xFF111111),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
    }
}
