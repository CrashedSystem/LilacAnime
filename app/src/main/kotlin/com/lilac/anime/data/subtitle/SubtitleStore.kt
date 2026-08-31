package com.lilac.anime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SubtitleStore {
    private const val PREF_NAME = "lilac_subtitle_store"
    private const val IGNORED_PREFIX = "ignored_"
    private const val FONT_PATH_PREFIX = "font_path_"
    private const val FONT_SOURCE_PREFIX = "font_source_"

    data class SavedSubtitle(
        val source: String,
        val path: String,
        val ignored: Boolean,
        val episodeMatch: Boolean
    )

    /** Reject stale subtitle paths whose filename explicitly identifies another episode. */
    fun pathMatchesEpisode(path: String, episodeNumber: Int): Boolean {
        val file = File(path)
        if (!file.isFile || episodeNumber <= 0) return false
        val name = file.nameWithoutExtension.lowercase(java.util.Locale.ROOT)
        // Our generated cache names contain the authoritative episode segment: _<ep>_direct / _<ep>_archive.
        val generated = Regex("_(\\d{1,3})_(?:direct|archive)(?:_|$)", RegexOption.IGNORE_CASE)
            .find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (generated != null) return generated == episodeNumber

        val patterns = listOf(
            Regex("(?:^|[^0-9])(?:episode|ep|e|#)\\s*0*(\\d{1,3})(?:$|[^0-9])", RegexOption.IGNORE_CASE),
            Regex("(?:^|[^0-9])0*(\\d{1,3})\\s*(?:화|회|편|話)(?:$|[^0-9])")
        )
        val explicit = patterns.asSequence()
            .flatMap { it.findAll(name).asSequence() }
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .firstOrNull()
        return explicit == null || explicit == episodeNumber
    }

    private fun key(animeId: String, episodeNumber: Int, source: String) = "${animeId}_${episodeNumber}_$source"
    private fun fontPathKey(animeId: String, source: String) = FONT_PATH_PREFIX + "${animeId}_$source"
    private fun fontSourceKey(animeId: String, source: String) = FONT_SOURCE_PREFIX + "${animeId}_$source"

    /**
     * Checks both the filename and ASS/SSA header metadata. Dialogue text is
     * intentionally ignored because it can legitimately mention another episode.
     * If the subtitle contains an explicit episode number in its header, it must
     * match the requested episode. With no explicit metadata we fall back to the
     * filename check.
     */
    fun subtitleMatchesEpisode(path: String, episodeNumber: Int): Boolean {
        if (!pathMatchesEpisode(path, episodeNumber)) return false
        val file = File(path)
        if (!file.isFile || episodeNumber <= 0) return false
        val ext = file.extension.lowercase(java.util.Locale.ROOT)
        if (ext !in setOf("ass", "ssa")) return true
        return try {
            val text = file.inputStream().bufferedReader().use { it.readText().take(120_000) }
            val header = text.lineSequence()
                .takeWhile { !it.trim().equals("[Events]", true) }
                .filter {
                    val t = it.trimStart()
                    t.startsWith("Title:", true) ||
                        t.startsWith("Original Script:", true) ||
                        t.startsWith("Comment:", true) ||
                        t.startsWith("Notes:", true)
                }
                .joinToString(" ")
            if (header.isBlank()) return true

            val normalized = header.lowercase(java.util.Locale.ROOT)
            val explicitNumbers = mutableListOf<Int>()
            val patterns = listOf(
                Regex("(?:episode|ep)\\s*0*(\\d{1,3})\\b", RegexOption.IGNORE_CASE),
                Regex("(?:^|[^0-9])0*(\\d{1,3})\\s*(?:화|회|편|話)(?:$|[^0-9])"),
                Regex("(?:^|[^a-z0-9])e0*(\\d{1,3})(?:$|[^a-z0-9])", RegexOption.IGNORE_CASE)
            )
            patterns.forEach { regex ->
                regex.findAll(normalized).forEach { m ->
                    m.groupValues.getOrNull(1)?.toIntOrNull()?.let { explicitNumbers += it }
                }
            }
            explicitNumbers.isEmpty() || explicitNumbers.all { it == episodeNumber }
        } catch (_: Exception) {
            true
        }
    }

    fun getSelectedFont(context: Context, animeId: String, source: String): String? =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(fontPathKey(animeId, source), null)
            ?.takeIf { File(it).isFile }

    fun saveSelectedFont(context: Context, animeId: String, source: String, path: String?) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .apply {
                if (path.isNullOrBlank()) remove(fontPathKey(animeId, source))
                else putString(fontPathKey(animeId, source), path)
                putString(fontSourceKey(animeId, source), source)
            }.apply()
    }

    fun clearSelectedFont(context: Context, animeId: String, source: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .remove(fontPathKey(animeId, source))
            .remove(fontSourceKey(animeId, source))
            .apply()
    }
    private fun ignoredKey(animeId: String, episodeNumber: Int, source: String) = IGNORED_PREFIX + key(animeId, episodeNumber, source)

    suspend fun save(context: Context, animeId: String, episodeNumber: Int, source: String, path: String?) = withContext(Dispatchers.IO) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(key(animeId, episodeNumber, source), path)
            .remove(ignoredKey(animeId, episodeNumber, source))
            .apply()
    }

    suspend fun get(context: Context, animeId: String, episodeNumber: Int, source: String): String? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val k = key(animeId, episodeNumber, source)
        val path = prefs.getString(k, null) ?: return@withContext null
        if (prefs.getBoolean(ignoredKey(animeId, episodeNumber, source), false)) return@withContext null
        if (subtitleMatchesEpisode(path, episodeNumber)) return@withContext path
        prefs.edit().remove(k).remove(ignoredKey(animeId, episodeNumber, source)).apply()
        File(path).takeIf(File::isFile)?.delete()
        null
    }

    suspend fun list(context: Context, animeId: String, episodeNumber: Int): List<SavedSubtitle> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        listOf("linkkf", "kairan", "csora").mapNotNull { source ->
            val path = prefs.getString(key(animeId, episodeNumber, source), null) ?: return@mapNotNull null
            if (!File(path).isFile) return@mapNotNull null
            SavedSubtitle(
                source = source,
                path = path,
                ignored = prefs.getBoolean(ignoredKey(animeId, episodeNumber, source), false),
                episodeMatch = subtitleMatchesEpisode(path, episodeNumber)
            )
        }
    }

    suspend fun setIgnored(context: Context, animeId: String, episodeNumber: Int, source: String, ignored: Boolean) = withContext(Dispatchers.IO) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(ignoredKey(animeId, episodeNumber, source), ignored)
            .apply()
    }

    suspend fun delete(context: Context, animeId: String, episodeNumber: Int, source: String) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val k = key(animeId, episodeNumber, source)
        val path = prefs.getString(k, null)
        prefs.edit().remove(k).remove(ignoredKey(animeId, episodeNumber, source))
            .remove(fontPathKey(animeId, source)).remove(fontSourceKey(animeId, source)).apply()
        path?.let { File(it).takeIf(File::isFile)?.delete() }
    }
}
