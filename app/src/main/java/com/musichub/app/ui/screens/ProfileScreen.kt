package com.musichub.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musichub.app.ui.components.styledSurface

/** Placeholder for the future account system. */
@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier.fillMaxSize().padding(top = top + 48.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Person, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text("Guest", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.fillMaxWidth().styledSurface().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Accounts are coming soon. Sign in to sync playlists and favourites across your devices.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = {}, enabled = false) { Text("Sign in (soon)") }
        }
    }
}
