package com.musichub.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.musichub.app.domain.model.ThemeMode
import com.musichub.app.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "musichub_settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val GLASS_EFFECT = booleanPreferencesKey("glass_effect_enabled")
        val DOWNLOAD_LYRICS = booleanPreferencesKey("download_lyrics_enabled")
        val RECENT = stringPreferencesKey("recent_tracks")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    private val preferences: Flow<Preferences> = dataStore.data.catch { e ->
        // A corrupted/unreadable file must not crash the UI - fall back to defaults.
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    /** Liquid Glass UI (true) vs Matte/Solid UI (false). */
    val isGlassEffectEnabled: Flow<Boolean> =
        preferences.map { it[Keys.GLASS_EFFECT] ?: true }.distinctUntilChanged()

    /** Whether lyrics are fetched and stored when a track is downloaded. */
    val downloadLyricsEnabled: Flow<Boolean> =
        preferences.map { it[Keys.DOWNLOAD_LYRICS] ?: true }.distinctUntilChanged()

    /** Last played tracks, newest first (used by "Continue Listening"). */
    val recentTracks: Flow<List<Track>> =
        preferences.map { decode(it[Keys.RECENT]) }.distinctUntilChanged()

    /** Selected app theme (System / Light / Dark / OLED). */
    val themeMode: Flow<ThemeMode> = preferences.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME_MODE] } ?: ThemeMode.SYSTEM
    }.distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setGlassEffectEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.GLASS_EFFECT] = enabled }
    }

    suspend fun setDownloadLyricsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DOWNLOAD_LYRICS] = enabled }
    }

    suspend fun addRecent(track: Track) {
        dataStore.edit { prefs ->
            val updated = (listOf(track.copy(streamUrl = null)) + decode(prefs[Keys.RECENT]).filter { it.id != track.id })
                .take(MAX_RECENT)
            prefs[Keys.RECENT] = encode(updated)
        }
    }

    private fun encode(tracks: List<Track>): String = JSONArray().apply {
        tracks.forEach {
            put(
                JSONObject()
                    .put("id", it.id).put("title", it.title).put("artist", it.artist)
                    .put("duration", it.duration).put("thumb", it.thumbnailUrl)
            )
        }
    }.toString()

    private fun decode(json: String?): List<Track> = try {
        if (json.isNullOrBlank()) emptyList() else {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Track(o.getString("id"), o.getString("title"), o.optString("artist"),
                    o.optLong("duration"), o.optString("thumb"), null)
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private companion object {
        const val MAX_RECENT = 15
    }
}
