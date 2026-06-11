package com.example.deskcat.pet

import androidx.compose.ui.geometry.Offset
import com.example.deskcat.DesktopPetUiState
import com.example.deskcat.PetMood
import com.example.deskcat.settings.DEFAULT_PET_NAME
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
    private var petName = DEFAULT_PET_NAME

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

    fun setPetName(name: String) {
        val normalized = name.trim().ifBlank { DEFAULT_PET_NAME }
        petName = normalized
        val current = _uiState.value
        if (current.speech == DEFAULT_SPEECH || current.speech == defaultSpeech(DEFAULT_PET_NAME)) {
            _uiState.value = current.copy(speech = defaultSpeech(normalized))
        }
    }

    fun currentPetName(): String = petName

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
                    "哇，桌面兜风开始啦，我会努力抓稳。",
                    "慢一点慢一点，${petSubject()}的小脚要打滑了。",
                    "换个位置也不错，我可以陪你看新的角落。",
                    "被带着跑起来了，今天的路线有点可爱。",
                    "收到移动指令，我已经乖乖挪窝啦。",
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
                "呼噜呼噜，摸摸最舒服了，刚才的小烦恼先放一边。",
                "再摸一下，我就要变成一小团安心感了。",
                "今天的快乐值被你补上来了，我也把好运分你一点。",
                "收到摸摸，${petSubject()}把这次互动认真藏进心里了。",
                "手法不错，尾巴已经开始自己晃了。",
                "你辛苦啦，先摸摸我，也顺便摸摸自己的情绪。",
                "被你点到啦，我现在是满格陪伴模式。",
                "嘿嘿，这一下很轻，我喜欢。",
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
                "想玩什么？我把小爪子准备好了。",
                "我准备好了，今天适合赢一点金币，也适合放松一下。",
                "玩一局可以提提神，顺便给零食攒预算。",
                "如果脑袋有点累，我们就用小游戏换换气。",
                "来吧，我负责可爱，你负责点开始。",
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
                "我先眯一会儿，精力正在一点点回满。",
                "把尾巴盖好，短暂休息一下，你也可以松口气。",
                "休息是为了等会儿更有精神陪你。",
                "今天已经很努力了，允许自己慢一点。",
                "我在旁边安静趴好，你忙完再叫我就行。",
                "充电中，顺便把安稳分你一半。",
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
            speech = listOf(
                "回到中间啦，这里视野最好。",
                "归位完成，我在最显眼的地方陪你。",
                "好，我坐回来了，像一个乖乖的小桌面挂件。",
                "中心位置收到，接下来继续守着你。",
            ).random(),
        )
    }

    fun buyFood(food: FoodItem, free: Boolean = false): Boolean {
        markUserInteraction()
        val current = _uiState.value
        if (!free && current.coins < food.price) {
            _uiState.value = current.copy(
                mood = PetMood.Hungry,
                speech = listOf(
                    "金币不够也没关系，先玩一局赚点零花吧。",
                    "小钱包有点轻，但我们可以慢慢攒。",
                    "这份先记在愿望清单里，等金币够了再买。",
                    "先不着急，${petSubject()}会陪你把金币一点点攒起来。",
                    "差一点点啦，下一局说不定就够了。",
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
            caughtCoins <= 0 -> listOf(
                "没接到金币也没关系，下次我会盯得更准。",
                "这局先热身，失败也算练习到了。",
                "金币躲得太灵活了，我们下次再抓它。",
            ).random()
            current.energy <= 0 -> listOf(
                "有点累，金币奖励先打个折，休息一下会更好。",
                "${petSubject()}电量不足，但还是努力接住了一点点。",
                "这局先省点力气，等恢复后再冲一把。",
            ).random()
            earnedCoins >= 18 -> listOf(
                "接得太准了，金币袋都鼓起来了。",
                "哇，今天是闪闪发光的大丰收。",
                "这手感太稳了，我想给你鼓掌。",
            ).random()
            earnedCoins >= 10 -> listOf(
                "节奏不错，这局的金币很扎实。",
                "接得很好，零食基金又变厚一点。",
                "稳稳拿下，我已经在心里转圈了。",
            ).random()
            else -> listOf(
                "玩得不错，金币收好啦。",
                "小小收获也很好，慢慢攒就会变多。",
                "这局有进账，已经很棒了。",
            ).random()
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
            in 6..10 -> listOf(
                "早安，今天也从摸摸${petSubject()}开始吧。",
                "早上的桌面很安静，我已经待命了。",
                "新的一天启动中，不用一下子做到完美。",
                "早安，我把今日份小小好运放在这里了。",
            )
            in 12..13 -> listOf(
                "午休时间到了，眼睛也该休息一下。",
                "中午适合短暂离开屏幕，回来我还在。",
                "给自己倒杯水吧，我会替你守住桌面。",
                "午间小暂停也很重要，别把自己绷太紧。",
            )
            in 18..21 -> listOf(
                "晚上好，今天的任务进度怎么样？",
                "忙了一天的话，可以先给自己一点缓冲时间。",
                "天色慢慢软下来了，你也可以慢慢放松。",
                "今天已经推进很多啦，剩下的我们一点点来。",
            )
            in 22..23, in 0..5 -> listOf(
                "夜深了，${petSubject()}建议你早点休息。",
                "这个点还在忙吗？我会安静陪着你。",
                "晚一点也没关系，但记得给自己留一点睡意。",
                "如果今天很难，也先到这里吧，明天还能继续。",
            )
            else -> null
        }
        if (timeSpeech != null && Random.nextFloat() < 0.35f) return timeSpeech.random()

        val urgentSpeech = when {
            current.hunger <= 15 -> "肚子已经咕咕叫了，一点点吃的就能救场。"
            current.energy <= 15 -> "电量有点低，我想找个角落趴一会儿，你也可以伸个懒腰。"
            current.happiness <= 25 -> "今天有点安静，摸摸或者玩一局都可以让我开心点。"
            current.happiness >= 90 && current.energy >= 50 -> "状态很好，感觉能陪你把今天的事稳稳收尾。"
            else -> null
        }
        if (urgentSpeech != null && Random.nextFloat() < 0.55f) return urgentSpeech

        return when (mood) {
            PetMood.Sleepy -> listOf(
                "有点困了，想找个角落趴一会儿。",
                "${petSubject()}电量偏低，适合休息一下。",
                "我先眯一会儿，有事轻轻叫我。",
                "桌面灯光刚刚好，适合打一个短盹。",
                "如果你也累了，我们就一起慢慢充电。",
                "困意冒出来了，今天可以不用一直硬撑。",
            )
            PetMood.Chill -> listOf(
                "今天适合安静陪着你。",
                "我在旁边待命，随时可以互动。",
                "现在节奏不错，我会尽量不打扰你。",
                "你忙你的，我负责把桌面气氛守住。",
                "我把自己调成安静陪伴模式啦。",
                "慢慢来，稳定推进也很厉害。",
                "这里很舒服，我就趴在旁边陪你。",
            )
            PetMood.Happy -> listOf(
                "状态不错，随时可以互动。",
                "今天的快乐值看起来很稳定。",
                "心情很好，想继续陪你。",
                "刚才那一会儿很舒服，我现在精神也不错。",
                "快乐值亮起来了，我想把这份轻松也分给你。",
                "你一靠近，桌面都变得热闹了一点。",
                "今天很适合做一点小小的好事。",
            )
            PetMood.Excited -> listOf(
                "我精神很好，想蹦蹦跳跳。",
                "要不要玩一局接金币？",
                "今天活力很满，适合做点有趣的事。",
                "我已经准备好冲刺了，金币在哪里？",
                "小爪子已经预热完毕，随时出发。",
                "感觉今天能把坏心情撞飞一点点。",
                "我现在像一个小弹簧，等你发号施令。",
            )
            PetMood.Hungry -> listOf(
                "肚子空空，来点好吃的吗？",
                "${petSubject()}想吃点东西补补状态。",
                "如果有一份小零食，我会更有精神。",
                "我不挑食，但现在真的有点饿。",
                "能量槽有点低，投喂一下就能重新亮起来。",
                "咕噜咕噜，肚子在发送温柔提醒。",
                "先补一点点也可以，我很好哄的。",
            )
        }.random()
    }

    private fun pickFoodSpeech(food: FoodItem): String {
        return when (food.id) {
            "kibble" -> listOf(
                "普通猫粮也很安心，先垫垫肚子。",
                "咔嚓咔嚓，基础口粮永远可靠。",
                "这一口很踏实，像给今天打了个小补丁。",
            )
            "fish" -> listOf(
                "小鱼干真香，快乐值正在上涨。",
                "这份小鱼干我可以认真记住。",
                "香香脆脆，${petSubject()}的眼睛都亮了一下。",
            )
            "milk" -> listOf(
                "喝完牛奶，精神恢复了一点。",
                "温温的牛奶很适合补充电量。",
                "电量回升中，我又可以多陪你一会儿。",
            )
            "can" -> listOf(
                "罐头满分，今天被照顾得很好。",
                "这一口很扎实，肚子马上安心了。",
                "被好好投喂了，${petSubject()}决定乖乖陪班。",
            )
            "cookie" -> listOf(
                "甜甜的，快乐值正在上升。",
                "点心不错，不过我会记得留点精力。",
                "收到甜甜补给，今天也可以有一点小奖励。",
            )
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
            PetMood.Hungry -> if (hunger <= 10) {
                "你离开得有点久，${petSubject()}肚子已经空空了，不过见到你就安心一点。"
            } else {
                "有点饿了，来点吃的会更有精神。"
            }
            PetMood.Sleepy -> if (energy <= 10) {
                "${petSubject()}电量见底，想先趴一会儿，你也别忘了休息。"
            } else {
                "刚才安静待机了一阵，现在有点困。"
            }
            PetMood.Happy -> "状态还不错，继续陪你待命，也给你留一份好心情。"
            PetMood.Excited -> "精神还很足，随时可以互动，今天也一起加一点小动力。"
            PetMood.Chill -> "我刚检查了一下状态，安静陪你继续工作。"
        }
    }

    private fun petSubject(): String = petName.ifBlank { DEFAULT_PET_NAME }

    private fun defaultSpeech(name: String): String = "你好，我是你的桌宠$name。"

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
    private const val DEFAULT_SPEECH = "你好，我是你的桌宠喵。"
}
