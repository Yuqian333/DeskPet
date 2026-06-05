package com.example.deskcat.pet

import androidx.compose.ui.geometry.Offset
import com.example.deskcat.DesktopPetUiState
import com.example.deskcat.PetMood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random

object PetStateRepository {
    private val _uiState = MutableStateFlow(DesktopPetUiState())
    val uiState: StateFlow<DesktopPetUiState> = _uiState.asStateFlow()
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val idleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var progressRepository: PetProgressRepository? = null
    private var progressLoadJob: Job? = null
    private var idleChatterJob: Job? = null
    private var lastUserInteractionAt = System.currentTimeMillis()

    fun bindProgressRepository(repository: PetProgressRepository) {
        progressRepository = repository
        progressLoadJob?.cancel()
        progressLoadJob = persistenceScope.launch {
            val progress = repository.progressFlow.first()
            val current = _uiState.value
            _uiState.value = current.copy(
                hunger = progress.hunger,
                happiness = progress.happiness,
                energy = progress.energy,
                coins = progress.coins.coerceIn(0, PetProgressRepository.MAX_COINS),
                petCount = progress.petCount,
            )
        }
    }

    fun startIdleChatter() {
        if (idleChatterJob != null) return
        idleChatterJob = idleScope.launch {
            while (true) {
                delay(IDLE_CHATTER_INTERVAL_MS)
                if (System.currentTimeMillis() - lastUserInteractionAt >= IDLE_CHATTER_MIN_IDLE_MS) {
                    nudgeIdleState()
                }
            }
        }
    }

    fun onStageReady(stageWidth: Float, stageHeight: Float) {
        val current = _uiState.value
        if (current.initialized || stageWidth <= 0f || stageHeight <= 0f) return

        val petX = (stageWidth * 0.5f) - 96f
        val petY = (stageHeight * 0.42f) - 96f
        _uiState.value = current.copy(
            position = Offset(petX.coerceAtLeast(24f), petY.coerceAtLeast(24f)),
            bounds = Offset(stageWidth, stageHeight),
            initialized = true,
        )
    }

    fun dragPet(deltaX: Float, deltaY: Float) {
        markUserInteraction()
        val current = _uiState.value
        val petSize = 192f
        val maxX = (current.bounds.x - petSize).coerceAtLeast(0f)
        val maxY = (current.bounds.y - petSize).coerceAtLeast(0f)

        _uiState.value = current.copy(
            position = Offset(
                (current.position.x + deltaX).coerceIn(0f, maxX),
                (current.position.y + deltaY).coerceIn(0f, maxY),
            ),
            mood = if (deltaX * deltaX + deltaY * deltaY > 400f) PetMood.Excited else current.mood,
            speech = if (deltaX * deltaX + deltaY * deltaY > 400f) "哇，带我去兜风！" else current.speech,
        )
    }

    fun pet() {
        markUserInteraction()
        updateStats(
            mood = PetMood.Happy,
            hungerDelta = -2,
            happinessDelta = 7,
            energyDelta = -1,
            speech = listOf(
                "呼噜呼噜，摸摸最舒服了。",
                "再摸一下，我就要打滚了。",
                "今天的快乐值已经拉满。",
            ).random(),
        )
    }

    fun feed() {
        markUserInteraction()
        buyFood(PetCatalog.foods.first())
    }

    fun play() {
        markUserInteraction()
        setSpeech("想玩什么？选一个小游戏吧。")
    }

    fun rest() {
        markUserInteraction()
        updateStats(
            mood = PetMood.Sleepy,
            hungerDelta = 1,
            happinessDelta = 2,
            energyDelta = 15,
            speech = "我先眯一会儿，充电中...",
        )
    }

    fun nudgeIdleState() {
        val current = _uiState.value
        val nextMood = pickIdleMood(current)
        val nextSpeech = pickIdleSpeech(nextMood)

        _uiState.value = current.copy(
            mood = nextMood,
            hunger = (current.hunger + 1).coerceIn(0, 100),
            happiness = (current.happiness - 1).coerceIn(0, 100),
            energy = (current.energy - 2).coerceIn(0, 100),
            speech = nextSpeech,
        )
        persistProgress()
    }

    fun resetPosition() {
        markUserInteraction()
        val current = _uiState.value
        val petX = (current.bounds.x * 0.5f) - 96f
        val petY = (current.bounds.y * 0.42f) - 96f
        _uiState.value = current.copy(
            position = Offset(petX.coerceAtLeast(24f), petY.coerceAtLeast(24f)),
            speech = "回到中间啦。",
        )
    }

