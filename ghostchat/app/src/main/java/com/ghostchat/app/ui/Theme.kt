package com.ghostchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostchat.app.BubbleMode
import com.ghostchat.app.Presets
import com.ghostchat.app.Strings
import com.ghostchat.app.ThemeSpec

val LocalGhost = staticCompositionLocalOf { Presets.ghostDark }
val LocalStrings = staticCompositionLocalOf { Strings(emptyMap(), emptyMap()) }

@Composable
fun GhostTheme(t: ThemeSpec, content: @Composable () -> Unit) {
    val txt = Color(t.text)
    val scheme = if (t.dark) darkColorScheme(
        primary = Color(t.primary), onPrimary = Color(t.onPrimary), background = Color(t.bg),
        onBackground = txt, surface = Color(t.surface), onSurface = txt,
        surfaceVariant = Color(t.surface), onSurfaceVariant = txt.copy(alpha = .7f)
    ) else lightColorScheme(
        primary = Color(t.primary), onPrimary = Color(t.onPrimary), background = Color(t.bg),
        onBackground = txt, surface = Color(t.surface), onSurface = txt,
        surfaceVariant = Color(t.surface), onSurfaceVariant = txt.copy(alpha = .7f)
    )
    CompositionLocalProvider(LocalGhost provides t) { MaterialTheme(colorScheme = scheme, content = content) }
}

@Composable
fun Bubble(text: String, out: Boolean, mode: BubbleMode, size: Float) {
    val g = LocalGhost.current
    val right = when (mode) { BubbleMode.SPLIT -> out; BubbleMode.ALL_LEFT -> false; BubbleMode.ALL_RIGHT -> true }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (right) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text, color = Color(g.text), fontSize = size.sp,
            modifier = Modifier.widthIn(max = 280.dp)
                .background(Color(if (out) g.bubbleOut else g.bubbleIn), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun topColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
    actionIconContentColor = MaterialTheme.colorScheme.onSurface
)
