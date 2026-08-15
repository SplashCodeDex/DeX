package com.dexstudios.dex.ui.components

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dexstudios.dex.R
import com.dexstudios.dex.network.ClientEngine
import com.dexstudios.dex.network.DeviceConfig
import com.dexstudios.dex.network.DownloadState
import com.dexstudios.dex.network.GoogleProfile
import com.dexstudios.dex.network.GoogleSignInManager
import com.dexstudios.dex.network.TcpDownloadService
import com.dexstudios.dex.network.UploadState
import com.dexstudios.dex.network.WebSocketClientService
import com.dexstudios.dex.ui.components.glass.LiquidGlassIconButton
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.dexstudios.dex.ui.state.TopAppBarState
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.koin.compose.koinInject

private var isProfileExpanded: Boolean
    get() = TopAppBarState.isProfileExpanded
    set(value) { TopAppBarState.isProfileExpanded = value }

private var isSearchExpanded: Boolean
    get() = TopAppBarState.isSearchExpanded
    set(value) { TopAppBarState.isSearchExpanded = value }

private enum class IslandContentState {
    IDLE,
    EXPANDED_PROFILE,
    COLLAPSED_TRANSFER,
    EXPANDED_TRANSFER
}

@Composable
fun FloatingTopAppBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    showSearch: Boolean = true,
) {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidth = with(density) { containerSize.width.toDp() }
    val expandedWidth = screenWidth - 32.dp

    // Signed-in Google profile: single combined flow — one recomposition instead of three
    val deviceConfig: DeviceConfig = koinInject()
    val clientEngine: ClientEngine = koinInject()

    val profile by deviceConfig.googleProfileFlow.collectAsState()
    val downloadState by TcpDownloadService.downloadState.collectAsStateWithLifecycle()
    val uploadState by clientEngine.uploadState.collectAsStateWithLifecycle()

    val isDownloading = downloadState.isDownloading
    val isUploading = uploadState.isUploading
    val isTransferActive = isDownloading || isUploading || downloadState.isSuccess || uploadState.isSuccess

    val islandState by remember {
        derivedStateOf {
            val transferActive = downloadState.isDownloading || uploadState.isUploading ||
                    downloadState.isSuccess || uploadState.isSuccess
            when {
                isProfileExpanded && transferActive -> IslandContentState.EXPANDED_TRANSFER
                isProfileExpanded -> IslandContentState.EXPANDED_PROFILE
                transferActive -> IslandContentState.COLLAPSED_TRANSFER
                else -> IslandContentState.IDLE
            }
        }
    }

    // Dynamic Island bouncy expansion (Avatar/Profile/Transfer)
    val islandWidth by animateDpAsState(
        targetValue = if (islandState == IslandContentState.EXPANDED_TRANSFER || islandState == IslandContentState.EXPANDED_PROFILE) expandedWidth else 56.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "islandWidth"
    )
    val islandHeight by animateDpAsState(
        targetValue = when (islandState) {
            IslandContentState.EXPANDED_TRANSFER -> 180.dp
            IslandContentState.EXPANDED_PROFILE -> 140.dp
            else -> 56.dp
        },
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
            delay(3.seconds)
            isProfileExpanded = false
        }
        lastProfileEmail = profile.email
    }

    // Auto-expand transfer details on start
    var wasTransferActive by remember { mutableStateOf(false) }
    LaunchedEffect(isTransferActive) {
        if (isTransferActive && !wasTransferActive) {
            isProfileExpanded = true
            delay(5.seconds)
            isProfileExpanded = false
        }
        wasTransferActive = isTransferActive
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            delay(100.milliseconds) // Wait for animation to start
            searchFocusRequester.requestFocus()
        } else {
            keyboardController?.hide()
            TopAppBarState.searchQuery = "" // Clear search when collapsed
        }
    }

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

    Box(modifier = modifier.fillMaxWidth()) {

        // The Top Bar Layout
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                .height(80.dp)
        ) {
            // Logo moved to MainScreen.kt to scroll behind content
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
                                    imageVector = MaterialSymbols.Search,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = TopAppBarState.searchQuery,
                                    onValueChange = { TopAppBarState.searchQuery = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(searchFocusRequester),
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(Color.White),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                                    decorationBox = { innerTextField ->
                                        if (TopAppBarState.searchQuery.isEmpty()) {
                                            Text(
                                                "Search devices...",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 18.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                if (TopAppBarState.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { TopAppBarState.searchQuery = "" },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = MaterialSymbols.Close,
                                            contentDescription = "Clear",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Icon(
                                imageVector = MaterialSymbols.Search,
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
            Box(contentAlignment = Alignment.Center) {
                if (isTransferActive) {
                    TransferProgressRing(
                        progress = if (isDownloading) downloadState.progress else uploadState.aggregateProgress,
                        modifier = Modifier.size(64.dp)
                    )
                }
                LiquidGlassIconButton(
                    onClick = {
                        // Expansion strictly for Google accounts, Guests, or active transfers
                        if (profile.email.isBlank() || profile.picture.isNotBlank() || isTransferActive) {
                            isProfileExpanded = !isProfileExpanded
                            if (isProfileExpanded) isSearchExpanded = false
                        }
                    },
                    width = islandWidth,
                    height = islandHeight,
                    backdrop = backdrop,
                    config = if (islandState != IslandContentState.IDLE && islandState != IslandContentState.COLLAPSED_TRANSFER) LiquidGlassPresets.DynamicIsland else LiquidGlassPresets.IconButton
                ) {
                    AnimatedContent(
                        targetState = islandState,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                        },
                        label = "islandContent"
                    )
 { state ->
                        when (state) {
                            IslandContentState.EXPANDED_TRANSFER -> {
                                ExpandedTransferContent(
                                    downloadState = downloadState,
                                    uploadState = uploadState,
                                    onCancel = {
                                        if (isDownloading) TcpDownloadService.cancelDownload(context)
                                        else clientEngine.cancelUpload(context)
                                        isProfileExpanded = false
                                    }
                                )
                            }
                            IslandContentState.EXPANDED_PROFILE -> {
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
                            }
                            IslandContentState.COLLAPSED_TRANSFER -> {
                                val currentPeerPicture = if (downloadState.isDownloading || downloadState.isSuccess) downloadState.peerPicture else uploadState.peerPicture
                                TransferIcon(
                                    isDownloading = isDownloading,
                                    isUploading = isUploading,
                                    modifier = Modifier.size(32.dp),
                                    peerPicture = currentPeerPicture
                                )
                            }
                            IslandContentState.IDLE -> {
                                CollapsedProfileContent(profile = profile)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferIcon(
    isDownloading: Boolean,
    isUploading: Boolean,
    modifier: Modifier = Modifier,
    peerPicture: String? = null
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val infiniteTransition = rememberInfiniteTransition(label = "transferRotation")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        // Spinning Frame (Dotted Circle)
        Icon(
            imageVector = MaterialSymbols.ArrowUploadCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation }
        )

        val translationY = remember { Animatable(0f) }

        LaunchedEffect(isUploading, isDownloading) {
            while (true) {
                if (isUploading) {
                    translationY.snapTo(10f)
                    translationY.animateTo(
                        targetValue = -10f,
                        animationSpec = tween(1500, easing = LinearOutSlowInEasing)
                    )
                } else if (isDownloading) {
                    translationY.snapTo(-10f)
                    translationY.animateTo(
                        targetValue = 10f,
                        animationSpec = tween(1500, easing = LinearOutSlowInEasing)
                    )
                } else {
                    translationY.animateTo(0f)
                    break
                }
            }
        }

        Icon(
            imageVector = if (isUploading) MaterialSymbols.ArrowUploadArrow else MaterialSymbols.ArrowDownloadArrow,
            contentDescription = if (isUploading) "Uploading" else "Downloading",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.translationY = translationY.value.dp.toPx()
                }
        )

        // Peer Avatar Overlay (AirDrop Style)
        if (!peerPicture.isNullOrBlank()) {
            AsyncImage(
                model = peerPicture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.Black, CircleShape)
            )
        }
    }
}

@Composable
private fun ExpandedTransferContent(
    downloadState: DownloadState,
    uploadState: UploadState,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    // Pick which state to display (priority to the one that is active or just succeeded)
    val isDownloadActive = downloadState.isDownloading || downloadState.isSuccess

    // Determine if we should show download info or upload info
    val showDownload = isDownloadActive

    val isSuccess = if (showDownload) downloadState.isSuccess else uploadState.isSuccess
    val progress = if (showDownload) downloadState.progress else uploadState.aggregateProgress
    val peerName = if (showDownload) downloadState.peerName else uploadState.peerName
    val peerPicture = if (showDownload) downloadState.peerPicture else uploadState.peerPicture
    val totalFiles = if (showDownload) downloadState.totalFiles else uploadState.totalFiles
    val fileName = if (showDownload) downloadState.fileName else uploadState.fileName
    val speedBps = if (showDownload) downloadState.speedBps else uploadState.speedBps

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Section: Icon, Info, and Thumbnail
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Left: Transfer Icon with Avatar Overlay
            TransferIcon(
                isDownloading = showDownload && !isSuccess,
                isUploading = !showDownload && !isSuccess,
                peerPicture = peerPicture,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Center: Title and Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (showDownload) "Incoming Transfer" else "Outgoing Transfer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = buildString {
                        if (!peerName.isNullOrBlank()) append("$peerName ")
                        append(if (showDownload) "is sharing " else "is receiving ")
                        append(if (totalFiles > 1) "$totalFiles files" else "a file")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right: File Thumbnail (Rounded Card)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (showDownload) MaterialSymbols.FileDownload else MaterialSymbols.FileUpload,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Middle Section: Status Text (Progress & Speed)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSuccess) "Transfer Complete" else "${(progress * 100).toInt()}% • ${formatSpeed(speedBps)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (isSuccess) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )

            if (!isSuccess) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom Section: Morphing Action Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (isSuccess) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f))
                .clickable {
                    if (isSuccess) {
                        try {
                            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) {
                            Toast.makeText(context, "Cannot open downloads", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        onCancel()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSuccess) "Open Folder" else "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = if (isSuccess) MaterialTheme.colorScheme.onPrimary else Color.White,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun TransferProgressRing(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "transferRingProgress"
    )
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        // Track
        drawCircle(
            color = primaryColor.copy(alpha = 0.1f),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
        // Progress
        drawArc(
            color = primaryColor,
            startAngle = -90f,
            sweepAngle = 360f * animatedProgress,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun formatSpeed(bps: Long): String = when {
    bps >= 1024L * 1024 * 1024 -> java.util.Locale.ROOT.let { String.format(it, "%.1f GB/s", bps / (1024f * 1024 * 1024)) }
    bps >= 1024L * 1024 -> java.util.Locale.ROOT.let { String.format(it, "%.1f MB/s", bps / (1024f * 1024)) }
    bps >= 1024L -> java.util.Locale.ROOT.let { String.format(it, "%.0f KB/s", bps / 1024f) }
    else -> "$bps B/s"
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
            imageVector = MaterialSymbols.AccountCircle,
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
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                DeXButton(
                    onClick = onSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Google,
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Sign in with Google",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun ExpandedTransferIslandPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp).size(360.dp, 180.dp)) {
            ExpandedTransferContent(
                downloadState = DownloadState(
                    fileName = "High_Res_Nature_Photo.jpg",
                    progress = 0.85f,
                    isDownloading = true,
                    speedBps = 12500000L, // 12.5 MB/s
                    doneFiles = 1,
                    totalFiles = 23,
                    peerName = "Danny Lopez",
                    peerPicture = "https://lh3.googleusercontent.com/a/ACg8ocL_6F3B1u8w8Z3h9Z3h9Z3h9Z3h9Z3h9Z3h=s96-c"
                ),
                uploadState = UploadState(),
                onCancel = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SuccessTransferIslandPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp).size(360.dp, 180.dp)) {
            ExpandedTransferContent(
                downloadState = DownloadState(
                    fileName = "Vacation_Video.mp4",
                    progress = 1.0f,
                    isDownloading = true,
                    isSuccess = true,
                    speedBps = 0L,
                    doneFiles = 5,
                    totalFiles = 5,
                    peerName = "Danny Lopez",
                    peerPicture = "https://lh3.googleusercontent.com/a/ACg8ocL_6F3B1u8w8Z3h9Z3h9Z3h9Z3h9Z3h9Z3h=s96-c"
                ),
                uploadState = UploadState(),
                onCancel = {}
            )
        }
    }
}
