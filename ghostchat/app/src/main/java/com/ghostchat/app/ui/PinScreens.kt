package com.ghostchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostchat.app.AppViewModel
import com.ghostchat.app.UiState
import kotlinx.coroutines.delay

@Composable
fun PinPad(len: Int, enabled: Boolean = true, onDone: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    val primary = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(len) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape)
                    .background(if (i < pin.length) primary else primary.copy(alpha = .25f)))
            }
        }
        Spacer(Modifier.height(32.dp))
        listOf("123", "456", "789", " 0<").forEach { row ->
            Row {
                row.forEach { c ->
                    Box(Modifier.size(78.dp).clip(CircleShape).clickable(enabled = enabled && c != ' ') {
                        if (c == '<') pin = pin.dropLast(1)
                        else {
                            val n = pin + c
                            if (n.length == len) { pin = ""; onDone(n) } else pin = n
                        }
                    }, contentAlignment = Alignment.Center) {
                        if (c != ' ') Text(if (c == '<') "⌫" else c.toString(), fontSize = 26.sp,
                            color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}

@Composable
fun PinSetupScreen(vm: AppViewModel) {
    val s = LocalStrings.current
    var len by remember { mutableStateOf(0) }
    var first by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("👻", fontSize = 56.sp)
        Text(s["welcome"], fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(32.dp))
        if (len == 0) {
            Text(s["choose_len"], color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))
            listOf(4, 6, 8).forEach { n ->
                Button(onClick = { len = n }, Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(s.fmt("digits", n)) }
            }
        } else {
            Text(if (first == null) s["create_pin"] else s["confirm_pin"], fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground)
            if (mismatch) Text(s["pin_mismatch"], color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(24.dp))
            PinPad(len) { pin ->
                when {
                    first == null -> { first = pin; mismatch = false }
                    pin == first -> vm.setPin(pin)
                    else -> { first = null; mismatch = true }
                }
            }
        }
    }
}

@Composable
fun LockScreen(vm: AppViewModel, st: UiState) {
    val s = LocalStrings.current
    var err by remember { mutableStateOf(false) }
    var until by remember { mutableStateOf(st.lockUntil) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) { while (now < until) { delay(500); now = System.currentTimeMillis() } }
    val left = ((until - now) / 1000).coerceAtLeast(0)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("👻", fontSize = 56.sp)
        Text(s["enter_pin"], fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(when { left > 0 -> s.fmt("locked_for", left); err -> s["wrong_pin"]; else -> " " },
            color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(24.dp))
        PinPad(st.pinLen, enabled = left == 0L) { pin ->
            vm.tryUnlock(pin) { ok, u -> if (!ok) { err = true; until = u; now = System.currentTimeMillis() } }
        }
    }
}
