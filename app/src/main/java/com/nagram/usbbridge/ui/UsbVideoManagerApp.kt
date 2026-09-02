package com.nagram.usbbridge.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nagram.usbbridge.ui.screens.DuplicatesScreen
import com.nagram.usbbridge.ui.screens.FilesScreen
import com.nagram.usbbridge.ui.screens.HomeScreen
import com.nagram.usbbridge.ui.screens.PlayerScreen
import com.nagram.usbbridge.ui.screens.ShizukuScreen
import com.nagram.usbbridge.ui.screens.TransferScreen
import com.nagram.usbbridge.ui.screens.VideosScreen

@Composable
fun UsbVideoManagerApp(
    viewModel: AppViewModel = viewModel(),
    onRequestPhoneStorageAccess: () -> Unit = {},
    onChooseUsbFolder: () -> Unit = {},
    onChooseSyncSource: () -> Unit = {},
    onChooseSyncTarget: () -> Unit = {},
) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.Home.route
    val bottomRoutes = AppDestination.bottomNavigation.map { it.route }.toSet()
    val showBottomBar = currentRoute in bottomRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                AppBottomNavigation(
                    currentRoute = currentRoute,
                    onDestinationSelected = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(AppDestination.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.padding(contentPadding),
            enterTransition = {
                fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 12 }
            },
            exitTransition = {
                fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -it / 16 }
            },
            popEnterTransition = {
                fadeIn(tween(180)) + slideInHorizontally(tween(220)) { -it / 12 }
            },
            popExitTransition = {
                fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { it / 16 }
            },
        ) {
            composable(AppDestination.Home.route) {
                HomeScreen(
                    state = state,
                    onOpenFiles = { navController.navigate(AppDestination.Files.route) },
                    onOpenVideos = { navController.navigate(AppDestination.Videos.route) },
                    onOpenDuplicates = { navController.navigate(AppDestination.Duplicates.route) },
                    onOpenTransfer = { navController.navigate(AppDestination.Transfer.route) },
                    onOpenShizuku = { navController.navigate(AppDestination.Shizuku.route) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            composable(AppDestination.Files.route) {
                FilesScreen(
                    state = state,
                    onStorageSelected = viewModel::selectStorage,
                    onRequestPhoneStorageAccess = onRequestPhoneStorageAccess,
                    onChooseUsbFolder = onChooseUsbFolder,
                    onRefresh = { viewModel.refreshStorageAccess(loadActiveLocation = true) },
                    onNavigateUp = viewModel::navigateUp,
                    onOpenEntry = viewModel::openEntry,
                    onOpenProtectedData = { viewModel.openProtectedFolder(BrowserOrigin.SHIZUKU_DATA) },
                    onOpenProtectedObb = { viewModel.openProtectedFolder(BrowserOrigin.SHIZUKU_OBB) },
                    onSetProtectedSyncSource = viewModel::setCurrentProtectedFolderAsSyncSource,
                    onOpenPlayer = { navController.navigate(AppDestination.Player.route) },
                    onOpenShizuku = { navController.navigate(AppDestination.Shizuku.route) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            composable(AppDestination.Videos.route) {
                VideosScreen(
                    onOpenPlayer = { navController.navigate(AppDestination.Player.route) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            composable(AppDestination.Duplicates.route) {
                DuplicatesScreen(
                    state = state,
                    onChooseRule = viewModel::chooseKeepRule,
                    onRequestDelete = viewModel::requestDelete,
                    onDismissDelete = viewModel::dismissDelete,
                    onOpenPlayer = { navController.navigate(AppDestination.Player.route) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            composable(AppDestination.Transfer.route) {
                TransferScreen(
                    state = state,
                    onChooseSource = onChooseSyncSource,
                    onChooseTarget = onChooseSyncTarget,
                    onRunSync = viewModel::runSmartSync,
                    onCancelSync = viewModel::cancelSmartSync,
                    onSetAutoSync = viewModel::setAutoSync,
                    onSetOnlyNewFiles = viewModel::setOnlyNewFiles,
                    onSetVerifyAfterCopy = viewModel::setVerifyAfterCopy,
                    onSetRunOnUsbConnect = viewModel::setRunOnUsbConnect,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            composable(AppDestination.Player.route) {
                PlayerScreen(
                    onBack = navController::navigateUp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            composable(AppDestination.Shizuku.route) {
                ShizukuScreen(
                    state = state,
                    onBack = navController::navigateUp,
                    onRequestAccess = viewModel::requestShizukuAccess,
                    onRefreshStatus = viewModel::refreshShizuku,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun AppBottomNavigation(
    currentRoute: String,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        AppDestination.bottomNavigation.forEach { destination ->
            val icon = requireNotNull(destination.icon)
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onDestinationSelected(destination) },
                icon = { Icon(icon, contentDescription = stringResource(destination.label)) },
                label = { Text(stringResource(destination.label), maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ),
            )
        }
    }
}
