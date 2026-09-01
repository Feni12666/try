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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nagram.usbbridge.R
import com.nagram.usbbridge.RecentTransferUi
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

private enum class ProTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    FILES("Files", Icons.Outlined.Folder),
    VIDEOS("Videos", Icons.Outlined.PlayCircleOutline),
    DUPLICATES("Duplicates", Icons.Outlined.ContentCopy),
    SYNC("Sync", Icons.Outlined.Sync)
}

@Composable
fun ShahadatProApp(
    viewModel: MainViewModel,
    distribution: String,
    onChooseUsb: () -> Unit,
    onRequestShizuku: () -> Unit,
    onStartSync: () -> Unit,
    onPauseSync: () -> Unit,
    onRequestPhoneAccess: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1450L)
        showSplash = false
    }

    if (showSplash) {
        PersonalSplash()
        return
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tab = ProTab.entries[selected]

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = Color.White,
                tonalElevation = 2.dp
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            Crossfade(targetState = tab, animationSpec = tween(220), label = "page") { current ->
                when (current) {
                    ProTab.HOME -> HomeScreen(state, distribution, onStartSync, onPauseSync, onOpenAppSettings)
                    ProTab.FILES -> FilesScreen(state, distribution, onChooseUsb, onRequestPhoneAccess)
                    ProTab.VIDEOS -> VideosScreen(state)
                    ProTab.DUPLICATES -> DuplicatesScreen()
                    ProTab.SYNC -> SyncScreen(
                        state = state,
                        onChooseUsb = onChooseUsb,
                        onRequestShizuku = onRequestShizuku,
                        onStartSync = onStartSync,
                        onPauseSync = onPauseSync,
                        onSetCleanupDelay = viewModel::setCleanupDelayMinutes
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalSplash() {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scale by animateFloatAsState(if (entered) 1f else 0.82f, tween(650), label = "splashScale")
    val alpha by animateFloatAsState(if (entered) 1f else 0f, tween(520), label = "splashAlpha")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7FAFF)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.scale(scale).alpha(alpha)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 14.dp,
                modifier = Modifier.size(132.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.profile_shahadat_full),
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }
            Text(
                "SHAHADAT",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String? = null, onSettings: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (onSettings != null) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    distribution: String,
    onStartSync: () -> Unit,
    onPauseSync: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Storage Manager", "Phone, USB, videos and safe sync", onOpenAppSettings) }
        item { PremiumStatusCard(state, onStartSync, onPauseSync) }
        item { StoragePair(state) }
        item { TransferCard(state) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Today", "${state.todayFiles} files", humanBytes(state.todayBytes), Modifier.weight(1f))
                MetricCard("Ready", state.readyCount.toString(), "of 10 prepared", Modifier.weight(1f))
                MetricCard("Attention", state.attention.toString(), if (state.safeMode) "Safe Mode" else "safe holds", Modifier.weight(1f))
            }
        }
        item { ConnectionCard(state, distribution) }
        item { SectionTitle("Recent activity") }
        if (state.recent.isEmpty()) {
            item { MutedCard("No transfer history yet.") }
        } else {
            items(state.recent, key = { "${it.updatedAt}:${it.name}" }) { RecentRow(it) }
        }
    }
}

@Composable
private fun PremiumStatusCard(state: HomeUiState, onStartSync: () -> Unit, onPauseSync: () -> Unit) {
    val allGood = state.shizukuGranted && state.usbSelected
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (allGood) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
                Column {
                    Text(
                        when {
                            state.safeMode -> "SAFE MODE ACTIVE"
                            state.running -> "SMART SYNC ACTIVE"
                            allGood -> "READY TO SYNC"
                            else -> "SETUP REQUIRED"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(state.lastStatus, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "Copy → Verify → ${state.cleanupDelayMinutes} min delay → Revalidate → Source delete",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            if (state.running) {
                OutlinedButton(onClick = onPauseSync, modifier = Modifier.fillMaxWidth()) { Text("Pause Smart Sync") }
            } else {
                Button(onClick = onStartSync, modifier = Modifier.fillMaxWidth()) { Text("Start Smart Sync") }
            }
        }
    }
}

@Composable
private fun StoragePair(state: HomeUiState) {
    val used = (state.phoneTotal - state.phoneFree).coerceAtLeast(0)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StorageMiniCard(
            icon = Icons.Outlined.PhoneAndroid,
            title = "Phone",
            value = humanBytes(used),
            subtitle = "${humanBytes(state.phoneFree)} free",
            modifier = Modifier.weight(1f)
        )
        StorageMiniCard(
            icon = Icons.Outlined.Usb,
            title = "USB / SSD",
            value = if (state.usbSelected) "Connected" else "Not selected",
            subtitle = if (state.usbSelected) "SAF access ready" else "Choose destination",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StorageMiniCard(icon: ImageVector, title: String, value: String, subtitle: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun TransferCard(state: HomeUiState) {
    val fraction by animateFloatAsState(state.currentPercent / 100f, tween(350), label = "transfer")
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Transfer queue", fontWeight = FontWeight.ExtraBold)
                    Text(
                        "${state.activeTransfers} active • ${state.readyCount} ready",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        if (state.safeMode) "1× Safe" else "2× Fast",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
            Text(
                state.currentName ?: "Ready for the next completed file",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (state.currentSpeed > 0) humanRate(state.currentSpeed) else "Waiting", style = MaterialTheme.typography.bodySmall)
                Text(if (state.currentEta >= 0) etaText(state.currentEta) else "", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ConnectionCard(state: HomeUiState, distribution: String) {
    Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("System status", fontWeight = FontWeight.ExtraBold)
            StatusLine("Shizuku", state.shizukuGranted, if (state.shizukuGranted) "Connected" else if (state.shizukuRunning) "Permission needed" else "Not running")
            StatusLine("USB destination", state.usbSelected, if (state.usbSelected) "Selected" else "Choose folder")
            StatusLine("Transfer journal", state.legacyJournalPresent, if (state.legacyJournalPresent) "Protected" else "Ready")
            StatusLine("Build", true, if (distribution == "DIRECT") "Direct / full file-manager mode" else "Play Store scoped mode")
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun FilesScreen(
    state: HomeUiState,
    distribution: String,
    onChooseUsb: () -> Unit,
    onRequestPhoneAccess: () -> Unit
) {
    var storageTab by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Files", "Browse Phone and USB separately") }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                TabRow(selectedTabIndex = storageTab, containerColor = Color.Transparent) {
                    Tab(
                        selected = storageTab == 0,
                        onClick = { storageTab = 0 },
                        text = { Text("Phone") },
                        icon = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null) }
                    )
                    Tab(
                        selected = storageTab == 1,
                        onClick = { storageTab = 1 },
                        text = { Text("USB / SSD") },
                        icon = { Icon(Icons.Outlined.Usb, contentDescription = null) }
                    )
                }
            }
        }
        item {
            if (storageTab == 0) {
                StorageOverview(
                    title = "Phone Storage",
                    primary = "${humanBytes((state.phoneTotal - state.phoneFree).coerceAtLeast(0))} / ${humanBytes(state.phoneTotal)}",
                    secondary = "${humanBytes(state.phoneFree)} free"
                )
            } else {
                StorageOverview(
                    title = "External Drive",
                    primary = if (state.usbSelected) "USB destination ready" else "No USB folder selected",
                    secondary = if (state.usbSelected) "Persistent SAF permission saved" else "Select the folder the app may manage"
                )
            }
        }
        item {
            if (storageTab == 0 && distribution == "DIRECT") {
                OutlinedButton(onClick = onRequestPhoneAccess, modifier = Modifier.fillMaxWidth()) { Text("Grant Full Phone File Access") }
            }
            if (storageTab == 1) {
                Button(onClick = onChooseUsb, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.usbSelected) "Change USB / SSD Folder" else "Choose USB / SSD Folder")
                }
            }
        }
        item { SectionTitle("File manager") }
        item {
            FeatureCard(
                "Two-way operations",
                "Copy, Move, Rename, Delete, New Folder, Grid/List and folder-tree navigation use the same Phone ↔ USB safety model."
            )
        }
        item {
            FeatureCard(
                "Safe cross-storage move",
                "Copy → verify content → finalize → selected cleanup delay → destination revalidation → source delete."
            )
        }
    }
}

@Composable
private fun VideosScreen(state: HomeUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Videos", "All video folders in one place") }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.PlayCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                    Text("Built-in Media3 player", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Play/pause, ±10s seek, fullscreen, brightness and volume gestures stay inside the app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Phone", "Video folders", "shared storage", Modifier.weight(1f))
                MetricCard("USB", "Video folders", if (state.usbSelected) "drive ready" else "connect drive", Modifier.weight(1f))
            }
        }
        item { FeatureCard("All Video Folders", "Phone | USB | All filtering, thumbnails, video count, total size, sorting and multi-select management.") }
    }
}

