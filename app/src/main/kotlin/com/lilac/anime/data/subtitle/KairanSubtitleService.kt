package com.lilac.anime

import kotlinx.coroutines.withTimeoutOrNull

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.datastore.preferences.core.edit
import com.lilac.anime.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
object KairanSubtitleService {
    private const val BLOG_URL = "https://kairan03.blogspot.com"
    private const val CACHE_DIR = "kairan_subtitles"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    suspend fun findSubtitle(context: Context, title: String, episodeNumber: Int): KairanSubtitleResult? =
        withContext(Dispatchers.IO) {
            try {
                Log.d("Kairan", "START_SEARCH title=[$title] episode=$episodeNumber")

                SubtitleStore.get(context, normalizeTitleForFile(title), episodeNumber, "kairan")
                    ?.takeIf { File(it).isFile }
                    ?.let { return@withContext KairanSubtitleResult.DirectFile(it) }

                val postUrl = findBlogPost(context, title, episodeNumber)
                if (postUrl == null) {
                    Log.w("Kairan", "POST_NOT_FOUND title=[$title] episode=$episodeNumber")
                    return@withContext null
                }

                Log.d("Kairan", "POST_FOUND title=[$title] episode=$episodeNumber url=$postUrl")
                val html = getText(postUrl)
                Log.d("Kairan", "POST_HTML_LOADED bytes=${html.length}")

                val links = extractGoogleDriveLinks(html)
                Log.d("Kairan", "DRIVE_LINK_COUNT count=${links.size}")

                for (link in links) {
                    val id = extractGoogleDriveId(link)
                    if (id == null) {
                        Log.w("Kairan", "INVALID_DRIVE_LINK url=$link")
                        continue
                    }
                    Log.d("Kairan", "TRY_DRIVE fileId=$id url=$link")
                    val local = downloadGoogleDriveSubtitle(context, id, title, episodeNumber)
                    if (local != null) {
                        Log.d("Kairan", "SUBTITLE_READY path=$local")
                        return@withContext KairanSubtitleResult.DirectFile(local)
                    }
                }

                Log.w("Kairan", "DRIVE_SUBTITLE_NOT_FOUND url=$postUrl")
                null
            } catch (e: Exception) {
                Log.e("Kairan", "FIND_SUBTITLE_FAILED title=[$title] ep=$episodeNumber", e)
                null
            }
        }

    private data class KairanSearchResult(
        val title: String,
        val url: String
    )

    private const val POST_CACHE_PREFS = "kairan_post_cache"
    private const val SEARCH_TOTAL_TIMEOUT_MS = 4500L

    private fun postCacheKey(title: String, episode: Int): String =
        "${normalizeTitleForFile(title)}#$episode"

    private fun cachedPostUrl(context: Context, title: String, episode: Int): String? =
        context.getSharedPreferences(POST_CACHE_PREFS, Context.MODE_PRIVATE)
            .getString(postCacheKey(title, episode), null)
            ?.takeIf { it.isNotBlank() }

