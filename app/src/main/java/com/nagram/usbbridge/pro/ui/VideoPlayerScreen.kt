package com.nagram.usbbridge.pro.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nagram.usbbridge.pro.video.VideoPlaybackRequest
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun PremiumVideoPlayerScreen(
    request: VideoPlaybackRequest,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { context.getSharedPreferences("player_positions", Context.MODE_PRIVATE) }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentIndex by remember { mutableIntStateOf(request.startIndex.coerceIn(0, request.items.lastIndex.coerceAtLeast(0))) }
    var speedMenu by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var gestureText by remember { mutableStateOf<String?>(null) }

    val player = remember(request.items) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(request.items.map { MediaItem.fromUri(it.uri) })
            if (request.items.isNotEmpty()) {
                val idx = request.startIndex.coerceIn(0, request.items.lastIndex)
                val saved = prefs.getLong(positionKey(request.items[idx].id), 0L).coerceAtLeast(0L)
                seekTo(idx, saved)
            }
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex.coerceIn(0, request.items.lastIndex.coerceAtLeast(0))
            }
        }
        player.addListener(listener)
        onDispose {
            if (request.items.isNotEmpty() && player.currentMediaItemIndex in request.items.indices) {
                prefs.edit().putLong(positionKey(request.items[player.currentMediaItemIndex].id), player.currentPosition.coerceAtLeast(0L)).apply()
            }
            player.removeListener(listener)
            player.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(player, currentIndex) {
        while (true) {
            delay(2500L)
            val idx = player.currentMediaItemIndex
            if (idx in request.items.indices) {
                prefs.edit().putLong(positionKey(request.items[idx].id), player.currentPosition.coerceAtLeast(0L)).apply()
            }
        }
    }

    LaunchedEffect(gestureText) {
        if (gestureText != null) {
            delay(800L)
            gestureText = null
        }
    }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.resizeMode = resizeMode
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Left gesture zone: double tap -10s, vertical swipe brightness.
        Box(
            Modifier
                .fillMaxHeight()
                .width(118.dp)
                .align(Alignment.CenterStart)
                .pointerInput(player) {
                    detectTapGestures(onDoubleTap = {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                        gestureText = "−10 sec"
                    })
                }
                .pointerInput(activity) {
                    detectVerticalDragGestures { _, dragAmount ->
                        val window = activity?.window ?: return@detectVerticalDragGestures
                        val current = window.attributes.brightness.takeIf { it >= 0f } ?: 0.5f
                        val next = (current - dragAmount / 900f).coerceIn(0.03f, 1f)
                        val params = window.attributes
                        params.brightness = next
                        window.attributes = params
                        gestureText = "Brightness ${(next * 100).roundToInt()}%"
                    }
                }
        )

        // Right gesture zone: double tap +10s, vertical swipe volume.
        Box(
            Modifier
                .fillMaxHeight()
                .width(118.dp)
                .align(Alignment.CenterEnd)
                .pointerInput(player) {
                    detectTapGestures(onDoubleTap = {
                        val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                        player.seekTo((player.currentPosition + 10_000L).coerceAtMost(duration))
                        gestureText = "+10 sec"
                    })
                }
                .pointerInput(audio) {
                    detectVerticalDragGestures { _, dragAmount ->
                        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val delta = (-dragAmount / 65f).roundToInt()
                        val next = (current + delta).coerceIn(0, maxVolume)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                        gestureText = "Volume ${((next.toFloat() / maxVolume) * 100).roundToInt()}%"
                    }
                }
        )

        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(
                        request.items.getOrNull(currentIndex)?.name ?: "Video",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${currentIndex + 1}/${request.items.size}",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                TextButton(onClick = { speedMenu = true }) { Text("${trimSpeed(speed)}×", color = Color.White) }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                        DropdownMenuItem(
                            text = { Text("${trimSpeed(value)}×") },
                            onClick = {
                                speed = value
                                player.setPlaybackSpeed(value)
                                speedMenu = false
                            }
                        )
                    }
                }
                IconButton(onClick = {
                    resizeMode = when (resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }) { Icon(Icons.Outlined.AspectRatio, contentDescription = "Resize", tint = Color.White) }
                IconButton(onClick = {
                    activity?.let { act ->
                        act.requestedOrientation = if (act.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        } else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }) { Icon(Icons.Outlined.Rotate90DegreesCcw, contentDescription = "Rotate", tint = Color.White) }
                if (Build.VERSION.SDK_INT >= 26) {
                    IconButton(onClick = {
                        activity?.enterPictureInPictureMode(
                            PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                        )
                    }) { Icon(Icons.Outlined.PictureInPictureAlt, contentDescription = "Picture in picture", tint = Color.White) }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0, 0L)
            }) { Icon(Icons.Outlined.FastRewind, contentDescription = "Previous", tint = Color.White) }
            IconButton(onClick = {
                if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            }) { Icon(Icons.Outlined.FastForward, contentDescription = "Next", tint = Color.White) }
        }

        gestureText?.let { text ->
            Surface(
                color = Color.Black.copy(alpha = 0.76f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(text, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
            }
        }
    }
}

private fun positionKey(id: String): String = "video_position_${id.hashCode()}"
private fun trimSpeed(value: Float): String = if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
