package com.nagram.usbbridge.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nagram.usbbridge.R
import com.nagram.usbbridge.ui.components.StatusPill
import com.nagram.usbbridge.ui.theme.AccentMint
import com.nagram.usbbridge.ui.theme.AccentViolet

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(Color(0xFF020508))) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentMint.copy(alpha = 0.42f), Color(0xFF0D1C29), Color(0xFF020508)),
                    ),
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White) }
                Text(
                    text = "VID_20260830_193244.mp4",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {}) { Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = Color.White) }
            }

            Column(modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Brightness6, contentDescription = stringResource(R.string.brightness), tint = Color.White.copy(alpha = 0.65f))
                Text(stringResource(R.string.brightness), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
            }
            Column(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = stringResource(R.string.volume), tint = Color.White.copy(alpha = 0.65f))
                Text(stringResource(R.string.volume), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Replay10, contentDescription = "Back 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp))
                Surface(shape = CircleShape, color = AccentMint) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.play), tint = Color(0xFF00201B), modifier = Modifier.padding(18.dp).size(32.dp))
                }
                Icon(Icons.Outlined.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp))
            }

            Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("01:11", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    Text("03:18", style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
                LinearProgressIndicator(
                    progress = { 0.36f },
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentMint,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Fullscreen, contentDescription = stringResource(R.string.fullscreen), tint = Color.White) }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "VID_20260830_193244.mp4",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "1080p · 1.24 GB · Phone/Android/data · 03:18",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(text = stringResource(R.string.exact_duplicate_verified), color = AccentMint)
                    StatusPill(text = stringResource(R.string.ui_foundation_badge), color = AccentViolet)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                        Text(stringResource(R.string.rename), modifier = Modifier.padding(start = 5.dp))
                    }
                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null)
                        Text(stringResource(R.string.move), modifier = Modifier.padding(start = 5.dp))
                    }
                    Button(onClick = {}, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Text(stringResource(R.string.delete), modifier = Modifier.padding(start = 5.dp))
                    }
                }
                Text(
                    text = stringResource(R.string.player_phase_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
