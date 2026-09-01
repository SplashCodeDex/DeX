package com.dexstudios.dex.window.components
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_check
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_close
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_qr_code
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_warning
import com.dexstudios.dex.core.domain.pairing.PairingEngine
import com.dexstudios.dex.core.domain.pairing.PairingState
import com.dexstudios.dex.ui.modifiers.shake
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import io.github.g0dkar.qrcode.QRCode
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.skia.Image as SkiaImage

sealed interface PinPairingUiState {
    data class PinView(
        val title: String = "Pairing Request",
        val subtitle: String = "",
        val pinCode: String = "48291",
        val enteredDigitCount: Int = 0,
        val remainingSeconds: Int = 60,
        val isError: Boolean = false,
    ) : PinPairingUiState

    data class QrView(val title: String = "Pairing Request", val subtitle: String = "", val qrPayload: String = "", val remainingSeconds: Int = 60) : PinPairingUiState

    data object Success : PinPairingUiState
}

/** Stable animated-view identity: countdown/digit ticks must never re-trigger the flip. */
private enum class PairingContentView {
    Pin,
    Qr,
    Success,
}

private val PinPairingUiState.contentView: PairingContentView
    get() = when (this) {
        is PinPairingUiState.PinView -> PairingContentView.Pin
        is PinPairingUiState.QrView -> PairingContentView.Qr
        PinPairingUiState.Success -> PairingContentView.Success
    }

/** Seconds left at which the countdown turns urgent (error accent + bold label). */
private const val URGENT_COUNTDOWN_SECONDS = 10

/** Copy-feedback reset delay so the chip reads "Copied" briefly, then reverts. */
private const val COPY_FEEDBACK_RESET_MS = 1500L

/**
 * PinPairingPanel Composable:
 * - 5-digit PIN display (44x56dp minimum digit boxes, 32sp bold, border morphing)
 * - Instruction block: phone-first, computer-second (replaces the inline glyph soup)
 * - Copy-code chip with verified clipboard write + "Copied" feedback
 * - Shared countdown: draining bar + label, error accent under [URGENT_COUNTDOWN_SECONDS]
 * - 168x168dp QR code view on a hairline-bordered white card, same countdown treatment
 * - QR <-> PIN horizontal flip transition (+-140dp slide, 250ms)
 * - 15px error shake animation
 * - Action hierarchy: quiet Cancel + strong method toggle for desktop-initiated offers;
 *   centered primary Cancel for phone-initiated offers (no Accept / Accept Once / QR toggle).
 */
