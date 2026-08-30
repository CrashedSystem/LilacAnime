package com.lilac.anime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object KairanSubtitleService {
    private const val TAG = "Kairan"
    private const val CACHE_DIR = "kairan_subtitles"
    private const val POST_CACHE_PREFS = "kairan_post_cache"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    suspend fun findSubtitle(context: Context, title: String, episodeNumber: Int): KairanSubtitleResult? =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "START_SEARCH title=[$title] episode=$episodeNumber")

                SubtitleStore.get(context, normalizeTitleForFile(title), episodeNumber, "kairan")
                    ?.takeIf { File(it).isFile }
                    ?.let {
                        Log.d(TAG, "LOCAL_SUBTITLE_HIT path=$it")
                        return@withContext KairanSubtitleResult.DirectFile(it)
                    }

                val postUrl = findBlogPost(context, title, episodeNumber)
                    ?: run {
                        Log.w(TAG, "POST_NOT_FOUND title=[$title] episode=$episodeNumber")
                        return@withContext null
                    }

                Log.d(TAG, "POST_FOUND url=$postUrl")
                val html = getText(postUrl)
                val links = extractGoogleDriveLinks(html)
                Log.d(TAG, "DRIVE_LINK_COUNT count=${links.size}")

                for (link in links) {
                    val id = extractGoogleDriveId(link) ?: continue
                    val local = downloadGoogleDriveSubtitle(context, id, title, episodeNumber)
                    if (local != null) {
                        Log.d(TAG, "SUBTITLE_READY path=$local")
                        return@withContext KairanSubtitleResult.DirectFile(local)
                    }
                }

                Log.w(TAG, "DRIVE_SUBTITLE_NOT_FOUND url=$postUrl")
                null
            } catch (e: Exception) {
                Log.e(TAG, "FIND_SUBTITLE_FAILED title=[$title] ep=$episodeNumber", e)
                null
            }
        }

    private fun postCacheKey(title: String, episode: Int): String =
        "${normalizeTitleForFile(title)}#$episode"

    private fun cachedPostUrl(context: Context, title: String, episode: Int): String? =
        context.getSharedPreferences(POST_CACHE_PREFS, Context.MODE_PRIVATE)
            .getString(postCacheKey(title, episode), null)
            ?.takeIf { it.isNotBlank() }

    private fun cachePostUrl(context: Context, title: String, episode: Int, url: String) {
        context.getSharedPreferences(POST_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(postCacheKey(title, episode), url)
            .apply()
    }

    private suspend fun findBlogPost(context: Context, title: String, episode: Int): String? {
        cachedPostUrl(context, title, episode)?.let { cached ->
            Log.d(TAG, "POST_CACHE_HIT episode=$episode url=$cached")
            return cached
        }

        val posts = KairanBlogRepository.getPosts(context)
        if (posts.isEmpty()) {
            Log.w(TAG, "BLOG_INDEX_EMPTY")
            return null
        }

        val match = KairanPostMatcher.findBestMatch(title, episode, posts)
        if (match == null) {
            Log.w(TAG, "BLOG_MATCH_MISS title=[$title] episode=$episode")
            return null
        }

        Log.d(TAG, "BLOG_MATCH similarity=${match.similarity} title=[${match.post.title}] url=${match.post.url}")
        cachePostUrl(context, title, episode, match.post.url)
        return match.post.url
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

