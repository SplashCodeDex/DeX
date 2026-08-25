package com.dexstudios.dex.window.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.auth.PairingEngine
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.delay

@Composable
fun InboundPairingDialogOverlay() {
    val incomingRequest by AuthState.incomingPairRequest.collectAsState()
    val request = incomingRequest

    AnimatedVisibility(
        visible = request != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        if (request != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        // Consumes clicks so they don't pass through to the background
                    },
                contentAlignment = Alignment.Center,
            ) {
                InboundPairingCard(
                    alias = request.alias,
                    deadlineElapsedMs = request.deadlineElapsedMs,
                    onPinEntered = { enteredPin ->
                        request.deferred.complete(enteredPin)
                    },
                    onCancel = {
                        request.deferred.complete("")
                    },
                )
            }
        }
    }
}

@Composable
private fun InboundPairingCard(alias: String, deadlineElapsedMs: Long, onPinEntered: (String) -> Unit, onCancel: () -> Unit) {
    var pinText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Countdown timer
    var remainingSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(deadlineElapsedMs) {
        while (true) {
            val remaining = ((deadlineElapsedMs - getTimeMillis()) / 1000).coerceAtLeast(0).toInt()
            remainingSeconds = remaining
            if (remaining <= 0) {
                onCancel()
                break
            }
            delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val cardShape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .width(360.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(24.dp),
            )
            .clip(cardShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Pairing Request",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$alias wants to connect.\nEnter the ${PairingEngine.PIN_LENGTH}-digit PIN displayed on the device.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN Entry Input
                BasicTextField(
                    value = pinText,
                    onValueChange = { newValue ->
                        val digitsOnly = newValue.filter { it.isDigit() }
                        if (digitsOnly.length <= PairingEngine.PIN_LENGTH) {
                            pinText = digitsOnly
                            if (digitsOnly.length == PairingEngine.PIN_LENGTH) {
                                onPinEntered(digitsOnly)
                            }
                        }
                    },
                    modifier = Modifier.focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.Transparent,
                    ),
                    cursorBrush = SolidColor(Color.Transparent),
                    decorationBox = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val pinString = pinText.padEnd(PairingEngine.PIN_LENGTH, ' ')
                            for (i in 0 until PairingEngine.PIN_LENGTH) {
                                val digit = if (i < pinString.length && pinString[i] != ' ') pinString[i].toString() else ""
                                PinDigitBox(
                                    digit = digit,
                                    isFilled = digit.isNotBlank(),
                                    isError = false,
                                )
                            }
                        }
                    },
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Expires in ${remainingSeconds}s",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onCancel) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
