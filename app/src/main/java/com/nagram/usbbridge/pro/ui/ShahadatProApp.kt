package com.nagram.usbbridge.pro.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.net.Uri
import java.io.File
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nagram.usbbridge.R
import com.nagram.usbbridge.pro.files.FileManagerViewModel
import com.nagram.usbbridge.pro.files.StorageKind
import com.nagram.usbbridge.pro.ui.theme.AppThemeMode
import com.nagram.usbbridge.pro.video.VideoLibraryViewModel
import com.nagram.usbbridge.pro.video.VideoPlaybackRequest
import com.nagram.usbbridge.pro.video.VideoItem
import kotlinx.coroutines.delay
import kotlin.math.ln
import kotlin.math.pow

private enum class ProTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    FILES("Files", Icons.Outlined.Folder),
    VIDEOS("Videos", Icons.Outlined.PlayCircleOutline),
    DUPLICATES("Duplicates", Icons.Outlined.ContentCopy),
    MORE("More", Icons.Outlined.MoreHoriz)
}

@Composable
fun ShahadatProApp(
    viewModel: MainViewModel,
    fileManagerViewModel: FileManagerViewModel,
    videoLibraryViewModel: VideoLibraryViewModel,
    distribution: String,
    onChooseUsb: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestPhoneAccess: () -> Unit,
    onRequestVideoAccess: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1450L); showSplash = false }
    if (showSplash) { PersonalSplash(); return }

    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var playback by remember { mutableStateOf<VideoPlaybackRequest?>(null) }

    if (playback != null) {
        PremiumVideoPlayerScreen(request = playback!!, onBack = { playback = null })
    } else {

    if (showSettings) {
        SettingsDialog(
            state = state,
            onTheme = viewModel::setThemeMode,
            onCleanup = viewModel::setCleanupDelayMinutes,
            onSystemSettings = onOpenAppSettings,
            onDismiss = { showSettings = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                ProTab.entries.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Crossfade(ProTab.entries[selected], animationSpec = tween(220), label = "tab") { tab ->
                when (tab) {
                    ProTab.HOME -> HomeScreen(state, distribution, onSettings = { showSettings = true })
                    ProTab.FILES -> ManualFilesScreen(
                        mainState = state,
                        viewModel = fileManagerViewModel,
                        distribution = distribution,
                        onChooseUsb = onChooseUsb,
                        onRequestPhoneAccess = onRequestPhoneAccess,
                        onRequestShizuku = onRequestShizuku,
                        onOpenVideoFile = { entry ->
                            val uri = when (entry.storage) {
                                StorageKind.PHONE -> Uri.fromFile(File(entry.id))
                                StorageKind.USB -> Uri.parse(entry.id)
                                StorageKind.SHIZUKU -> null
                            }
                            if (uri != null) {
                                playback = VideoPlaybackRequest(
                                    items = listOf(
                                        VideoItem(
                                            id = "file:${entry.id}",
                                            name = entry.name,
                                            uri = uri,
                                            folder = state.current?.label ?: "Files",
                                            size = entry.size,
                                            durationMs = 0L,
                                            width = 0,
                                            height = 0,
                                            modifiedMs = entry.modified,
                                            storage = entry.storage
                                        )
                                    ),
                                    startIndex = 0
                                )
                            }
                        }
                    )
                    ProTab.VIDEOS -> PremiumVideosScreen(
                        viewModel = videoLibraryViewModel,
                        onRequestPermission = onRequestVideoAccess,
                        onChooseUsb = onChooseUsb,
                        onPlay = { playback = it }
                    )
                    ProTab.DUPLICATES -> DuplicatesScreen()
                    ProTab.MORE -> MoreScreen(
                        state = state,
                        onRequestShizuku = onRequestShizuku,
                        onChooseUsb = onChooseUsb,
                        onSettings = { showSettings = true }
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun PersonalSplash() {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scale by animateFloatAsState(if (entered) 1f else 0.84f, tween(650), label = "splashScale")
    val alpha by animateFloatAsState(if (entered) 1f else 0f, tween(520), label = "splashAlpha")
    Box(Modifier.fillMaxSize().background(Color(0xFFF7FAFF)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.scale(scale).alpha(alpha)) {
            Surface(shape = CircleShape, color = Color.White, shadowElevation = 14.dp, modifier = Modifier.size(132.dp)) {
                Image(
                    painter = painterResource(R.drawable.profile_shahadat_full),
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }
            Text("SHAHADAT", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color(0xFF2563EB))
        }
    }
}

@Composable
private fun HomeScreen(state: HomeUiState, distribution: String, onSettings: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Video & Storage Pro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Your files. Your folders. Your control.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
            }
        }
        item { StoragePair(state) }
        item { ManualOperationCard(state) }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Manual-first file manager", fontWeight = FontWeight.ExtraBold)
                    Text("Nothing is copied automatically. Select files yourself, then choose Copy or Move and browse to any destination folder.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Move safety: Copy → Verify → ${state.cleanupDelayMinutes} min → Revalidate → Source delete", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("System", fontWeight = FontWeight.ExtraBold)
                    Text("USB: ${if (state.usbSelected) "Selected ✓" else "Choose in Files"}")
                    Text("Shizuku: ${if (state.shizukuGranted) "Connected ✓" else if (state.shizukuRunning) "Permission needed" else "Optional / not running"}")
                    Text("Build: ${if (distribution == "DIRECT") "Direct full-file mode" else "Play Store scoped mode"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StoragePair(state: HomeUiState) {
    val used = (state.phoneTotal - state.phoneFree).coerceAtLeast(0L)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniStorage(Icons.Outlined.PhoneAndroid, "Phone", humanBytes(used), "${humanBytes(state.phoneFree)} free", Modifier.weight(1f))
        MiniStorage(Icons.Outlined.Usb, "USB / SSD", if (state.usbSelected) "Ready" else "Not selected", if (state.usbSelected) "SAF access" else "Choose folder", Modifier.weight(1f))
    }
}

@Composable
private fun MiniStorage(icon: ImageVector, title: String, value: String, detail: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
internal fun ManualOperationCard(state: HomeUiState) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (state.running) "File operation" else "Transfer engine", fontWeight = FontWeight.ExtraBold)
                Text(if (state.running) "${state.activeTransfers} active • ${state.readyCount} ready" else "Idle", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(state.currentName ?: state.lastStatus, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { state.currentPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (state.running) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (state.currentSpeed > 0) "${humanBytes(state.currentSpeed)}/s" else if (state.paused) "Paused" else "Preparing")
                    Text(if (state.currentEta >= 0) etaText(state.currentEta) else "")
                }
            }
        }
    }
}

@Composable
private fun DuplicatesScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Duplicates", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Exact duplicate plan", fontWeight = FontWeight.ExtraBold)
                    Text("Exact bytes → duration pre-check → quick fingerprint → final SHA-256 confirmation. Filename is never trusted.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("When two videos match, both previews will appear side-by-side with Size and Duration plus Keep Newest / Keep Oldest / Delete Selected.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Similar Video", fontWeight = FontWeight.ExtraBold)
                    Text("Optional deep scan with frame fingerprints will be added separately so daily file browsing stays fast.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(state: HomeUiState, onRequestShizuku: () -> Unit, onChooseUsb: () -> Unit, onSettings: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("More", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Storage, access, transfer and appearance", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Auto Sync is OFF by default", fontWeight = FontWeight.ExtraBold)
                    Text("There is no fixed Nagram source and no automatic destination. Manual Copy / Move in Files is the active workflow.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("A future rule can only run after you explicitly choose Source folder + Target folder and turn that rule ON.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Optional Shizuku", fontWeight = FontWeight.ExtraBold)
                    Text(if (state.shizukuGranted) "Connected ✓" else "Use only when you choose a restricted Android/data or Android/obb source.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onRequestShizuku, modifier = Modifier.fillMaxWidth()) { Text(if (state.shizukuGranted) "Shizuku connected" else "Grant Shizuku") }
                    OutlinedButton(onClick = onChooseUsb, modifier = Modifier.fillMaxWidth()) { Text(if (state.usbSelected) "Change USB / SSD root" else "Choose USB / SSD root") }
                }
            }
        }
    }
}

@Composable
private fun SimpleInfoScreen(title: String, body: String) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
        item { Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Text(body, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

@Composable
private fun SettingsDialog(
    state: HomeUiState,
    onTheme: (AppThemeMode) -> Unit,
    onCleanup: (Int) -> Unit,
    onSystemSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Appearance", fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(AppThemeMode.SYSTEM, AppThemeMode.LIGHT).forEach { mode ->
                            FilterChip(selected = state.themeMode == mode, onClick = { onTheme(mode) }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(AppThemeMode.DARK, AppThemeMode.AMOLED).forEach { mode ->
                            FilterChip(selected = state.themeMode == mode, onClick = { onTheme(mode) }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                }
                Text("Cleanup delay", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 5).forEach { min ->
                        FilterChip(selected = state.cleanupDelayMinutes == min, onClick = { onCleanup(min) }, label = { Text("$min min") })
                    }
                }
                Text("Move deletes the source only after copy + SHA-256 verification + delay + final destination revalidation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onSystemSettings, modifier = Modifier.fillMaxWidth()) { Text("Android app settings") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

private fun humanBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val group = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(group.toDouble())
    return if (group == 0) "$bytes B" else String.format(java.util.Locale.US, "%.1f %s", value, units[group])
}

private fun etaText(seconds: Long): String = when {
    seconds < 60 -> "~${seconds}s"
    seconds < 3600 -> "~${seconds / 60}m ${seconds % 60}s"
    else -> "~${seconds / 3600}h ${(seconds % 3600) / 60}m"
}