@Composable
private fun DuplicatesScreen() {
    var duplicateMode by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Duplicates", "Exact matches and optional similar-video deep scan") }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                TabRow(selectedTabIndex = duplicateMode, containerColor = Color.Transparent) {
                    Tab(selected = duplicateMode == 0, onClick = { duplicateMode = 0 }, text = { Text("Exact") })
                    Tab(selected = duplicateMode == 1, onClick = { duplicateMode = 1 }, text = { Text("Similar") })
                }
            }
        }
        if (duplicateMode == 0) {
            item { FeatureCard("Exact Match", "Exact bytes → duration prefilter → quick fingerprint → full SHA-256 confirmation. Filename never decides identity.") }
            item { DuplicateComparisonPreview() }
        } else {
            item { FeatureCard("Similar Video • Deep Scan", "Optional frame fingerprinting can find re-encoded, resolution-changed or watermarked versions. Results remain manual-review only.") }
            item { FeatureCard("Sensitivity", "Strict / Balanced / Loose. Similar results are never labeled as exact duplicates.") }
        }
    }
}

@Composable
private fun DuplicateComparisonPreview() {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Comparison layout", fontWeight = FontWeight.ExtraBold)
            Text("When an exact pair is found, both copies appear side by side before any manual delete.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompareFileCard("Older copy", "Size", "Duration", Modifier.weight(1f))
                CompareFileCard("Newer copy", "Size", "Duration", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("Keep Newest") }
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("Keep Oldest") }
            }
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Delete Selected")
            }
        }
    }
}

