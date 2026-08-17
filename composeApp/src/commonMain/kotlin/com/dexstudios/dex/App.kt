package com.dexstudios.dex

import androidx.compose.foundation.layout.Box
import com.dexstudios.dex.core.designsystem.icons.MaterialSymbols as DeXIcons
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dexstudios.dex.core.designsystem.components.FloatingPillNavBar
import com.dexstudios.dex.core.designsystem.components.NavBarItem
import com.dexstudios.dex.core.designsystem.icons.MaterialSymbols
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.feature.discovery.MainScreen
import com.dexstudios.dex.feature.history.HistoryScreen
import com.dexstudios.dex.feature.settings.SettingsScreen
import com.dexstudios.dex.mirror.MirrorScreen
import com.dexstudios.dex.core.designsystem.theme.LocalBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.wallpaper_laptop
import org.jetbrains.compose.resources.painterResource
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun App() {
    DeXTheme {
        val navController = rememberNavController()
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStackEntry?.destination?.route ?: "discovery"
        val backdrop = rememberLayerBackdrop()

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isCompact = maxWidth < 600.dp

                // Desktop/Global Wallpaper behind everything, captured by the backdrop
                Image(
                    painter = painterResource(Res.drawable.wallpaper_laptop),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                )

                NavHost(
                navController = navController,
                startDestination = "discovery",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("discovery") {
                    MainScreen(isCompact = isCompact)
                }
                composable("history") {
                    HistoryScreen()
                }
                composable("settings") {
                    SettingsScreen()
                }
                composable("mirror") {
                    MirrorScreen()
                }
            }

            val navItems = listOf(
                NavBarItem(
                    selectedIcon = DeXIcons.Search,
                    unselectedIcon = DeXIcons.Search,
                    contentDescription = "Radar",
                    isSelected = currentRoute == "discovery",
                    onClick = {
                        if (currentRoute != "discovery") navController.navigate("discovery") {
                            popUpTo("discovery") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                ),
                NavBarItem(
                    selectedIcon = MaterialSymbols.History,
                    unselectedIcon = MaterialSymbols.History,
                    contentDescription = "History",
                    isSelected = currentRoute == "history",
                    onClick = {
                        if (currentRoute != "history") navController.navigate("history") {
                            popUpTo("discovery") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                ),
                NavBarItem(
                    selectedIcon = DeXIcons.Settings,
                    unselectedIcon = DeXIcons.Settings,
                    contentDescription = "Settings",
                    isSelected = currentRoute == "settings",
                    onClick = {
                        if (currentRoute != "settings") navController.navigate("settings") {
                            popUpTo("discovery") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                ),
                NavBarItem(
                    selectedIcon = MaterialSymbols.Smartphone,
                    unselectedIcon = MaterialSymbols.Smartphone,
                    contentDescription = "Mirror",
                    isSelected = currentRoute == "mirror",
                    onClick = {
                        if (currentRoute != "mirror") navController.navigate("mirror") {
                            popUpTo("discovery") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            )

            FloatingPillNavBar(
                items = navItems,
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
        }
    }
}
