package com.nagram.usbbridge.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nagram.usbbridge.R
import com.nagram.usbbridge.ui.AppUiState
import com.nagram.usbbridge.ui.DemoDuplicateFiles
import com.nagram.usbbridge.ui.DuplicateFile
import com.nagram.usbbridge.ui.DuplicateKeepRule
import com.nagram.usbbridge.ui.decideDuplicate
import com.nagram.usbbridge.ui.components.ScreenHeader
import com.nagram.usbbridge.ui.components.StatusPill
import com.nagram.usbbridge.ui.theme.AccentMint
import com.nagram.usbbridge.ui.theme.AccentViolet

@Composable
fun DuplicatesScreen(
    state: AppUiState,
    onChooseRule: (DuplicateKeepRule) -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val decision = decideDuplicate(DemoDuplicateFiles, state.keepRule)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenHeader(
                    title = stringResource(R.string.duplicates_title),
                    subtitle = stringResource(R.string.duplicates_subtitle),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {}) { Icon(Icons.Outlined.Tune, contentDescription = null) }
            }
        }
        item { DuplicateSummaryCard() }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = true, onClick = {}, label = { Text(stringResource(R.string.exact)) })
                FilterChip(selected = false, onClick = {}, label = { Text(stringResource(R.string.similar)) })
                FilterChip(selected = false, onClick = {}, label = { Text(stringResource(R.string.history)) })
            }
        }
        item {
            StatusPill(
                text = stringResource(R.string.phone_and_usb),
                color = AccentViolet,
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(text = stringResource(R.string.exact_group), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(R.string.exact_verified),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = AccentMint)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DuplicateFileSpecCard(
                            file = DemoDuplicateFiles[0],
                            isKept = decision.kept.id == DemoDuplicateFiles[0].id,
                            onOpenPlayer = onOpenPlayer,
                            modifier = Modifier.weight(1f),
                        )
                        DuplicateFileSpecCard(
                            file = DemoDuplicateFiles[1],
                            isKept = decision.kept.id == DemoDuplicateFiles[1].id,
                            onOpenPlayer = onOpenPlayer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onChooseRule(DuplicateKeepRule.NEWEST) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                1.dp,
                                if (state.keepRule == DuplicateKeepRule.NEWEST) AccentMint else MaterialTheme.colorScheme.outline,
                            ),
                        ) {
                            Text(stringResource(R.string.keep_newest))
                        }
                        OutlinedButton(
                            onClick = { onChooseRule(DuplicateKeepRule.OLDEST) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                1.dp,
                                if (state.keepRule == DuplicateKeepRule.OLDEST) AccentMint else MaterialTheme.colorScheme.outline,
                            ),
                        ) {
                            Text(stringResource(R.string.keep_oldest))
                        }
                    }
                    Button(onClick = onRequestDelete, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Text(
                            text = stringResource(R.string.delete_selected),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (state.deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.delete_confirmation_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_confirmation_body) +
                        "\n\n" + decision.selectedForDeletion.name,
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissDelete) { Text(stringResource(R.string.confirm_delete)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun DuplicateSummaryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(AccentViolet.copy(alpha = 0.22f), MaterialTheme.colorScheme.surface),
                    ),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(text = "2.4 GB", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        text = stringResource(R.string.space_recoverable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(text = "6 exact copies", color = AccentMint)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = AccentMint, modifier = Modifier.size(18.dp))
                Text(text = stringResource(R.string.no_auto_delete), style = MaterialTheme.typography.bodySmall, color = AccentMint)
            }
        }
    }
}

@Composable
private fun DuplicateFileSpecCard(
    file: DuplicateFile,
    isKept: Boolean,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (isKept) AccentMint else AccentViolet
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, if (isKept) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.58f), MaterialTheme.colorScheme.surfaceVariant),
                        ),
                    )
                    .clickable(onClick = onOpenPlayer)
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.play), modifier = Modifier.padding(7.dp))
                }
                if (isKept) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                        shape = CircleShape,
                        color = AccentMint,
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.padding(3.dp).size(14.dp))
                    }
                }
            }
            Text(text = file.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(text = file.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            SpecLine(stringResource(R.string.file_size), file.sizeLabel)
            SpecLine(stringResource(R.string.file_duration), file.durationLabel)
            SpecLine(stringResource(R.string.file_modified), file.modifiedLabel)
            if (isKept) {
                StatusPill(text = stringResource(R.string.recommended), color = AccentMint)
            }
        }
    }
}

@Composable
private fun SpecLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