@Composable
private fun CompareFileCard(title: String, size: String, duration: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.fillMaxWidth().height(74.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.PlayCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Text(title, fontWeight = FontWeight.Bold)
            Text("$size • $duration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SyncScreen(
    state: HomeUiState,
    onChooseUsb: () -> Unit,
    onRequestShizuku: () -> Unit,
    onStartSync: () -> Unit,
    onPauseSync: () -> Unit,
    onSetCleanupDelay: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Smart Sync", "Automatic, anti-duplicate and safely recoverable") }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusLine("Shizuku", state.shizukuGranted, if (state.shizukuGranted) "Connected" else "Optional advanced source access")
                    if (!state.shizukuGranted) {
                        OutlinedButton(onClick = onRequestShizuku, modifier = Modifier.fillMaxWidth()) { Text("Connect Shizuku") }
                    }
                    HorizontalDivider()
                    StatusLine("USB / SSD", state.usbSelected, if (state.usbSelected) "Destination ready" else "Select a destination folder")
                    OutlinedButton(onClick = onChooseUsb, modifier = Modifier.fillMaxWidth()) { Text(if (state.usbSelected) "Change Destination" else "Choose Destination") }
                }
            }
        }
        item { CleanupDelaySelector(state.cleanupDelayMinutes, onSetCleanupDelay) }
        item {
            FeatureCard(
                "Fast Queue",
                if (state.safeMode)
                    "Safe Mode: 1 active transfer. Up to 10 files can still be prepared in the background."
                else
                    "2 active transfers + up to 10 prepared files. If repeated safety failures occur, the engine automatically falls back to 1 active transfer."
            )
        }
        item { FeatureCard("Anti-Duplicate Sync", "Already-confirmed content on the selected USB is skipped. Same displayed MB alone never causes a skip or delete.") }
        item {
            if (state.running) {
                OutlinedButton(onClick = onPauseSync, modifier = Modifier.fillMaxWidth()) { Text("Pause Smart Sync") }
            } else {
                Button(onClick = onStartSync, modifier = Modifier.fillMaxWidth()) { Text("Start Smart Sync") }
            }
        }
    }
}

@Composable
private fun CleanupDelaySelector(selected: Int, onSelect: (Int) -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Cleanup Delay", fontWeight = FontWeight.ExtraBold)
            Text("After a verified transfer, wait before destination revalidation and source cleanup.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5).forEach { minute ->
                    FilterChip(
                        selected = selected == minute,
                        onClick = { onSelect(minute) },
                        label = { Text("$minute min") },
                        leadingIcon = if (selected == minute) {
                            { Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(17.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                if (selected == 3) "3 min • Recommended" else if (selected == 1) "1 min • Faster cleanup" else "5 min • Extra cautious",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StorageOverview(title: String, primary: String, secondary: String) {
    Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeatureCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MutedCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(13.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun RecentRow(item: RecentTransferUi) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(friendlyStatus(item.status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(humanBytes(item.sizeBytes), style = MaterialTheme.typography.bodySmall)
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.updatedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun friendlyStatus(status: String): String = when (status) {
    "COMPLETED" -> "Transferred safely"
    "CLEANUP_PENDING" -> "Verified • cleanup pending"
    "DUPLICATE_VERIFIED" -> "Duplicate confirmed • skipped"
    "WAITING_FOR_COMPLETION" -> "Waiting for download completion"
    "VERIFICATION_FAILED" -> "Verification failed • original kept"
    "NEEDS_ATTENTION" -> "Needs attention • original kept"
    else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

private fun humanBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val group = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(group.toDouble())
    return if (group == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[group])
}

private fun humanRate(bytesPerSecond: Long): String = "${humanBytes(bytesPerSecond)}/s"
private fun etaText(seconds: Long): String = when {
    seconds < 60 -> "~${seconds}s"
    seconds < 3600 -> "~${seconds / 60}m ${seconds % 60}s"
    else -> "~${seconds / 3600}h ${(seconds % 3600) / 60}m"
}
