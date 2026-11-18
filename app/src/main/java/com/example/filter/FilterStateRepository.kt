package com.example.filter

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Thread-safe, zero-overhead shared state repository synchronizing MainActivity,
 * FilterViewModel, FilterOverlayService, and the in-game floating controller.
 */
object FilterStateRepository {
    private const val PREFS_NAME = "neurallens_filter_prefs"
    private const val KEY_ENABLED = "filter_enabled"
    private const val KEY_INTENSITY = "filter_intensity"
    private const val KEY_PRESET = "filter_preset"
    private const val KEY_FLOATING_BUBBLE = "floating_bubble"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(DlssFilterState())
    val state: StateFlow<DlssFilterState> = _state.asStateFlow()

    fun ensureInitialized(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = sp
            val restored = DlssFilterState(
                isEnabled = sp.getBoolean(KEY_ENABLED, false),
                intensity = sp.getInt(KEY_INTENSITY, 65).coerceIn(0, 100),
                preset = DlssPreset.fromId(sp.getString(KEY_PRESET, DlssPreset.BALANCED.id)),
                showFloatingBubble = sp.getBoolean(KEY_FLOATING_BUBBLE, false),
                isServiceRunning = _state.value.isServiceRunning
            )
            _state.value = restored
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(isEnabled = enabled) }
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
    }

    fun toggleEnabled(): Boolean {
        val next = !_state.value.isEnabled
        setEnabled(next)
        return next
    }

    fun setIntensity(intensity: Int) {
        val clamped = intensity.coerceIn(0, 100)
        _state.update { it.copy(intensity = clamped) }
        prefs?.edit()?.putInt(KEY_INTENSITY, clamped)?.apply()
    }

    fun setPreset(preset: DlssPreset) {
        _state.update { it.copy(preset = preset) }
        prefs?.edit()?.putString(KEY_PRESET, preset.id)?.apply()
    }

    fun cyclePreset() {
        setPreset(_state.value.preset.next())
    }

    fun setFloatingBubble(enabled: Boolean) {
        _state.update { it.copy(showFloatingBubble = enabled) }
        prefs?.edit()?.putBoolean(KEY_FLOATING_BUBBLE, enabled)?.apply()
    }

    fun setServiceRunning(running: Boolean) {
        _state.update { it.copy(isServiceRunning = running) }
    }
}