@Composable
fun PinPairingPanel(state: PinPairingUiState, onToggleQrPin: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier, statusMessage: String? = null) {
    val isError = (state as? PinPairingUiState.PinView)?.isError == true

    // Newest real snapshot per view kind. The AnimatedContent below keys on the view KIND,
    // so per-second countdown ticks and keystroke digit updates mutate these snapshots and
    // recompose the content IN PLACE instead of replaying the slide transition forever.
    var pinSnapshot by remember { mutableStateOf<PinPairingUiState.PinView?>(null) }
    var qrSnapshot by remember { mutableStateOf<PinPairingUiState.QrView?>(null) }
    when (val s = state) {
        is PinPairingUiState.PinView -> pinSnapshot = s
        is PinPairingUiState.QrView -> qrSnapshot = s
        else -> Unit
    }

    val switchQrToPinAnim: AnimatedContentTransitionScope<PairingContentView>.() -> ContentTransform = {
        if (targetState == PairingContentView.Pin) {
            (
                slideInHorizontally(
                    initialOffsetX = { 140 },
                    animationSpec = DockCardAnimations.PanelSlideOffsetSpec,
                ) + fadeIn(DockCardAnimations.PanelSlideSpec)
                ).togetherWith(
                slideOutHorizontally(
                    targetOffsetX = { -140 },
                    animationSpec = DockCardAnimations.PanelSlideOffsetSpec,
                ) + fadeOut(DockCardAnimations.PanelSlideSpec),
            )
        } else {
            (
                slideInHorizontally(
                    initialOffsetX = { -140 },
                    animationSpec = DockCardAnimations.PanelSlideOffsetSpec,
                ) + fadeIn(DockCardAnimations.PanelSlideSpec)
                ).togetherWith(
                slideOutHorizontally(
                    targetOffsetX = { 140 },
                    animationSpec = DockCardAnimations.PanelSlideOffsetSpec,
                ) + fadeOut(DockCardAnimations.PanelSlideSpec),
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Header: identity line + status line, close affordance pinned top-right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = when (state) {
                        is PinPairingUiState.PinView -> state.title
                        is PinPairingUiState.QrView -> state.title
                        is PinPairingUiState.Success -> "Connected"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when (state) {
                    is PinPairingUiState.PinView -> state.subtitle
                    is PinPairingUiState.QrView -> state.subtitle
                    is PinPairingUiState.Success -> "Device paired successfully"
                }
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .bubbleFluidity()
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.2f), ambientColor = Color.Black.copy(alpha = 0.1f))
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .shinyGlare(shape = RoundedCornerShape(14.dp))
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_fluent_close),
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Center Content Area with QR <-> PIN Flip Transitions
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = state.contentView,
                transitionSpec = switchQrToPinAnim,
                label = "pairingFlip",
            ) { view ->
                when (view) {
                    PairingContentView.Pin -> {
                        val currentState = pinSnapshot
                        if (currentState == null) {
                            Box(Modifier.height(140.dp))
                        } else {
                            PinContentView(
                                pinState = currentState,
                                liveSeconds = (state as? PinPairingUiState.PinView)?.remainingSeconds
                                    ?: currentState.remainingSeconds,
                                statusMessage = statusMessage,
                            )
                        }
                    }

                    PairingContentView.Qr -> {
                        val currentState = qrSnapshot
                        if (currentState == null) {
                            Box(Modifier.height(140.dp))
                        } else {
                            QrContentView(
                                qrState = currentState,
                                statusMessage = statusMessage,
                            )
                        }
                    }

                    PairingContentView.Success -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(36.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_fluent_check),
                                    contentDescription = "Success",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Devices connected",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "File transfers are now enabled",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons Row:
        // - QR View (Desktop-initiated): quiet Cancel + primary "Use PIN Code".
        // - PIN View (Phone or Desktop-initiated): single centered primary "Cancel" button.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state is PinPairingUiState.QrView) {
                    PairingActionButton(
                        label = "Cancel",
                        background = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onCancel,
                    )

                    PairingActionButton(
                        label = "Use PIN Code",
                        background = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        onClick = onToggleQrPin,
                    )
                } else if (state is PinPairingUiState.PinView) {
                    PairingActionButton(
                        label = "Cancel",
                        background = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        onClick = onCancel,
                    )
                }
            }
        }
    }
}

/**
 * PIN phase content: 5-digit PIN display, countdown timer, and instruction message.
 */
