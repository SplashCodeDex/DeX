package com.dexstudios.dex

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.dexstudios.dex.ui.components.bubbleFluidity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexstudios.dex.network.AuthState
import com.dexstudios.dex.R
import com.dexstudios.dex.ui.components.FloatingPillNavBar
import com.dexstudios.dex.ui.components.FloatingTopAppBar
import com.dexstudios.dex.ui.components.NavBarItem
import com.dexstudios.dex.ui.components.OnboardingDialog
import com.dexstudios.dex.ui.state.TopAppBarState
import com.dexstudios.dex.ui.history.HistoryScreen
import com.dexstudios.dex.ui.main.MainScreen
import com.dexstudios.dex.ui.settings.SettingsScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.mutableStateOf
import org.koin.compose.koinInject
import com.dexstudios.dex.network.MessageHandler
import com.dexstudios.dex.ui.icons.MaterialSymbols

/** The top-level tab destinations, in navbar order. */
private val tabs = listOf(Main, History, Settings)

@Composable
fun MainNavigation(windowSizeClass: WindowSizeClass) {
  val messageHandler: MessageHandler = koinInject()
  // Tabs are siblings, not a navigation stack: switching replaces the selected
  // tab, and back on any tab falls through to the system (exit).
  var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
  val currentRoute = tabs[selectedTabIndex]

  val mainListState = rememberLazyListState()
  val historyListState = rememberLazyListState()

  val devicesFilled = MaterialSymbols.Devices
  val devicesOutlined = MaterialSymbols.Devices
  val historyFilled = MaterialSymbols.History
  val tuneFilled = MaterialSymbols.Tune

  val navItems = listOf(
    NavBarItem(
      selectedIcon = devicesFilled,
      unselectedIcon = devicesOutlined,
      contentDescription = "Devices",
      isSelected = currentRoute == Main,
      onClick = { selectedTabIndex = 0 },
    ),
    NavBarItem(
      selectedIcon = historyFilled,
      unselectedIcon = historyFilled, // Standardized to filled for performance
      contentDescription = "History",
      isSelected = currentRoute == History,
      onClick = { selectedTabIndex = 1 },
    ),
    NavBarItem(
      selectedIcon = tuneFilled,
      unselectedIcon = tuneFilled, // Standardized to filled for performance
      contentDescription = "Settings",
      isSelected = currentRoute == Settings,
      onClick = { selectedTabIndex = 2 },
    )
  )

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Glass backdrop: captures all screen content (Devices, History, Settings)
    // via AnimatedContent, so the glass navbar and the glass PIN card sample
    // whatever is behind them. Glass elements are drawn OUTSIDE this captured
    // subtree — a backdrop that captures the glass sampling it is a render loop
    // and crashes (SIGSEGV).
    val contentBackdrop = rememberLayerBackdrop()
    val incomingPairRequest by AuthState.incomingPairRequest.collectAsStateWithLifecycle()

    val isDimmed = (TopAppBarState.isProfileExpanded ||
                    TopAppBarState.isOnboardingVisible || incomingPairRequest != null)
    val globalDimAlpha by animateFloatAsState(
        targetValue = if (isDimmed) 0.75f else 0f,
        animationSpec = tween(500),
        label = "globalDimAlpha"
    )

    // Back button handling for expanded overlays
    BackHandler(enabled = TopAppBarState.isProfileExpanded || TopAppBarState.isSearchExpanded) {
        TopAppBarState.isProfileExpanded = false
        TopAppBarState.isSearchExpanded = false
    }

    val context = LocalContext.current
    val resources = LocalResources.current

    val onboardingPrefs = remember { context.getSharedPreferences("dex_onboarding", android.content.Context.MODE_PRIVATE) }
    var showOnboarding by remember { mutableStateOf(!onboardingPrefs.getBoolean("onboarding_done", false)) }

    LaunchedEffect(showOnboarding) {
        TopAppBarState.isOnboardingVisible = showOnboarding
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .layerBackdrop(contentBackdrop)
    ) {
      // Tab switching animates with the iOS-style crossfade. Each tab's UI state
      // (scroll position, etc.) is preserved while it is not visible.
      val tabStateHolder = rememberSaveableStateHolder()
      AnimatedContent(
        targetState = currentRoute,
        transitionSpec = { NavigationTransitions.tabSwitch() },
        modifier = Modifier.fillMaxSize(),
        label = "tabs"
      ) { tab ->
        tabStateHolder.SaveableStateProvider(tab.toString()) {
          when (tab) {
            Main -> MainScreen(
              modifier = Modifier,
              listState = mainListState,
              windowSizeClass = windowSizeClass
            )
            History -> HistoryScreen(
              modifier = Modifier,
              listState = historyListState
            )
            Settings -> SettingsScreen(
              modifier = Modifier.safeDrawingPadding()
            )
          }
        }
      }

      if (globalDimAlpha > 0f) {
        // Dim recorded INTO the backdrop (not overlaid on top) so the glass PIN
        // card samples the dimmed scene behind it - the documented pattern for
        // glass dialogs.
        Box(
          modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = globalDimAlpha }
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    TopAppBarState.isProfileExpanded = false
                    TopAppBarState.isSearchExpanded = false
                }
            )
        )
      }
    }

    androidx.compose.animation.AnimatedVisibility(
      visible = true,
      enter = slideInVertically(initialOffsetY = { it }),
      exit = slideOutVertically(targetOffsetY = { it }),
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
          FloatingPillNavBar(items = navItems, backdrop = contentBackdrop)
      }
    }

    AnimatedVisibility(
        visible = currentRoute == Main || currentRoute == History,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter)
    ) {
        Box {
            val activeListState = if (currentRoute == Main) mainListState else historyListState
            val scrollOffset by remember {
                derivedStateOf {
                    if (activeListState.firstVisibleItemIndex == 0) {
                        activeListState.firstVisibleItemScrollOffset.toFloat()
                    } else {
                        500f
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 2.dp)
                    .graphicsLayer {
                        translationY = -scrollOffset * 0.5f
                        val s = (1f + (scrollOffset / 800f)).coerceAtMost(1.5f)
                        scaleX = s
                        scaleY = s
                        alpha = (1f - scrollOffset / 300f).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.dex_logo),
                    contentDescription = "DeX Logo",
                    modifier = Modifier
                        .height(60.dp)
                        .bubbleFluidity(targetScale = 0.85f, pullFactor = 0.25f),
                    contentScale = ContentScale.Fit
                )
            }

            FloatingTopAppBar(
                backdrop = contentBackdrop
            )
        }
    }

    incomingPairRequest?.let { req ->
        com.dexstudios.dex.ui.components.PairingRequestDialog(
            alias = req.alias,
            expectedPin = req.pin,
            onAccept = { enteredPin ->
                req.deferred.complete(enteredPin)
                com.dexstudios.dex.network.AuthState.incomingPairRequest.value = null
                Toast.makeText(context, resources.getString(R.string.paired_successfully), Toast.LENGTH_SHORT).show()
            },
            onReject = {
                req.deferred.complete("")
                com.dexstudios.dex.network.AuthState.incomingPairRequest.value = null
            },
            deadlineElapsedMs = req.deadlineElapsedMs,
            onDigitEntered = { count ->
                messageHandler.sendPinDigitEntered(count)
            }
        )
    }

    if (showOnboarding) {
        OnboardingDialog(
            onDismiss = {
                onboardingPrefs.edit { putBoolean("onboarding_done", true) }
                showOnboarding = false
            }
        )
    }
  }
}
