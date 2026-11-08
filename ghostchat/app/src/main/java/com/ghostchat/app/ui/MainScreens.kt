package com.ghostchat.app.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostchat.app.*

@Composable
fun MainScreen(vm: AppViewModel, st: UiState, sv: SessionVM) {
    var page by rememberSaveable { mutableStateOf("list") }
    val call = sv.call
    val inc = sv.incoming
    val open = sv.openChat
    BackHandler(open == null && page != "list") {
        page = when (page) { "themeEditor", "langEditor" -> "settings"; else -> "list" }
    }
    when {
        call != null -> CallScreen(sv, call)
        inc != null -> IncomingCallScreen(sv, inc)
        open != null -> { BackHandler { sv.closeChat() }; ChatScreen(st, sv, open) }
        else -> when (page) {
            "list", "archived" -> ChatList(st, sv, page == "archived") { page = it }
            "settings" -> SettingsScreen(vm, st, sv, { page = "list" }, { page = "themeEditor" }, { page = "langEditor" })
            "themeEditor" -> ThemeEditor(st.theme, { vm.saveTheme(it); page = "settings" }, { page = "settings" })
            "langEditor" -> LangEditor({ vm.saveLang(it); page = "settings" }, { page = "settings" })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatList(st: UiState, sv: SessionVM, archivedView: Boolean, go: (String) -> Unit) {
    val s = LocalStrings.current
    var showNew by remember { mutableStateOf(false) }
    val shown = sv.chats.filter { it.archived == archivedView }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(colors = topColors(), title = { Text(if (archivedView) s["archived"] else s["chats"]) },
            navigationIcon = { if (archivedView) IconButton({ go("list") }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { IconButton({ go("settings") }) { Icon(Icons.Default.Settings, null) } })
    }, floatingActionButton = {
        if (!archivedView) FloatingActionButton({ sv.error = null; showNew = true }) { Icon(Icons.Default.Add, null) }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (!archivedView) item {
                val n = sv.chats.count { it.archived }
                if (n > 0) Row(Modifier.fillMaxWidth().clickable { go("archived") }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) { Text("🗄") }
                    Spacer(Modifier.width(12.dp))
                    Text("${s["archived"]} ($n)", color = MaterialTheme.colorScheme.onBackground)
                }
            }
            if (shown.isEmpty()) item {
                Text(if (archivedView) s["no_archived"] else s["no_chats"], Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(shown, key = { it.conversationId }) { c ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    Row(Modifier.fillMaxWidth().combinedClickable(onClick = { sv.open(c) }, onLongClick = { menu = true }).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.other); Spacer(Modifier.width(12.dp))
                        Column {
                            Text(c.other.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            val last = c.last
                            Text(if (last == null) "@${c.other.tag}" else if (last.kind == "voice") "🎤 ${s["voice_note"]}" else last.body.orEmpty(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = (st.textSize - 2).sp, maxLines = 1)
                        }
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(if (c.archived) s["unarchive"] else s["archive"]) }, onClick = { menu = false; sv.toggleArchive(c) })
                    }
                }
            }
        }
    }
    if (showNew) {
        var tag by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showNew = false }, title = { Text(s["new_chat"]) }, text = {
            Column {
                OutlinedTextField(tag, { tag = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } }, label = { Text(s["find_by_tag"]) },
                    prefix = { Text("@") }, singleLine = true)
                sv.error?.let { Text(s[it], color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {
            TextButton({ sv.startChat(tag) { showNew = false } }, enabled = tag.length >= 3 && !sv.busy) { Text(s["start"]) }
        })
    }
}

@Composable
private fun Section(t: String) = Text(t, Modifier.padding(top = 20.dp, bottom = 4.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

@Composable
private fun RadioRow(selected: Boolean, label: String, onClick: () -> Unit, extra: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null); Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground); extra()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, st: UiState, sv: SessionVM, onBack: () -> Unit, onNewTheme: () -> Unit, onNewLang: () -> Unit) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) sv.changeAvatar(uri) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(colors = topColors(), title = { Text(s["settings"]) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            item {
                sv.me?.let { me ->
                    Section(s["my_profile"])
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(me, 64.dp); Spacer(Modifier.width(16.dp))
                        Column {
                            Text(me.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            Text("@${me.tag}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row {
                        TextButton({ picker.launch("image/*") }) { Text(s["change_photo"]) }
                        TextButton({
                            val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, s.fmt("share_text", me.tag)) }
                            ctx.startActivity(Intent.createChooser(i, null))
                        }) { Text(s["share_tag"]) }
                    }
                }
                Section(s["text_size"])
                Slider(st.textSize, { vm.setTextSize(it) }, valueRange = 12f..28f)
                Section(s["bubbles"])
                RadioRow(st.bubbleMode == BubbleMode.SPLIT, s["bubbles_split"], { vm.setBubbles(BubbleMode.SPLIT) })
                RadioRow(st.bubbleMode == BubbleMode.ALL_LEFT, s["bubbles_left"], { vm.setBubbles(BubbleMode.ALL_LEFT) })
                RadioRow(st.bubbleMode == BubbleMode.ALL_RIGHT, s["bubbles_right"], { vm.setBubbles(BubbleMode.ALL_RIGHT) })
                Spacer(Modifier.height(8.dp))
                Bubble(s["msg_in"], false, st.bubbleMode, st.textSize)
                Spacer(Modifier.height(6.dp))
                Bubble(s["msg_out"], true, st.bubbleMode, st.textSize)
                Section(s["theme"])
            }
            items(Presets.all + st.customThemes) { t ->
                RadioRow(st.theme.name == t.name, t.name, { vm.selectTheme(t) }) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Color(t.primary)))
                    if (st.customThemes.contains(t)) TextButton({ vm.deleteTheme(t.name) }) { Text("✕") }
                }
            }
            item {
                OutlinedButton(onNewTheme, Modifier.fillMaxWidth()) { Text(s["new_theme"]) }
                Section(s["languages"])
            }
            items(Builtin.langs.values.toList() + st.customLangs) { l ->
                RadioRow(st.langCode == l.code, l.name, { vm.selectLang(l.code) })
            }
            item {
                OutlinedButton(onNewLang, Modifier.fillMaxWidth()) { Text(s["new_language"]) }
                Spacer(Modifier.height(16.dp))
                OutlinedButton({ sv.signOut() }, Modifier.fillMaxWidth()) { Text(s["sign_out"]) }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
