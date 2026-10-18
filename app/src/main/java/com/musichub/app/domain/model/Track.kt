package com.musichub.app.domain.model

/**
 * @param duration length in **seconds** (0 if unknown)
 * @param streamUrl direct audio URL; `null` for search results (resolved lazily by
 *   [com.musichub.app.data.media.MediaRepository.fetchTrackDetails]) or a `file://` URI for downloaded tracks.
 *   Remote stream URLs expire after a few hours - never persist them.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val thumbnailUrl: String,
    val streamUrl: String?,
)

data class TrackDetails(
    val track: Track,
    val lyrics: String?,
    val highResCoverUrl: String?,
)

fun Long.formatDuration(): String {
    if (this <= 0) return "--:--"
    val h = this / 3600
    val m = (this % 3600) / 60
    val s = this % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
