package com.example.dex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dex.R
import com.example.dex.ui.components.DeXPanel
import com.example.dex.ui.components.bubbleFluidity

@Composable
fun NetworkErrorDialog(
    error: String,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.error_network_title)
) {
    // We use a Dialog with usePlatformDefaultWidth = false to overlay the whole screen,
    // but note: on Android, Dialog creates a new window which cannot sample the backdrop of the main window.
    // To achieve true spatial glass, we must render this as an in-layout overlay.
    // We use a full-screen Box that consumes clicks.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)) // dim layer
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        DeXPanel(
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.9f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // consume clicks on dialog
                )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Text(error, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DeXTextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dismiss), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun PairingRequestDialog(
    alias: String,
    expectedPin: String,
    onAccept: (String) -> Unit,
    onReject: () -> Unit
) {
    var enteredPin by rememberSaveable { mutableStateOf("") }
    var isError by remember { mutableStateOf(value = false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        DeXPanel(
            shape = RoundedCornerShape(48.dp),
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.9f)
                .bubbleFluidity(targetScale = 0.98f)
        ) {
            // Close Button
            IconButton(
                onClick = onReject,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.padding(top = 48.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.pairing_enter_pin).lowercase(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    stringResource(R.string.pairing_from, alias),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                PinInputField(
                    value = enteredPin,
                    onValueChange = {
                        if (it.length <= 6) {
                            enteredPin = it
                            isError = false
                        }
                    },
                    isError = isError
                )

                if (isError) {
                    Text(
                        stringResource(R.string.pairing_wrong_pin),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                DeXButton(
                    onClick = {
                        if (enteredPin == expectedPin) {
                            onAccept(enteredPin)
                        } else {
                            isError = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(CircleShape),
                    enabled = enteredPin.length == 6,
                    shape = CircleShape
                ) {
                    if (enteredPin.length < 6) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(3) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = if (index == 0) 1f else 0.4f),
                                            CircleShape
                                        )
                                )
                            }
                        }
                    } else {
                        Text(stringResource(R.string.accept).lowercase(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PinInputField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { index ->
                    val char = when {
                        index < value.length -> value[index].toString()
                        else -> ""
                    }
                    val isFocused = index == value.length

                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 64.dp)
                            .background(
                                color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = when {
                                    isError -> MaterialTheme.colorScheme.error
                                    isFocused -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                },
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        )

                        // Cursor simulation
                        if (isFocused) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                                    .width(12.dp)
                                    .height(2.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    )
}

