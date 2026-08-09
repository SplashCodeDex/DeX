package com.example.dex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.dex.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.dex.ui.components.FloatingPillNavBar
import com.example.dex.ui.components.NavBarItem
import com.example.dex.ui.history.HistoryScreen
import com.example.dex.ui.main.MainScreen
import com.example.dex.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)
  val currentRoute = backStack.lastOrNull()

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
      onClick = {
        if (currentRoute != Main) {
          backStack.add(Main)
        }
      }
    ),
    NavBarItem(
      selectedIcon = historyFilled,
      unselectedIcon = historyOutlined,
      contentDescription = "History",
      isSelected = currentRoute == History,
      onClick = {
        if (currentRoute != History) {
          backStack.add(History)
        }
      }
    ),
    NavBarItem(
      selectedIcon = tuneFilled,
      unselectedIcon = tuneOutlined,
      contentDescription = "Settings",
      isSelected = currentRoute == Settings,
      onClick = {
        if (currentRoute != Settings) {
          backStack.add(Settings)
        }
      }
    )
  )

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Glass backdrop: captures all screen content (Devices, History, Settings)
    // via NavDisplay, so the glass navbar and the glass PIN card sample whatever
    // is behind them. Glass elements are drawn OUTSIDE this captured subtree —
    // a backdrop that captures the glass sampling it is a render loop and
    // crashes (SIGSEGV).
    val contentBackdrop = rememberLayerBackdrop()
    val incomingPairRequest by com.example.dex.network.AuthState.incomingPairRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
      modifier = Modifier
        .fillMaxSize()
        .layerBackdrop(contentBackdrop)
    ) {
      NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = Modifier,
        transitionSpec = {
          fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
        },
        entryProvider =
          entryProvider {
            entry<Main> {
              MainScreen(
                modifier = Modifier.safeDrawingPadding()
              )
            }
            entry<History> {
              HistoryScreen(
                modifier = Modifier.safeDrawingPadding()
              )
            }
            entry<Settings> {
              SettingsScreen(
                modifier = Modifier.safeDrawingPadding()
              )
            }
          },
      )

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

    incomingPairRequest?.let { req ->
        com.example.dex.ui.components.PairingRequestDialog(
            alias = req.alias,
            expectedPin = req.pin,
            onAccept = { enteredPin ->
                req.deferred.complete(enteredPin)
                com.example.dex.network.AuthState.incomingPairRequest.value = null
                Toast.makeText(context, context.getString(R.string.paired_successfully), Toast.LENGTH_SHORT).show()
            },
            onReject = {
                req.deferred.complete("")
                com.example.dex.network.AuthState.incomingPairRequest.value = null
            },
            backdrop = contentBackdrop
        )
    }
  }
}
