package com.lilac.anime

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.datastore.preferences.core.edit
import com.lilac.anime.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SubtitleStore {
    private const val PREF_NAME = "lilac_subtitle_store"
    private fun key(animeId: String, episodeNumber: Int, source: String) = "${animeId}_${episodeNumber}_$source"
    suspend fun save(context: Context, animeId: String, episodeNumber: Int, source: String, path: String?) = withContext(Dispatchers.IO) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(key(animeId, episodeNumber, source), path).apply()
    }
    suspend fun get(context: Context, animeId: String, episodeNumber: Int, source: String): String? = withContext(Dispatchers.IO) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(key(animeId, episodeNumber, source), null)?.takeIf { File(it).isFile }
    }
}