@Composable
private fun PinContentView(pinState: PinPairingUiState.PinView, liveSeconds: Int, statusMessage: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 5-Digit PIN Display with 15px error shake
        Row(
            modifier = Modifier
                .shake(pinState.isError)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pinString = pinState.pinCode.padEnd(PairingEngine.PIN_LENGTH, ' ')
            for (i in 0 until PairingEngine.PIN_LENGTH) {
                val digit = if (i < pinString.length && pinString[i] != ' ') pinString[i].toString() else ""
                val isFilled = i < pinState.enteredDigitCount || digit.isNotBlank()
                PinDigitBox(
                    digit = digit,
                    isFilled = isFilled,
                    isError = pinState.isError,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Timer comes before the message
        PairingCountdown(remainingSeconds = liveSeconds)

        Spacer(modifier = Modifier.height(10.dp))

        // Message: "Enter this PIN on Pairing Device" (with "PIN" bold, no glyph/icons)
        Text(
            text = buildAnnotatedString {
                append("Enter this ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                    append("PIN")
                }
                append(" on Pairing Device")
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        PairingStatusMessage(message = statusMessage)
    }
}

/**
 * QR phase content: a larger, hairline-framed code with a single scan instruction, the same
 * countdown treatment as the PIN view, and the inline failure slot for an undeliverable PIN request.
 */
@Composable
private fun QrContentView(qrState: PinPairingUiState.QrView, statusMessage: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 168x168dp QR View Surface on White Rounded Card
        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            StyledQrMatrixCanvas(
                payload = qrState.qrPayload.ifBlank { "dex://pair/local" },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        PairingCountdown(remainingSeconds = qrState.remainingSeconds)

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Open DeX on your phone and scan",
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        PairingStatusMessage(message = statusMessage)
    }
}

/**
 * Shared countdown timer label, switching to the error accent once the offer is about to lapse.
 */
@Composable
private fun PairingCountdown(remainingSeconds: Int, modifier: Modifier = Modifier) {
    val urgent = remainingSeconds in 1..URGENT_COUNTDOWN_SECONDS
    Text(
        text = "Expires in ${remainingSeconds}s",
        fontSize = 12.sp,
        fontWeight = if (urgent) FontWeight.SemiBold else FontWeight.Normal,
        color = if (urgent) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        },
        modifier = modifier,
    )
}

/** Inline failure slot (e.g. a PIN push that never reached the phone); reserves no space when idle. */
@Composable
private fun PairingStatusMessage(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(DockCardAnimations.QuickFadeSpec) + expandVertically(tween(DockCardAnimations.CONTENT_COLLAPSE_MS)),
        exit = fadeOut(DockCardAnimations.QuickFadeSpec) + shrinkVertically(tween(DockCardAnimations.CONTENT_COLLAPSE_MS)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_fluent_warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = message.orEmpty(),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Legacy AnimatedActionBtn parity: MinWidth 80, Padding 16,10, CornerRadius 12,
 * 14sp Medium label dead-centered in the box (WPF Button content alignment).
 */
@Composable
private fun PairingActionButton(label: String, background: Color, contentColor: Color, onClick: () -> Unit, leadingIcon: androidx.compose.ui.graphics.painter.Painter? = null) {
    Box(
        modifier = Modifier
            .bubbleFluidity()
            .defaultMinSize(minWidth = 80.dp)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.2f), ambientColor = Color.Black.copy(alpha = 0.1f))
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .shinyGlare(shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    painter = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.padding(end = 6.dp).size(16.dp),
                )
            }
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Digit box with WPF-parity presentation: filled accent background, transparent idle border,
 * 32sp bold digits (legacy MainWindow.xaml icPinDigits template: AccentBrush fill, BorderThickness 2).
 */
@Composable
internal fun PinDigitBox(digit: String, isFilled: Boolean, isError: Boolean) {
    val popScale by animateFloatAsState(
        targetValue = if (isFilled) 1.0f else 0.95f,
        animationSpec = DockCardAnimations.PressSinkSpec,
        label = "digitPop",
    )

    val borderStroke = when {
        isError -> BorderStroke(2.dp, MaterialTheme.colorScheme.error)

        // Bright ring on the accent fill echoes the legacy shimmer sweep (no gradients).
        isFilled -> BorderStroke(2.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))

        else -> BorderStroke(2.dp, Color.Transparent)
    }

    Box(
        modifier = Modifier
            .width(44.dp)
            .height(56.dp)
            .graphicsLayer {
                scaleX = popScale
                scaleY = popScale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary)
            .border(borderStroke, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digit,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StyledQrMatrixCanvas(payload: String, modifier: Modifier = Modifier) {
    val imageBitmap = remember(payload) {
        val rawBytes = QRCode(payload).render().getBytes()
        SkiaImage.makeFromEncoded(rawBytes).toComposeImageBitmap()
    }
    Image(bitmap = imageBitmap, contentDescription = "QR Code", modifier = modifier)
}

/**
 * PairingEngine integration overload.
 *
 * Legacy WPF parity for pairing initiation flows:
 * - Desktop-initiated (clicking a discovered device): panel opens on the QR view ("Open DeX on your phone and scan")
 *   with the toggle reading "Use PIN Code". Tapping "Use PIN Code" requests a PIN from the phone and flips to digits view
 *   with "Show QR Code" toggle available.
 * - Phone-initiated (Android taps Connect): panel surfaces directly on the PIN digits view with ONLY the "Cancel"
 *   button (legacy WPF -HideAcceptButtons and -ShowQrToggle omitted).
 */
@Composable
fun PinPairingPanel(pairingEngine: PairingEngine, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val engineState by pairingEngine.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Countdown derives from the engine's enforced PIN deadline (the same TTL the expiry
    // sweep honors), so the panel can never advertise time the engine will not accept.
    val deadlineMs = when (val s = engineState) {
        is PairingState.QrPhase -> s.expiresAtMillis
        is PairingState.PinPhase -> s.expiresAtMillis
        else -> 0L
    }
    var remainingSeconds by remember { mutableStateOf(PairingEngine.PIN_TTL_SECONDS) }

    LaunchedEffect(deadlineMs) {
        if (deadlineMs <= 0L) {
            remainingSeconds = PairingEngine.PIN_TTL_SECONDS
        } else {
            while (true) {
                val remaining = ((deadlineMs - getTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                remainingSeconds = remaining
                if (remaining <= 0) break
                delay(500)
            }
        }
    }

    // Inline failure surface for a PIN request the phone could not receive (legacy toasted
    // "Device Not Connected"); cleared as soon as the session state moves again.
    var pinRequestFailed by remember { mutableStateOf(false) }
    LaunchedEffect(engineState) { pinRequestFailed = false }

    fun pairingTitle(alias: String): String = if (alias.isBlank()) "Pairing Request" else "Pairing with $alias"

    val uiState: PinPairingUiState = when (val s = engineState) {
        is PairingState.Idle -> PinPairingUiState.PinView(
            subtitle = "",
            pinCode = "-".repeat(PairingEngine.PIN_LENGTH),
            remainingSeconds = remainingSeconds,
        )

        // QR-first: the desktop-initiated flow lands here before anything else happens.
        is PairingState.QrPhase -> PinPairingUiState.QrView(
            title = pairingTitle(s.alias),
            subtitle = "",
            qrPayload = QrPayloadGenerator.generateLocalPayload(),
            remainingSeconds = remainingSeconds,
        )

        is PairingState.PinPhase -> PinPairingUiState.PinView(
            title = pairingTitle(s.alias),
            subtitle = "",
            pinCode = s.pinCode,
            enteredDigitCount = s.digitCount,
            isError = s.isError,
            remainingSeconds = remainingSeconds,
        )

        is PairingState.Success -> PinPairingUiState.Success

        is PairingState.Error -> PinPairingUiState.PinView(
            subtitle = "Pairing failed",
            isError = true,
            remainingSeconds = remainingSeconds,
        )
    }

    PinPairingPanel(
        state = uiState,
        onToggleQrPin = {
            if (engineState is PairingState.QrPhase) {
                scope.launch {
                    pinRequestFailed = !pairingEngine.requestPinForActiveDevice()
                }
            }
        },
        onCancel = {
            val s = engineState
            if (s is PairingState.PinPhase) {
                pairingEngine.rejectInboundPairing()
            } else {
                pairingEngine.reset()
            }
            onClose()
        },
        modifier = modifier,
        statusMessage = if (pinRequestFailed) {
            "The phone has no active connection. Open DeX on the phone and try again."
        } else {
            null
        },
    )
}
