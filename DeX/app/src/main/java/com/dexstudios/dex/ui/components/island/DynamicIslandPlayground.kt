package com.dexstudios.dex.ui.components.island

import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpOffset
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.components.glass.LiquidGlassShadowProperties
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.kyant.backdrop.Backdrop
import timber.log.Timber

/**
 * Filter categories for grouping playground configuration settings.
 */
enum class PlaygroundSettingsCategory(val title: String) {
    ALL("All"),
    MOTION("Springs & Motion"),
    FLUIDITY("Tactile Fluidity"),
    PARALLAX("Parallax Scaling"),
    SHADOWS("Shadow Variants"),
    COLORS("Button Colors"),
    BLUR("Content Blur")
}

/**
 * Interactive Live Playground for tuning and testing [DynamicPillButton]
 * physics, springs, and morphing states in real time.
 */
@Composable
fun DynamicIslandPlayground(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null
) {
    var isPillExpanded by remember { mutableStateOf(false) }
    var isCompanionExpanded by remember { mutableStateOf(false) }

    // Live physics tuning states - CodeDeX Tuned Defaults
    var expandDamping by remember { mutableFloatStateOf(0.40f) }
    var collapseDamping by remember { mutableFloatStateOf(0.56f) }
    var stiffness by remember { mutableFloatStateOf(301f) }
    var pressScale by remember { mutableFloatStateOf(1.08f) }
    var pullFactor by remember { mutableFloatStateOf(0.14f) }
    var elasticity by remember { mutableFloatStateOf(0.13f) }
    var scalePressSpeed by remember { mutableFloatStateOf(288f) }
    var scalePressDamping by remember { mutableFloatStateOf(0.35f) }
    var scaleSettleSpeed by remember { mutableFloatStateOf(300f) }
    var showControls by remember { mutableStateOf(true) }
    var isCompanionActive by remember { mutableStateOf(false) }
    var pillExpandedFluidity by remember { mutableStateOf(true) }

    // Live shadow tuning states - Unexpanded Variant (CodeDeX Tuned Defaults)
    var unexpandedShadowRadius by remember { mutableFloatStateOf(23f) }
    var unexpandedShadowOffsetY by remember { mutableFloatStateOf(18f) }
    var unexpandedShadowAlpha by remember { mutableFloatStateOf(0.28f) }
    var unexpandedInnerShadowRadius by remember { mutableFloatStateOf(10f) }
    var unexpandedInnerShadowOffsetY by remember { mutableFloatStateOf(2f) }
    var unexpandedInnerShadowAlpha by remember { mutableFloatStateOf(0.22f) }

    // Live shadow tuning states - Expanded Variant (CodeDeX Tuned Defaults)
    var expandedShadowRadius by remember { mutableFloatStateOf(28f) }
    var expandedShadowOffsetY by remember { mutableFloatStateOf(24f) }
    var expandedShadowAlpha by remember { mutableFloatStateOf(0.20f) }
    var expandedInnerShadowRadius by remember { mutableFloatStateOf(12f) }
    var expandedInnerShadowOffsetY by remember { mutableFloatStateOf(7f) }
    var expandedInnerShadowAlpha by remember { mutableFloatStateOf(0.11f) }

    // Tab for shadow tuning: 0 = Unexpanded Variant, 1 = Expanded Variant
    var shadowVariantTab by remember { mutableStateOf(0) }

    // Live button color tuning states (Resting & Expanded)
    var restingButtonColor by remember { mutableStateOf(DynamicColorVariants.Default.restingColor) }
    var restingButtonAlpha by remember { mutableFloatStateOf(DynamicColorVariants.Default.restingAlpha) }
    var expandedButtonColor by remember { mutableStateOf(DynamicColorVariants.Default.expandedColor) }
    var expandedButtonAlpha by remember { mutableFloatStateOf(DynamicColorVariants.Default.expandedAlpha) }

    // Tab for color tuning: 0 = Resting State Color, 1 = Expanded State Color
    var colorStateTab by remember { mutableStateOf(0) }

    // Live optical content blur tuning states
    var enableContentBlur by remember { mutableStateOf(true) }
    var maxContentBlur by remember { mutableFloatStateOf(8f) }
    var blurRiseDuration by remember { mutableFloatStateOf(75f) }
    var blurOnExpand by remember { mutableStateOf(true) }
    var blurOnCollapse by remember { mutableStateOf(true) }

    // Active configuration category tab for playground controls
    var activeCategory by remember { mutableStateOf(PlaygroundSettingsCategory.ALL) }

    val liveUnexpandedShadow = remember(
        unexpandedShadowRadius,
        unexpandedShadowOffsetY,
        unexpandedShadowAlpha,
        unexpandedInnerShadowRadius,
        unexpandedInnerShadowOffsetY,
        unexpandedInnerShadowAlpha
    ) {
        LiquidGlassShadowProperties(
            radius = unexpandedShadowRadius.dp,
            color = Color.Black,
            alpha = unexpandedShadowAlpha,
            offset = DpOffset(0.dp, unexpandedShadowOffsetY.dp),
            innerRadius = unexpandedInnerShadowRadius.dp,
            innerColor = Color.Black,
            innerAlpha = unexpandedInnerShadowAlpha,
            innerOffset = DpOffset(0.dp, unexpandedInnerShadowOffsetY.dp)
        )
    }

    val liveExpandedShadow = remember(
        expandedShadowRadius,
        expandedShadowOffsetY,
        expandedShadowAlpha,
        expandedInnerShadowRadius,
        expandedInnerShadowOffsetY,
        expandedInnerShadowAlpha
    ) {
        LiquidGlassShadowProperties(
            radius = expandedShadowRadius.dp,
            color = Color.Black,
            alpha = expandedShadowAlpha,
            offset = DpOffset(0.dp, expandedShadowOffsetY.dp),
            innerRadius = expandedInnerShadowRadius.dp,
            innerColor = Color.Black,
            innerAlpha = expandedInnerShadowAlpha,
            innerOffset = DpOffset(0.dp, expandedInnerShadowOffsetY.dp)
        )
    }

    // Grouped live configurations
    val liveMotion = remember(expandDamping, collapseDamping, stiffness) {
        DynamicMotionConfig(
            expandDampingRatio = expandDamping,
            collapseDampingRatio = collapseDamping,
            stiffness = stiffness
        )
    }

    val liveFluidity = remember(
        pressScale,
        pullFactor,
        elasticity,
        scalePressSpeed,
        scalePressDamping,
        scaleSettleSpeed
    ) {
        DynamicFluidityConfig(
            enabled = true,
            pressScale = pressScale,
            pullFactor = pullFactor,
            elasticity = elasticity,
            scalePressSpeed = scalePressSpeed,
            scalePressDamping = scalePressDamping,
            scaleSettleSpeed = scaleSettleSpeed
        )
    }

    val liveShadows = remember(liveUnexpandedShadow, liveExpandedShadow) {
        DynamicShadowVariants(
            unexpanded = liveUnexpandedShadow,
            expanded = liveExpandedShadow
        )
    }

    val liveColors = remember(
        restingButtonColor,
        restingButtonAlpha,
        expandedButtonColor,
        expandedButtonAlpha
    ) {
        DynamicColorVariants(
            restingColor = restingButtonColor,
            restingAlpha = restingButtonAlpha,
            expandedColor = expandedButtonColor,
            expandedAlpha = expandedButtonAlpha
        )
    }

    val liveContentBlur = remember(
        enableContentBlur,
        maxContentBlur,
        blurRiseDuration,
        blurOnExpand,
        blurOnCollapse
    ) {
        DynamicContentBlurConfig(
            enabled = enableContentBlur,
            maxBlur = maxContentBlur.dp,
            blurOnExpand = blurOnExpand,
            blurOnCollapse = blurOnCollapse,
            riseDurationMillis = blurRiseDuration.toInt()
        )
    }

    val livePillExpandedFluidity = remember(pillExpandedFluidity) {
        if (pillExpandedFluidity) ExpandedBubbleFluidityConfig.Inherit else ExpandedBubbleFluidityConfig.Exempt
    }

    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val exportConfigToClipboard: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val exportText = buildString {
            appendLine("// === DeX Dynamic Island Tuned Preference ===")
            appendLine("val myMotion = DynamicMotionConfig(")
            appendLine("    expandDampingRatio = ${"%.2f".format(expandDamping)}f,")
            appendLine("    collapseDampingRatio = ${"%.2f".format(collapseDamping)}f,")
            appendLine("    stiffness = ${stiffness.toInt()}f")
            appendLine(")")
            appendLine()
            appendLine("val myFluidity = DynamicFluidityConfig(")
            appendLine("    enabled = true,")
            appendLine("    pressScale = ${"%.2f".format(pressScale)}f,")
            appendLine("    pullFactor = ${"%.2f".format(pullFactor)}f,")
            appendLine("    elasticity = ${"%.2f".format(elasticity)}f,")
            appendLine("    scalePressSpeed = ${scalePressSpeed.toInt()}f,")
            appendLine("    scalePressDamping = ${"%.2f".format(scalePressDamping)}f,")
            appendLine("    scaleSettleSpeed = ${scaleSettleSpeed.toInt()}f")
            appendLine(")")
            appendLine()
            appendLine("val myShadows = DynamicShadowVariants(")
            appendLine("    unexpanded = LiquidGlassShadowProperties(")
            appendLine("        radius = ${unexpandedShadowRadius.toInt()}.dp,")
            appendLine("        alpha = ${"%.2f".format(unexpandedShadowAlpha)}f,")
            appendLine("        offset = DpOffset(0.dp, ${unexpandedShadowOffsetY.toInt()}.dp),")
            appendLine("        innerRadius = ${unexpandedInnerShadowRadius.toInt()}.dp,")
            appendLine("        innerAlpha = ${"%.2f".format(unexpandedInnerShadowAlpha)}f,")
            appendLine("        innerOffset = DpOffset(0.dp, ${unexpandedInnerShadowOffsetY.toInt()}.dp)")
            appendLine("    ),")
            appendLine("    expanded = LiquidGlassShadowProperties(")
            appendLine("        radius = ${expandedShadowRadius.toInt()}.dp,")
            appendLine("        alpha = ${"%.2f".format(expandedShadowAlpha)}f,")
            appendLine("        offset = DpOffset(0.dp, ${expandedShadowOffsetY.toInt()}.dp),")
            appendLine("        innerRadius = ${expandedInnerShadowRadius.toInt()}.dp,")
            appendLine("        innerAlpha = ${"%.2f".format(expandedInnerShadowAlpha)}f,")
            appendLine("        innerOffset = DpOffset(0.dp, ${expandedInnerShadowOffsetY.toInt()}.dp)")
            appendLine("    )")
            appendLine(")")
            appendLine()
            appendLine("val myColors = DynamicColorVariants(")
            appendLine("    restingColor = Color(${restingButtonColor.toHexCode()}),")
            appendLine("    restingAlpha = ${"%.2f".format(restingButtonAlpha)}f,")
            appendLine("    expandedColor = Color(${expandedButtonColor.toHexCode()}),")
            appendLine("    expandedAlpha = ${"%.2f".format(expandedButtonAlpha)}f")
            appendLine(")")
            appendLine()
            appendLine("val myContentBlur = DynamicContentBlurConfig(")
            appendLine("    enabled = $enableContentBlur,")
            appendLine("    maxBlur = ${maxContentBlur.toInt()}.dp,")
            appendLine("    blurOnExpand = $blurOnExpand,")
            appendLine("    blurOnCollapse = $blurOnCollapse,")
            appendLine("    riseDurationMillis = ${blurRiseDuration.toInt()}")
            appendLine(")")
            appendLine()
            appendLine("// Expanded Fluidity Exemptions:")
            appendLine("// pillExpandedFluidity = $pillExpandedFluidity")
        }

        coroutineScope.launch {
            clipboard.setClipEntry(ClipData.newPlainText("Dynamic Island Config", exportText).toClipEntry())
        }
        Timber.i("[DynamicIslandPlayground] Exported Config:\n$exportText")
        Toast.makeText(context, "Tuned config copied to clipboard! Paste it into chat.", Toast.LENGTH_LONG).show()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.White
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Screen-level background scrim for outside-tap dismissal (sits behind stage)
            if (isPillExpanded || isCompanionExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isPillExpanded = false
                                isCompanionExpanded = false
                            }
                        )
                )
            }

            // Main Stage: The isolated Dynamic Button
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 90.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DynamicPillButton(
                        isExpanded = isPillExpanded,
                        onExpandedChange = { expand ->
                            if (expand) isCompanionExpanded = false
                            isPillExpanded = expand
                        },
                        expandedWidth = 220.dp,
                        motion = liveMotion,
                        fluidity = liveFluidity,
                        expandedFluidityConfig = livePillExpandedFluidity,
                        shadows = liveShadows,
                        colors = liveColors,
                        contentBlur = liveContentBlur,
                        backdrop = backdrop,
                        collapsedContent = {
                            // Collapsed Mock Pill: Circle with Action Icon & status
                            val isRestingDark = restingButtonColor.luminance() < 0.5f || restingButtonAlpha < 0.25f
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isRestingDark) MaterialTheme.colorScheme.primaryContainer else Color.Black.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbols.CheckCircle,
                                        contentDescription = "Pill Action",
                                        tint = if (isRestingDark) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFF1E1E24),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        expandedContent = { collapse ->
                            // Expanded Mock Pill: Strict stadium capsule (never rectangular)
                            val isPillDark = expandedButtonColor.luminance() < 0.5f || expandedButtonAlpha < 0.25f
                            val pillTitleColor = if (isPillDark) Color.White else Color(0xFF1E1E24)
                            val pillSubtitleColor = if (isPillDark) Color.White.copy(alpha = 0.7f) else Color(0xFF4A4A52)
                            val pillIconBg = if (isPillDark) MaterialTheme.colorScheme.primaryContainer else Color.Black.copy(alpha = 0.08f)
                            val pillIconTint = if (isPillDark) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFF1E1E24)
                            val pillCloseBg = if (isPillDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.08f)
                            val pillCloseTint = if (isPillDark) Color.White else Color(0xFF1E1E24)

                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(pillIconBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = MaterialSymbols.CheckCircle,
                                            contentDescription = null,
                                            tint = pillIconTint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Dynamic Pill",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = pillTitleColor,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "Stadium Capsule Morph",
                                            fontSize = 11.sp,
                                            color = pillSubtitleColor,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(pillCloseBg)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = collapse
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbols.Close,
                                        contentDescription = "Close",
                                        tint = pillCloseTint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )

                    // Companion Button: Fully expandable pill (mirrors the left pill's kinematics)
                    DynamicPillButton(
                        isExpanded = isCompanionExpanded,
                        onExpandedChange = { expand ->
                            if (expand) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isPillExpanded = false
                                // Preserve the companion's original toggle identity: tap flips the action state
                                isCompanionActive = !isCompanionActive
                            }
                            isCompanionExpanded = expand
                        },
                        expandedWidth = 200.dp,
                        motion = liveMotion,
                        fluidity = liveFluidity,
                        expandedFluidityConfig = livePillExpandedFluidity,
                        shadows = liveShadows,
                        colors = liveColors,
                        contentBlur = liveContentBlur,
                        backdrop = backdrop,
                        collapsedContent = {
                            // Collapsed Mock Pill: Circle with toggleable Action Icon & status
                            val isRestingDark = restingButtonColor.luminance() < 0.5f || restingButtonAlpha < 0.25f
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isCompanionActive -> MaterialTheme.colorScheme.primary
                                                isRestingDark -> MaterialTheme.colorScheme.primaryContainer
                                                else -> Color.Black.copy(alpha = 0.08f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbols.Check,
                                        contentDescription = "Companion Action",
                                        tint = when {
                                            isCompanionActive -> MaterialTheme.colorScheme.onPrimary
                                            isRestingDark -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> Color(0xFF1E1E24)
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        expandedContent = { collapse ->
                            // Expanded Mock Pill: Strict stadium capsule (never rectangular)
                            val isPillDark = expandedButtonColor.luminance() < 0.5f || expandedButtonAlpha < 0.25f
                            val pillTitleColor = if (isPillDark) Color.White else Color(0xFF1E1E24)
                            val pillSubtitleColor = if (isPillDark) Color.White.copy(alpha = 0.7f) else Color(0xFF4A4A52)
                            val pillIconBg = if (isPillDark) MaterialTheme.colorScheme.primaryContainer else Color.Black.copy(alpha = 0.08f)
                            val pillIconTint = if (isPillDark) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFF1E1E24)
                            val pillActionBg = when {
                                isCompanionActive -> MaterialTheme.colorScheme.primary
                                isPillDark -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                else -> Color.Black.copy(alpha = 0.08f)
                            }
                            val pillActionTint = if (isCompanionActive) MaterialTheme.colorScheme.onPrimary else pillIconTint
                            val pillCloseBg = if (isPillDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.08f)
                            val pillCloseTint = if (isPillDark) Color.White else Color(0xFF1E1E24)

                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(pillIconBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = MaterialSymbols.Check,
                                            contentDescription = null,
                                            tint = pillIconTint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Companion Pill",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = pillTitleColor,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "Expandable Action",
                                            fontSize = 11.sp,
                                            color = pillSubtitleColor,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Toggle Action: preserves the original companion toggle identity
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(pillActionBg)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                isCompanionActive = !isCompanionActive
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbols.Check,
                                        contentDescription = "Toggle Action",
                                        tint = pillActionTint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(pillCloseBg)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = collapse
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbols.Close,
                                        contentDescription = "Close",
                                        tint = pillCloseTint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }

            // Top Physics Tuning Control HUD
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
                    .zIndex(150f)
            ) {
                // Header Bar: Title and Exit Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dynamic Island Lab",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Button(
                        onClick = onDismiss,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Exit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Bar: Export, Config Toggler & Reset Default
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = exportConfigToClipboard,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Export Config", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { showControls = !showControls },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF1F5F9),
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = if (showControls) "Hide Sliders" else "Tweak Sliders",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            expandDamping = 0.40f
                            collapseDamping = 0.56f
                            stiffness = 301f
                            pressScale = 1.08f
                            pullFactor = 0.14f
                            elasticity = 0.13f
                            scalePressSpeed = 288f
                            scalePressDamping = 0.35f
                            scaleSettleSpeed = 300f
                            unexpandedShadowRadius = 23f
                            unexpandedShadowOffsetY = 18f
                            unexpandedShadowAlpha = 0.28f
                            unexpandedInnerShadowRadius = 10f
                            unexpandedInnerShadowOffsetY = 2f
                            unexpandedInnerShadowAlpha = 0.22f
                            expandedShadowRadius = 28f
                            expandedShadowOffsetY = 24f
                            expandedShadowAlpha = 0.20f
                            expandedInnerShadowRadius = 12f
                            expandedInnerShadowOffsetY = 7f
                            expandedInnerShadowAlpha = 0.11f
                            restingButtonColor = DynamicColorVariants.Default.restingColor
                            restingButtonAlpha = DynamicColorVariants.Default.restingAlpha
                            expandedButtonColor = DynamicColorVariants.Default.expandedColor
                            expandedButtonAlpha = DynamicColorVariants.Default.expandedAlpha
                            enableContentBlur = true
                            maxContentBlur = 8f
                            blurRiseDuration = 75f
                            blurOnExpand = true
                            blurOnCollapse = true
                            pillExpandedFluidity = true
                            Toast.makeText(context, "Applied CodeDeX Tuned Defaults", Toast.LENGTH_SHORT).show()
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF1F5F9),
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "Default",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                    }
                }

                // Collapsible Tuning Controls Card
                if (showControls) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            // Configuration Group Selector Chips
                            Text("CONFIGURATION GROUPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PlaygroundSettingsCategory.values().forEach { category ->
                                    FilterChip(
                                        selected = activeCategory == category,
                                        onClick = { activeCategory = category },
                                        label = { Text(category.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }

                            // 1. SPRINGS & MOTION SECTION
                            if (activeCategory == PlaygroundSettingsCategory.ALL || activeCategory == PlaygroundSettingsCategory.MOTION) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("SPRINGS & MOTION (OVERSHOOT & DAMPING)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PresetChip("Default", expandDamping == 0.40f && collapseDamping == 0.56f && stiffness == 301f) {
                                        expandDamping = 0.40f
                                        collapseDamping = 0.56f
                                        stiffness = 301f
                                    }
                                    PresetChip("Snappy", expandDamping == 0.75f && collapseDamping == 0.85f && stiffness == 600f) {
                                        expandDamping = 0.75f
                                        collapseDamping = 0.85f
                                        stiffness = 600f
                                    }
                                    PresetChip("Bouncy", expandDamping == 0.50f && collapseDamping == 0.65f && stiffness == 350f) {
                                        expandDamping = 0.50f
                                        collapseDamping = 0.65f
                                        stiffness = 350f
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                // Expanding Damping Ratio Slider
                                Text("Expanding Damping: ${"%.2f".format(expandDamping)} (Opening bounce)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Slider(
                                    value = expandDamping,
                                    onValueChange = { expandDamping = it },
                                    valueRange = 0.35f..1.0f
                                )

                                // Collapsing Damping Ratio Slider
                                Text("Collapsing Damping: ${"%.2f".format(collapseDamping)} (Closing settle)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Slider(
                                    value = collapseDamping,
                                    onValueChange = { collapseDamping = it },
                                    valueRange = 0.35f..1.0f
                                )

                                // Stiffness Slider
                                Text("Stiffness: ${stiffness.toInt()} (Response speed)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Slider(
                                    value = stiffness,
                                    onValueChange = { stiffness = it },
                                    valueRange = 150f..900f
                                )
                            }

                            // 2. TACTILE FLUIDITY SECTION
                            if (activeCategory == PlaygroundSettingsCategory.ALL || activeCategory == PlaygroundSettingsCategory.FLUIDITY) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("TACTILE BUBBLE FLUIDITY (PRESS, PULL & ELASTICITY)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))

                                // Expanded State Fluidity Exemption Control
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PresetChip("Expanded Stadium: ${if (pillExpandedFluidity) "ACTIVE" else "EXEMPT"}", selected = pillExpandedFluidity) {
                                        pillExpandedFluidity = !pillExpandedFluidity
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Press Squish / Swell Scale Slider
                                val scaleMode = when {
                                    pressScale < 0.99f -> " (Squish)"
                                    pressScale > 1.01f -> " (Swell / Expand)"
                                    else -> " (Neutral)"
                                }
                                Text("Press Scale: ${"%.2f".format(pressScale)}$scaleMode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Slider(
                                    value = pressScale,
                                    onValueChange = { pressScale = it },
                                    valueRange = 0.70f..1.50f
                                )

                                // Pull Factor Slider
                                Text("Pull Factor: ${"%.2f".format(pullFactor)} (Finger drag tracking)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Slider(
                                    value = pullFactor,
                                    onValueChange = { pullFactor = it },
                                    valueRange = 0.00f..0.45f
                                )

                                // Jelly Elasticity Slider
                                val elasticityMode = when {
                                    elasticity < 0.20f -> " (Firm / Rigid)"
                                    elasticity < 0.70f -> " (Bouncy Jelly)"
                                    else -> " (Hyper Jelly Wobble)"
                                }
                                Text("Jelly Elasticity: ${"%.2f".format(elasticity)}$elasticityMode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Slider(
                                    value = elasticity,
                                    onValueChange = { elasticity = it },
                                    valueRange = 0.00f..1.00f
                                )
                            }

                            // 3. PARALLAX SCALING DYNAMICS SECTION
                            if (activeCategory == PlaygroundSettingsCategory.ALL || activeCategory == PlaygroundSettingsCategory.PARALLAX) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("PARALLAX SCALING (SPEED, SMOOTHNESS & SETTLE)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))

                                // Press Parallax Scale Speed Slider
                                val pressSpeedLabel = when {
                                    scalePressSpeed < 400f -> " (Soft / Relaxed)"
                                    scalePressSpeed > 900f -> " (Snappy / Instant)"
                                    else -> " (Natural Apple Speed)"
                                }
                                Text("Press Parallax Speed: ${scalePressSpeed.toInt()}f$pressSpeedLabel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Slider(
                                    value = scalePressSpeed,
                                    onValueChange = { scalePressSpeed = it },
                                    valueRange = 150f..1500f
                                )

                                // Press Parallax Smoothness / Damping Slider
                                val dampingLabel = when {
                                    scalePressDamping < 0.60f -> " (Bouncy Elastic)"
                                    scalePressDamping > 0.85f -> " (Critically Silky)"
                                    else -> " (Natural Organic)"
                                }
                                Text("Press Parallax Smoothness: ${"%.2f".format(scalePressDamping)}$dampingLabel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Slider(
                                    value = scalePressDamping,
                                    onValueChange = { scalePressDamping = it },
                                    valueRange = 0.35f..1.00f
                                )

                                // Scale Settle Speed Slider
                                val settleSpeedLabel = when {
                                    scaleSettleSpeed < 800f -> " (Soft / Slower Settle)"
                                    scaleSettleSpeed > 2200f -> " (Snappy / Instant Settle)"
                                    else -> " (Natural Apple Speed)"
                                }
                                Text("Scale Settle Speed: ${scaleSettleSpeed.toInt()}f$settleSpeedLabel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Slider(
                                    value = scaleSettleSpeed,
                                    onValueChange = { scaleSettleSpeed = it },
                                    valueRange = 300f..3000f
                                )
                            }

                            // 4. SHADOW PROPERTIES SECTION
                            if (activeCategory == PlaygroundSettingsCategory.ALL || activeCategory == PlaygroundSettingsCategory.SHADOWS) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    "SHADOW PROPERTIES (TWO VARIANTS)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                // Variant Selector Tabs: Unexpanded vs Expanded
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = shadowVariantTab == 0,
                                        onClick = { shadowVariantTab = 0 },
                                        label = { Text("Unexpanded Variant", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                    FilterChip(
                                        selected = shadowVariantTab == 1,
                                        onClick = { shadowVariantTab = 1 },
                                        label = { Text("Expanded Variant", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Preset chips for shadows
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PresetChip("Default", selected = unexpandedShadowRadius == 23f && expandedShadowRadius == 28f) {
                                        unexpandedShadowRadius = 23f
                                        unexpandedShadowOffsetY = 18f
                                        unexpandedShadowAlpha = 0.28f
                                        unexpandedInnerShadowRadius = 10f
                                        unexpandedInnerShadowOffsetY = 2f
                                        unexpandedInnerShadowAlpha = 0.22f

                                        expandedShadowRadius = 28f
                                        expandedShadowOffsetY = 24f
                                        expandedShadowAlpha = 0.20f
                                        expandedInnerShadowRadius = 12f
                                        expandedInnerShadowOffsetY = 7f
                                        expandedInnerShadowAlpha = 0.11f
                                    }
                                    PresetChip("Search Deep", selected = false) {
                                        expandedShadowRadius = 33f
                                        expandedShadowOffsetY = 36f
                                        expandedShadowAlpha = 0.25f
                                        expandedInnerShadowRadius = 6f
                                        expandedInnerShadowOffsetY = 23f
                                        expandedInnerShadowAlpha = 0.08f
                                    }
                                    PresetChip("No Shadows", selected = false) {
                                        if (shadowVariantTab == 0) {
                                            unexpandedShadowRadius = 0f
                                            unexpandedShadowAlpha = 0f
                                            unexpandedInnerShadowRadius = 0f
                                            unexpandedInnerShadowAlpha = 0f
                                        } else {
                                            expandedShadowRadius = 0f
                                            expandedShadowAlpha = 0f
                                            expandedInnerShadowRadius = 0f
                                            expandedInnerShadowAlpha = 0f
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (shadowVariantTab == 0) {
                                    // 1. Unexpanded Variant Tuning (All Shadow Parameters)
                                    Text("Outer Drop Shadow Radius: ${unexpandedShadowRadius.toInt()}dp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(
                                        value = unexpandedShadowRadius,
                                        onValueChange = { unexpandedShadowRadius = it },
                                        valueRange = 0f..60f
                                    )

                                    Text("Outer Drop Shadow Offset Y: ${unexpandedShadowOffsetY.toInt()}dp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(
                                        value = unexpandedShadowOffsetY,
                                        onValueChange = { unexpandedShadowOffsetY = it },
                                        valueRange = 0f..60f
                                    )

                                    Text("Outer Drop Shadow Alpha: ${"%.2f".format(unexpandedShadowAlpha)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(
                                        value = unexpandedShadowAlpha,
                                        onValueChange = { unexpandedShadowAlpha = it },
                                        valueRange = 0f..1f
                                    )

                                    Text("Inner Shadow Radius: ${unexpandedInnerShadowRadius.toInt()}dp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(
                                        value = unexpandedInnerShadowRadius,
                                        onValueChange = { unexpandedInnerShadowRadius = it },
                                        valueRange = 0f..40f
                                    )

                                    Text("Inner Shadow Offset Y: ${unexpandedInnerShadowOffsetY.toInt()}dp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(
                                        value = unexpandedInnerShadowOffsetY,
                                        onValueChange = { unexpandedInnerShadowOffsetY = it },
                                        valueRange = 0f..40f
                                    )

                                    Text("Inner Shadow Alpha: ${"%.2f".format(unexpandedInnerShadowAlpha)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(
                                        value = unexpandedInnerShadowAlpha,
                                        onValueChange = { unexpandedInnerShadowAlpha = it },
                                        valueRange = 0f..1f
                                    )
                                } else {
                                    // 2. Expanded Variant Tuning (All Shadow Parameters)
                                    Text("Outer Drop Shadow Radius: ${expandedShadowRadius.toInt()}dp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(
                                        value = expandedShadowRadius,
                                        onValueChange = { expandedShadowRadius = it },
                                        valueRange = 0f..80f
                                    )

                                    Text("Outer Drop Shadow Offset Y: ${expandedShadowOffsetY.toInt()}dp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(
                                        value = expandedShadowOffsetY,
                                        onValueChange = { expandedShadowOffsetY = it },
                                        valueRange = 0f..60f
                                    )

                                    Text("Outer Drop Shadow Alpha: ${"%.2f".format(expandedShadowAlpha)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(
                                        value = expandedShadowAlpha,
                                        onValueChange = { expandedShadowAlpha = it },
                                        valueRange = 0f..1f
                                    )

                                    Text("Inner Shadow Radius: ${expandedInnerShadowRadius.toInt()}dp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(
                                        value = expandedInnerShadowRadius,
                                        onValueChange = { expandedInnerShadowRadius = it },
                                        valueRange = 0f..40f
                                    )

                                    Text("Inner Shadow Offset Y: ${expandedInnerShadowOffsetY.toInt()}dp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(
                                        value = expandedInnerShadowOffsetY,
                                        onValueChange = { expandedInnerShadowOffsetY = it },
                                        valueRange = 0f..50f
                                    )

                                    Text("Inner Shadow Alpha: ${"%.2f".format(expandedInnerShadowAlpha)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(
                                        value = expandedInnerShadowAlpha,
                                        onValueChange = { expandedInnerShadowAlpha = it },
                                        valueRange = 0f..1f
                                    )
                                }
                            }

                            // 5. BUTTON COLOR OPTIONS (RESTING & EXPANDED)
                            if (activeCategory == PlaygroundSettingsCategory.ALL || activeCategory == PlaygroundSettingsCategory.COLORS) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    "BUTTON COLOR OPTIONS (SURFACE TINT & OPACITY)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                // State Selector Tabs: Resting vs Expanded
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = colorStateTab == 0,
                                        onClick = { colorStateTab = 0 },
                                        label = { Text("Resting State Color", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                    FilterChip(
                                        selected = colorStateTab == 1,
                                        onClick = { colorStateTab = 1 },
                                        label = { Text("Expanded State Color", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                val currentColor = if (colorStateTab == 0) restingButtonColor else expandedButtonColor
                                val currentAlpha = if (colorStateTab == 0) restingButtonAlpha else expandedButtonAlpha

                                Text(
                                    text = if (colorStateTab == 0) "Resting Color Palette" else "Expanded Color Palette",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                val swatches = listOf(
                                    "Obsidian" to Color(0xFF121214),
                                    "Midnight" to Color(0xFF0F172A),
                                    "Deep Slate" to Color(0xFF1E293B),
                                    "Charcoal" to Color(0xFF262626),
                                    "Pure White" to Color(0xFFFFFFFF),
                                    "Indigo" to Color(0xFF312E81),
                                    "Violet" to Color(0xFF4C1D95),
                                    "Emerald" to Color(0xFF064E3B),
                                    "Crimson" to Color(0xFF881337),
                                    "Amber" to Color(0xFF78350F),
                                    "Ocean" to Color(0xFF0C4A6E)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    swatches.forEach { (name, color) ->
                                        val isSelected = currentColor == color
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(
                                                    BorderStroke(
                                                        if (isSelected) 2.5.dp else 1.dp,
                                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                                                    ),
                                                    CircleShape
                                                )
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    if (colorStateTab == 0) {
                                                        restingButtonColor = color
                                                    } else {
                                                        expandedButtonColor = color
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = MaterialSymbols.Check,
                                                    contentDescription = name,
                                                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "${if (colorStateTab == 0) "Resting" else "Expanded"} Opacity (Alpha): ${"%.2f".format(currentAlpha)} (${(currentAlpha * 100).toInt()}%)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Slider(
                                    value = currentAlpha,
                                    onValueChange = {
                                        if (colorStateTab == 0) {
                                            restingButtonAlpha = it
                                        } else {
                                            expandedButtonAlpha = it
                                        }
                                    },
                                    valueRange = 0.0f..1.0f
                                )
                            }

                            // 6. TRANSIENT OPTICAL CONTENT BLUR SECTION
                            if (activeCategory == PlaygroundSettingsCategory.ALL || activeCategory == PlaygroundSettingsCategory.BLUR) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    "TRANSIENT OPTICAL CONTENT BLUR (EXPANSION & COLLAPSE)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                // Presets for Content Blur
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PresetChip("Default (8dp)", enableContentBlur && maxContentBlur == 8f && blurRiseDuration == 75f) {
                                        enableContentBlur = true
                                        maxContentBlur = 8f
                                        blurRiseDuration = 75f
                                        blurOnExpand = true
                                        blurOnCollapse = true
                                    }
                                    PresetChip("Subtle (5dp)", enableContentBlur && maxContentBlur == 5f && blurRiseDuration == 60f) {
                                        enableContentBlur = true
                                        maxContentBlur = 5f
                                        blurRiseDuration = 60f
                                        blurOnExpand = true
                                        blurOnCollapse = true
                                    }
                                    PresetChip("Cinematic (14dp)", enableContentBlur && maxContentBlur == 14f && blurRiseDuration == 90f) {
                                        enableContentBlur = true
                                        maxContentBlur = 14f
                                        blurRiseDuration = 90f
                                        blurOnExpand = true
                                        blurOnCollapse = true
                                    }
                                    PresetChip("Off", !enableContentBlur || maxContentBlur == 0f) {
                                        enableContentBlur = false
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Master Toggle & Activation Phase Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PresetChip("Master Blur: ${if (enableContentBlur) "ACTIVE" else "DISABLED"}", selected = enableContentBlur) {
                                        enableContentBlur = !enableContentBlur
                                    }
                                    PresetChip("On Expand: ${if (blurOnExpand) "YES" else "NO"}", selected = blurOnExpand) {
                                        blurOnExpand = !blurOnExpand
                                    }
                                    PresetChip("On Collapse: ${if (blurOnCollapse) "YES" else "NO"}", selected = blurOnCollapse) {
                                        blurOnCollapse = !blurOnCollapse
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Peak Blur Radius Slider
                                Text("Peak Blur Radius: ${maxContentBlur.toInt()}dp (Transient Dissipation)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Slider(
                                    value = maxContentBlur,
                                    onValueChange = { maxContentBlur = it },
                                    valueRange = 0f..25f,
                                    enabled = enableContentBlur
                                )

                                // Blur Rise Duration Slider
                                Text("Blur Peak Acceleration: ${blurRiseDuration.toInt()}ms (Time to Peak)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Slider(
                                    value = blurRiseDuration,
                                    onValueChange = { blurRiseDuration = it },
                                    valueRange = 30f..200f,
                                    enabled = enableContentBlur
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun StatusPill(label: String, color: Color, selected: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) color else Color.Transparent, CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/**
 * Formats a [Color] into an 8-character hex literal (e.g. 0xFF121214) for clean export.
 */
private fun Color.toHexCode(): String {
    val a = (alpha * 255 + 0.5f).toInt().coerceIn(0, 255)
    val r = (red * 255 + 0.5f).toInt().coerceIn(0, 255)
    val g = (green * 255 + 0.5f).toInt().coerceIn(0, 255)
    val b = (blue * 255 + 0.5f).toInt().coerceIn(0, 255)
    return "0x%02X%02X%02X%02X".format(a, r, g, b)
}
