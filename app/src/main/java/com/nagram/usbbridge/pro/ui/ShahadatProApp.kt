package com.nagram.usbbridge.pro.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nagram.usbbridge.R
import com.nagram.usbbridge.RecentTransferUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

private enum class ProTab(val label: String, val glyph: String) {
    HOME("Home", "⌂"),
    FILES("Files", "▣"),
    VIDEOS("Videos", "▶"),
    DUPLICATES("Duplicates", "◫"),
    SYNC("Sync", "↻")
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tab = ProTab.entries[selected]

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                ProTab.entries.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Text(item.glyph, fontWeight = FontWeight.Bold) },
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
            when (tab) {
                ProTab.HOME -> HomeScreen(state, distribution, onStartSync, onPauseSync)
                ProTab.FILES -> FilesScreen(state, distribution, onChooseUsb, onRequestPhoneAccess)
                ProTab.VIDEOS -> VideosScreen(state)
                ProTab.DUPLICATES -> DuplicatesScreen()
                ProTab.SYNC -> SyncScreen(
                    state = state,
                    onChooseUsb = onChooseUsb,
                    onRequestShizuku = onRequestShizuku,
                    onStartSync = onStartSync,
                    onPauseSync = onPauseSync,
                    onOpenAppSettings = onOpenAppSettings
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    distribution: String,
    onStartSync: () -> Unit,
    onPauseSync: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ProfileHeader(distribution) }
        item { SafetyStatusCard(state, onStartSync, onPauseSync) }
        item { TransferCard(state) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Today", "${state.todayFiles} files", humanBytes(state.todayBytes), Modifier.weight(1f))
                MetricCard("Ready", state.readyCount.toString(), "look-ahead queue", Modifier.weight(1f))
                MetricCard("Attention", state.attention.toString(), "safe holds", Modifier.weight(1f))
            }
        }
        item { ConnectionCard(state) }
        item {
            Text("Recent activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (state.recent.isEmpty()) {
            item { MutedCard("No transfer history yet.") }
        } else {
            items(state.recent, key = { "${it.updatedAt}:${it.name}" }) { RecentRow(it) }
        }
    }
}

