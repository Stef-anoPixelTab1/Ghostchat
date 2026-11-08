package com.ghostchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ghostchat.app.*

@Composable
fun ColorRow(label: String, c: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(c)).border(1.dp, Color.Gray, CircleShape))
            Spacer(Modifier.width(8.dp)); Text(label)
        }
        listOf(16 to Color.Red, 8 to Color.Green, 0 to Color.Blue).forEach { (sh, col) ->
            Slider(((c shr sh) and 0xFF).toFloat(), { nv ->
                val mask = (0xFF shl sh).inv()
                onChange((c and mask) or (nv.toInt() shl sh))
            }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = col, activeTrackColor = col))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditor(base: ThemeSpec, onSave: (ThemeSpec) -> Unit, onBack: () -> Unit) {
    var t by remember { mutableStateOf(base.copy(name = "")) }
    // Tutta la schermata usa il tema in modifica: anteprima dal vivo.
    GhostTheme(t) {
        val s = LocalStrings.current
        Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
            TopAppBar(colors = topColors(), title = { Text(s["new_theme"]) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { TextButton({ onSave(t) }, enabled = t.name.isNotBlank()) { Text(s["save"]) } })
        }) { pad ->
            LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp)) {
                item {
                    OutlinedTextField(t.name, { t = t.copy(name = it) }, Modifier.fillMaxWidth(), label = { Text(s["name"]) }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s["dark_mode"], Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
                        Switch(t.dark, { t = t.copy(dark = it) })
                    }
                    Bubble(s["msg_in"], false, BubbleMode.SPLIT, 16f)
                    Spacer(Modifier.height(6.dp))
                    Bubble(s["msg_out"], true, BubbleMode.SPLIT, 16f)
                    ColorRow(s["c_bg"], t.bg) { t = t.copy(bg = it) }
                    ColorRow(s["c_surface"], t.surface) { t = t.copy(surface = it) }
                    ColorRow(s["c_primary"], t.primary) { t = t.copy(primary = it) }
                    ColorRow(s["c_onprimary"], t.onPrimary) { t = t.copy(onPrimary = it) }
                    ColorRow(s["c_out"], t.bubbleOut) { t = t.copy(bubbleOut = it) }
                    ColorRow(s["c_in"], t.bubbleIn) { t = t.copy(bubbleIn = it) }
                    ColorRow(s["c_text"], t.text) { t = t.copy(text = it) }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LangEditor(onSave: (Lang) -> Unit, onBack: () -> Unit) {
    val s = LocalStrings.current
    val en = Builtin.langs.getValue("en").map
    var name by remember { mutableStateOf("") }
    val vals = remember { mutableStateMapOf<String, String>() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(colors = topColors(), title = { Text(s["new_language"]) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = {
                TextButton({
                    val slug = name.lowercase().filter { it.isLetterOrDigit() }
                    onSave(Lang("x_$slug", name.trim(), vals.filterValues { it.isNotBlank() }.toMap()))
                }, enabled = name.any { it.isLetterOrDigit() }) { Text(s["save"]) }
            })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(s["lang_hint"], color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(s["name"]) }, singleLine = true)
            }
            items(Builtin.keys) { k ->
                OutlinedTextField(vals[k] ?: "", { vals[k] = it }, Modifier.fillMaxWidth(), label = { Text(en.getValue(k)) }, singleLine = true)
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
