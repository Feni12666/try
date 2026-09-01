package com.nagram.usbbridge.pro.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nagram.usbbridge.pro.files.BrowserEntry
import com.nagram.usbbridge.pro.files.ConflictPolicy
import com.nagram.usbbridge.pro.files.FileManagerViewModel
import com.nagram.usbbridge.pro.files.ManualOperation
import com.nagram.usbbridge.pro.files.StorageKind
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

enum class FileSort { NAME, DATE, SIZE, TYPE }
enum class FileCategory { ALL, FOLDERS, VIDEO, IMAGE, AUDIO, DOCUMENT }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManualFilesScreen(
    mainState: HomeUiState,
    viewModel: FileManagerViewModel,
    distribution: String,
    onChooseUsb: () -> Unit,
    onRequestPhoneAccess: () -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenVideoFile: (BrowserEntry) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var grid by rememberSaveable { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showProperties by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(FileSort.NAME) }
    var category by rememberSaveable { mutableStateOf(FileCategory.ALL) }

    LaunchedEffect(Unit) { viewModel.refreshPermissionsAndStorage() }

    if (showCreate) {
        NameDialog("New folder", "Folder name", onDismiss = { showCreate = false }) { viewModel.createFolder(it); showCreate = false }
    }
    if (showRename) {
        NameDialog("Rename", "New name", onDismiss = { showRename = false }) { viewModel.renameSelected(it); showRename = false }
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete selected items?") },
            text = { Text("This is a permanent delete. Nothing is deleted automatically by the file manager.") },
            confirmButton = { Button(onClick = { viewModel.deleteSelected(); showDelete = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
        )
    }
    if (showProperties) {
        val item = state.entries.firstOrNull { state.selected.contains(it.id) }
        if (item != null) PropertiesDialog(item = item, onDismiss = { showProperties = false }) else showProperties = false
    }

    if (state.destinationMode != null) {
        DestinationChooser(
            state = state,
            onChooseStorage = viewModel::chooseDestinationStorage,
            onChooseShizukuData = viewModel::chooseRestrictedDestinationData,
            onChooseShizukuObb = viewModel::chooseRestrictedDestinationObb,
            onOpen = viewModel::openDestination,
            onUp = viewModel::destinationUp,
            onConflict = viewModel::setConflictPolicy,
            onConfirm = viewModel::confirmDestination,
            onCancel = viewModel::cancelDestination,
            onChooseUsb = onChooseUsb
        )
        return
    }

    val visibleEntries = remember(state.entries, query, sort, category) {
        state.entries
            .asSequence()
            .filter { entry ->
                val q = query.trim().lowercase(Locale.US)
                q.isBlank() || entry.name.lowercase(Locale.US).contains(q)
            }
            .filter { entry -> matchesCategory(entry, category) }
            .sortedWith(fileComparator(sort))
            .toList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Files", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Phone + USB file manager • manual source and destination", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = viewModel::refresh) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh") }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("New folder") }, leadingIcon = { Icon(Icons.Outlined.Add, null) }, onClick = { menuOpen = false; showCreate = true })
                    DropdownMenuItem(text = { Text(if (state.showHidden) "Hide hidden files" else "Show hidden files") }, onClick = { menuOpen = false; viewModel.setShowHidden(!state.showHidden) })
                    DropdownMenuItem(text = { Text(if (grid) "List view" else "Grid view") }, onClick = { menuOpen = false; grid = !grid })
                }
            }
        }

        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            TabRow(selectedTabIndex = if (state.storage == StorageKind.USB) 1 else 0, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(
                    selected = state.storage != StorageKind.USB,
                    onClick = { query = ""; viewModel.selectStorage(StorageKind.PHONE) },
                    text = { Text("Phone") },
                    icon = { Icon(Icons.Outlined.PhoneAndroid, null) }
                )
                Tab(
                    selected = state.storage == StorageKind.USB,
                    onClick = { query = ""; viewModel.selectStorage(StorageKind.USB) },
                    text = { Text("USB / SSD") },
                    icon = { Icon(Icons.Outlined.Usb, null) }
                )
            }
        }

        if (state.storage == StorageKind.PHONE && distribution == "DIRECT" && !state.phoneAccessGranted) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Full phone access is needed to browse shared storage", fontWeight = FontWeight.Bold)
                    Button(onClick = onRequestPhoneAccess, modifier = Modifier.fillMaxWidth()) { Text("Grant Phone Storage Access") }
                }
            }
        }

        if (state.storage != StorageKind.USB) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Restricted folders • optional Shizuku", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(if (state.shizukuReady) "Connected ✓" else "Off", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                    if (state.shizukuReady) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = viewModel::openShizukuData, modifier = Modifier.weight(1f)) { Text("Android/data") }
                            OutlinedButton(onClick = viewModel::openShizukuObb, modifier = Modifier.weight(1f)) { Text("Android/obb") }
                        }
                    } else {
                        OutlinedButton(onClick = onRequestShizuku, modifier = Modifier.fillMaxWidth()) { Text("Grant Shizuku for Android/data & obb") }
                    }
                }
            }
        }

        if (state.storage == StorageKind.USB && !state.usbAvailable) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose the USB / SSD root or any folder you want this app to manage.", fontWeight = FontWeight.Bold)
                    Button(onClick = onChooseUsb, modifier = Modifier.fillMaxWidth()) { Text("Choose USB / SSD Folder") }
                }
            }
        }

        ManualOperationCard(mainState)
        if (mainState.running) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = if (mainState.paused) viewModel::resumeTransfer else viewModel::pauseTransfer, modifier = Modifier.weight(1f)) {
                    Text(if (mainState.paused) "Resume" else "Pause")
                }
                OutlinedButton(onClick = viewModel::cancelTransfer, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = viewModel::up, enabled = state.stack.size > 1) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Up") }
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.stack.forEachIndexed { index, location ->
                    TextButton(onClick = { viewModel.jumpTo(index) }) { Text(location.label, maxLines = 1) }
                    if (index < state.stack.lastIndex) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Outlined.List else Icons.Outlined.GridView, contentDescription = "View") }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Search this folder") },
                shape = RoundedCornerShape(16.dp)
            )
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Outlined.Sort, contentDescription = "Sort") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    FileSort.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(sortLabel(mode)) },
                            onClick = { sort = mode; sortMenu = false }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            FileCategory.entries.forEach { item ->
                FilterChip(selected = category == item, onClick = { category = item }, label = { Text(categoryLabel(item)) })
            }
        }

        state.message?.let {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(it, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (state.selected.isNotEmpty()) {
            SelectionBar(
                count = state.selected.size,
                onCopy = { viewModel.beginDestination(ManualOperation.COPY) },
                onMove = { viewModel.beginDestination(ManualOperation.MOVE) },
                onRename = { if (state.selected.size == 1) showRename = true },
                onInfo = { if (state.selected.size == 1) showProperties = true },
                onDelete = { showDelete = true },
                onClear = viewModel::clearSelection
            )
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                visibleEntries.isEmpty() -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(if (query.isBlank() && category == FileCategory.ALL) "Folder is empty" else "No matching files", fontWeight = FontWeight.Bold)
                }
                grid -> LazyVerticalGrid(columns = GridCells.Adaptive(132.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    gridItems(visibleEntries, key = { it.id }) { entry ->
                        FileGridItem(
                            entry,
                            state.selected.contains(entry.id),
                            onClick = {
                                if (state.selected.isNotEmpty()) viewModel.toggleSelection(entry)
                                else if (entry.isDirectory) viewModel.open(entry)
                                else if (isVideoEntry(entry) && entry.storage != StorageKind.SHIZUKU) onOpenVideoFile(entry)
                                else viewModel.toggleSelection(entry)
                            },
                            onLongClick = { viewModel.toggleSelection(entry) }
                        )
                    }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(visibleEntries, key = { it.id }) { entry ->
                        FileListItem(
                            entry,
                            state.selected.contains(entry.id),
                            onClick = {
                                if (state.selected.isNotEmpty()) viewModel.toggleSelection(entry)
                                else if (entry.isDirectory) viewModel.open(entry)
                                else if (isVideoEntry(entry) && entry.storage != StorageKind.SHIZUKU) onOpenVideoFile(entry)
                                else viewModel.toggleSelection(entry)
                            },
                            onLongClick = { viewModel.toggleSelection(entry) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(entry: BrowserEntry, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(if (entry.isDirectory) "Folder" else "${humanBytesLocal(entry.size)} • ${formatDate(entry.modified)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Checkbox(checked = true, onCheckedChange = { onLongClick() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridItem(entry: BrowserEntry, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
            Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(if (entry.isDirectory) "Folder" else humanBytesLocal(entry.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected) Checkbox(checked = true, onCheckedChange = { onLongClick() })
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$count selected", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("Clear") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onCopy, modifier = Modifier.weight(1f)) { Text("Copy") }
                Button(onClick = onMove, modifier = Modifier.weight(1f)) { Text("Move") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRename, enabled = count == 1, modifier = Modifier.weight(1f)) { Text("Rename") }
                OutlinedButton(onClick = onInfo, enabled = count == 1, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Info, null); Spacer(Modifier.width(5.dp)); Text("Info") }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(5.dp)); Text("Delete") }
            }
        }
    }
}

@Composable
private fun DestinationChooser(
    state: com.nagram.usbbridge.pro.files.FileManagerUiState,
    onChooseStorage: (StorageKind) -> Unit,
    onChooseShizukuData: () -> Unit,
    onChooseShizukuObb: () -> Unit,
    onOpen: (BrowserEntry) -> Unit,
    onUp: () -> Unit,
    onConflict: (ConflictPolicy) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onChooseUsb: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = if (state.destinationStack.size > 1) onUp else onCancel) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(if (state.destinationMode == ManualOperation.MOVE) "Choose move destination" else "Choose copy destination", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("You choose the exact destination folder.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (state.destinationStorage == null) {
            Text("Where do you want to put the selected files?", fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth().clickable { onChooseStorage(StorageKind.PHONE) }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text("Phone Storage", fontWeight = FontWeight.ExtraBold) }
            }
            Card(Modifier.fillMaxWidth().clickable { if (state.usbAvailable) onChooseStorage(StorageKind.USB) else onChooseUsb() }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Usb, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text(if (state.usbAvailable) "USB / SSD" else "Choose USB / SSD first", fontWeight = FontWeight.ExtraBold) }
            }
            if (state.shizukuReady) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onChooseShizukuData, modifier = Modifier.weight(1f)) { Text("Android/data") }
                    OutlinedButton(onClick = onChooseShizukuObb, modifier = Modifier.weight(1f)) { Text("Android/obb") }
                }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        } else {

        Text(state.destinationCurrent?.label ?: "Destination", fontWeight = FontWeight.ExtraBold)
        Text("If the same name exists:", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ConflictPolicy.entries.forEach { policy ->
                FilterChip(
                    selected = state.conflictPolicy == policy,
                    onClick = { onConflict(policy) },
                    label = { Text(when (policy) { ConflictPolicy.KEEP_BOTH -> "Keep Both"; ConflictPolicy.REPLACE -> "Replace"; ConflictPolicy.SKIP -> "Skip" }) }
                )
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.destinationEntries, key = { it.id }) { entry ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(entry) }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        Text("MOVE safety: Copy → Verify → Delay → Revalidate → Source Delete.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text(if (state.destinationMode == ManualOperation.MOVE) "Move here safely" else "Copy here") }
        }
    }
}

