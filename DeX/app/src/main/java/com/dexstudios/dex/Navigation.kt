package com.dexstudios.dex

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexstudios.dex.R
import com.dexstudios.dex.ui.components.FloatingPillNavBar
import com.dexstudios.dex.ui.components.FloatingTopAppBar
import com.dexstudios.dex.ui.components.NavBarItem
import com.dexstudios.dex.ui.history.HistoryScreen
import com.dexstudios.dex.ui.main.MainScreen
import com.dexstudios.dex.ui.settings.SettingsScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** The top-level tab destinations, in navbar order. */
private val tabs = listOf(Main, History, Settings)

@Composable
fun MainNavigation() {
  // Tabs are siblings, not a navigation stack: switching replaces the selected
  // tab, and back on any tab falls through to the system (exit).
  var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
  val currentRoute = tabs[selectedTabIndex]

  val mainListState = rememberLazyListState()
  val historyListState = rememberLazyListState()

  val devicesFilled = ImageVector.vectorResource(R.drawable.ic_devices_filled)
  val devicesOutlined = ImageVector.vectorResource(R.drawable.ic_devices_outlined)
  val historyFilled = ImageVector.vectorResource(R.drawable.ic_history_filled)
  val historyOutlined = ImageVector.vectorResource(R.drawable.ic_history_outlined)
  val tuneFilled = ImageVector.vectorResource(R.drawable.ic_tune_filled)
  val tuneOutlined = ImageVector.vectorResource(R.drawable.ic_tune_outlined)

  val navItems = listOf(
    NavBarItem(
      selectedIcon = devicesFilled,
      unselectedIcon = devicesOutlined,
      contentDescription = "Devices",
      isSelected = currentRoute == Main,
      onClick = { selectedTabIndex = 0 }
    ),
    NavBarItem(
      selectedIcon = historyFilled,
      unselectedIcon = historyOutlined,
      contentDescription = "History",
      isSelected = currentRoute == History,
      onClick = { selectedTabIndex = 1 }
    ),
    NavBarItem(
      selectedIcon = tuneFilled,
      unselectedIcon = tuneOutlined,
      contentDescription = "Settings",
      isSelected = currentRoute == Settings,
      onClick = { selectedTabIndex = 2 }
    )
  )

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Glass backdrop: captures all screen content (Devices, History, Settings)
    // via AnimatedContent, so the glass navbar and the glass PIN card sample
    // whatever is behind them. Glass elements are drawn OUTSIDE this captured
    // subtree — a backdrop that captures the glass sampling it is a render loop
    // and crashes (SIGSEGV).
    val contentBackdrop = rememberLayerBackdrop()
    val incomingPairRequest by com.dexstudios.dex.network.AuthState.incomingPairRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current

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
              listState = mainListState
            )
            History -> HistoryScreen(
              modifier = Modifier.safeDrawingPadding(),
              listState = historyListState
            )
            Settings -> SettingsScreen(
              modifier = Modifier.safeDrawingPadding()
            )
          }
        }
      }

      if (incomingPairRequest != null) {
        // Dim recorded INTO the backdrop (not overlaid on top) so the glass PIN
        // card samples the dimmed scene behind it — the documented pattern for
        // glass dialogs.
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
        )
      }
    }

    androidx.compose.animation.AnimatedVisibility(
      visible = true,
      enter = slideInVertically(initialOffsetY = { it }),
      exit = slideOutVertically(targetOffsetY = { it }),
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
    ) {
      FloatingPillNavBar(items = navItems, backdrop = contentBackdrop)
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = currentRoute == Main || currentRoute == History,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter)
    ) {
        Box {
            val activeListState = if (currentRoute == Main) mainListState else historyListState
            val scrollOffset = if (activeListState.firstVisibleItemIndex == 0) {
                activeListState.firstVisibleItemScrollOffset.toFloat()
            } else {
                500f
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
                        .blur(radius = (scrollOffset / 25f).coerceIn(0f, 12f).dp)
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
            backdrop = contentBackdrop,
            deadlineElapsedMs = req.deadlineElapsedMs
        )
    }
  }
}
