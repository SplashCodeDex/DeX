package com.dexstudios.dex.window.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.auth.PairingEngine
import com.dexstudios.dex.auth.PairingState
import com.dexstudios.dex.core.designsystem.icons.MaterialSymbols
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.ui.modifiers.shake
import kotlinx.coroutines.delay
import io.github.g0dkar.qrcode.QRCode
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.math.roundToInt

sealed interface PinPairingUiState {
    data class PinView(
        val title: String = "Pairing Request",
        val subtitle: String = "Enter This Pin On Your Phone 📱 or PC 💻",
        val pinCode: String = "482910",
        val enteredDigitCount: Int = 0,
        val remainingSeconds: Int = 60,
        val isError: Boolean = false,
        val statusText: String = "Enter This Pin On Your Phone 📱 or PC 💻"
    ) : PinPairingUiState

    data class QrView(
        val title: String = "Pairing Request",
        val subtitle: String = "Scan with DeX Mobile",
        val qrPayload: String = "",
        val remainingSeconds: Int = 60
    ) : PinPairingUiState

    data object Success : PinPairingUiState
}

/**
 * PinPairingPanel Composable:
 * - 6-digit PIN display (44x56dp minimum digit boxes, 32sp bold, border morphing)
 * - 140x140dp QR code view with 60s countdown timer
 * - QR <-> PIN horizontal flip transition (+-140dp slide, 250ms)
 * - 15px error shake animation
 * - Action buttons: Cancel, QR/PIN toggle, Accept, Accept Once (Guest)
 */
@Composable
fun PinPairingPanel(
    state: PinPairingUiState,
    onToggleQrPin: () -> Unit,
    onAccept: () -> Unit,
    onAcceptOnce: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isError = (state as? PinPairingUiState.PinView)?.isError == true

    val switchQrToPinAnim: AnimatedContentTransitionScope<PinPairingUiState>.() -> ContentTransform = {
        if (targetState is PinPairingUiState.PinView) {
            (slideInHorizontally(
                initialOffsetX = { 140 },
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(250))).togetherWith(
                slideOutHorizontally(
                    targetOffsetX = { -140 },
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(250))
            )
        } else {
            (slideInHorizontally(
                initialOffsetX = { -140 },
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(250))).togetherWith(
                slideOutHorizontally(
                    targetOffsetX = { 140 },
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(250))
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = when (state) {
                        is PinPairingUiState.PinView -> state.title
                        is PinPairingUiState.QrView -> state.title
                        is PinPairingUiState.Success -> "Connected!"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (state) {
                        is PinPairingUiState.PinView -> state.subtitle
                        is PinPairingUiState.QrView -> state.subtitle
                        is PinPairingUiState.Success -> "Device paired successfully"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MaterialSymbols.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Center Content Area with QR <-> PIN Flip Transitions
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = switchQrToPinAnim,
                label = "pairingFlip"
            ) { currentState ->
                when (currentState) {
                    is PinPairingUiState.PinView -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // 6-Digit PIN Display with 15px error shake
                            Row(
                                modifier = Modifier
                                    .shake(currentState.isError)
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val pinString = currentState.pinCode.padEnd(6, ' ')
                                for (i in 0 until 6) {
                                    val digit = if (i < pinString.length && pinString[i] != ' ') pinString[i].toString() else ""
                                    val isFilled = i < currentState.enteredDigitCount || digit.isNotBlank()
                                    PinDigitBox(
                                        digit = digit,
                                        isFilled = isFilled,
                                        isError = currentState.isError
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Expires in ${currentState.remainingSeconds}s",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    is PinPairingUiState.QrView -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // 140x140dp QR View Surface on White Rounded Card
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                StyledQrMatrixCanvas(
                                    payload = currentState.qrPayload.ifBlank { "dex://pair/local" },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Expires in ${currentState.remainingSeconds}s",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    is PinPairingUiState.Success -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Check,
                                    contentDescription = "Success",
                                    tint = Color.Black,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Pairing Complete",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons Row: Cancel, QR/PIN Toggle, Accept, Accept Once (Guest)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel Button (80dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // QR / PIN Toggle Button
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onToggleQrPin() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (state is PinPairingUiState.QrView) MaterialSymbols.Pin else MaterialSymbols.QrCode,
                        contentDescription = "Toggle QR/PIN",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 4.dp).size(16.dp)
                    )
                    Text(
                        text = if (state is PinPairingUiState.QrView) "PIN CODE" else "QR CODE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Accept Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onAccept() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Accept",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            // Accept Once (Guest) Button
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .clickable { onAcceptOnce() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Accept Once",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 44x56dp minimum digit box with 32sp bold text and border morphing.
 */
@Composable
internal fun PinDigitBox(
    digit: String,
    isFilled: Boolean,
    isError: Boolean
) {
    val popScale by animateFloatAsState(
        targetValue = if (isFilled) 1.0f else 0.95f,
        animationSpec = tween(100, easing = FastOutSlowInEasing),
        label = "digitPop"
    )

    val borderStroke = when {
        isError -> BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        isFilled -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(borderStroke, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StyledQrMatrixCanvas(
    payload: String,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(payload) {
        val rawBytes = QRCode(payload).render().getBytes()
        SkiaImage.makeFromEncoded(rawBytes).toComposeImageBitmap()
    }
    Image(bitmap = imageBitmap, contentDescription = "QR Code", modifier = modifier)
}

/**
 * PairingEngine integration overload for backwards and automated orchestration compatibility.
 */
@Composable
fun PinPairingPanel(
    pairingEngine: PairingEngine,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val engineState by pairingEngine.state.collectAsState()
    var isQrMode by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(60) }

    // 60s countdown timer
    LaunchedEffect(Unit) {
        remainingSeconds = 60
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
        if (remainingSeconds == 0) {
            pairingEngine.reset()
            onClose()
        }
    }

    val uiState: PinPairingUiState = when (val s = engineState) {
        is PairingState.Idle -> PinPairingUiState.PinView(
            pinCode = "------",
            remainingSeconds = remainingSeconds
        )
        is PairingState.QrPhase -> if (isQrMode) {
            PinPairingUiState.QrView(
                qrPayload = "dex://${s.ip}:${s.fingerprint}",
                remainingSeconds = remainingSeconds
            )
        } else {
            PinPairingUiState.PinView(
                pinCode = "------",
                remainingSeconds = remainingSeconds
            )
        }
        is PairingState.PinPhase -> if (isQrMode) {
            PinPairingUiState.QrView(
                qrPayload = "dex://${s.ip}:${s.fingerprint}",
                remainingSeconds = remainingSeconds
            )
        } else {
            PinPairingUiState.PinView(
                pinCode = s.pinCode,
                enteredDigitCount = s.digitCount,
                isError = s.isError,
                remainingSeconds = remainingSeconds
            )
        }
        is PairingState.Success -> PinPairingUiState.Success
        is PairingState.Error -> PinPairingUiState.PinView(
            isError = true,
            remainingSeconds = remainingSeconds
        )
    }

    PinPairingPanel(
        state = uiState,
        onToggleQrPin = { isQrMode = !isQrMode },
        onAccept = {
            pairingEngine.reset()
            onClose()
        },
        onAcceptOnce = {
            pairingEngine.reset()
            onClose()
        },
        onCancel = {
            pairingEngine.reset()
            onClose()
        },
        modifier = modifier
    )
}
