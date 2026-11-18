package com.example

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.example.filter.DlssFilterState
import com.example.filter.DlssPreset
import com.example.filter.FilterStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionStatus(
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true
) {
    val allRequiredGranted: Boolean
        get() = hasOverlayPermission && hasNotificationPermission
}

class FilterViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    val filterState: StateFlow<DlssFilterState> = FilterStateRepository.state

    private val _permissions = MutableStateFlow(readPermissions())
    val permissions: StateFlow<PermissionStatus> = _permissions.asStateFlow()

    init {
        FilterStateRepository.ensureInitialized(appContext)
        refreshPermissions()
        // If user previously left filter or bubble active and permission is granted, ensure service syncs
        if (readPermissions().hasOverlayPermission) {
            val current = filterState.value
            if (current.isEnabled || current.showFloatingBubble) {
                FilterOverlayService.syncService(appContext)
            }
        }
    }

    fun refreshPermissions() {
        val updated = readPermissions()
        _permissions.value = updated
        if (!updated.hasOverlayPermission && filterState.value.isEnabled) {
            FilterStateRepository.setEnabled(false)
        }
    }

    fun toggleMasterPower() {
        if (!readPermissions().hasOverlayPermission) return
        FilterStateRepository.toggleEnabled()
        FilterOverlayService.syncService(appContext)
    }

    fun setIntensity(intensity: Int) {
        FilterStateRepository.setIntensity(intensity)
        if (readPermissions().hasOverlayPermission && filterState.value.isEnabled) {
            FilterOverlayService.syncService(appContext)
        }
    }

    fun selectPreset(preset: DlssPreset) {
        FilterStateRepository.setPreset(preset)
        if (readPermissions().hasOverlayPermission &&
            (filterState.value.isEnabled || filterState.value.showFloatingBubble)
        ) {
            FilterOverlayService.syncService(appContext)
        }
    }

    fun setFloatingBubbleEnabled(enabled: Boolean) {
        if (enabled && !readPermissions().hasOverlayPermission) return
        FilterStateRepository.setFloatingBubble(enabled)
        FilterOverlayService.syncService(appContext)
    }

    private fun readPermissions(): PermissionStatus {
        val overlayGranted = Settings.canDrawOverlays(appContext)
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return PermissionStatus(
            hasOverlayPermission = overlayGranted,
            hasNotificationPermission = notifGranted
        )
    }
}
