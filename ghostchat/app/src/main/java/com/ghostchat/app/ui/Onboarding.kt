package com.ghostchat.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.text.KeyboardOptions
import com.ghostchat.app.SessionVM

private val TAG_RE = Regex("^[a-z0-9_]{3,20}$")

@Composable
fun OnboardingScreen(sv: SessionVM) {
    val s = LocalStrings.current
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photo = it }
    val step = if (sv.authed != true) (if (sv.emailSent == null) 0 else 1) else 2

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("👻", fontSize = 56.sp)
        Text(if (step == 2) s["profile_title"] else s["welcome"], fontSize = 24.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(24.dp))
        when (step) {
            0 -> {
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(s["email"]) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Spacer(Modifier.height(12.dp))
                Button({ sv.sendCode(email.trim()) }, Modifier.fillMaxWidth(), enabled = !sv.busy && email.contains("@")) { Text(s["send_code"]) }
            }
            1 -> {
                Text(s.fmt("code_sent", sv.emailSent ?: ""), color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(code, { code = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text(s["code"]) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                Spacer(Modifier.height(12.dp))
                Button({ sv.verify(code) }, Modifier.fillMaxWidth(), enabled = !sv.busy && code.length >= 6) { Text(s["verify"]) }
                TextButton({ sv.emailSent = null; sv.error = null }) { Text(s["email"]) }
            }
            else -> {
                Box(Modifier.size(110.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                    .clickable { picker.launch("image/*") }, contentAlignment = Alignment.Center) {
                    if (photo != null) AsyncImage(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text("📷", fontSize = 36.sp)
                }
                TextButton({ picker.launch("image/*") }) { Text(s["choose_photo"]) }
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(s["display_name"]) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(tag, { tag = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(20) }, Modifier.fillMaxWidth(),
                    label = { Text(s["tag"]) }, prefix = { Text("@") }, singleLine = true, supportingText = { Text(s["tag_hint"]) })
                Spacer(Modifier.height(12.dp))
                Button({ sv.createProfile(tag, name, photo) }, Modifier.fillMaxWidth(), enabled = !sv.busy && TAG_RE.matches(tag)) { Text(s["start"]) }
            }
        }
        sv.error?.let { Spacer(Modifier.height(12.dp)); Text(s[it], color = MaterialTheme.colorScheme.error) }
    }
}
