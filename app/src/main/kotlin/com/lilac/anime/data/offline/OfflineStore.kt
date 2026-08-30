package com.lilac.anime

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import com.lilac.anime.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
object OfflineStore {
    private const val PREF_NAME = "lilac_offline_store"

    suspend fun savePlayerSettings(context: Context, settings: PlayerSettings) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("pref_default_quality", settings.defaultQuality)
            putString("pref_subtitle_font", settings.subtitleFont)
            putFloat("pref_subtitle_size", settings.subtitleSize)
            putInt("pref_text_color", settings.textColor)
            putInt("pref_background_color", settings.backgroundColor)
            putInt("pref_stroke_color", settings.strokeColor)
            putLong("pref_sync_offset_ms", settings.syncOffsetMs)
            putFloat(
                "pref_subtitle_bottom_padding_fraction",
                settings.subtitleBottomPaddingFraction
            )
            putString("pref_subtitle_source", settings.subtitleSourcePreference)
            putString("pref_custom_font_path", settings.customFontPath)
            putBoolean("pref_show_ani_skip_button", settings.showAniSkipButton)
            putInt("pref_double_tap_seek_seconds", settings.doubleTapSeekSeconds)
            apply()
        }
    }

    suspend fun getPlayerSettings(context: Context): PlayerSettings = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // Older Csora builds temporarily applied a global +1000ms offset. That
        // offset was stored in the normal player preference, so clear only that
        // one legacy value once and then leave all future user adjustments alone.
        val syncOffset = prefs.getLong("pref_sync_offset_ms", 0L)
        val migratedLegacyCsoraSync = prefs.getBoolean("migrated_legacy_csora_sync_1000", false)
        val normalizedSyncOffset = if (!migratedLegacyCsoraSync && syncOffset == 1000L) {
            prefs.edit()
                .putLong("pref_sync_offset_ms", 0L)
                .putBoolean("migrated_legacy_csora_sync_1000", true)
                .apply()
            0L
        } else {
            if (!migratedLegacyCsoraSync) {
                prefs.edit().putBoolean("migrated_legacy_csora_sync_1000", true).apply()
            }
            syncOffset
        }

        PlayerSettings(
            defaultQuality = prefs.getString("pref_default_quality", "1080p") ?: "1080p",
            subtitleFont = prefs.getString("pref_subtitle_font", "기본체") ?: "기본체",
            subtitleSize = prefs.getFloat("pref_subtitle_size", 100f),
            textColor = prefs.getInt("pref_text_color", android.graphics.Color.WHITE),
            backgroundColor = prefs.getInt("pref_background_color", android.graphics.Color.TRANSPARENT),
            strokeColor = prefs.getInt("pref_stroke_color", android.graphics.Color.BLACK),
            syncOffsetMs = normalizedSyncOffset,
            subtitleBottomPaddingFraction = prefs.getFloat(
                "pref_subtitle_bottom_padding_fraction",
                0.12f
            ).coerceIn(0.03f, 0.45f),
            subtitleSourcePreference = prefs.getString("pref_subtitle_source", "linkkf")
                ?.takeIf { it == "linkkf" || it == "kairan" || it == "csora" } ?: "linkkf",
            customFontPath = prefs.getString("pref_custom_font_path", null),
            showAniSkipButton = prefs.getBoolean("pref_show_ani_skip_button", true),
            doubleTapSeekSeconds = prefs.getInt("pref_double_tap_seek_seconds", 10).coerceIn(1, 120)
        )
    }

    suspend fun saveWatchHistory(context: Context, history: List<WatchProgress>) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        history.forEach { item ->
            val json = JSONObject().apply {
                put("animeId", item.animeId)
                put("episodeNumber", item.episodeNumber)
                put("progress", item.progress.toDouble())
            }
            array.put(json)
        }
        prefs.edit().putString("saved_watch_history", array.toString()).apply()
    }

    suspend fun getWatchHistory(context: Context): List<WatchProgress> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString("saved_watch_history", null) ?: return@withContext emptyList()
        try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<WatchProgress>()
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                list.add(
                    WatchProgress(
                        animeId = json.getString("animeId"),
                        episodeNumber = json.getInt("episodeNumber"),
                        progress = json.getDouble("progress").toFloat()
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveLibrary(context: Context, library: Set<String>) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray(library.toList())
        prefs.edit().putString("saved_library", jsonArray.toString()).apply()
    }

    suspend fun getLibrary(context: Context): Set<String> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString("saved_library", null) ?: return@withContext emptySet()
        try {
            val jsonArray = JSONArray(jsonString)
            val set = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                set.add(jsonArray.getString(i))
            }
            set
        } catch (e: Exception) {
            emptySet()
        }
    }

    suspend fun saveAnimeList(context: Context, list: List<Anime>) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        list.forEach { anime ->
            val json = JSONObject().apply {
                put("id", anime.id)
                put("title", anime.title)
                put("poster", anime.poster)
                put("backdrop", anime.backdrop)
                put("description", anime.description)
                put("genres", JSONArray(anime.genres))
            }
            array.put(json)
        }
        prefs.edit().putString("cached_anime_list", array.toString()).apply()
    }

    suspend fun getSavedAnimeList(context: Context): List<Anime> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString("cached_anime_list", null) ?: return@withContext emptyList()
        try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<Anime>()
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                val genresJson = json.optJSONArray("genres")
                val genresList = mutableListOf<String>()
                if (genresJson != null) {
                    for (j in 0 until genresJson.length()) {
                        genresList.add(genresJson.getString(j))
                    }
                }
                list.add(
                    Anime(
                        id = json.getString("id"),
                        title = json.getString("title"),
                        poster = json.optString("poster", ""),
                        backdrop = json.optString("backdrop", ""),
                        description = json.optString("description", ""),
                        genres = genresList
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveAnime(context: Context, anime: Anime) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("id", anime.id)
            put("title", anime.title)
            put("poster", anime.poster)
            put("backdrop", anime.backdrop)
            put("description", anime.description)
            put("genres", JSONArray(anime.genres))
        }
        prefs.edit().putString("anime_${anime.id}", json.toString()).apply()
    }

    suspend fun getAnime(context: Context, animeId: String): Anime? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString("anime_$animeId", null) ?: return@withContext null
        try {
            val json = JSONObject(jsonString)
            val genresJson = json.optJSONArray("genres")
            val genresList = mutableListOf<String>()
            if (genresJson != null) {
                for (i in 0 until genresJson.length()) {
                    genresList.add(genresJson.getString(i))
                }
            }
            Anime(
                id = json.getString("id"),
                title = json.getString("title"),
                poster = json.optString("poster", ""),
                backdrop = json.optString("backdrop", ""),
                description = json.optString("description", ""),
                genres = genresList
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveEpisode(context: Context, animeId: String, episode: Episode) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = "ep_${animeId}_${episode.number}"
        val json = JSONObject().apply {
            put("id", episode.id)
            put("number", episode.number)
            put("title", episode.title)
            put("videoUrl", episode.videoUrl)
            put("vttUrl", episode.vttUrl)
        }
        prefs.edit().putString(key, json.toString()).apply()
    }

    suspend fun getEpisode(context: Context, animeId: String, episodeNumber: Int): Episode? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = "ep_${animeId}_${episodeNumber}"
        val jsonString = prefs.getString(key, null) ?: return@withContext null
        try {
            val json = JSONObject(jsonString)
            Episode(
                id = json.getString("id"),
                number = json.getInt("number"),
                title = json.getString("title"),
                videoUrl = if (json.has("videoUrl") && !json.isNull("videoUrl")) json.getString("videoUrl") else null,
                vttUrl = if (json.has("vttUrl") && !json.isNull("vttUrl")) json.getString("vttUrl") else null
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getEpisodesForAnime(context: Context, animeId: String): List<Episode> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val episodes = mutableListOf<Episode>()
        val prefix = "ep_${animeId}_"

        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { key ->
            val jsonString = prefs.getString(key, null)
            if (jsonString != null) {
                try {
                    val json = JSONObject(jsonString)
                    episodes.add(
                        Episode(
                            id = json.getString("id"),
                            number = json.getInt("number"),
                            title = json.getString("title"),
                            videoUrl = if (json.has("videoUrl") && !json.isNull("videoUrl")) json.getString("videoUrl") else null,
                            vttUrl = if (json.has("vttUrl") && !json.isNull("vttUrl")) json.getString("vttUrl") else null
                        )
                    )
                } catch (_: Exception) {}
            }
        }
        episodes.sortedBy { it.number }
    }

    suspend fun removeEpisode(context: Context, animeId: String, episodeNumber: Int) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = "ep_${animeId}_${episodeNumber}"
        prefs.edit().remove(key).apply()
    }
}

