package com.ghostchat.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ghostchat.app.*
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class VoiceRecorder(private val ctx: Context) {
    private var mr: MediaRecorder? = null
    private var file: File? = null
    private var t0 = 0L

    fun start(): Boolean = try {
        val f = File.createTempFile("vn", ".m4a", ctx.cacheDir)
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64000); r.setAudioSamplingRate(44100)
        r.setOutputFile(f.absolutePath); r.prepare(); r.start()
        mr = r; file = f; t0 = System.currentTimeMillis(); true
    } catch (e: Exception) { false }

    fun stop(): Pair<File, Int>? {
        val r = mr ?: return null
        mr = null
        val f = file ?: return null
        val d = (System.currentTimeMillis() - t0).toInt()
        return try { r.stop(); r.release(); f to d } catch (e: Exception) { r.release(); f.delete(); null }
    }
}

private fun fmtTime(iso: String?): String = runCatching {
    OffsetDateTime.parse(iso!!).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault("")

@Composable
fun VoiceBubble(url: String, durationMs: Int, color: Color) {
    var playing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    val sec = durationMs / 1000
    Row(Modifier.clickable {
        if (playing) {
            runCatching { player?.stop() }; player?.release(); player = null; playing = false
        } else {
            val p = MediaPlayer()
            try {
                p.setDataSource(url)
                p.setOnPreparedListener { it.start() }
                p.setOnCompletionListener { it.release(); player = null; playing = false }
                p.prepareAsync(); player = p; playing = true
            } catch (e: Exception) { p.release() }
        }
    }, verticalAlignment = Alignment.CenterVertically) {
        Text(if (playing) "⏸" else "▶", fontSize = 22.sp, color = color)
        Spacer(Modifier.width(10.dp))
        Text("🎤 %d:%02d".format(sec / 60, sec % 60), color = color)
    }
}

@Composable
fun MessageItem(m: Msg, mine: Boolean, mode: BubbleMode) {
    val g = LocalGhost.current
    val right = when (mode) { BubbleMode.SPLIT -> mine; BubbleMode.ALL_LEFT -> false; BubbleMode.ALL_RIGHT -> true }
    val txt = Color(g.text)
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = if (right) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(Modifier.widthIn(max = 290.dp).background(Color(if (mine) g.bubbleOut else g.bubbleIn), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
            if (m.kind == "voice") VoiceBubble(m.mediaUrl.orEmpty(), m.durationMs ?: 0, txt)
            else Text(m.body.orEmpty(), color = txt, fontSize = m.fontSize.sp)   // la dimensione l'ha scelta chi ha scritto
            Text(fmtTime(m.createdAt), fontSize = 11.sp, color = txt.copy(alpha = .6f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(st: UiState, sv: SessionVM, chat: ChatItem) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val me = sv.me ?: return
    var text by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(st.textSize) }
    var sizeMenu by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    val rec = remember { VoiceRecorder(ctx) }
    val list = rememberLazyListState()
    val startCall = rememberCallStarter { video -> sv.startCall(video) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(sv.messages.size) { if (sv.messages.isNotEmpty()) list.animateScrollToItem(sv.messages.lastIndex) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(colors = topColors(),
            title = { Row(verticalAlignment = Alignment.CenterVertically) { Avatar(chat.other, 36.dp); Spacer(Modifier.width(10.dp)); Text(chat.other.title) } },
            navigationIcon = { IconButton({ sv.closeChat() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = {
                IconButton({ startCall(false) }) { Icon(Icons.Default.Call, null) }
                IconButton({ startCall(true) }) { Text("🎥", fontSize = 20.sp) }
            })
    }, bottomBar = {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton({ sizeMenu = true }) { Text("Aa") }
                DropdownMenu(sizeMenu, { sizeMenu = false }) {
                    Column(Modifier.padding(16.dp).width(240.dp)) {
                        Text("${s["text_size_msg"]}: ${size.toInt()}")
                        Slider(size, { size = it }, valueRange = 12f..32f)
                    }
                }
            }
            if (recording) Text("🔴 ${s["hold_to_record"]}", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            else OutlinedTextField(text, { text = it }, Modifier.weight(1f), textStyle = TextStyle(fontSize = size.sp),
                placeholder = { Text(s["message"]) }, maxLines = 4)
            if (text.isNotBlank()) IconButton({ sv.sendText(text.trim(), size); text = "" }) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = MaterialTheme.colorScheme.primary)
            } else Box(Modifier.size(48.dp).pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (!ok) { micLauncher.launch(Manifest.permission.RECORD_AUDIO); return@detectTapGestures }
                    if (rec.start()) {
                        recording = true
                        val released = tryAwaitRelease()
                        recording = false
                        val r = rec.stop()
                        if (r != null) { if (released && r.second > 600) sv.sendVoice(r.first, r.second) else r.first.delete() }
                    }
                })
            }, contentAlignment = Alignment.Center) { Text("🎤", fontSize = 24.sp) }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), state = list, contentPadding = PaddingValues(12.dp)) {
            items(sv.messages, key = { it.id }) { m -> MessageItem(m, m.senderId == me.id, st.bubbleMode) }
        }
    }
}
