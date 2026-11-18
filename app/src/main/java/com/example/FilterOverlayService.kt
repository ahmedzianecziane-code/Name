package com.example

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.filter.DlssColorCalibration
import com.example.filter.DlssFilterState
import com.example.filter.DlssPreset
import com.example.filter.FilterStateRepository
import com.example.filter.OverlayRenderParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Ultra-lightweight hardware-accelerated screen filter overlay service.
 *
 * Uses SurfaceFlinger pass-through window flags (FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE |
 * FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS) with View.LAYER_TYPE_HARDWARE and zero
 * MediaProjection / CPU pixel loops / polling timers.
 */
class FilterOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: DlssOverlayView? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null

    private var bubbleController: FloatingControllerHelper? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateCollectionJob: Job? = null

    private var lastNotificationState: Triple<Boolean, Int, DlssPreset>? = null

    override fun onCreate() {
        super.onCreate()
        FilterStateRepository.ensureInitialized(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        FilterStateRepository.setServiceRunning(true)

        startObservingFilterState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FilterStateRepository.ensureInitialized(applicationContext)

        when (intent?.action) {
            ACTION_TOGGLE_POWER -> {
                val nowEnabled = FilterStateRepository.toggleEnabled()
                if (!nowEnabled && !FilterStateRepository.state.value.showFloatingBubble) {
                    stopServiceGracefully()
                    return START_NOT_STICKY
                }
            }

            ACTION_STOP_SERVICE -> {
                FilterStateRepository.setEnabled(false)
                FilterStateRepository.setFloatingBubble(false)
                stopServiceGracefully()
                return START_NOT_STICKY
            }
        }

        val currentState = FilterStateRepository.state.value
        promoteToForeground(currentState)
        syncOverlayWindows(currentState)

        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayView?.requestLayout()
        overlayView?.invalidate()
        bubbleController?.onScreenMetricsChanged()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        bubbleController?.collapseIfExpanded()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            bubbleController?.collapseIfExpanded()
        }
    }

    override fun onDestroy() {
        stateCollectionJob?.cancel()
        serviceScope.cancel()
        detachOverlayView()
        detachFloatingBubble()
        FilterStateRepository.setServiceRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startObservingFilterState() {
        stateCollectionJob?.cancel()
        stateCollectionJob = serviceScope.launch {
            FilterStateRepository.state.collectLatest { state ->
                syncOverlayWindows(state)
                updateNotificationIfNeeded(state)

                // If neither the filter nor the floating bubble is active, keep service running
                // only when explicitly toggled; otherwise stop cleanly when disabled externally.
                if (!state.isEnabled && !state.showFloatingBubble) {
                    stopServiceGracefully()
                }
            }
        }
    }

    private fun syncOverlayWindows(state: DlssFilterState) {
        val canDraw = canDrawOverlaysCompat()
        if (!canDraw) {
            detachOverlayView()
            detachFloatingBubble()
            return
        }

        // 1. Full-screen pass-through hardware filter layer
        if (state.isEnabled && state.intensity > 0) {
            ensureOverlayAttached()
            overlayView?.applyFilterState(state.preset, state.intensity)
        } else {
            // Keep view detached or GONE so SurfaceFlinger performs 0 compositing passes
            overlayView?.applyFilterState(state.preset, 0)
        }

        // 2. Optional draggable floating controller (must be added after filter overlay)
        if (state.showFloatingBubble) {
            ensureBubbleAttached()
            bubbleController?.updateState(state)
        } else {
            detachFloatingBubble()
        }
    }

    private fun ensureOverlayAttached() {
        if (overlayView != null) return

        val view = DlssOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
            overlayLayoutParams = params
        } catch (_: Exception) {
            overlayView = null
            overlayLayoutParams = null
        }
    }

    private fun detachOverlayView() {
        overlayView?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        overlayLayoutParams = null
    }

    private fun ensureBubbleAttached() {
        if (bubbleController != null) return
        val controller = FloatingControllerHelper(this, windowManager)
        if (controller.attach()) {
            bubbleController = controller
        }
    }

    private fun detachFloatingBubble() {
        bubbleController?.detach()
        bubbleController = null
    }

    private fun stopServiceGracefully() {
        detachOverlayView()
        detachFloatingBubble()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun canDrawOverlaysCompat(): Boolean =
        Settings.canDrawOverlays(this)

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun promoteToForeground(state: DlssFilterState) {
        val notification = buildNotification(state)
        lastNotificationState = Triple(state.isEnabled, state.intensity, state.preset)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotificationIfNeeded(state: DlssFilterState) {
        val key = Triple(state.isEnabled, state.intensity, state.preset)
        if (key == lastNotificationState) return
        lastNotificationState = key
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: DlssFilterState): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, FilterOverlayService::class.java).apply {
            action = ACTION_TOGGLE_POWER
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FilterOverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (state.isEnabled) {
            getString(R.string.notif_title_active, state.preset.title, state.intensity)
        } else {
            getString(R.string.notif_title_standby, state.preset.title)
        }

        val toggleLabel = if (state.isEnabled) {
            getString(R.string.notif_action_toggle_off)
        } else {
            getString(R.string.notif_action_toggle_on)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(getString(R.string.notif_body))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPendingIntent)
            .addAction(0, toggleLabel, togglePendingIntent)
            .addAction(0, getString(R.string.notif_action_stop), stopPendingIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "neurallens_overlay_channel"
        const val NOTIFICATION_ID = 5050
        const val ACTION_SYNC = "com.example.action.SYNC_OVERLAY"
        const val ACTION_TOGGLE_POWER = "com.example.action.TOGGLE_POWER"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"

        fun syncService(context: Context) {
            val state = FilterStateRepository.state.value
            if (!Settings.canDrawOverlays(context)) return

            if (state.isEnabled || state.showFloatingBubble) {
                val intent = Intent(context, FilterOverlayService::class.java).apply {
                    action = ACTION_SYNC
                }
                ContextCompat.startForegroundService(context, intent)
            } else if (state.isServiceRunning) {
                val intent = Intent(context, FilterOverlayService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.startService(intent)
            }
        }
    }
}

/**
 * Hardware-accelerated full-screen color-grading overlay View.
 * Strictly zero object allocations inside onDraw() and zero polling loops.
 */
private class DlssOverlayView(context: Context) : View(context) {

    private var currentPreset: DlssPreset = DlssPreset.BALANCED
    private var currentIntensity: Int = 0
    private var params: OverlayRenderParams = DlssColorCalibration.computeParams(currentPreset, 0)

    // Pre-allocated Paints for 0-GC draws
    private val baseTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val shadowVignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val specularBloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val hardwareLayerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colorMatrix = ColorMatrix()

    private var viewWidth = 0f
    private var viewHeight = 0f

    init {
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
        // Strictly enforce GPU hardware layer compositing
        setLayerType(LAYER_TYPE_HARDWARE, hardwareLayerPaint)
    }

    fun applyFilterState(preset: DlssPreset, intensity: Int) {
        val clamped = intensity.coerceIn(0, 100)
        if (currentPreset == preset && currentIntensity == clamped) return

        currentPreset = preset
        currentIntensity = clamped
        params = DlssColorCalibration.computeParams(preset, clamped)

        if (clamped == 0) {
            visibility = GONE
            return
        }

        if (visibility != VISIBLE) {
            visibility = VISIBLE
        }

        configurePaintsAndShaders()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
        if (currentIntensity > 0) {
            configurePaintsAndShaders()
        }
    }

    private fun configurePaintsAndShaders() {
        val p = params

        // Apply calibrated ColorMatrixColorFilter to the hardware layer paint
        colorMatrix.set(p.colorMatrixValues)
        hardwareLayerPaint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        setLayerPaint(hardwareLayerPaint)

        // 1. Base tone-grade tint using hardware BlendMode / PorterDuff
        baseTintPaint.color = ColorUtils.setAlphaComponent(
            p.baseTintColor,
            (p.baseTintAlpha * 255f).roundToInt().coerceIn(0, 255)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            baseTintPaint.blendMode = BlendMode.SRC_OVER
        } else {
            baseTintPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }

        if (viewWidth <= 0f || viewHeight <= 0f) return

        val cx = viewWidth * 0.5f
        val cy = viewHeight * 0.5f
        val outerRadius = max(viewWidth, viewHeight) * 0.78f

        // 2. Micro-shadow contrast vignette (deepens peripheral HDR contrast without crushing center)
        val shadowEdgeWithAlpha = ColorUtils.setAlphaComponent(
            p.shadowVignetteColor,
            (p.shadowVignetteAlpha * 255f).roundToInt().coerceIn(0, 255)
        )
        val shadowMidWithAlpha = ColorUtils.setAlphaComponent(
            p.shadowVignetteColor,
            (p.shadowVignetteAlpha * 0.30f * 255f).roundToInt().coerceIn(0, 255)
        )
        shadowVignettePaint.shader = RadialGradient(
            cx,
            cy,
            outerRadius,
            intArrayOf(Color.TRANSPARENT, shadowMidWithAlpha, shadowEdgeWithAlpha),
            floatArrayOf(0.32f, 0.72f, 1.0f),
            Shader.TileMode.CLAMP
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            shadowVignettePaint.blendMode = BlendMode.MULTIPLY
        } else {
            shadowVignettePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }

        // 3. Subtle specular highlight clarity bloom
        val specularCenterAlpha = ColorUtils.setAlphaComponent(
            p.specularGlowColor,
            (p.specularGlowAlpha * 255f).roundToInt().coerceIn(0, 255)
        )
        specularBloomPaint.shader = RadialGradient(
            cx,
            cy * 0.85f,
            outerRadius * 0.72f,
            intArrayOf(specularCenterAlpha, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            specularBloomPaint.blendMode = BlendMode.SOFT_LIGHT
        } else {
            specularBloomPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (currentIntensity <= 0 || viewWidth <= 0f || viewHeight <= 0f) return

        // Strictly 3 GPU-cached rect draws, zero heap allocations
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, baseTintPaint)
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, shadowVignettePaint)
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, specularBloomPaint)
    }
}

/**
 * Compact, draggable in-game floating quick-controller bubble.
 * Snaps cleanly to screen edges and expands into an interactive mini HUD.
 */
private class FloatingControllerHelper(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var rootContainer: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var collapsedPill: LinearLayout? = null
    private var collapsedDot: View? = null
    private var collapsedPercentText: TextView? = null

    private var expandedCard: LinearLayout? = null
    private var expandedStatusText: TextView? = null
    private var expandedPowerBtn: TextView? = null
    private var expandedPercentText: TextView? = null
    private var expandedSeekBar: SeekBar? = null
    private var expandedPresetBtn: TextView? = null

    private var isExpanded = false
    private var latestState = FilterStateRepository.state.value
    private var snapAnimator: ValueAnimator? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    fun attach(): Boolean {
        if (rootContainer != null) return true

        val dm = context.resources.displayMetrics
        val container = FrameLayout(context)

        val pill = buildCollapsedPill()
        val card = buildExpandedCard()
        card.visibility = View.GONE

        container.addView(pill)
        container.addView(card)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - dp(68)
            y = (dm.heightPixels * 0.34f).roundToInt()
        }

        attachDragListener(pill, lp, container)

        return try {
            windowManager.addView(container, lp)
            rootContainer = container
            layoutParams = lp
            updateState(latestState)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun detach() {
        snapAnimator?.cancel()
        snapAnimator = null
        rootContainer?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
            }
        }
        rootContainer = null
        layoutParams = null
    }

    fun collapseIfExpanded() {
        if (isExpanded) {
            setExpanded(false)
        }
    }

    fun onScreenMetricsChanged() {
        val lp = layoutParams ?: return
        val container = rootContainer ?: return
        val dm = context.resources.displayMetrics
        val viewW = max(container.width, dp(56))
        val viewH = max(container.height, dp(56))

        lp.x = if (lp.x + viewW / 2 < dm.widthPixels / 2) {
            dp(10)
        } else {
            (dm.widthPixels - viewW - dp(10)).coerceAtLeast(dp(10))
        }
        lp.y = lp.y.coerceIn(dp(40), (dm.heightPixels - viewH - dp(40)).coerceAtLeast(dp(40)))
        try {
            windowManager.updateViewLayout(container, lp)
        } catch (_: Exception) {
        }
    }

    fun updateState(state: DlssFilterState) {
        latestState = state
        val activeColor = if (state.isEnabled) 0xFF00E676.toInt() else 0xFF637087.toInt()

        collapsedDot?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(activeColor)
        }
        collapsedPercentText?.text = if (state.isEnabled) "${state.intensity}%" else "OFF"
        collapsedPercentText?.setTextColor(if (state.isEnabled) 0xFFF3F6FC.toInt() else 0xFF9AA7BD.toInt())

        collapsedPill?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(24).toFloat()
            setColor(0xE6121722.toInt())
            setStroke(dp(1.5f), if (state.isEnabled) 0xB300E676.toInt() else 0xFF28344B.toInt())
        }

        expandedStatusText?.text = if (state.isEnabled) "DLSS 5 • ACTIVE" else "DLSS 5 • STANDBY"
        expandedStatusText?.setTextColor(activeColor)

        expandedPowerBtn?.text = if (state.isEnabled) "ON" else "OFF"
        expandedPowerBtn?.setTextColor(if (state.isEnabled) 0xFF0B0E14.toInt() else 0xFFF3F6FC.toInt())
        expandedPowerBtn?.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(if (state.isEnabled) 0xFF00E676.toInt() else 0xFF28344B.toInt())
        }

        expandedPercentText?.text = "${state.intensity}%"
        if (expandedSeekBar?.progress != state.intensity) {
            expandedSeekBar?.progress = state.intensity
        }
        expandedPresetBtn?.text = "Preset: ${state.preset.shortLabel}"
    }

    private fun buildCollapsedPill(): LinearLayout {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumWidth = dp(56)
            minimumHeight = dp(48)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val dot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply {
                marginEnd = dp(6)
            }
        }
        val label = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            text = "OFF"
        }

        pill.addView(dot)
        pill.addView(label)
        collapsedDot = dot
        collapsedPercentText = label
        collapsedPill = pill
        return pill
    }

    private fun buildExpandedCard(): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = FrameLayout.LayoutParams(dp(236), FrameLayout.LayoutParams.WRAP_CONTENT)
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xF2121722.toInt())
                setStroke(dp(1.2f), 0xFF394B6B.toInt())
            }
        }

        // Top row: Status + Close button
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val statusText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
        }
        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(0xFF9AA7BD.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            setOnClickListener { setExpanded(false) }
        }
        headerRow.addView(statusText)
        headerRow.addView(closeBtn)

        // Second row: Power Toggle + Preset Cycle
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(10))
        }
        val powerBtn = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(68), dp(48)).apply {
                marginEnd = dp(8)
            }
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener {
                FilterStateRepository.toggleEnabled()
            }
        }
        val presetBtn = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0xFF00B0FF.toInt())
            typeface = Typeface.MONOSPACE
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0x2600B0FF)
                setStroke(dp(1f), 0x6600B0FF)
            }
            setOnClickListener {
                FilterStateRepository.cyclePreset()
            }
        }
        actionRow.addView(powerBtn)
        actionRow.addView(presetBtn)

        // Third row: Intensity label + percentage
        val sliderLabelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sliderLabel = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = "Intensity"
            setTextColor(0xFF9AA7BD.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        val percentText = TextView(context).apply {
            setTextColor(0xFF00E676.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
        }
        sliderLabelRow.addView(sliderLabel)
        sliderLabelRow.addView(percentText)

        val seekBar = SeekBar(context).apply {
            max = 100
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        FilterStateRepository.setIntensity(progress)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        card.addView(headerRow)
        card.addView(actionRow)
        card.addView(sliderLabelRow)
        card.addView(seekBar)

        expandedStatusText = statusText
        expandedPowerBtn = powerBtn
        expandedPresetBtn = presetBtn
        expandedPercentText = percentText
        expandedSeekBar = seekBar
        expandedCard = card
        return card
    }

    private fun setExpanded(expand: Boolean) {
        isExpanded = expand
        collapsedPill?.visibility = if (expand) View.GONE else View.VISIBLE
        expandedCard?.visibility = if (expand) View.VISIBLE else View.GONE

        val container = rootContainer ?: return
        val lp = layoutParams ?: return
        val dm = context.resources.displayMetrics
        container.post {
            val width = container.width.coerceAtLeast(dp(56))
            if (lp.x + width > dm.widthPixels - dp(10)) {
                lp.x = (dm.widthPixels - width - dp(10)).coerceAtLeast(dp(10))
                try {
                    windowManager.updateViewLayout(container, lp)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun attachDragListener(
        dragTarget: View,
        lp: WindowManager.LayoutParams,
        container: View
    ) {
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isDragging = false

        dragTarget.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    initialX = lp.x
                    initialY = lp.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val dm = context.resources.displayMetrics
                        lp.x = (initialX + dx.roundToInt()).coerceIn(0, dm.widthPixels - container.width)
                        lp.y = (initialY + dy.roundToInt()).coerceIn(dp(24), dm.heightPixels - container.height - dp(24))
                        try {
                            windowManager.updateViewLayout(container, lp)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        setExpanded(true)
                    } else {
                        snapToNearestEdge(lp, container)
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun snapToNearestEdge(lp: WindowManager.LayoutParams, container: View) {
        val dm = context.resources.displayMetrics
        val viewWidth = max(container.width, dp(56))
        val targetX = if (lp.x + viewWidth / 2 < dm.widthPixels / 2) {
            dp(10)
        } else {
            (dm.widthPixels - viewWidth - dp(10)).coerceAtLeast(dp(10))
        }

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(lp.x, targetX).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                lp.x = anim.animatedValue as Int
                try {
                    windowManager.updateViewLayout(container, lp)
                } catch (_: Exception) {
                }
            }
            start()
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density).roundToInt()
}
