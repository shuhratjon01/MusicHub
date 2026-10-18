package com.musichub.app.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musichub.app.data.download.DownloadState
import com.musichub.app.domain.model.Track
import com.musichub.app.domain.model.formatDuration

/** Cover image (URL or local File) with a neutral placeholder while loading / on failure. */
@Composable
fun TrackCover(model: Any?, modifier: Modifier = Modifier, radius: Dp = 12.dp) {
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
    )
}

/** List row. Tap = play. [coverModel] can be a URL or a local File. */
@Composable
fun RemoteTrackRow(
    track: Track,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    coverModel: Any? = track.thumbnailUrl,
    onClick: () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().styledSurface(16.dp).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackCover(coverModel, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "${track.artist} • ${track.duration.formatDuration()}", maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(0.65f),
            )
        }
        trailing()
    }
}

/** Horizontal-list card ("Continue Listening"). */
@Composable
fun RemoteTrackCard(track: Track, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(150.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        TrackCover(track.thumbnailUrl, Modifier.size(150.dp), 16.dp)
        Spacer(Modifier.height(8.dp))
        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground)
        Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(0.65f))
    }
}

/** Grid cell ("Top Recommendations"). */
@Composable
fun RemoteGridTrack(track: Track, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().styledSurface().clickable(onClick = onClick).padding(10.dp)) {
        TrackCover(track.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(1f), 14.dp)
        Spacer(Modifier.height(6.dp))
        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground)
        Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(0.65f))
    }
}

/** Download status indicator + the three-dots menu. */
@Composable
fun TrackTrailing(
    track: Track,
    downloadState: DownloadState?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onLyrics: () -> Unit,
) {
    when (downloadState) {
        DownloadState.Queued -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        is DownloadState.Downloading -> CircularProgressIndicator(
            progress = { downloadState.progress / 100f },
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        DownloadState.Completed -> Icon(
            Icons.Default.CheckCircle, "Downloaded",
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp),
        )
        is DownloadState.Failed -> Icon(
            Icons.Default.Refresh, "Download failed",
            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp),
        )
        null -> Unit
    }
    TrackMenu(track, downloadState, onPlay, onDownload, onCancel, onDelete, onLyrics)
}

@Composable
fun TrackMenu(
    track: Track,
    downloadState: DownloadState?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onLyrics: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    @Composable
    fun Item(label: String, icon: ImageVector, action: () -> Unit) {
        DropdownMenuItem(
            text = { Text(label) },
            leadingIcon = { Icon(icon, null) },
            onClick = { expanded = false; action() },
        )
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onBackground.copy(0.8f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Item("Play", Icons.Default.PlayArrow, onPlay)
            when (downloadState) {
                null, is DownloadState.Failed -> Item("Download", Icons.Default.Download, onDownload)
                DownloadState.Queued, is DownloadState.Downloading ->
                    Item("Cancel download", Icons.Default.Close, onCancel)
                DownloadState.Completed -> Item("Delete download", Icons.Default.Delete, onDelete)
            }
            Item("Lyrics", Icons.Default.Description, onLyrics)
            Item("Share", Icons.Default.Share) { shareTrack(context, track) }
        }
    }
}

private fun shareTrack(context: Context, track: Track) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${track.title} — ${track.artist}\nhttps://music.youtube.com/watch?v=${track.id}")
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
