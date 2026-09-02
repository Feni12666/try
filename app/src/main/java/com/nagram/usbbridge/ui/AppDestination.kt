package com.nagram.usbbridge.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.nagram.usbbridge.R

sealed class AppDestination(
    val route: String,
    @param:StringRes val label: Int,
    val icon: ImageVector?,
) {
    data object Home : AppDestination("home", R.string.nav_home, Icons.Outlined.Home)
    data object Files : AppDestination("files", R.string.nav_files, Icons.Outlined.Folder)
    data object Videos : AppDestination("videos", R.string.nav_videos, Icons.Outlined.VideoLibrary)
    data object Duplicates : AppDestination("duplicates", R.string.nav_duplicates, Icons.Outlined.ContentCopy)
    data object Transfer : AppDestination("transfer", R.string.nav_transfer, Icons.Outlined.SwapHoriz)
    data object Player : AppDestination("player", R.string.player_title, null)
    data object Shizuku : AppDestination("shizuku", R.string.shizuku_title, null)

    companion object {
        val bottomNavigation = listOf(Home, Files, Videos, Duplicates, Transfer)
    }
}