@Composable
private fun ProfileHeader(distribution: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.profile_shahadat),
            contentDescription = "SHAHADAT profile",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(CircleShape)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("SHAHADAT PRO", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("Premium Video & Storage Manager", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (distribution == "DIRECT") "DIRECT" else "STORE",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SafetyStatusCard(state: HomeUiState, onStartSync: () -> Unit, onPauseSync: () -> Unit) {
    val allGood = state.shizukuGranted && state.usbSelected
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (state.running) "SMART SYNC ACTIVE" else if (allGood) "READY TO SYNC" else "SETUP REQUIRED",
                color = if (allGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(state.lastStatus, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Safety rule: Copy → Verify → 3 min delay → Revalidate → Source delete",
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
private fun TransferCard(state: HomeUiState) {
    val fraction by animateFloatAsState(state.currentPercent / 100f, label = "transfer")
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Current transfer", fontWeight = FontWeight.Bold)
                Text("${state.currentPercent}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(
                state.currentName ?: "Ready for next file",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (state.currentSpeed > 0) humanRate(state.currentSpeed) else "—", style = MaterialTheme.typography.bodySmall)
                Text(if (state.currentEta >= 0) etaText(state.currentEta) else "—", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ConnectionCard(state: HomeUiState) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Connections", fontWeight = FontWeight.Bold)
            StatusLine("Nagram safety journal", state.legacyJournalPresent, if (state.legacyJournalPresent) "Preserved" else "New install")
            StatusLine("Shizuku", state.shizukuGranted, if (state.shizukuGranted) "Connected" else if (state.shizukuRunning) "Permission needed" else "Not running")
            StatusLine("USB destination", state.usbSelected, if (state.usbSelected) "Selected" else "Choose folder")
            StatusLine("Legacy settings", state.legacyPrefsPresent, if (state.legacyPrefsPresent) "Preserved" else "Fresh")
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (ok) "✓" else "•", color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Files", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("Phone and USB are intentionally separated.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            TabRow(selectedTabIndex = storageTab) {
                Tab(selected = storageTab == 0, onClick = { storageTab = 0 }, text = { Text("Phone Storage") })
                Tab(selected = storageTab == 1, onClick = { storageTab = 1 }, text = { Text("USB / SSD") })
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
                    title = "USB / SSD",
                    primary = if (state.usbSelected) "Destination connected" else "No destination selected",
                    secondary = if (state.usbSelected) "SAF tree permission preserved" else "Choose the folder that SHAHADAT PRO may manage"
                )
            }
        }
        item {
            if (storageTab == 0 && distribution == "DIRECT") {
                OutlinedButton(onClick = onRequestPhoneAccess, modifier = Modifier.fillMaxWidth()) { Text("Grant Phone File Access") }
            }
            if (storageTab == 1) {
                Button(onClick = onChooseUsb, modifier = Modifier.fillMaxWidth()) { Text(if (state.usbSelected) "Change USB Folder" else "Choose USB Folder") }
            }
        }
        item {
            MutedCard("Milestone A foundation is active. Grid/List browsing, Copy/Move/Rename/Delete and folder creation arrive in Milestone B without changing this storage split.")
        }
    }
}

@Composable
private fun VideosScreen(state: HomeUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Videos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
        item { MutedCard("All Video Folders index is wired to the new Room media database foundation. Full device/USB indexing and Media3 player arrive in Milestone E.") }
        item { MetricCard("Legacy transfers today", "${state.todayFiles}", humanBytes(state.todayBytes), Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun DuplicatesScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Duplicates", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
        item { FeatureCard("Exact Match", "Bytes + duration + quick fingerprint → SHA-256 confirmation. Filename never decides deletion.") }
        item { FeatureCard("Similar Video", "Optional deep frame fingerprinting is reserved for Milestone C so it cannot slow normal sync.") }
        item { FeatureCard("Safety", "No duplicate action may delete the only confirmed copy.") }
    }
}

@Composable
private fun SyncScreen(
    state: HomeUiState,
    onChooseUsb: () -> Unit,
    onRequestShizuku: () -> Unit,
    onStartSync: () -> Unit,
    onPauseSync: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Smart Sync", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
        item { FeatureCard("Fast Queue", "1 active USB transfer + up to 10 prepared files. Scan continues while copying.") }
        item { FeatureCard("Anti-Duplicate", "Always on for automatic sync. Full SHA-256 only when a candidate needs confirmation.") }
        item { FeatureCard("Verified Cleanup", "3-minute cleanup delay. Correct destination is revalidated before source deletion.") }
        item {
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusLine("Shizuku", state.shizukuGranted, if (state.shizukuGranted) "Ready" else "Required for Nagram protected source")
                    if (!state.shizukuGranted) OutlinedButton(onClick = onRequestShizuku, modifier = Modifier.fillMaxWidth()) { Text("Grant Shizuku") }
                    StatusLine("USB", state.usbSelected, if (state.usbSelected) "Ready" else "Select destination")
                    OutlinedButton(onClick = onChooseUsb, modifier = Modifier.fillMaxWidth()) { Text(if (state.usbSelected) "Change USB Destination" else "Choose USB Destination") }
                    HorizontalDivider()
                    if (state.running) {
                        OutlinedButton(onClick = onPauseSync, modifier = Modifier.fillMaxWidth()) { Text("Pause Smart Sync") }
                    } else {
                        Button(onClick = onStartSync, modifier = Modifier.fillMaxWidth()) { Text("Start Smart Sync") }
                    }
                }
            }
        }
        item { OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) { Text("Android App Settings") } }
    }
}

@Composable
private fun StorageOverview(title: String, primary: String, secondary: String) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeatureCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MutedCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(13.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun RecentRow(item: RecentTransferUi) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
