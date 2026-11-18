package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.filter.DlssColorCalibration
import com.example.filter.DlssFilterState
import com.example.filter.DlssPreset
import com.example.ui.theme.AmberRayTrace
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderBright
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeuralLensTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            NeuralLensTheme {
                NeuralLensScreen()
            }
        }
    }
}

@Composable
fun NeuralLensScreen(
    viewModel: FilterViewModel = viewModel()
) {
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    val openOverlaySettings: () -> Unit = {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val accentColor by animateColorAsState(
        targetValue = when {
            !filterState.isEnabled -> CyanAccent
            filterState.preset == DlssPreset.CINEMATIC -> AmberRayTrace
            filterState.preset == DlssPreset.COMPETITIVE -> CyanAccent
            else -> EmeraldAccent
        },
        animationSpec = spring(),
        label = "accent_color"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .drawBehind {
                    // Subtle ambient radial lighting matching obsidian + emerald/cyan hardware vibe
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                (if (filterState.isEnabled) accentColor else CyanAccent).copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.14f),
                            radius = size.width * 0.85f
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.14f),
                        radius = size.width * 0.85f
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                CyanAccent.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.85f),
                            radius = size.width * 0.65f
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.85f),
                        radius = size.width * 0.65f
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeaderBar(
                        isActive = filterState.isEnabled,
                        accentColor = accentColor
                    )

                    AnimatedVisibility(visible = !permissions.allRequiredGranted) {
                        PermissionsCard(
                            hasOverlay = permissions.hasOverlayPermission,
                            hasNotification = permissions.hasNotificationPermission,
                            onGrantOverlay = openOverlaySettings,
                            onGrantNotification = requestNotificationPermission
                        )
                    }

                    MasterPowerSection(
                        state = filterState,
                        hasOverlayPermission = permissions.hasOverlayPermission,
                        accentColor = accentColor,
                        onToggle = {
                            if (permissions.hasOverlayPermission) {
                                viewModel.toggleMasterPower()
                            } else {
                                openOverlaySettings()
                            }
                        }
                    )

                    IntensityControlCard(
                        intensity = filterState.intensity,
                        isEnabled = filterState.isEnabled,
                        accentColor = accentColor,
                        onIntensityChange = viewModel::setIntensity
                    )

                    PresetsSection(
                        selectedPreset = filterState.preset,
                        onSelectPreset = viewModel::selectPreset
                    )

                    LiveCalibrationPreviewCard(
                        state = filterState
                    )

                    FloatingBubbleCard(
                        checked = filterState.showFloatingBubble,
                        hasOverlayPermission = permissions.hasOverlayPermission,
                        onCheckedChange = { enabled ->
                            if (permissions.hasOverlayPermission) {
                                viewModel.setFloatingBubbleEnabled(enabled)
                            } else {
                                openOverlaySettings()
                            }
                        }
                    )

                    HardwareTelemetryFooter()

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(
    isActive: Boolean,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianCard)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(EmeraldAccent.copy(alpha = 0.6f), CyanAccent.copy(alpha = 0.6f))),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = "NeuralLens Emblem",
                    tint = EmeraldAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.header_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        StatusPill(isActive = isActive, accentColor = accentColor)
    }
}

@Composable
private fun StatusPill(
    isActive: Boolean,
    accentColor: Color
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) accentColor.copy(alpha = 0.16f) else ObsidianCard,
        animationSpec = spring(),
        label = "pill_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) accentColor.copy(alpha = 0.7f) else GlassBorder,
        animationSpec = spring(),
        label = "pill_border"
    )

    Surface(
        modifier = Modifier.testTag("status_pill"),
        shape = RoundedCornerShape(50),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) accentColor else TextMuted)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = stringResource(if (isActive) R.string.status_active else R.string.status_standby),
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) accentColor else TextSecondary
            )
        }
    }
}