    private fun cachePostUrl(context: Context, title: String, episode: Int, url: String) {
        context.getSharedPreferences(POST_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit().putString(postCacheKey(title, episode), url).apply()
    }

    private suspend fun findBlogPost(context: Context, title: String, episode: Int): String? {
        cachedPostUrl(context, title, episode)?.let { cached ->
            Log.d("Kairan", "POST_CACHE_HIT episode=$episode url=$cached")
            return cached
        }

        val normalizedTitle = kairanSearchTitle(title)
        if (normalizedTitle.isBlank()) return null

        val words = normalizedTitle.split(' ')
            .filter { it.length >= 2 }
            .distinct()
            .sortedByDescending { it.length }

        // Fast path: one full-title request first. Only use 1~2 core words as fallback.
        val fallbackQueries = words.take(2)
            .filter { it != normalizedTitle }
        val searchQueries = buildList {
            add(normalizedTitle)
            addAll(fallbackQueries)
        }.distinct()

        val deadlineResult = withTimeoutOrNull(SEARCH_TOTAL_TIMEOUT_MS) {
            for (query in searchQueries) {
                val results = searchKairanBlog(query)
                Log.d("Kairan", "BLOG_SEARCH_RESULTS query=[$query] count=${results.size}")

                var bestUrl: String? = null
                var bestScore = Int.MIN_VALUE
                for (result in results) {
                    val titleScore = scoreKairanSearchTitle(title, result.title)
                    val episodeMatch = hasKairanEpisode(result.title, result.url, episode)
                    if (titleScore <= 0 || !episodeMatch) continue
                    if (titleScore > bestScore) {
                        bestScore = titleScore
                        bestUrl = result.url
                    }
                }

                if (bestUrl != null) {
                    Log.d("Kairan", "BLOG_SEARCH_MATCH score=$bestScore query=[$query] url=$bestUrl")
                    return@withTimeoutOrNull bestUrl
                }
            }
            null
        }

        deadlineResult?.let { cachePostUrl(context, title, episode, it) }
        if (deadlineResult == null) {
            Log.w("Kairan", "BLOG_SEARCH_TIMEOUT_OR_MISS title=[$title] episode=$episode")
        }
        return deadlineResult
    }

    private fun searchKairanBlog(query: String): List<KairanSearchResult> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$BLOG_URL/search?q=$encoded"
            Log.d("Kairan", "BLOG_SEARCH_REQUEST url=$url")

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 3500
                useCaches = false
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )
                setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            }

            try {
                val code = connection.responseCode
                val html = (
                    if (code in 200..299) connection.inputStream
                    else connection.errorStream
                )?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                Log.d(
                    "Kairan",
                    "BLOG_SEARCH_HTTP code=$code bytes=${html.length} query=[$query]"
                )

                if (code !in 200..299 || html.isBlank()) return emptyList()

                parseKairanSearchResults(html)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w("Kairan", "BLOG_SEARCH_FAILED query=[$query]", e)
            emptyList()
        }
    }

    private fun parseKairanSearchResults(html: String): List<KairanSearchResult> {
        val out = linkedMapOf<String, KairanSearchResult>()

        // Blogger themes normally render search-result post titles as links
        // inside .post-title/.entry-title, but custom Kairan themes can vary.
        // Therefore inspect all anchors and keep only real Blogger post URLs.
        val anchorRegex = Regex(
            """<a\b[^>]*href\s*=\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        for (match in anchorRegex.findAll(html)) {
            val rawUrl = htmlDecode(match.groupValues[1]).trim()
            val rawTitle = htmlDecode(stripHtml(match.groupValues[2])).trim()
            if (rawTitle.isBlank()) continue

            val postUrl = normalizeKairanPostUrl(rawUrl) ?: continue
            val key = postUrl.lowercase(Locale.ROOT)
            if (key !in out) {
                out[key] = KairanSearchResult(rawTitle, postUrl)
            }
        }

        return out.values.toList()
    }

    private fun normalizeKairanPostUrl(rawUrl: String): String? {
        val decoded = rawUrl
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .trim()

        val absolute = when {
            decoded.startsWith("https://kairan03.blogspot.com/") -> decoded
            decoded.startsWith("http://kairan03.blogspot.com/") ->
                decoded.replaceFirst("http://", "https://")
            decoded.startsWith("/") -> "$BLOG_URL$decoded"
            else -> return null
        }

        // Blogger post permalinks use /YYYY/MM/slug.html.  Restricting to
        // these URLs prevents menu/search/category links from becoming posts.
        return if (
            Regex(
                """https?://kairan03\.blogspot\.com/\d{4}/\d{1,2}/[^\s"'<>]+\.html(?:[?#].*)?$""",
                RegexOption.IGNORE_CASE
            ).matches(absolute)
        ) {
            absolute
        } else {
            null
        }
    }

    private fun stripHtml(value: String): String {
        return value
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun htmlDecode(value: String): String {
        return value
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
            .replace("&apos;", "'", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
            }
    }

    private fun kairanSearchTitle(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun scoreKairanSearchTitle(query: String, postTitle: String): Int {
        val left = kairanSearchTitle(query)
        val right = kairanSearchTitle(postTitle)
        if (left.isBlank() || right.isBlank()) return 0
        if (left == right) return 10000
        if (right.contains(left) || left.contains(right)) return 8000

        val leftWords = left.split(' ').filter { it.isNotBlank() }.toSet()
        val rightWords = right.split(' ').filter { it.isNotBlank() }.toSet()
        if (leftWords.isEmpty() || rightWords.isEmpty()) return 0

        val overlap = leftWords.intersect(rightWords).size
        if (overlap == 0) return 0

        val ratio = overlap.toDouble() / minOf(leftWords.size, rightWords.size).toDouble()
        return when {
            ratio >= 0.75 -> 6000
            ratio >= 0.5 -> 4000
            else -> 0
        }
    }

    private fun hasKairanEpisode(postTitle: String, url: String, episode: Int): Boolean {
        if (episode <= 0) return false

        val title = kairanSearchTitle(postTitle)
        val urlText = url.lowercase(Locale.ROOT)
        val ep = episode.toString()

        val explicitPatterns = listOf(
            Regex("(?:^|\\s|[\\[\\]\\(\\)_.-])0*$ep(?:\\s*화|\\s*회|\\s*편|\\s*話)(?:$|\\s|[\\[\\]\\(\\)_.-])", RegexOption.IGNORE_CASE),
            Regex("(?:^|\\s|[\\[\\]\\(\\)_.-])(?:ep|e|episode|#)\\s*0*$ep(?:$|\\s|[\\[\\]\\(\\)_.-])", RegexOption.IGNORE_CASE),
            Regex("(?:^|\\s|[\\[\\]\\(\\)_.-])0*$ep(?:$|\\s|[\\[\\]\\(\\)_.-])", RegexOption.IGNORE_CASE)
        )

        if (explicitPatterns.any { it.containsMatchIn(title) }) return true

        val urlEpisode = Regex(
            "(?:-|_)0*$ep\\.html(?:$|[?#])",
            RegexOption.IGNORE_CASE
        )
        return urlEpisode.containsMatchIn(urlText)
    }

    private fun extractGoogleDriveLinks(html: String): List<String> {
        val out = linkedSetOf<String>()
        val absolute = Regex("""https?://(?:drive|docs)\.google\.com/[^\s\"'<>\\]+""", RegexOption.IGNORE_CASE)
        absolute.findAll(html).forEach { m ->
            val u = m.value.replace("&amp;", "&").replace("\\/", "/").trimEnd(')',']','}','\"','\'')
            if (extractGoogleDriveId(u) != null) out += u
        }
        val href = Regex("""href=[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
        href.findAll(html).forEach { m ->
            val u = m.groupValues[1].replace("&amp;", "&").replace("\\/", "/")
            if (extractGoogleDriveId(u) != null) out += u
        }
        return out.toList()
    }

    private fun extractGoogleDriveId(url: String): String? {
        Regex("""/file/d/([^/?]+)""").find(url)?.let { return it.groupValues[1] }
        Regex("""[?&]id=([^&]+)""").find(url)?.let { return it.groupValues[1] }
        return null
    }

    private fun downloadGoogleDriveSubtitle(context: Context, fileId: String, title: String, episode: Int): String? {
        // ASS는 오프라인 재생에 필요하므로 cacheDir이 아니라 filesDir에 영구 보관한다.
        val dir = File(context.filesDir, CACHE_DIR).apply { mkdirs() }
        val safe = normalizeTitle(title).replace(' ', '_').ifBlank { "subtitle" }.take(60)
        val urls = listOf(
            "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t",
            "https://drive.google.com/uc?export=download&id=$fileId"
        )
        for (downloadUrl in urls) {
            val temp = File(dir, "${safe}_${episode}_${System.currentTimeMillis()}.tmp")
            try {
                Log.d("Kairan", "DOWNLOAD_REQUEST url=$downloadUrl")
                val c = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 15000; readTimeout = 60000
                    instanceFollowRedirects = true; setRequestProperty("User-Agent", USER_AGENT); setRequestProperty("Accept", "*/*")
                }
                try {
                    val code = c.responseCode
                    Log.d("Kairan", "DOWNLOAD_HTTP code=$code")
                    if (code !in 200..299) continue
                    c.inputStream.use { input -> FileOutputStream(temp).use { input.copyTo(it) } }
                    Log.d("Kairan", "DOWNLOAD_SIZE bytes=${temp.length()}")
                    if (temp.length() < 100) { Log.w("Kairan", "DOWNLOAD_TOO_SMALL"); temp.delete(); continue }
                    if (looksLikeHtml(temp)) { Log.w("Kairan", "DOWNLOAD_RETURNED_HTML"); temp.delete(); continue }
                    if (!isAssFile(temp)) { Log.w("Kairan", "DOWNLOAD_NOT_ASS"); temp.delete(); continue }

                    val assInfo = inspectAssFile(temp)
                    Log.d(
                        "Kairan",
                        "ASS_INFO dialogue=${assInfo.dialogueCount} " +
                            "positioned=${assInfo.positionedCount} " +
                            "moving=${assInfo.movingCount} " +
                            "playRes=${assInfo.playResX}x${assInfo.playResY} " +
                            "styles=${assInfo.styleCount}"
                    )

                    val target = File(dir, "${safe}_${episode}.ass")
                    temp.copyTo(target, overwrite = true); temp.delete()
                    return target.absolutePath
                } finally { c.disconnect() }
            } catch (e: Exception) {
                Log.w("Kairan", "DOWNLOAD_FAILED url=$downloadUrl", e); temp.delete()
            }
        }
        return null
    }

    private data class AssInfo(
        val dialogueCount: Int,
        val positionedCount: Int,
        val movingCount: Int,
        val playResX: Int?,
        val playResY: Int?,
        val styleCount: Int
    )

    private fun inspectAssFile(file: File): AssInfo = try {
        val text = file.inputStream().bufferedReader().use { it.readText() }
        val lower = text.lowercase(Locale.ROOT)

        val dialogueLines = text.lineSequence()
            .filter { it.trimStart().startsWith("dialogue:", ignoreCase = true) }
            .toList()

        val positioned = dialogueLines.count {
            Regex("""\\pos\s*\(""", RegexOption.IGNORE_CASE).containsMatchIn(it)
        }

        val moving = dialogueLines.count {
            Regex("""\\move\s*\(""", RegexOption.IGNORE_CASE).containsMatchIn(it)
        }

        val playResX = Regex(
            """(?im)^\s*playresx\s*[:=]\s*(\d+)"""
        ).find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val playResY = Regex(
            """(?im)^\s*playresy\s*[:=]\s*(\d+)"""
        ).find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val styleCount = text.lineSequence().count {
            val t = it.trimStart()
            t.startsWith("style:", ignoreCase = true) ||
                t.startsWith("format:", ignoreCase = true) && lower.contains("[v4+ styles]")
        }

        AssInfo(
            dialogueCount = dialogueLines.size,
            positionedCount = positioned,
            movingCount = moving,
            playResX = playResX,
            playResY = playResY,
            styleCount = styleCount
        )
    } catch (_: Exception) {
        AssInfo(0, 0, 0, null, null, 0)
    }

    private fun isAssFile(file: File): Boolean = try {
        val sample = file.inputStream().bufferedReader().use { it.readText().take(20000).lowercase(Locale.ROOT) }
        sample.contains("[script info]") && sample.contains("[events]")
    } catch (_: Exception) { false }

    private fun looksLikeHtml(file: File): Boolean = try {
        val sample = file.inputStream().bufferedReader().use { it.readText().take(3000).trimStart().lowercase(Locale.ROOT) }
        sample.startsWith("<!doctype html") || sample.startsWith("<html") || sample.contains("<head")
    } catch (_: Exception) { false }

    private fun getText(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT); setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json,*/*")
        }
        return try {
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            text
        } finally { c.disconnect() }
    }

    internal fun normalizeTitleForFile(value: String): String = normalizeTitle(value)

    private fun normalizeTitle(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("""\[[^]]*]"""), " ")
        .replace(Regex("""\([^)]*\)"""), " ")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .replace(Regex("""\s+"""), " ").trim()
}