    fun buyFood(food: FoodItem, free: Boolean = false): Boolean {
        markUserInteraction()
        val current = _uiState.value
        if (!free && current.coins < food.price) {
            _uiState.value = current.copy(
                mood = PetMood.Hungry,
                speech = "金币不够，先玩一局吧。",
            )
            return false
        }

        val coinCost = if (free) 0 else food.price
        _uiState.value = current.copy(
            mood = if (food.happinessDelta >= 8) PetMood.Happy else PetMood.Chill,
            hunger = (current.hunger + food.hungerDelta).coerceIn(0, 100),
            happiness = (current.happiness + food.happinessDelta).coerceIn(0, 100),
            energy = (current.energy + food.energyDelta).coerceIn(0, 100),
            coins = (current.coins - coinCost).coerceIn(0, PetProgressRepository.MAX_COINS),
            petCount = current.petCount + 1,
            speech = food.successSpeech,
        )
        persistProgress()
        return true
    }

    fun finishCoinGame(caughtCoins: Int): Int {
        markUserInteraction()
        val current = _uiState.value
        val earnedCoins = if (current.energy <= 0) {
            caughtCoins / 2
        } else {
            caughtCoins
        }.coerceAtLeast(0)
        val speech = when {
            caughtCoins <= 0 -> "没接到金币也没关系，下次再来。"
            current.energy <= 0 -> "有点累，金币奖励减半啦。"
            earnedCoins >= 15 -> "接得太准了，金币袋鼓起来了！"
            else -> "玩得不错，金币收好啦。"
        }

        _uiState.value = current.copy(
            mood = if (earnedCoins > 0) PetMood.Excited else PetMood.Chill,
            hunger = (current.hunger - 4).coerceIn(0, 100),
            happiness = (current.happiness + 8 + earnedCoins / 3).coerceIn(0, 100),
            energy = (current.energy - 10).coerceIn(0, 100),
            coins = (current.coins + earnedCoins).coerceIn(0, PetProgressRepository.MAX_COINS),
            petCount = current.petCount + 1,
            speech = speech,
        )
        persistProgress()
        return earnedCoins
    }

    fun setSpeech(speech: String) {
        markUserInteraction()
        _uiState.value = _uiState.value.copy(speech = speech)
    }

    private fun markUserInteraction() {
        lastUserInteractionAt = System.currentTimeMillis()
    }

    private fun pickIdleMood(current: DesktopPetUiState): PetMood {
        val weightedMoods = buildList {
            repeat(4) { add(PetMood.Chill) }
            if (current.hunger < 35) repeat(5) { add(PetMood.Hungry) }
            if (current.energy < 35) repeat(5) { add(PetMood.Sleepy) }
            if (current.happiness > 75) repeat(3) { add(PetMood.Happy) }
            if (current.happiness > 88 && current.energy > 45) repeat(2) { add(PetMood.Excited) }
            if (current.hunger >= 55 && current.energy >= 45) add(PetMood.Happy)
        }
        return weightedMoods.random()
    }

    private fun pickIdleSpeech(mood: PetMood): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeSpeech = when (hour) {
            in 6..10 -> "早安，今天也从摸摸小猫开始吧。"
            in 12..13 -> "午休时间到了，记得让眼睛也休息一下。"
            in 22..23, in 0..5 -> "夜深了，小猫建议你早点休息。"
            else -> null
        }
        if (timeSpeech != null && Random.nextFloat() < 0.35f) return timeSpeech

        return when (mood) {
            PetMood.Sleepy -> listOf(
                "有点困了，想找个角落躺一会儿。",
                "小猫电量偏低，适合休息一下。",
                "我先眯一会儿，有事轻轻叫我。",
            )
            PetMood.Chill -> listOf(
                "今天适合安静陪着你。",
                "我在旁边待命，随时可以互动。",
                "桌面很安静，小猫也很安静。",
            )
            PetMood.Happy -> listOf(
                "状态不错，随时可以互动。",
                "今天的快乐值看起来很稳定。",
                "小猫心情很好，想继续陪你。",
            )
            PetMood.Excited -> listOf(
                "我精神很好，想蹦蹦跳跳！",
                "要不要玩一局接金币？",
                "今天活力很满，适合做点有趣的事。",
            )
            PetMood.Hungry -> listOf(
                "肚子空空，来点好吃的吗？",
                "小猫想吃小鱼干。",
                "小猫可以吃下一头牛！",
            )
        }.random()
    }

    private fun updateStats(
        mood: PetMood,
        hungerDelta: Int,
        happinessDelta: Int,
        energyDelta: Int,
        speech: String,
    ) {
        val current = _uiState.value
        _uiState.value = current.copy(
            mood = mood,
            hunger = (current.hunger + hungerDelta).coerceIn(0, 100),
            happiness = (current.happiness + happinessDelta).coerceIn(0, 100),
            energy = (current.energy + energyDelta).coerceIn(0, 100),
            petCount = current.petCount + 1,
            speech = speech,
        )
        persistProgress()
    }

    private fun persistProgress() {
        val repository = progressRepository ?: return
        val state = _uiState.value
        persistenceScope.launch {
            repository.save(state)
        }
    }

    private const val IDLE_CHATTER_INTERVAL_MS = 18_000L
    private const val IDLE_CHATTER_MIN_IDLE_MS = 12_000L
}