@Composable
private fun PermissionsCard(
    hasOverlay: Boolean,
    hasNotification: Boolean,
    onGrantOverlay: () -> Unit,
    onGrantNotification: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianCard.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Layers,
                    contentDescription = null,
                    tint = WarningAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Required Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            if (!hasOverlay) {
                PermissionRow(
                    icon = Icons.Outlined.Visibility,
                    title = stringResource(R.string.permission_overlay_title),
                    description = stringResource(R.string.permission_overlay_desc),
                    isGranted = false,
                    testTag = "grant_overlay_button",
                    onGrant = onGrantOverlay
                )
            }

            if (!hasNotification) {
                PermissionRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = stringResource(R.string.permission_notifications_title),
                    description = stringResource(R.string.permission_notifications_desc),
                    isGranted = false,
                    testTag = "grant_notification_button",
                    onGrant = onGrantNotification
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    testTag: String,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface.copy(alpha = 0.8f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CyanAccent,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Button(
            onClick = onGrant,
            enabled = !isGranted,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(testTag),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldAccent,
                contentColor = ObsidianBg
            )
        ) {
            Text(
                text = stringResource(if (isGranted) R.string.btn_granted else R.string.btn_grant),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun MasterPowerSection(
    state: DlssFilterState,
    hasOverlayPermission: Boolean,
    accentColor: Color,
    onToggle: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "power_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val ringProgress by animateFloatAsState(
        targetValue = if (state.isEnabled) state.intensity / 100f else 0f,
        animationSpec = spring(),
        label = "ring_progress"
    )

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = ObsidianCard.copy(alpha = 0.88f),
        border = BorderStroke(
            1.dp,
            if (state.isEnabled) accentColor.copy(alpha = 0.45f) else GlassBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .semantics {
                        contentDescription = if (state.isEnabled) {
                            "Deactivate DLSS 5 filter"
                        } else {
                            "Activate DLSS 5 filter"
                        }
                    }
                    .testTag("master_power_button")
                    .clip(CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = size.minDimension * 0.42f

                    // Outer ambient glow when active
                    if (state.isEnabled) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.36f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = (size.minDimension / 2f) * pulseScale
                            ),
                            center = center,
                            radius = (size.minDimension / 2f) * pulseScale
                        )
                    }

                    // Track circle
                    drawCircle(
                        color = GlassBorderBright.copy(alpha = 0.45f),
                        radius = baseRadius,
                        style = Stroke(width = 6.dp.toPx())
                    )

                    // Active intensity arc
                    if (ringProgress > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(EmeraldAccent, CyanAccent, accentColor, EmeraldAccent)
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f * ringProgress,
                            useCenter = false,
                            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                            topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                            size = androidx.compose.ui.geometry.Size(baseRadius * 2f, baseRadius * 2f)
                        )
                    }
                }

                // Inner tactile button core
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .clip(CircleShape)
                        .background(
                            brush = if (state.isEnabled) {
                                Brush.radialGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.25f),
                                        ObsidianCardElevated
                                    )
                                )
                            } else {
                                Brush.radialGradient(
                                    colors = listOf(
                                        ObsidianCardElevated,
                                        ObsidianSurface
                                    )
                                )
                            }
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (state.isEnabled) accentColor else GlassBorderBright,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (state.isEnabled) accentColor else TextSecondary,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (state.isEnabled) "ON" else "OFF",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.isEnabled) accentColor else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = when {
                    !hasOverlayPermission -> stringResource(R.string.power_permission_needed)
                    state.isEnabled -> stringResource(R.string.power_disable)
                    else -> stringResource(R.string.power_enable)
                },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (state.isEnabled) {
                    "${state.preset.title} • ${state.intensity}% Hardware Grading"
                } else {
                    "Tap power core to engage system-wide visual grading"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun IntensityControlCard(
    intensity: Int,
    isEnabled: Boolean,
    accentColor: Color,
    onIntensityChange: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var lastHapticBucket by remember { mutableIntStateOf(intensity / 5) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = ObsidianCard.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.intensity_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "$intensity%",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = if (intensity == 0) TextSecondary else accentColor,
                    modifier = Modifier.testTag("intensity_value_text")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = intensity.toFloat(),
                onValueChange = { raw ->
                    val rounded = raw.roundToInt().coerceIn(0, 100)
                    val bucket = rounded / 5
                    if (bucket != lastHapticBucket) {
                        lastHapticBucket = bucket
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onIntensityChange(rounded)
                },
                valueRange = 0f..100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("intensity_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = ObsidianSurface
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (intensity == 0) {
                        stringResource(R.string.intensity_zero_hint)
                    } else if (!isEnabled) {
                        "Calibrated for next activation"
                    } else {
                        "HDR Micro-Contrast & Specular Curve"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 45, 75, 100).forEach { step ->
                        val isSelected = intensity == step
                        Surface(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onIntensityChange(step)
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) accentColor.copy(alpha = 0.2f) else ObsidianSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) accentColor else GlassBorder
                            )
                        ) {
                            Text(
                                text = "$step%",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) accentColor else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetsSection(
    selectedPreset: DlssPreset,
    onSelectPreset: (DlssPreset) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.presets_title),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DlssPreset.entries.forEach { preset ->
                val isSelected = preset == selectedPreset
                val presetAccent = when (preset) {
                    DlssPreset.BALANCED -> EmeraldAccent
                    DlssPreset.CINEMATIC -> AmberRayTrace
                    DlssPreset.COMPETITIVE -> CyanAccent
                }
                val presetIcon = when (preset) {
                    DlssPreset.BALANCED -> Icons.Rounded.AutoAwesome
                    DlssPreset.CINEMATIC -> Icons.Rounded.MovieFilter
                    DlssPreset.COMPETITIVE -> Icons.Rounded.FlashOn
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("preset_chip_${preset.id}")
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectPreset(preset)
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) presetAccent.copy(alpha = 0.14f) else ObsidianCard.copy(alpha = 0.85f),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) presetAccent else GlassBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(presetAccent.copy(alpha = if (isSelected) 0.24f else 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = presetIcon,
                                contentDescription = null,
                                tint = presetAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = preset.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Selected preset",
                                tint = presetAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Text(
                                text = preset.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCalibrationPreviewCard(
    state: DlssFilterState
) {
    var comparingOriginal by remember { mutableStateOf(false) }

    val effectiveIntensity = if (comparingOriginal) 0 else state.intensity
    val params = remember(state.preset, effectiveIntensity) {
        DlssColorCalibration.computeParams(state.preset, effectiveIntensity)
    }

    val previewColorFilter = remember(params.colorMatrixValues) {
        ColorFilter.colorMatrix(ColorMatrix(params.colorMatrixValues))
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = ObsidianCard.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Compare,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.preview_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }

                Text(
                    text = if (comparingOriginal) "ORIGINAL" else "${state.preset.shortLabel} $effectiveIntensity%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (comparingOriginal) WarningAmber else EmeraldAccent
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, GlassBorderBright, RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                comparingOriginal = true
                                tryAwaitRelease()
                                comparingOriginal = false
                            }
                        )
                    }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_preview_scene),
                    contentDescription = "DLSS 5 live color grading calibration scene",
                    contentScale = ContentScale.Crop,
                    colorFilter = previewColorFilter,
                    modifier = Modifier.fillMaxSize()
                )

                // Mirror the exact hardware overlay blend layers in Compose Canvas
                if (effectiveIntensity > 0) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color = Color(params.baseTintColor).copy(alpha = params.baseTintAlpha),
                            blendMode = BlendMode.SrcOver
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(params.shadowVignetteColor).copy(alpha = params.shadowVignetteAlpha * 0.35f),
                                    Color(params.shadowVignetteColor).copy(alpha = params.shadowVignetteAlpha)
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.5f),
                                radius = size.maxDimension * 0.72f
                            ),
                            blendMode = BlendMode.Multiply
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(params.specularGlowColor).copy(alpha = params.specularGlowAlpha),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.42f),
                                radius = size.maxDimension * 0.58f
                            ),
                            blendMode = BlendMode.Softlight
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    shape = RoundedCornerShape(50),
                    color = ObsidianBg.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, GlassBorderBright.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = stringResource(R.string.preview_hold_compare),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingBubbleCard(
    checked: Boolean,
    hasOverlayPermission: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianCard.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, if (checked) CyanAccent.copy(alpha = 0.45f) else GlassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyanAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureInPictureAlt,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bubble_toggle_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Text(
                    text = if (hasOverlayPermission) {
                        stringResource(R.string.bubble_toggle_desc)
                    } else {
                        "Grant overlay access above to enable the in-game bubble"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag("floating_bubble_switch"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = EmeraldAccent,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = ObsidianSurface,
                    uncheckedBorderColor = GlassBorderBright
                )
            )
        }
    }
}

@Composable
private fun HardwareTelemetryFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TelemetryChip(
            icon = Icons.Outlined.Bolt,
            label = "HW Layer",
            modifier = Modifier.weight(1f)
        )
        TelemetryChip(
            icon = Icons.Outlined.Layers,
            label = "0% Capture",
            modifier = Modifier.weight(1f)
        )
        TelemetryChip(
            icon = Icons.Outlined.Visibility,
            label = "0 FPS Drop",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TelemetryChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, GlassBorder.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EmeraldAccent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
