package com.ghostchat.app.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ghostchat.app.ActiveCall
import com.ghostchat.app.IncomingCall
import com.ghostchat.app.SessionVM
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.launch

/** Chiede i permessi (microfono, e fotocamera se video) e poi esegue onGo(video). */
@Composable
fun rememberCallStarter(onGo: (Boolean) -> Unit): (Boolean) -> Unit {
    var pendingVideo by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) onGo(pendingVideo)
    }
    return { video ->
        pendingVideo = video
        launcher.launch(if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        else arrayOf(Manifest.permission.RECORD_AUDIO))
    }
}

@Composable
fun IncomingCallScreen(sv: SessionVM, inc: IncomingCall) {
    val s = LocalStrings.current
    val accept = rememberCallStarter { sv.accept() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        Text(if (inc.row.video) s["incoming_video"] else s["incoming_call"], color = MaterialTheme.colorScheme.onSurfaceVariant)
        Avatar(inc.from, 140.dp)
        Text(inc.from.title, fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button({ sv.decline() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) { Text(s["decline"]) }
            Button({ accept(inc.row.video) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text(s["accept"]) }
        }
    }
}

@Composable
private fun VideoView(room: Room, track: VideoTrack?, modifier: Modifier) {
    if (track == null) return
    key(track) {
        AndroidView(modifier = modifier, factory = { ctx ->
            TextureViewRenderer(ctx).also { room.initVideoRenderer(it); track.addRenderer(it) }
        }, onRelease = { track.removeRenderer(it) })
    }
}

@Composable
fun CallScreen(sv: SessionVM, call: ActiveCall) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val room = remember { LiveKit.create(ctx.applicationContext) }
    var remote by remember { mutableStateOf<VideoTrack?>(null) }
    var local by remember { mutableStateOf<VideoTrack?>(null) }
    var muted by remember { mutableStateOf(false) }
    var camOn by remember { mutableStateOf(call.video) }
    var connected by remember { mutableStateOf(false) }
    BackHandler { sv.hangUp() }

    LaunchedEffect(room) {
        room.events.collect { ev ->
            when (ev) {
                is RoomEvent.TrackSubscribed -> (ev.track as? VideoTrack)?.let { remote = it }
                is RoomEvent.TrackUnsubscribed -> if (ev.track is VideoTrack) remote = null
                else -> {}
            }
        }
    }
    // Ci si collega alla stanza solo quando l'altro ha risposto.
    LaunchedEffect(call.status) {
        if (call.status == "accepted") {
            try {
                val (token, url) = sv.livekitToken(call.id)
                room.connect(url, token)
                room.localParticipant.setMicrophoneEnabled(true)
                if (call.video) room.localParticipant.setCameraEnabled(true)
                connected = true
            } catch (e: Exception) { sv.error = e.message; sv.hangUp() }
        }
    }
    DisposableEffect(room) { onDispose { room.disconnect(); room.release() } }

    Box(Modifier.fillMaxSize().background(Color(0xFF101418))) {
        VideoView(room, remote, Modifier.fillMaxSize())
        if (remote == null) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(call.other, 140.dp)
            Spacer(Modifier.height(16.dp))
            Text(call.other.title, fontSize = 26.sp, color = Color.White)
            Text(if (connected) "●" else if (call.status == "ringing") s["ringing"] else s["connecting"], color = Color.White.copy(alpha = .7f))
        }
        VideoView(room, local, Modifier.align(Alignment.TopEnd).padding(16.dp).size(110.dp, 150.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)))
        Row(Modifier.align(Alignment.BottomCenter).padding(32.dp), horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically) {
            RoundBtn(if (muted) "🔇" else "🎙", Color(0x55FFFFFF)) {
                muted = !muted; scope.launch { runCatching { room.localParticipant.setMicrophoneEnabled(!muted) } }
            }
            if (call.video) RoundBtn(if (camOn) "📷" else "🚫", Color(0x55FFFFFF)) {
                camOn = !camOn
                scope.launch {
                    runCatching { room.localParticipant.setCameraEnabled(camOn) }
                    local = if (camOn) room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack else null
                }
            }
            RoundBtn("📞", Color(0xFFD32F2F)) { sv.hangUp() }
        }
    }
}

@Composable
private fun RoundBtn(label: String, bg: Color, onClick: () -> Unit) {
    Button(onClick, Modifier.size(64.dp), shape = CircleShape, contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg)) { Text(label, fontSize = 24.sp) }
}
