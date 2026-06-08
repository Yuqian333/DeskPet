package com.example.deskcat.pet

import androidx.compose.ui.geometry.Offset
import com.example.deskcat.DesktopPetUiState
import com.example.deskcat.PetMood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private var lastDecayAtMillis = System.currentTimeMillis()

    fun bindProgressRepository(repository: PetProgressRepository) {
        progressRepository = repository
        progressLoadJob?.cancel()
        progressLoadJob = persistenceScope.launch {
            val progress = repository.progressFlow.first()
            val now = System.currentTimeMillis()
            val restored = _uiState.value.copy(
                hunger = progress.hunger,
                happiness = progress.happiness,
                energy = progress.energy,
                coins = progress.coins.coerceIn(0, PetProgressRepository.MAX_COINS),
                petCount = progress.petCount,
            )
            val decayed = applyNaturalDecay(
                current = restored,
                elapsedMillis = now - progress.lastUpdatedAtMillis,
                includeSpeech = true,
            )
            _uiState.value = decayed
            lastDecayAtMillis = now
            if (decayed != restored) persistProgress()
        }
    }

    fun startIdleChatter() {
        if (idleChatterJob != null) return
        idleChatterJob = idleScope.launch {
            while (true) {
                delay(IDLE_CHATTER_INTERVAL_MS)
                applyElapsedDecay()
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
        val movingFast = deltaX * deltaX + deltaY * deltaY > 400f

        _uiState.value = current.copy(
            position = Offset(
                (current.position.x + deltaX).coerceIn(0f, maxX),
                (current.position.y + deltaY).coerceIn(0f, maxY),
            ),
            mood = if (movingFast) PetMood.Excited else current.mood,
            speech = if (movingFast) {
                listOf(
                    "哇，今天的桌面路线有点刺激。",
                    "慢一点慢一点，小猫的爪子要打滑了。",
                    "换个位置也不错，我能看见新的风景了。",
                ).random()
            } else {
                current.speech
            },
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
                "再摸一下，我就要原地打滚了。",
                "今天的快乐值被你补上来了。",
                "收到摸摸，小猫把这次互动记在心里了。",
                "手法不错，尾巴已经开始自己晃了。",
            ).random(),
        )
    }

    fun feed() {
        markUserInteraction()
        buyFood(PetCatalog.foods.first())
    }

    fun play() {
        markUserInteraction()
        setSpeech(
            listOf(
                "想玩什么？选一个小游戏吧。",
                "我准备好了，今天适合赢一点金币。",
                "玩一局可以提提神，也能给零食攒预算。",
            ).random(),
        )
    }

    fun rest() {
        markUserInteraction()
        updateStats(
            mood = PetMood.Sleepy,
            hungerDelta = -1,
            happinessDelta = 4,
            energyDelta = 25,
            speech = listOf(
                "我先眯一会儿，精力正在恢复。",
                "把尾巴盖好，短暂休息一下。",
                "休息是为了等会儿更有精神陪你。",
            ).random(),
        )
    }

    fun nudgeIdleState() {
        applyElapsedDecay()
        val current = _uiState.value
        val nextMood = pickIdleMood(current)
        _uiState.value = current.copy(
            mood = nextMood,
            speech = pickIdleSpeech(current, nextMood),
        )
    }

    fun resetPosition() {
        markUserInteraction()
        val current = _uiState.value
        val petX = (current.bounds.x * 0.5f) - 96f
        val petY = (current.bounds.y * 0.42f) - 96f
        _uiState.value = current.copy(
            position = Offset(petX.coerceAtLeast(24f), petY.coerceAtLeast(24f)),
            speech = "回到中间啦，这里视野最好。",
        )
    }

    fun buyFood(food: FoodItem, free: Boolean = false): Boolean {
        markUserInteraction()
        val current = _uiState.value
        if (!free && current.coins < food.price) {
            _uiState.value = current.copy(
                mood = PetMood.Hungry,
                speech = listOf(
                    "金币不够，先玩一局赚点零花吧。",
                    "小钱包有点轻，小游戏可能会帮上忙。",
                    "这份先记在愿望清单里，等金币够了再买。",
                ).random(),
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
            speech = pickFoodSpeech(food),
        )
        persistProgress()
        return true
    }

    fun finishCoinGame(caughtCoins: Int): Int {
        markUserInteraction()
        val current = _uiState.value
        val earnedCoins = if (current.energy <= 0) caughtCoins / 2 else caughtCoins
        val speech = when {
            caughtCoins <= 0 -> "没接到金币也没关系，下次我会盯得更准。"
            current.energy <= 0 -> "有点累，金币奖励先打个折，休息一下会更好。"
            earnedCoins >= 18 -> "接得太准了，金币袋都鼓起来了。"
            earnedCoins >= 10 -> "节奏不错，这局的金币很扎实。"
            else -> "玩得不错，金币收好啦。"
        }

        _uiState.value = current.copy(
            mood = if (earnedCoins > 0) PetMood.Excited else PetMood.Chill,
            hunger = (current.hunger - 4).coerceIn(0, 100),
            happiness = (current.happiness + 8 + earnedCoins / 3).coerceIn(0, 100),
            energy = (current.energy - 12).coerceIn(0, 100),
            coins = (current.coins + earnedCoins.coerceAtLeast(0)).coerceIn(0, PetProgressRepository.MAX_COINS),
            petCount = current.petCount + 1,
            speech = speech,
        )
        persistProgress()
        return earnedCoins.coerceAtLeast(0)
    }

    fun finishTeaserGame() {

        val current = _uiState.value

        _uiState.value = current.copy(
            hunger = (current.hunger - 20).coerceIn(0,100),
            happiness = (current.happiness+20).coerceIn(0,100),
            energy = (current.energy - 20).coerceIn(0,100)
        )


        persistProgress()
    }
    fun setSpeech(speech: String) {
        markUserInteraction()
        _uiState.value = _uiState.value.copy(speech = speech)
    }

    fun reactToWeather(mood: PetMood, speech: String) {
        markUserInteraction()
        _uiState.value = _uiState.value.copy(
            mood = mood,
            speech = speech,
        )
    }

    private fun markUserInteraction() {
        applyElapsedDecay()
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

    private fun pickIdleSpeech(current: DesktopPetUiState, mood: PetMood): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeSpeech = when (hour) {
            in 6..10 -> listOf("早安，今天也从摸摸小猫开始吧。", "早上的桌面很安静，我已经待命了。")
            in 12..13 -> listOf("午休时间到了，眼睛也该休息一下。", "中午适合短暂离开屏幕，回来我还在。")
            in 18..21 -> listOf("晚上好，今天的任务进度怎么样？", "忙了一天的话，可以先给自己一点缓冲时间。")
            in 22..23, in 0..5 -> listOf("夜深了，小猫建议你早点休息。", "这个点还在忙吗？我会安静陪着你。")
            else -> null
        }
        if (timeSpeech != null && Random.nextFloat() < 0.35f) return timeSpeech.random()

        val urgentSpeech = when {
            current.hunger <= 15 -> "肚子已经咕咕叫了，一点点吃的就能救场。"
            current.energy <= 15 -> "电量有点低，我想找个角落趴一会儿。"
            current.happiness <= 25 -> "今天有点安静，摸摸或者玩一局都可以让我开心点。"
            current.happiness >= 90 && current.energy >= 50 -> "状态很好，感觉能陪你把今天的事稳稳收尾。"
            else -> null
        }
        if (urgentSpeech != null && Random.nextFloat() < 0.55f) return urgentSpeech

        return when (mood) {
            PetMood.Sleepy -> listOf(
                "有点困了，想找个角落趴一会儿。",
                "小猫电量偏低，适合休息一下。",
                "我先眯一会儿，有事轻轻叫我。",
                "桌面灯光刚刚好，适合打一个短盹。",
            )
            PetMood.Chill -> listOf(
                "今天适合安静陪着你。",
                "我在旁边待命，随时可以互动。",
                "现在节奏不错，我会尽量不打扰你。",
                "你忙你的，我负责把桌面气氛守住。",
            )
            PetMood.Happy -> listOf(
                "状态不错，随时可以互动。",
                "今天的快乐值看起来很稳定。",
                "心情很好，想继续陪你。",
                "刚才那一会儿很舒服，我现在精神也不错。",
            )
            PetMood.Excited -> listOf(
                "我精神很好，想蹦蹦跳跳。",
                "要不要玩一局接金币？",
                "今天活力很满，适合做点有趣的事。",
                "我已经准备好冲刺了，金币在哪里？",
            )
            PetMood.Hungry -> listOf(
                "肚子空空，来点好吃的吗？",
                "小猫想吃点东西补补状态。",
                "如果有一份小零食，我会更有精神。",
                "我不挑食，但现在真的有点饿。",
            )
        }.random()
    }

    private fun pickFoodSpeech(food: FoodItem): String {
        return when (food.id) {
            "kibble" -> listOf("普通猫粮也很安心，先垫垫肚子。", "咔嚓咔嚓，基础口粮永远可靠。")
            "fish" -> listOf("小鱼干真香，快乐值正在上涨。", "这份小鱼干我可以认真记住。")
            "milk" -> listOf("喝完牛奶，精神恢复了一点。", "温温的牛奶很适合补充电量。")
            "can" -> listOf("罐头满分，今天被照顾得很好。", "这一口很扎实，肚子马上安心了。")
            "cookie" -> listOf("甜甜的，快乐值正在上升。", "点心不错，不过我会记得留点精力。")
            else -> listOf("${food.name} 收到，我会好好吃完。", "吃饱一点，陪你的时间就能更久一点。")
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

    private fun applyElapsedDecay() {
        val now = System.currentTimeMillis()
        val elapsedMillis = now - lastDecayAtMillis
        val current = _uiState.value
        val decayed = applyNaturalDecay(current = current, elapsedMillis = elapsedMillis, includeSpeech = false)
        if (decayed != current) {
            _uiState.value = decayed
            persistProgress()
        }
        if (elapsedMillis >= MIN_DECAY_INTERVAL_MS) {
            lastDecayAtMillis = now
        }
    }

    private fun applyNaturalDecay(
        current: DesktopPetUiState,
        elapsedMillis: Long,
        includeSpeech: Boolean,
    ): DesktopPetUiState {
        if (elapsedMillis < MIN_DECAY_INTERVAL_MS) return current

        val periods = (elapsedMillis.coerceIn(0L, MAX_DECAY_ELAPSED_MS) / MIN_DECAY_INTERVAL_MS).toInt()
        if (periods <= 0) return current

        var hunger = current.hunger
        var energy = current.energy
        var happiness = current.happiness
        repeat(periods) {
            hunger = (hunger - 2).coerceIn(0, 100)
            energy = (energy - 1 - if (hunger < 20) 1 else 0).coerceIn(0, 100)
            happiness = when {
                hunger < 20 || energy < 20 -> happiness - 2
                hunger >= 60 && energy >= 55 && happiness < 80 -> happiness
                else -> happiness - 1
            }.coerceIn(0, 100)
        }

        val nextMood = when {
            hunger <= 25 -> PetMood.Hungry
            energy <= 25 -> PetMood.Sleepy
            happiness >= 80 && energy >= 35 -> PetMood.Happy
            else -> PetMood.Chill
        }

        return current.copy(
            hunger = hunger,
            happiness = happiness,
            energy = energy,
            mood = nextMood,
            speech = if (includeSpeech) pickDecaySpeech(nextMood, hunger, energy) else current.speech,
        )
    }

    private fun pickDecaySpeech(mood: PetMood, hunger: Int, energy: Int): String {
        return when (mood) {
            PetMood.Hungry -> if (hunger <= 10) "你离开得有点久，小猫肚子已经空空了。" else "有点饿了，来点吃的会更有精神。"
            PetMood.Sleepy -> if (energy <= 10) "小猫电量见底，想先趴一会儿。" else "刚才安静待机了一阵，现在有点困。"
            PetMood.Happy -> "状态还不错，继续陪你待命。"
            PetMood.Excited -> "精神还很足，随时可以互动。"
            PetMood.Chill -> "我刚检查了一下状态，安静陪你继续工作。"
        }
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
    private const val MIN_DECAY_INTERVAL_MS = 30 * 60 * 1000L
    private const val MAX_DECAY_ELAPSED_MS = 12 * 60 * 60 * 1000L
}
