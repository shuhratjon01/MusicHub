package com.musichub.app.data

import androidx.compose.ui.graphics.Color

data class MockTrack(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val colors: List<Color>,
)

data class Playlist(val id: Int, val name: String, val tracksCount: Int, val colors: List<Color>)

object MockData {
    private val palettes = listOf(
        listOf(Color(0xFFFF512F), Color(0xFFDD2476)),
        listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
        listOf(Color(0xFF7F00FF), Color(0xFFE100FF)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        listOf(Color(0xFFF7971E), Color(0xFFFFD200)),
        listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
    )
    private val titles = listOf("Neon Drift", "Afterglow", "Glass Rain", "Midnight Signal", "Echo Bloom", "Velvet Static", "Low Orbit", "Paper Moons")
    private val artists = listOf("Nova Reign", "Lumen", "The Parallels", "Kira Vale", "Hollow Atlas", "Sunset Protocol")
    private val albums = listOf("Skyline", "Prism", "Night Shift", "Holograms", "Tidal")

    fun track(i: Int) = MockTrack(
        id = i,
        title = if (i >= titles.size) "${titles[i % titles.size]} ${i / titles.size + 1}" else titles[i],
        artist = artists[i % artists.size],
        album = albums[i % albums.size],
        duration = "%d:%02d".format(2 + i % 3, (i * 17) % 60),
        colors = palettes[i % palettes.size],
    )

    /** Simulates a paginated backend call. */
    fun page(offset: Int, size: Int = 20): List<MockTrack> = List(size) { track(offset + it) }

    val recent: List<MockTrack> = List(8) { track(it) }
    val top: List<MockTrack> = List(6) { track(it + 3) }
    val downloaded: List<MockTrack> = List(5) { track(it + 10) }
    val playlists: List<Playlist> = listOf(
        Playlist(1, "Late Night Drive", 42, palettes[2]),
        Playlist(2, "Focus Flow", 31, palettes[1]),
        Playlist(3, "Workout Fuel", 55, palettes[0]),
        Playlist(4, "Chill Vibes", 27, palettes[3]),
        Playlist(5, "Indie Gold", 38, palettes[4]),
    )
}
