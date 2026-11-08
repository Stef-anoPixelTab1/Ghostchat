package com.ghostchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ghostchat.app.Profile

@Composable
fun Avatar(p: Profile, size: Dp = 48.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        if (p.avatarUrl != null) AsyncImage(p.avatarUrl, null, Modifier.size(size), contentScale = ContentScale.Crop)
        else Text(p.title.removePrefix("@").take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold, fontSize = (size.value / 2.5f).sp)
    }
}
