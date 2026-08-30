package com.lilac.anime

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.lilac.anime.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

suspend fun prepareSyncedSubtitleFile(
    context: Context,
    subtitlePath: String,
    animeId: String,
    episodeNumber: Int,
    offsetMs: Long
): String? = withContext(Dispatchers.IO) {
    if (offsetMs == 0L) return@withContext subtitlePath

    try {
        val lower = subtitlePath.lowercase(Locale.ROOT)
        if (!lower.endsWith(".vtt") && !lower.endsWith(".srt")) return@withContext null

        val sourceText = if (
            subtitlePath.startsWith("http://") || subtitlePath.startsWith("https://")
        ) {
            val connection = (URL(subtitlePath).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 30000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) LilacAnime/1.0")
            }
            try {
                if (connection.responseCode !in 200..299) return@withContext null
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
        } else {
            File(
                subtitlePath.removePrefix("file://")
            ).takeIf { it.isFile }?.readText(Charsets.UTF_8) ?: return@withContext null
        }

        val timestampRegex = Regex(
            """(\d{1,2}:)?\d{2}:\d{2}[.,]\d{3}\s*-->\s*(\d{1,2}:)?\d{2}:\d{2}[.,]\d{3}"""
        )
        val timePartRegex = Regex("""\d{1,2}:\d{2}:\d{2}[.,]\d{3}""")

        fun parseTime(value: String): Long {
            val normalized = value.replace(',', '.')
            val parts = normalized.split(':')
            return when (parts.size) {
                2 -> {
                    val sec = parts[0].toLong()
                    val ms = parts[1].toLong()
                    sec * 1000L + ms
                }
                3 -> {
                    val hour = parts[0].toLong()
                    val minute = parts[1].toLong()
                    val secParts = parts[2].split('.')
                    hour * 3_600_000L +
                        minute * 60_000L +
                        secParts[0].toLong() * 1000L +
                        secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull().orZero()
                }
                else -> 0L
            }
        }

        fun Long.formatVtt(): String {
            val safe = coerceAtLeast(0L)
            val h = safe / 3_600_000L
            val m = (safe % 3_600_000L) / 60_000L
            val s = (safe % 60_000L) / 1000L
            val ms = safe % 1000L
            return "%02d:%02d:%02d.%03d".format(Locale.ROOT, h, m, s, ms)
        }

        val adjusted = sourceText.lineSequence().joinToString("\n") { line ->
            if (!timestampRegex.containsMatchIn(line)) {
                line
            } else {
                timePartRegex.replace(line) { match ->
                    (parseTime(match.value) + offsetMs).coerceAtLeast(0L).formatVtt()
                }
            }
        }

        val dir = File(context.filesDir, "synced_subtitles").apply { mkdirs() }
        val safeOffset = if (offsetMs >= 0) "p$offsetMs" else "m${-offsetMs}"
        val extension = if (lower.endsWith(".srt")) "srt" else "vtt"
        val target = File(dir, "${animeId}_${episodeNumber}_${safeOffset}.$extension")
        target.writeText(adjusted, Charsets.UTF_8)
        Log.d("Subtitle", "SYNCED_FILE path=${target.absolutePath} offsetMs=$offsetMs")
        target.absolutePath
    } catch (e: Exception) {
        Log.e("Subtitle", "SYNC_PREPARE_FAILED path=$subtitlePath offsetMs=$offsetMs", e)
        null
    }
}
