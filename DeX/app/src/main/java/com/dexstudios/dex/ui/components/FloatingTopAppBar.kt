package com.dexstudios.dex.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.dexstudios.dex.R
import com.dexstudios.dex.network.DeviceConfig
import com.dexstudios.dex.network.GoogleProfile
import com.dexstudios.dex.network.GoogleSignInManager
import com.dexstudios.dex.network.WebSocketClientService
import com.dexstudios.dex.ui.components.glass.LiquidGlassIconButton
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun FloatingTopAppBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    showSearch: Boolean = true,
) {
    var isProfileExpanded by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val expandedWidth = screenWidth - 32.dp

    // Dynamic Island bouncy expansion (Avatar)
    val islandWidth by animateDpAsState(
        targetValue = if (isProfileExpanded) expandedWidth else 56.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "islandWidth"
    )
    val islandHeight by animateDpAsState(
        targetValue = if (isProfileExpanded) 140.dp else 56.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "islandHeight"
    )

    // Dynamic Island bouncy expansion (Search)
    val searchWidth by animateDpAsState(
        targetValue = if (isSearchExpanded) expandedWidth else 56.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "searchWidth"
    )
    val searchHeight by animateDpAsState(
        targetValue = if (isSearchExpanded) 72.dp else 56.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "searchHeight"
    )

    // Signed-in Google profile: single combined flow — one recomposition instead of three
    val deviceConfig: DeviceConfig = koinInject()
    val profile by deviceConfig.googleProfileFlow.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    // Celebration flow: stay expanded for 3s after successful sign-in
    var lastProfileEmail by remember { mutableStateOf(profile.email) }
    LaunchedEffect(profile.email) {
        if (lastProfileEmail.isBlank() && profile.email.isNotBlank()) {
            isProfileExpanded = true
            delay(3000)
            isProfileExpanded = false
        }
        lastProfileEmail = profile.email
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            delay(100) // Wait for animation to start
            searchFocusRequester.requestFocus()
        } else {
            keyboardController?.hide()
        }
    }

    val anyExpanded = isProfileExpanded || isSearchExpanded
    val focusAlpha by animateFloatAsState(
        targetValue = if (anyExpanded) 1f else 0f,
        animationSpec = tween(500),
        label = "focusAlpha"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (anyExpanded) 0f else 1f,
        animationSpec = tween(400),
        label = "contentAlpha"
    )
    val avatarAlpha by animateFloatAsState(
        targetValue = if (isSearchExpanded) 0f else 1f,
        animationSpec = tween(400),
        label = "avatarAlpha"
    )
    val searchAlpha by animateFloatAsState(
        targetValue = if (isProfileExpanded) 0f else 1f,
        animationSpec = tween(400),
        label = "searchAlpha"
    )

    Box(modifier = modifier.then(if (anyExpanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth())) {
        // Outside tap dismissal & Focus Blur layer
        if (focusAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0.5f)
                    .graphicsLayer { alpha = focusAlpha }
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            isProfileExpanded = false
                            isSearchExpanded = false
                        }
                    )
            )
        }

        // The Top Bar Layout
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                .height(80.dp)
        ) {
            // Brand Logo (Fades out when expanded) with interactive Bubble Fluidity physics
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .graphicsLayer { alpha = contentAlpha },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.dex_logo),
                    contentDescription = "DeX Logo",
                    modifier = Modifier
                        .height(80.dp)
                        .bubbleFluidity(targetScale = 0.85f, pullFactor = 0.25f),
                    contentScale = ContentScale.Fit
                )
            }

        }

        // Action Buttons Group / Search Island (Right side, overlaps when expanded)
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 16.dp)
                .zIndex(if (isSearchExpanded) 2f else 1f)
                .graphicsLayer { alpha = searchAlpha },
            contentAlignment = Alignment.TopEnd
        ) {
            if (showSearch) {
                LiquidGlassIconButton(
                    onClick = {
                        isSearchExpanded = !isSearchExpanded
                        if (isSearchExpanded) isProfileExpanded = false
                    },
                    width = searchWidth,
                    height = searchHeight,
                    backdrop = backdrop,
                    config = if (isSearchExpanded) LiquidGlassPresets.DynamicIsland else LiquidGlassPresets.IconButton
                ) {
                    AnimatedContent(
                        targetState = isSearchExpanded,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                        },
                        label = "searchContent"
                    ) { expanded ->
                        if (expanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_search),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(searchFocusRequester),
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(Color.White),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                "Search devices...",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 16.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                            }
                        } else {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_search),
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(56.dp))
            }
        }

        // User Avatar / Dynamic Island (overlaps Logo & Search when expanded)
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopStart)
                .padding(top = 8.dp, start = 16.dp)
                .zIndex(if (isProfileExpanded) 2f else 1f)
                .graphicsLayer { alpha = avatarAlpha },
            contentAlignment = Alignment.TopStart
        ) {
            LiquidGlassIconButton(
                onClick = {
                    // Expansion strictly for Google accounts or Guests
                    if (profile.email.isBlank() || profile.picture.isNotBlank()) {
                        isProfileExpanded = !isProfileExpanded
                        if (isProfileExpanded) isSearchExpanded = false
                    }
                },
                width = islandWidth,
                height = islandHeight,
                backdrop = backdrop,
                config = if (isProfileExpanded) LiquidGlassPresets.DynamicIsland else LiquidGlassPresets.IconButton
            ) {
                AnimatedContent(
                    targetState = isProfileExpanded,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    },
                    label = "islandContent"
                ) { expanded ->
                    if (expanded) {
                        ExpandedProfileContent(
                            profile = profile,
                            onSignIn = {
                                val activity = context as? android.app.Activity
                                if (activity != null) {
                                    scope.launch {
                                        val credential = GoogleSignInManager.signIn(activity)
                                        val email = credential?.let { GoogleSignInManager.applyToDeviceConfig(it, deviceConfig) }
                                        if (email != null) {
                                            Toast.makeText(context, resources.getString(R.string.google_signed_in_as, email), Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, resources.getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    } else {
                        CollapsedProfileContent(profile = profile)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsedProfileContent(profile: GoogleProfile) {
    if (profile.picture.isNotBlank()) {
        AsyncImage(
            model = profile.picture,
            contentDescription = "Profile",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
    } else if (profile.email.isNotBlank()) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (profile.name.ifBlank { profile.email }).first().uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    } else {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_account_circle),
            contentDescription = "Profile",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun ExpandedProfileContent(
    profile: GoogleProfile,
    onSignIn: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (profile.email.isBlank()) {
            // Guest State
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Sign in to sync your devices",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                DeXButton(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign in with Google", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            AsyncImage(
                model = profile.picture,
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name.ifBlank { "User" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = profile.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
