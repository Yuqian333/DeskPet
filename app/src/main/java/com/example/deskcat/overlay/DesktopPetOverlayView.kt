package com.example.deskcat.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.deskcat.MainActivity
import com.example.deskcat.PetMood
import com.example.deskcat.R
import com.example.deskcat.ai.AiAnimState
import com.example.deskcat.pack.PetPack
import com.example.deskcat.pack.PetPackLoader
import com.example.deskcat.pet.PetStateRepository
import com.example.deskcat.settings.PetImageResolver
import com.example.deskcat.settings.PetPreferencesRepository
import com.example.deskcat.settings.PetSettingsUiState
import com.example.deskcat.settings.toAnimParams
import com.example.deskcat.weather.WeatherRepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DesktopPetOverlayView(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()
    private fun dp(value: Int): Int = dp(value.toFloat())

    private val overlayLayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(16)
        y = dp(200)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val petPreferencesRepository = PetPreferencesRepository(context)
    private val rootView = FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    private val contentLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }
    private val speechView = TextView(context).apply {
        visibility = View.GONE
        setTextColor(0xFF111111.toInt())
        textSize = 14f
        maxWidth = dp(260)
        minWidth = dp(180)
        maxLines = 5
        ellipsize = TextUtils.TruncateAt.END
        setLineSpacing(0f, 1.12f)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = roundedDrawable(0xF7FFFFFF.toInt(), 18f, 0x22000000.toInt())
    }
    private val actionRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = View.GONE
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(320), LinearLayout.LayoutParams.WRAP_CONTENT)
        minimumWidth = dp(320)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = roundedDrawable(0xEEFFFFFF.toInt(), 18f, 0x22000000.toInt())
    }
    private val petFrame = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(132), dp(132))
        minimumHeight = dp(132)
        minimumWidth = dp(132)
        clipChildren = false
        clipToPadding = false
        foregroundGravity = Gravity.CENTER
    }
    private val petImage = ImageView(context).apply {
        layoutParams = FrameLayout.LayoutParams(dp(132), dp(132), Gravity.CENTER)
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
        setImageResource(R.drawable.cat5_re)
    }
    private val pawView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
        visibility = View.GONE
    }

    private var attached = false
    private var expanded = false
    private var collapsedToEdge = false
    private var dockOnRight = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0
    private var downY = 0
    private var moved = false
    private var currentMood: PetMood = PetMood.Chill
    private var currentSpeech: String = ""
    private var currentSettings = PetSettingsUiState()
    private var currentPack: PetPack? = null
    private var aiFrames: List<android.graphics.Bitmap>? = null
    private var aiFrameJob: Job? = null
    private var animationJob: Job? = null
    private var autoMoveJob: Job? = null
    private var pawAnimJob: Job? = null
    private var lastInteractionAt = System.currentTimeMillis()

    init {
        setupActionButtons()
        petImage.layoutParams = FrameLayout.LayoutParams(dp(132), dp(132), Gravity.CENTER)
        petFrame.addView(petImage)
        pawView.layoutParams = FrameLayout.LayoutParams(dp(132), dp(132), Gravity.CENTER)
        petFrame.addView(pawView)
        contentLayout.addView(speechView)
        contentLayout.addView(spaceView(dp(8)))
        contentLayout.addView(actionRow)
        contentLayout.addView(spaceView(dp(8)))
        contentLayout.addView(petFrame)
        rootView.addView(contentLayout)

        petFrame.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downX = overlayLayoutParams.x
                    downY = overlayLayoutParams.y
                    moved = false
                    lastInteractionAt = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (abs(deltaX) > 6 || abs(deltaY) > 6) {
                        moved = true
                        autoMoveJob?.cancel()
                    }
                    overlayLayoutParams.x = clampX(downX + deltaX.toInt())
                    overlayLayoutParams.y = clampY(downY + deltaY.toInt())
                    updateOverlayPosition()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        settleToEdgeIfNeeded()
                    } else if (collapsedToEdge) {
                        expandFromEdge()
                    } else {
                        toggleExpanded()
                    }
                    false
                }
                else -> false
            }
        }

        scope.launch {
            PetStateRepository.uiState.collect { uiState ->
                currentMood = uiState.mood
                currentSpeech = uiState.speech
                speechView.text = currentSpeech
                refreshSpeechVisibility()
                if (!collapsedToEdge) {
                    playMoodSequence(currentMood)
                    scheduleAutoMove()
                }
            }
        }

        scope.launch {
            petPreferencesRepository.settingsFlow.collect { settings ->
                currentSettings = settings
                updatePetImageFromSettings()
                if (!collapsedToEdge) {
                    scheduleAutoMove()
                }
            }
        }

        scope.launch {
            AiAnimState.frames.collect { frames ->
                aiFrames = frames
                if (frames != null) {
                    startAiFrameLoop(frames)
                } else {
                    stopAiFrameLoop()
                    updatePetImageFromSettings()
                }
            }
        }
    }

    fun show() {
        if (attached) return
        windowManager.addView(rootView, overlayLayoutParams)
        attached = true
        updatePetImageFromSettings()
        playMoodSequence(currentMood)
        scheduleAutoMove()
    }

    fun remove() {
        animationJob?.cancel()
        pawAnimJob?.cancel()
        aiFrameJob?.cancel()
        scope.cancel()
        if (!attached) return
        windowManager.removeView(rootView)
        attached = false
    }

    fun collapseToEdge() {
        if (!attached) return
        lastInteractionAt = System.currentTimeMillis()
        collapsedToEdge = true
        expanded = false
        dockOnRight = overlayLayoutParams.x > screenWidth() / 2
        overlayLayoutParams.x = if (dockOnRight) {
            max(0, screenWidth() - collapsedWidth())
        } else {
            0
        }
        overlayLayoutParams.y = clampY(overlayLayoutParams.y)
        speechView.visibility = View.GONE
        actionRow.visibility = View.GONE
        autoMoveJob?.cancel()
        animationJob?.cancel()
        stopPawAnimation()
        if (currentSettings.usePetPack || currentSettings.useCustomImage) {
            updatePetImageFromSettings()
        } else {
            petImage.setImageResource(R.drawable.cat9_re)
        }
        updateOverlayPosition()
    }

    private fun toggleExpanded() {
        expanded = !expanded
        collapsedToEdge = false
        refreshExpandedViews()
        if (!expanded) {
            playMoodSequence(currentMood)
        }
        scheduleAutoMove()
    }

    private fun expandFromEdge() {
        collapsedToEdge = false
        expanded = true
        overlayLayoutParams.x = if (dockOnRight) {
            clampX(screenWidth() - expandedWidth() - dp(12))
        } else {
            clampX(dp(12))
        }
        refreshExpandedViews()
        updateOverlayPosition()
        playMoodSequence(currentMood)
    }

    private fun settleToEdgeIfNeeded() {
        val threshold = dp(36)
        val rightLimit = max(0, screenWidth() - collapsedWidth())
        collapsedToEdge = overlayLayoutParams.x <= threshold || overlayLayoutParams.x >= rightLimit - threshold
        if (collapsedToEdge) {
            dockOnRight = overlayLayoutParams.x > screenWidth() / 2
            expanded = false
            overlayLayoutParams.x = if (dockOnRight) rightLimit else 0
            overlayLayoutParams.y = clampY(overlayLayoutParams.y)
            speechView.visibility = View.GONE
            actionRow.visibility = View.GONE
            animationJob?.cancel()
            stopPawAnimation()
            if (currentSettings.usePetPack || currentSettings.useCustomImage) {
                updatePetImageFromSettings()
            } else {
                petImage.setImageResource(R.drawable.cat9_re)
            }
        } else {
            overlayLayoutParams.x = clampX(overlayLayoutParams.x)
            overlayLayoutParams.y = clampY(overlayLayoutParams.y)
            playMoodSequence(currentMood)
        }
        updateOverlayPosition()
    }

    private fun refreshExpandedViews() {
        if (expanded) {
            speechView.measure(unspecifiedMeasureSpec(), unspecifiedMeasureSpec())
            actionRow.measure(unspecifiedMeasureSpec(), unspecifiedMeasureSpec())
        }
        actionRow.visibility = if (expanded) View.VISIBLE else View.GONE
        refreshSpeechVisibility()
        rootView.requestLayout()
    }

    private fun refreshSpeechVisibility() {
        if (expanded) {
            speechView.measure(unspecifiedMeasureSpec(), unspecifiedMeasureSpec())
        }
        speechView.visibility = if (expanded && currentSpeech.isNotBlank()) View.VISIBLE else View.GONE
        rootView.requestLayout()
    }

    private fun playMoodSequence(mood: PetMood) {
        if (collapsedToEdge) {
            if (!currentSettings.usePetPack && !currentSettings.useCustomImage) {
                petImage.setImageResource(R.drawable.cat9_re)
            }
            stopPawAnimation()
            return
        }
        if (currentSettings.usePetPack || currentSettings.useCustomImage) {
            updatePetImageFromSettings()
            schedulePawAnimation(mood)
            return
        }
        animationJob?.cancel()
        animationJob = scope.launch {
            when (mood) {
                PetMood.Chill -> petImage.setImageResource(R.drawable.cat5_re)
                PetMood.Happy -> {
                    petImage.setImageResource(R.drawable.cat5_re)
                    delay(220)
                    petImage.setImageResource(R.drawable.cat6_re)
                    delay(220)
                    petImage.setImageResource(R.drawable.cat7_re)
                }
                PetMood.Sleepy -> {
                    petImage.setImageResource(R.drawable.cat6_re)
                    delay(260)
                    petImage.setImageResource(R.drawable.cat7_re)
                }
                PetMood.Excited -> {
                    petImage.setImageResource(R.drawable.cat8_re)
                    delay(280)
                    petImage.setImageResource(R.drawable.cat9_re)
                    delay(240)
                    petImage.setImageResource(R.drawable.cat5_re)
                }
                PetMood.Hungry -> petImage.setImageResource(R.drawable.cat5_re)
            }
        }
        schedulePawAnimation(mood)
    }

    private fun schedulePawAnimation(mood: PetMood) {
        pawAnimJob?.cancel()
        val pack = currentPack ?: run { pawView.visibility = View.GONE; return }
        val animParams = currentSettings.petStyle.toAnimParams()
        pawAnimJob = scope.launch {
            when (mood) {
                PetMood.Excited -> {
                    val beatDuration = (600 / animParams.speedMultiplier).toLong()
                    while (true) {
                        pawView.setImageBitmap(pack.pawDown)
                        pawView.visibility = View.VISIBLE
                        delay(beatDuration / 2)
                        pawView.setImageBitmap(pack.pawUp)
                        delay(beatDuration / 2)
                    }
                }
                PetMood.Happy -> {
                    val beatDuration = (700 / animParams.speedMultiplier).toLong()
                    pawView.setImageBitmap(pack.pawDown)
                    pawView.visibility = View.VISIBLE
                    delay(beatDuration / 2)
                    pawView.setImageBitmap(pack.pawUp)
                    delay(beatDuration / 2)
                    stopPawAnimation()
                }
                PetMood.Chill -> {
                    delay(3_000)
                    pawView.setImageBitmap(pack.pawDown)
                    pawView.visibility = View.VISIBLE
                    delay(400)
                    pawView.setImageBitmap(pack.pawUp)
                    delay(400)
                    stopPawAnimation()
                }
                else -> stopPawAnimation()
            }
        }
    }

    private fun stopPawAnimation() {
        pawAnimJob?.cancel()
        pawView.visibility = View.GONE
    }

    private fun startAiFrameLoop(frames: List<android.graphics.Bitmap>) {
        aiFrameJob?.cancel()
        if (frames.isEmpty()) return
        aiFrameJob = scope.launch {
            var index = 0
            while (true) {
                petImage.setImageBitmap(frames[index])
                index = (index + 1) % frames.size
                delay(125L)
            }
        }
    }

    private fun stopAiFrameLoop() {
        aiFrameJob?.cancel()
        aiFrameJob = null
    }

    private fun setupActionButtons() {
        actionRow.addView(createActionButton("喂食") { PetStateRepository.feed() })
        actionRow.addView(spaceView(dp(10), 1))
        actionRow.addView(createActionButton("玩耍") { PetStateRepository.play() })
        actionRow.addView(spaceView(dp(10), 1))
        actionRow.addView(createActionButton("天气") {
            scope.launch {
                WeatherRepositoryProvider.get(context).cachedOrRefreshSavedCity(speak = true)
            }
        })
        actionRow.addView(spaceView(dp(10), 1))
        actionRow.addView(createActionButton("打开") {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(intent)
        })
        scheduleAutoMove()
    }

    private fun createActionButton(text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 13f
            minWidth = dp(56)
            setTextColor(0xFF111111.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedDrawable(0xFFF1E7D7.toInt(), 999f, 0)
            setOnClickListener { onClick() }
        }
    }

    private fun updatePetImageFromSettings() {
        val size = dp(132f * currentSettings.sizeScale)
        petFrame.layoutParams = LinearLayout.LayoutParams(size, size)
        petFrame.minimumWidth = size
        petFrame.minimumHeight = size
        petImage.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
        rootView.requestLayout()

        when {
            currentSettings.usePetPack -> {
                scope.launch(Dispatchers.IO) {
                    val pack = PetPackLoader.loadFromDir(context, currentSettings.petPackDir)
                    launch(Dispatchers.Main) {
                        currentPack = pack
                        if (pack != null) {
                            petImage.setImageBitmap(pack.body)
                            applyPawLayout(pack, size)
                            pawView.setImageBitmap(pack.pawUp)
                            pawView.visibility = View.GONE
                        } else {
                            petImage.setImageResource(R.drawable.cat5_re)
                            pawView.visibility = View.GONE
                        }
                    }
                }
            }
            currentSettings.useCustomImage -> {
                currentPack = null
                scope.launch(Dispatchers.IO) {
                    val bitmap = PetImageResolver.decodeAndRemoveBackground(context, currentSettings.imageUri)
                        ?: PetImageResolver.decodeBitmap(context, currentSettings.imageUri)
                    launch(Dispatchers.Main) {
                        if (bitmap != null) {
                            petImage.setImageBitmap(bitmap)
                        } else {
                            petImage.setImageResource(R.drawable.cat5_re)
                        }
                        pawView.visibility = View.GONE
                    }
                }
            }
            else -> {
                currentPack = null
                petImage.setImageResource(R.drawable.cat5_re)
                pawView.visibility = View.GONE
            }
        }
    }

    private fun applyPawLayout(pack: PetPack, containerSize: Int) {
        val cfg = pack.pawConfig
        val pawW = (containerSize * cfg.widthRatio).toInt()
        val pawH = (pawW.toFloat() * pack.pawUp.height / pack.pawUp.width).toInt()
        val offsetX = ((containerSize * cfg.xRatio) - pawW / 2).toInt()
        val offsetY = ((containerSize * cfg.yRatio) - pawH / 2).toInt()
        val lp = FrameLayout.LayoutParams(pawW, pawH)
        lp.leftMargin = offsetX
        lp.topMargin = offsetY
        pawView.layoutParams = lp
    }

    private fun scheduleAutoMove() {
        autoMoveJob?.cancel()
        if (!currentSettings.autoMoveEnabled || expanded || collapsedToEdge || !attached) return
        val animParams = currentSettings.petStyle.toAnimParams()
        val baseDelay = (3_000 / animParams.speedMultiplier).toLong()
        autoMoveJob = scope.launch {
            delay(baseDelay)
            if (!expanded && !collapsedToEdge && attached && System.currentTimeMillis() - lastInteractionAt >= baseDelay) {
                val drift = dp(18f * animParams.bounceMagnitude)
                val direction = if (dockOnRight) -1 else 1
                overlayLayoutParams.x = clampX(overlayLayoutParams.x + drift * direction)
                overlayLayoutParams.y = clampY(overlayLayoutParams.y - dp(8f * animParams.bounceMagnitude))
                updateOverlayPosition()
                lastInteractionAt = System.currentTimeMillis()
                scheduleAutoMove()
            }
        }
    }

    private fun updateOverlayPosition() {
        if (attached) {
            rootView.post {
                windowManager.updateViewLayout(rootView, overlayLayoutParams)
            }
        }
    }

    private fun clampX(value: Int): Int {
        val maxX = max(0, screenWidth() - if (expanded) expandedWidth() else collapsedWidth())
        return min(max(0, value), maxX)
    }

    private fun clampY(value: Int): Int {
        val maxY = max(0, screenHeight() - if (expanded) expandedHeight() else collapsedHeight())
        return min(max(0, value), maxY)
    }

    private fun screenWidth(): Int = context.resources.displayMetrics.widthPixels
    private fun screenHeight(): Int = context.resources.displayMetrics.heightPixels
    private fun collapsedWidth(): Int = dp(132) + dp(16)
    private fun collapsedHeight(): Int = dp(132) + dp(16)
    private fun expandedWidth(): Int = dp(320)
    private fun expandedHeight(): Int = collapsedHeight() + dp(96)

    private fun unspecifiedMeasureSpec(): Int =
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

    private fun roundedDrawable(fillColor: Int, radiusDp: Float, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fillColor)
            if (strokeColor != 0) setStroke(dp(1), strokeColor)
        }
    }

    private fun spaceView(width: Int, height: Int = width): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(width, height)
        }
    }
}