@Composable
private fun NameDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { Button(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PropertiesDialog(item: BrowserEntry, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.name, fontWeight = FontWeight.Bold)
                Text("Type: ${if (item.isDirectory) "Folder" else fileTypeLabel(item)}")
                if (!item.isDirectory) Text("Size: ${humanBytesLocal(item.size)} (${item.size} bytes)")
                if (item.modified > 0L) Text("Modified: ${formatDate(item.modified)}")
                Text("Storage: ${when (item.storage) { StorageKind.PHONE -> "Phone"; StorageKind.USB -> "USB / SSD"; StorageKind.SHIZUKU -> "Restricted • Shizuku" }}")
                Text("Location: ${item.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

private fun fileComparator(sort: FileSort): Comparator<BrowserEntry> {
    val folderFirst = compareBy<BrowserEntry> { !it.isDirectory }
    val secondary = when (sort) {
        FileSort.NAME -> compareBy<BrowserEntry> { it.name.lowercase(Locale.US) }
        FileSort.DATE -> compareByDescending<BrowserEntry> { it.modified }
        FileSort.SIZE -> compareByDescending<BrowserEntry> { it.size }
        FileSort.TYPE -> compareBy<BrowserEntry> { extensionOf(it.name) }.thenBy { it.name.lowercase(Locale.US) }
    }
    return folderFirst.then(secondary)
}

private fun matchesCategory(entry: BrowserEntry, category: FileCategory): Boolean {
    if (category == FileCategory.ALL) return true
    if (category == FileCategory.FOLDERS) return entry.isDirectory
    if (entry.isDirectory) return false
    val ext = extensionOf(entry.name)
    return when (category) {
        FileCategory.VIDEO -> ext in setOf("mp4", "mkv", "mov", "webm", "avi", "m4v", "3gp", "ts")
        FileCategory.IMAGE -> ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")
        FileCategory.AUDIO -> ext in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")
        FileCategory.DOCUMENT -> ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip", "rar", "7z", "apk")
        else -> false
    }
}

private fun categoryLabel(category: FileCategory): String = when (category) {
    FileCategory.ALL -> "All"
    FileCategory.FOLDERS -> "Folders"
    FileCategory.VIDEO -> "Videos"
    FileCategory.IMAGE -> "Images"
    FileCategory.AUDIO -> "Audio"
    FileCategory.DOCUMENT -> "Documents"
}

private fun sortLabel(sort: FileSort): String = when (sort) {
    FileSort.NAME -> "Sort by name"
    FileSort.DATE -> "Sort by newest"
    FileSort.SIZE -> "Sort by size"
    FileSort.TYPE -> "Sort by type"
}

private fun isVideoEntry(item: BrowserEntry): Boolean = extensionOf(item.name) in setOf("mp4", "mkv", "mov", "webm", "avi", "m4v", "3gp", "ts")

private fun fileTypeLabel(item: BrowserEntry): String = extensionOf(item.name).uppercase(Locale.US).ifBlank { item.mimeType ?: "File" }
private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.US)

private fun humanBytesLocal(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val group = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(group.toDouble())
    return if (group == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[group])
}

private fun formatDate(ms: Long): String = if (ms <= 0L) "" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))
