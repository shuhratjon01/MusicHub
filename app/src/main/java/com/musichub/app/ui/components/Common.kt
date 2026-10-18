package com.musichub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musichub.app.data.Playlist
import com.musichub.app.data.MockTrack
import com.musichub.app.ui.theme.LocalUiStyle
import com.musichub.app.ui.theme.UiStyle

/** Card background that follows the current Glass / Matte style. */
@Composable
fun Modifier.styledSurface(radius: Dp = 20.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return if (LocalUiStyle.current == UiStyle.Glass) {
        this.clip(shape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f))
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(0.45f), Color.White.copy(0.05f), Color.White.copy(0.25f))
                ),
                shape,
            )
    } else {
        this.clip(shape).background(MaterialTheme.colorScheme.surface)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
fun CoverArt(colors: List<Color>, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(size / 2.5f))
    }
}

@Composable
fun TrackCard(track: MockTrack, modifier: Modifier = Modifier) {
    Column(modifier.width(150.dp)) {
        CoverArt(track.colors, 150.dp)
        Spacer(Modifier.height(8.dp))
        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground)
        Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(0.65f))
    }
}

@Composable
fun PlaylistCard(playlist: Playlist, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(170.dp)
            .styledSurface()
            .padding(12.dp)
    ) {
        CoverArt(playlist.colors, 146.dp)
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground)
        Text("${playlist.tracksCount} tracks", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(0.65f))
    }
}

@Composable
fun TrackRow(track: MockTrack, modifier: Modifier = Modifier, rank: Int? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .styledSurface(16.dp)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text("$rank", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(0.7f))
        }
        CoverArt(track.colors, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground)
            Text("${track.artist} • ${track.album}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(0.65f))
        }
        Text(track.duration, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(0.65f))
        IconButton(onClick = { }) {
            Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onBackground.copy(0.7f))
        }
    }
}
