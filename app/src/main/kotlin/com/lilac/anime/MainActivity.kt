package com.lilac.anime

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.factory.AssRenderersFactory
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import io.github.peerless2012.ass.media.widget.AssSubtitleView
import com.lilac.anime.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.zip.ZipInputStream
import java.net.URLConnection
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import android.content.Intent
import kotlinx.coroutines.flow.update

// ============================================================
// DATA MODELS
// ============================================================

data class StreamQuality(
    val label: String,
    val url: String
)

data class PlayerSettings(
    val defaultQuality: String = "1080p",
    val subtitleFont: String = "기본체",
    val subtitleSize: Float = 100f,
    val textColor: Int = android.graphics.Color.WHITE,
    val backgroundColor: Int = android.graphics.Color.TRANSPARENT,
    val strokeColor: Int = android.graphics.Color.BLACK,
    val syncOffsetMs: Long = 0L,
    val customFontPath: String? = null
)

data class ExoVideoQualityOption(
    val label: String,
    val width: Int,
    val height: Int,
    val isAuto: Boolean = false
)

data class AniSkipSegment(
    val type: String,
    val startTime: Double,
    val endTime: Double,
    val episodeLength: Double
)

object AniSkipService {
    private const val BASE_URL = "https://api.aniskip.com/v2"
    private const val JIKAN_URL = "https://api.jikan.moe/v4"
    private const val ANILIST_URL = "https://graphql.anilist.co"
    private const val KITSU_URL = "https://kitsu.io/api/edge"

    private data class MalCandidate(
        val malId: Int,
        val score: Int,
        val matchedTitle: String,
        val seasonNumber: Int? = null,
        val year: Int? = null,
        val romajiTitle: String? = null
    )

    suspend fun getSkipTimes(
        title: String,
        episodeNumber: Int,
        episodeLengthSeconds: Int
    ): List<AniSkipSegment> = withContext(Dispatchers.IO) {
        try {
            Log.d(
                "AniSkip",
                "START title=\"$title\" episode=$episodeNumber length=$episodeLengthSeconds"
            )

            val malId = findMalId(title)
            Log.d("AniSkip", "MAL_ID=$malId title=\"$title\"")

            if (malId == null) {
                Log.e("AniSkip", "MAL ID를 찾지 못했습니다.")
                return@withContext emptyList()
            }

            val actualLength = episodeLengthSeconds.coerceAtLeast(0)

            // 길이 필터 없는 결과와 실제 영상 길이로 조회한 결과를 모두 가져온다.
            // 길이가 크게 다른 rough 결과를 먼저 선택하면 스킵 위치가 틀어질 수 있다.
            Log.d(
                "AniSkip",
                "FETCH rough malId=$malId episode=$episodeNumber episodeLength=0"
            )
            val rough = requestSkipTimes(
                malId = malId,
                episodeNumber = episodeNumber,
                episodeLength = 0
            )

            Log.d("AniSkip", "ROUGH_RESULT count=${rough.size} values=$rough")

            val exact = if (actualLength > 0) {
                Log.d(
                    "AniSkip",
                    "FETCH exact malId=$malId episode=$episodeNumber episodeLength=$actualLength"
                )
                requestSkipTimes(
                    malId = malId,
                    episodeNumber = episodeNumber,
                    episodeLength = actualLength
                )
            } else {
                emptyList()
            }

            Log.d("AniSkip", "EXACT_RESULT count=${exact.size} values=$exact")

            val all = (rough + exact)
                .distinctBy {
                    "${it.type}:${it.startTime}:${it.endTime}:${it.episodeLength}"
                }

            if (all.isEmpty()) {
                Log.w(
                    "AniSkip",
                    "NO_SKIP_DATA malId=$malId episode=$episodeNumber actualLength=$actualLength"
                )
                return@withContext emptyList()
            }

            val selected = all
                .groupBy { it.type }
                .mapNotNull { (type, values) ->
                    val chosen = values.minByOrNull { segment ->
                        if (
                            actualLength > 0 &&
                            segment.episodeLength > 0.0
                        ) {
                            kotlin.math.abs(
                                segment.episodeLength - actualLength.toDouble()
                            )
                        } else {
                            Double.MAX_VALUE
                        }
                    }

                    chosen?.also {
                        Log.d(
                            "AniSkip",
                            "SELECT type=$type start=${it.startTime} end=${it.endTime} " +
                                "sourceLength=${it.episodeLength} " +
                                "localLength=$actualLength " +
                                "lengthDiff=${
                                    if (actualLength > 0 && it.episodeLength > 0.0)
                                        it.episodeLength - actualLength
                                    else
                                        0.0
                                }"
                        )
                    }
                }
                .sortedBy { it.startTime }

            selected.forEach {
                Log.d(
                    "AniSkip",
                    "SEGMENT type=${it.type} start=${it.startTime} " +
                        "end=${it.endTime} sourceLength=${it.episodeLength}"
                )
            }

            selected
        } catch (e: Exception) {
            Log.e("AniSkip", "getSkipTimes exception", e)
            emptyList()
        }
    }

    private fun requestSkipTimes(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Int
    ): List<AniSkipSegment> {
        val url = URL(
            "$BASE_URL/skip-times/$malId/$episodeNumber" +
                "?types=op" +
                "&types=ed" +
                "&types=mixed-op" +
                "&types=mixed-ed" +
                "&episodeLength=$episodeLength"
        )

        Log.d("AniSkip", "REQUEST $url")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Connection", "close")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) LilacAnime/1.0")
        }

        return try {
            val responseCode = connection.responseCode
            Log.d("AniSkip", "HTTP $responseCode url=$url")

            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Log.d("AniSkip", "BODY ${body.take(8000)}")

            if (responseCode !in 200..299) {
                Log.e("AniSkip", "HTTP_ERROR code=$responseCode body=${body.take(2000)}")
                return emptyList()
            }

            val root = JSONObject(body)
            val found = root.optBoolean("found", false)
            Log.d("AniSkip", "FOUND=$found status=${root.optInt("statusCode", responseCode)}")

            val results = root.optJSONArray("results") ?: return emptyList()

            buildList {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val type = item.optString("skipType").ifBlank {
                        item.optString("skip_type")
                    }

                    if (type !in setOf("op", "ed", "mixed-op", "mixed-ed")) continue

                    val interval = item.optJSONObject("interval") ?: continue
                    val start = interval.optDouble("startTime", interval.optDouble("start_time", Double.NaN))
                    val end = interval.optDouble("endTime", interval.optDouble("end_time", Double.NaN))
                    val sourceLength = item.optDouble(
                        "episodeLength",
                        item.optDouble("episode_length", 0.0)
                    )

                    if (start.isFinite() && end.isFinite() && end > start) {
                        add(
                            AniSkipSegment(
                                type = type,
                                startTime = start,
                                endTime = end,
                                episodeLength = sourceLength
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AniSkip", "REQUEST_EXCEPTION url=$url", e)
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun translateText(
        text: String,
        source: String,
        target: String,
        provider: String
    ): String? {
        if (text.isBlank()) return null

        return try {
            val url = when (provider) {
                "mymemory" -> URL(
                    "https://api.mymemory.translated.net/get" +
                        "?q=${Uri.encode(text)}" +
                        "&langpair=${Uri.encode(source)}%7C${Uri.encode(target)}"
                )
                else -> URL(
                    "https://translate.googleapis.com/translate_a/single" +
                        "?client=gtx" +
                        "&sl=$source" +
                        "&tl=$target" +
                        "&dt=t" +
                        "&q=${Uri.encode(text)}"
                )
            }

            Log.d(
                "AniSkip",
                "TRANSLATE REQUEST provider=$provider source=$source target=$target text=\"$text\""
            )

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                useCaches = false
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Android) LilacAnime/1.0"
                )
            }

            try {
                val code = connection.responseCode
                val body = (
                    if (code in 200..299) connection.inputStream
                    else connection.errorStream
                )?.bufferedReader()?.use { it.readText() }.orEmpty()

                Log.d(
                    "AniSkip",
                    "TRANSLATE HTTP=$code provider=$provider source=$source target=$target body=${body.take(2500)}"
                )

                if (code !in 200..299 || body.isBlank()) {
                    return null
                }

                val result = if (provider == "mymemory") {
                    JSONObject(body)
                        .optJSONObject("responseData")
                        ?.optString("translatedText")
                        .orEmpty()
                } else {
                    val root = JSONArray(body)
                    val first = root.optJSONArray(0)
                    if (first == null) {
                        ""
                    } else {
                        buildString {
                            for (i in 0 until first.length()) {
                                val row = first.optJSONArray(i) ?: continue
                                append(row.optString(0))
                            }
                        }
                    }
                }
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                Log.d(
                    "AniSkip",
                    "TRANSLATE RESULT provider=$provider source=$source target=$target result=\"$result\""
                )

                result.takeIf { it.isNotBlank() }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(
                "AniSkip",
                "TRANSLATE exception provider=$provider source=$source target=$target text=\"$text\"",
                e
            )
            null
        }
    }

    private fun translateKoreanToJapanese(text: String): String? {
        translateText(text, "ko", "ja", "mymemory")?.let { return it }
        translateText(text, "ko", "ja", "google")?.let { return it }
        return null
    }

    private fun translateKoreanToEnglish(text: String): String? {
        translateText(text, "ko", "en", "mymemory")?.let { return it }
        translateText(text, "ko", "en", "google")?.let { return it }
        return null
    }

    private fun findMalId(title: String): Int? {
        val requestedSeason = extractRequestedSeason(title)
        val titleCandidates = buildTitleCandidates(title)
        val original = titleCandidates.firstOrNull().orEmpty()
        val seasonless = titleCandidates.getOrNull(1).orEmpty()
        val baseTitle = seasonless.ifBlank { original }

        Log.d(
            "AniSkip",
            "TITLE_RESOLVE original=\"$title\" cleaned=\"$original\" " +
                "seasonless=\"$seasonless\" season=$requestedSeason"
        )

        val knownAliases = linkedMapOf(
            "도망을잘치는도련님" to listOf(
                "逃げ上手の若君",
                "Nige Jouzu no Wakagimi",
                "The Elusive Samurai"
            ),
            "전생했더니슬라임이었던건에대하여" to listOf(
                "転生したらスライムだった件",
                "Tensei Shitara Slime Datta Ken",
                "That Time I Got Reincarnated as a Slime"
            )
        )

        val normalizedOriginal = normalizeTitle(title)
        val knownAlias = knownAliases.entries
            .firstOrNull { normalizedOriginal.contains(it.key) }
            ?.value
            .orEmpty()

        if (knownAlias.isNotEmpty()) {
            Log.d("AniSkip", "TITLE_RESOLVE knownAliases=$knownAlias")

            for (alias in knownAlias) {
                Log.d("AniSkip", "KNOWN_ALIAS search=\"$alias\"")
                val matches = findMalCandidatesWithAniList(alias, requestedSeason)
                chooseBestMalCandidate(matches, requestedSeason)?.let { selected ->
                    Log.d(
                        "AniSkip",
                        "KNOWN_ALIAS SELECT malId=${selected.malId} " +
                            "romaji=\"${selected.romajiTitle}\" title=\"${selected.matchedTitle}\" " +
                            "score=${selected.score} season=${selected.seasonNumber}"
                    )
                    return selected.malId
                }
            }
        }

        val queryCandidates = linkedSetOf<String>()

        if (baseTitle.isNotBlank() && baseTitle.any { it in 'A'..'Z' || it in 'a'..'z' }) {
            queryCandidates += baseTitle
        }

        val japaneseTitle = translateKoreanToJapanese(baseTitle)
        Log.d("AniSkip", "TITLE_RESOLVE japanese=\"$japaneseTitle\"")

        if (!japaneseTitle.isNullOrBlank()) {
            queryCandidates += japaneseTitle
        }

        val englishTitle = translateKoreanToEnglish(baseTitle)
        Log.d("AniSkip", "TITLE_RESOLVE english=\"$englishTitle\"")

        if (!englishTitle.isNullOrBlank()) {
            queryCandidates += englishTitle
        }

        for (query in queryCandidates) {
            Log.d("AniSkip", "AniList SEARCH query=\"$query\" season=$requestedSeason")

            val matches = findMalCandidatesWithAniList(
                query,
                requestedSeason
            )

            chooseBestMalCandidate(matches, requestedSeason)?.let { selected ->
                Log.d(
                    "AniSkip",
                    "AniList SELECT malId=${selected.malId} " +
                        "romaji=\"${selected.romajiTitle}\" " +
                        "title=\"${selected.matchedTitle}\" " +
                        "score=${selected.score} season=${selected.seasonNumber} year=${selected.year}"
                )
                return selected.malId
            }
        }

        val fallbackQueries = linkedSetOf<String>()
        fallbackQueries.addAll(queryCandidates)

        if (baseTitle.isNotBlank()) {
            fallbackQueries += baseTitle
        }

        for (query in fallbackQueries) {
            Log.d("AniSkip", "Jikan FALLBACK search=\"$query\"")
            val jikanMatches = findMalCandidatesWithJikan(
                query,
                requestedSeason
            )

            chooseBestMalCandidate(jikanMatches, requestedSeason)?.let { selected ->
                Log.d(
                    "AniSkip",
                    "Jikan SELECT malId=${selected.malId} " +
                        "title=\"${selected.matchedTitle}\" score=${selected.score} season=${selected.seasonNumber}"
                )
                return selected.malId
            }
        }

        for (query in fallbackQueries) {
            Log.d("AniSkip", "Kitsu FALLBACK search=\"$query\"")
            val kitsuMatches = findMalCandidatesWithKitsu(
                query,
                requestedSeason
            )

            chooseBestMalCandidate(kitsuMatches, requestedSeason)?.let { selected ->
                Log.d(
                    "AniSkip",
                    "Kitsu SELECT malId=${selected.malId} " +
                        "title=\"${selected.matchedTitle}\" score=${selected.score} season=${selected.seasonNumber}"
                )
                return selected.malId
            }
        }

        for (query in fallbackQueries) {
            Log.d("AniSkip", "MAL FALLBACK search=\"$query\"")
            val malMatches = findMalCandidatesWithMalSearch(
                query,
                requestedSeason
            )

            chooseBestMalCandidate(malMatches, requestedSeason)?.let { selected ->
                Log.d(
                    "AniSkip",
                    "MAL SELECT malId=${selected.malId} " +
                        "title=\"${selected.matchedTitle}\" score=${selected.score} season=${selected.seasonNumber}"
                )
                return selected.malId
            }
        }

        Log.e(
            "AniSkip",
            "MAL ID not found title=\"$title\" japanese=\"$japaneseTitle\" english=\"$englishTitle\""
        )
        return null
    }

    private fun findMalCandidatesWithAniList(
        title: String,
        requestedSeason: Int?
    ): List<MalCandidate> {
        val query = """
            query (${'$'}search: String) {
                Page(page: 1, perPage: 25) {
                    media(
                        search: ${'$'}search
                        type: ANIME
                    ) {
                        id
                        idMal
                        season
                        seasonYear
                        format
                        episodes
                        title {
                            romaji
                            english
                            native
                        }
                        synonyms
                    }
                }
            }
        """.trimIndent()

        return try {
            val body = JSONObject()
                .put("query", query)
                .put(
                    "variables",
                    JSONObject().put("search", title)
                )
                .toString()

            val connection =
                (URL(ANILIST_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                    useCaches = false
                    setRequestProperty(
                        "Content-Type",
                        "application/json"
                    )
                    setRequestProperty(
                        "Accept",
                        "application/json"
                    )
                    setRequestProperty(
                        "User-Agent",
                        "LilacAnime/1.0"
                    )
                }

            try {
                connection.outputStream.use { output ->
                    output.write(
                        body.toByteArray(Charsets.UTF_8)
                    )
                    output.flush()
                }

                val responseCode = connection.responseCode
                val responseStream =
                    if (responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val response =
                    responseStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: ""

                Log.d(
                    "AniSkip",
                    "AniList HTTP=$responseCode candidate=\"$title\" response=${response.take(2500)}"
                )

                if (
                    responseCode !in 200..299 ||
                    response.isBlank()
                ) {
                    return emptyList()
                }

                val root = JSONObject(response)

                val media =
                    root.optJSONObject("data")
                        ?.optJSONObject("Page")
                        ?.optJSONArray("media")
                        ?: return emptyList()

                val normalizedQuery =
                    normalizeTitle(title)

                buildList {
                    for (i in 0 until media.length()) {
                        val item =
                            media.optJSONObject(i)
                                ?: continue

                        val malId =
                            item.optInt("idMal", 0)

                        if (malId <= 0) continue

                        val names =
                            mutableListOf<String>()

                        val romajiTitle =
                            item.optJSONObject("title")
                                ?.optString("romaji")
                                ?.trim()
                                ?.takeIf { it.isNotBlank() }

                        item.optJSONObject("title")
                            ?.let { titleObj ->
                                romajiTitle?.let(names::add)

                                titleObj.optString("english")
                                    .takeIf { it.isNotBlank() }
                                    ?.let(names::add)

                                titleObj.optString("native")
                                    .takeIf { it.isNotBlank() }
                                    ?.let(names::add)
                            }

                        item.optJSONArray("synonyms")
                            ?.let { synonyms ->
                                for (j in 0 until synonyms.length()) {
                                    synonyms.optString(j)
                                        .takeIf { it.isNotBlank() }
                                        ?.let(names::add)
                                }
                            }

                        val romajiScore = romajiTitle?.let {
                            compareTitles(
                                normalizedQuery,
                                normalizeTitle(it)
                            )
                        } ?: 0

                        val titleScore = names.maxOfOrNull {
                            compareTitles(
                                normalizedQuery,
                                normalizeTitle(it)
                            )
                        } ?: 0

                        val strongestTitleScore = maxOf(titleScore, romajiScore)

                        val combinedText = names.joinToString(" ")

                        val detectedSeason = extractSeasonNumber(combinedText)
                        val anilistSeason = item.optString("season").trim()
                        val seasonYear = item.optInt("seasonYear", 0).takeIf { it > 0 }
                        val episodes = item.optInt("episodes", 0).takeIf { it > 0 }

                        var score = strongestTitleScore

                        if (requestedSeason != null) {
                            if (detectedSeason == requestedSeason) {
                                score += 5000
                            } else if (
                                detectedSeason != null &&
                                detectedSeason != requestedSeason
                            ) {
                                score -= 5000
                            }
                        }

                        // 제목에 시즌 번호가 없더라도 sequel/season 제목은 romaji/english/native에
                        // 숫자 표현이 포함되는 경우가 많으므로 추가 가산점을 준다.
                        if (requestedSeason != null) {
                            val seasonTokens = listOf(
                                "${requestedSeason}th season",
                                "${requestedSeason}st season",
                                "${requestedSeason}nd season",
                                "${requestedSeason}rd season",
                                "season $requestedSeason",
                                "part $requestedSeason",
                                "${requestedSeason}rd season",
                                "${requestedSeason}th season"
                            )

                            val hasRequestedSeasonToken = names.any { name ->
                                val n = normalizeTitle(name)
                                seasonTokens.any { token -> n.contains(token) } ||
                                    n.contains("${requestedSeason}기") ||
                                    n.contains("제 ${requestedSeason} 기") ||
                                    n.contains("${requestedSeason}th") ||
                                    n.contains("${requestedSeason}nd") ||
                                    n.contains("${requestedSeason}rd") ||
                                    n.contains("${requestedSeason}st")
                            }

                            if (hasRequestedSeasonToken) score += 3000
                        }

                        val format =
                            item.optString("format")

                        if (
                            requestedSeason != null &&
                            format == "TV"
                        ) {
                            score += 100
                        }

                        val year = seasonYear

                        val matchedTitle =
                            names.maxByOrNull {
                                compareTitles(
                                    normalizedQuery,
                                    normalizeTitle(it)
                                )
                            }.orEmpty()

                        Log.d(
                            "AniSkip",
                            "AniList candidate malId=$malId score=$score title=\"$matchedTitle\" " +
                                "romaji=\"$romajiTitle\" season=$detectedSeason year=$year names=$names"
                        )

                        add(
                            MalCandidate(
                                malId = malId,
                                score = score,
                                matchedTitle = matchedTitle,
                                seasonNumber = detectedSeason,
                                year = year,
                                romajiTitle = romajiTitle
                            )
                        )
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(
                "AniSkip",
                "AniList exception candidate=\"$title\"",
                e
            )
            emptyList()
        }
    }

    private fun findMalCandidatesWithMalSearch(
        title: String,
        requestedSeason: Int?
    ): List<MalCandidate> {
        return try {
            val query = Uri.encode(title)
            val url = URL(
                "https://myanimelist.net/search/prefix.json" +
                    "?type=anime&keyword=$query"
            )

            Log.d("AniSkip", "MAL REQUEST $url")

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Android) LilacAnime/1.0"
                )
            }

            try {
                val code = connection.responseCode
                val body = (
                    if (code in 200..299) connection.inputStream
                    else connection.errorStream
                )?.bufferedReader()?.use { it.readText() }.orEmpty()

                Log.d(
                    "AniSkip",
                    "MAL HTTP=$code candidate=\"$title\" response=${body.take(2500)}"
                )

                if (code !in 200..299 || body.isBlank()) {
                    return emptyList()
                }

                val root = JSONObject(body)
                val categories = root.optJSONArray("categories") ?: return emptyList()

                val items = buildList {
                    for (i in 0 until categories.length()) {
                        val category = categories.optJSONObject(i) ?: continue
                        val categoryItems = category.optJSONArray("items") ?: continue

                        for (j in 0 until categoryItems.length()) {
                            categoryItems.optJSONObject(j)?.let { add(it) }
                        }
                    }
                }

                val normalizedQuery = normalizeTitle(title)

                buildList {
                    for (item in items) {
                        val malId = item.optInt("id", 0)
                        if (malId <= 0) continue

                        val name = item.optString("name").trim()
                        if (name.isBlank()) continue

                        val normalizedName = normalizeTitle(name)
                        val titleScore = compareTitles(normalizedQuery, normalizedName)
                        var score = titleScore

                        val detectedSeason = extractSeasonNumber(name)

                        if (requestedSeason != null && titleScore > 0) {
                            if (detectedSeason == requestedSeason) {
                                score += 5000
                            } else if (
                                detectedSeason != null &&
                                detectedSeason != requestedSeason
                            ) {
                                score -= 5000
                            }
                        }

                        Log.d(
                            "AniSkip",
                            "MAL candidate malId=$malId score=$score " +
                                "title=\"$name\" season=$detectedSeason"
                        )

                        add(
                            MalCandidate(
                                malId = malId,
                                score = score,
                                matchedTitle = name,
                                seasonNumber = detectedSeason,
                                year = null
                            )
                        )
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(
                "AniSkip",
                "MAL exception candidate=\"$title\"",
                e
            )
            emptyList()
        }
    }

    private fun findMalCandidatesWithJikan(
        title: String,
        requestedSeason: Int?
    ): List<MalCandidate> {
        return try {
            val query = Uri.encode(title)
            val url = URL(
                "$JIKAN_URL/anime?q=$query&limit=25"
            )

            Log.d(
                "AniSkip",
                "Jikan REQUEST $url"
            )

            val connection =
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    useCaches = false
                    setRequestProperty(
                        "Accept",
                        "application/json"
                    )
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Android) LilacAnime/1.0"
                    )
                }

            try {
                val responseCode =
                    connection.responseCode

                val responseStream =
                    if (responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val body =
                    responseStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: ""

                Log.d(
                    "AniSkip",
                    "Jikan HTTP=$responseCode candidate=\"$title\" response=${body.take(2000)}"
                )

                if (
                    responseCode !in 200..299 ||
                    body.isBlank()
                ) {
                    return emptyList()
                }

                val data =
                    JSONObject(body)
                        .optJSONArray("data")
                        ?: return emptyList()

                val normalizedQuery =
                    normalizeTitle(title)

                buildList {
                    for (i in 0 until data.length()) {
                        val item =
                            data.optJSONObject(i)
                                ?: continue

                        val malId =
                            item.optInt("mal_id", 0)

                        if (malId <= 0) continue

                        val names =
                            mutableListOf<String>()

                        item.optString("title")
                            .takeIf { it.isNotBlank() }
                            ?.let(names::add)

                        item.optString("title_english")
                            .takeIf { it.isNotBlank() }
                            ?.let(names::add)

                        item.optString("title_japanese")
                            .takeIf { it.isNotBlank() }
                            ?.let(names::add)

                        item.optJSONArray("titles")
                            ?.let { titles ->
                                for (j in 0 until titles.length()) {
                                    titles.optJSONObject(j)
                                        ?.optString("title")
                                        ?.takeIf {
                                            it.isNotBlank()
                                        }
                                        ?.let(names::add)
                                }
                            }

                        val titleScore =
                            names.maxOfOrNull {
                                compareTitles(
                                    normalizedQuery,
                                    normalizeTitle(it)
                                )
                            } ?: 0

                        val combinedText =
                            names.joinToString(" ")

                        val detectedSeason =
                            extractSeasonNumber(
                                combinedText
                            )

                        var score = titleScore

                        if (
                            requestedSeason != null
                        ) {
                            if (
                                detectedSeason ==
                                requestedSeason
                            ) {
                                score += 5000
                            } else if (
                                detectedSeason != null &&
                                detectedSeason != requestedSeason
                            ) {
                                score -= 2500
                            }
                        }

                        val year =
                            item.optString("year")
                                .toIntOrNull()
                                ?: item.optJSONObject("aired")
                                    ?.optString("from")
                                    ?.take(4)
                                    ?.toIntOrNull()

                        val matchedTitle =
                            names.maxByOrNull {
                                compareTitles(
                                    normalizedQuery,
                                    normalizeTitle(it)
                                )
                            }.orEmpty()

                        Log.d(
                            "AniSkip",
                            "Jikan candidate malId=$malId score=$score title=\"$matchedTitle\" season=$detectedSeason year=$year"
                        )

                        add(
                            MalCandidate(
                                malId = malId,
                                score = score,
                                matchedTitle = matchedTitle,
                                seasonNumber = detectedSeason,
                                year = year
                            )
                        )
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(
                "AniSkip",
                "Jikan exception candidate=\"$title\"",
                e
            )
            emptyList()
        }
    }

    private fun findMalCandidatesWithKitsu(
        title: String,
        requestedSeason: Int?
    ): List<MalCandidate> {
        return try {
            val query = Uri.encode(title)
            val url = URL(
                "$KITSU_URL/anime?filter[text]=$query&page[limit]=20&include=mappings"
            )

            Log.d(
                "AniSkip",
                "Kitsu REQUEST $url"
            )

            val connection =
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    useCaches = false
                    setRequestProperty(
                        "Accept",
                        "application/vnd.api+json"
                    )
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Android) LilacAnime/1.0"
                    )
                }

            try {
                val responseCode =
                    connection.responseCode

                val responseStream =
                    if (responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val body =
                    responseStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: ""

                Log.d(
                    "AniSkip",
                    "Kitsu HTTP=$responseCode candidate=\"$title\" response=${body.take(2500)}"
                )

                if (
                    responseCode !in 200..299 ||
                    body.isBlank()
                ) {
                    return emptyList()
                }

                val root = JSONObject(body)
                val data =
                    root.optJSONArray("data")
                        ?: return emptyList()

                val included =
                    root.optJSONArray("included")

                val normalizedQuery =
                    normalizeTitle(title)

                buildList {
                    for (i in 0 until data.length()) {
                        val item =
                            data.optJSONObject(i)
                                ?: continue

                        val attributes =
                            item.optJSONObject("attributes")
                                ?: continue

                        val names =
                            mutableListOf<String>()

                        attributes.optString("canonicalTitle")
                            .takeIf { it.isNotBlank() }
                            ?.let(names::add)

                        attributes.optJSONObject("titles")
                            ?.let { titles ->
                                val keys =
                                    titles.keys()

                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    titles.optString(key)
                                        .takeIf {
                                            it.isNotBlank()
                                        }
                                        ?.let(names::add)
                                }
                            }

                        val titleScore =
                            names.maxOfOrNull {
                                compareTitles(
                                    normalizedQuery,
                                    normalizeTitle(it)
                                )
                            } ?: 0

                        val combinedText =
                            names.joinToString(" ")

                        val detectedSeason =
                            extractSeasonNumber(
                                combinedText
                            )

                        var score = titleScore

                        if (
                            requestedSeason != null
                        ) {
                            if (
                                detectedSeason ==
                                requestedSeason
                            ) {
                                score += 5000
                            } else if (
                                detectedSeason != null &&
                                detectedSeason != requestedSeason
                            ) {
                                score -= 2500
                            }
                        }

                        val year =
                            attributes.optString(
                                "startDate"
                            )
                                .take(4)
                                .toIntOrNull()

                        var malId =
                            findKitsuMalId(
                                item,
                                included
                            )

                        if (malId == null) {
                            val slug =
                                item.optString("id")

                            if (
                                slug.isNotBlank()
                            ) {
                                malId =
                                    findMalIdFromKitsuSlug(
                                        slug
                                    )
                            }
                        }

                        if (malId == null) {
                            Log.d(
                                "AniSkip",
                                "Kitsu result has no MAL mapping title=$names"
                            )
                            continue
                        }

                        val matchedTitle =
                            names.maxByOrNull {
                                compareTitles(
                                    normalizedQuery,
                                    normalizeTitle(it)
                                )
                            }.orEmpty()

                        Log.d(
                            "AniSkip",
                            "Kitsu candidate malId=$malId score=$score title=\"$matchedTitle\" season=$detectedSeason year=$year"
                        )

                        add(
                            MalCandidate(
                                malId = malId,
                                score = score,
                                matchedTitle = matchedTitle,
                                seasonNumber = detectedSeason,
                                year = year
                            )
                        )
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(
                "AniSkip",
                "Kitsu exception candidate=\"$title\"",
                e
            )
            emptyList()
        }
    }

    private fun findKitsuMalId(
        item: JSONObject,
        included: JSONArray?
    ): Int? {
        val relationships =
            item.optJSONObject("relationships")
                ?: return null

        val mappings =
            relationships.optJSONObject("mappings")
                ?: return null

        val data =
            mappings.optJSONArray("data")
                ?: return null

        for (i in 0 until data.length()) {
            val mapping =
                data.optJSONObject(i)
                    ?: continue

            val mappingId =
                mapping.optString("id")

            if (
                mappingId.isBlank()
            ) {
                continue
            }

            val includedMapping =
                included?.let {
                    findIncludedObject(
                        it,
                        "mappings",
                        mappingId
                    )
                }

            val attributes =
                includedMapping
                    ?.optJSONObject("attributes")
                    ?: continue

            val externalSite =
                attributes.optString(
                    "externalSite"
                )

            val externalId =
                attributes.optString(
                    "externalId"
                )

            if (
                externalId.isNotBlank() &&
                (
                    externalSite.equals(
                        "myanimelist",
                        true
                    ) ||
                    externalSite.equals(
                        "MyAnimeList",
                        true
                    ) ||
                    externalSite.contains(
                        "mal",
                        true
                    )
                )
            ) {
                return externalId.toIntOrNull()
            }
        }

        return null
    }

    private fun findIncludedObject(
        included: JSONArray,
        type: String,
        id: String
    ): JSONObject? {
        for (i in 0 until included.length()) {
            val item =
                included.optJSONObject(i)
                    ?: continue

            if (
                item.optString("type") == type &&
                item.optString("id") == id
            ) {
                return item
            }
        }

        return null
    }

    private fun findMalIdFromKitsuSlug(
        kitsuId: String
    ): Int? {
        return try {
            val url =
                URL(
                    "$KITSU_URL/anime/$kitsuId?include=mappings"
                )

            val connection =
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    useCaches = false
                    setRequestProperty(
                        "Accept",
                        "application/vnd.api+json"
                    )
                }

            try {
                if (
                    connection.responseCode !in 200..299
                ) {
                    return null
                }

                val body =
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                val root =
                    JSONObject(body)

                val included =
                    root.optJSONArray("included")

                val data =
                    root.optJSONObject("data")
                        ?: return null

                return findKitsuMalId(
                    data,
                    included
                )
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(
                "AniSkip",
                "Kitsu mapping exception id=$kitsuId",
                e
            )
            null
        }
    }

    private fun chooseBestMalCandidate(
        candidates: List<MalCandidate>,
        requestedSeason: Int?
    ): MalCandidate? {
        if (candidates.isEmpty()) return null

        val grouped = candidates
            .groupBy { it.malId }
            .values
            .mapNotNull { matches ->
                matches.maxByOrNull { it.score }
            }

        val eligible = grouped.filter { candidate ->
            val seasonOk = requestedSeason == null ||
                candidate.seasonNumber == null ||
                candidate.seasonNumber == requestedSeason

            val threshold = if (requestedSeason != null) 4500 else 6500

            seasonOk && candidate.score >= threshold
        }

        if (eligible.isEmpty()) {
            Log.w(
                "AniSkip",
                "MAL_SELECT no strong candidate requestedSeason=$requestedSeason " +
                    "candidates=${grouped.sortedByDescending { it.score }.take(5)}"
            )
            return null
        }

        return eligible.maxWithOrNull(
            compareBy<MalCandidate> {
                if (
                    requestedSeason != null &&
                    it.seasonNumber == requestedSeason
                ) 1 else 0
            }.thenBy { it.score }
        )
    }

    private fun buildTitleCandidates(
        title: String
    ): List<String> {
        val cleaned =
            title
                .replace(
                    Regex("\\[[^]]*]"),
                    " "
                )
                .replace(
                    Regex("\\([^)]*\\)"),
                    " "
                )
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim()

        val candidates =
            linkedSetOf<String>()

        fun addCandidate(value: String) {
            val candidate =
                value
                    .replace(
                        Regex("\\s+"),
                        " "
                    )
                    .trim()

            if (
                candidate.isNotBlank()
            ) {
                candidates.add(candidate)
            }
        }

        addCandidate(cleaned)

        val seasonless =
            cleaned
                .replace(
                    Regex(
                        "(?i)(?:\\b(?:season|part|cour|"
                            + "season\\s*[0-9]+|part\\s*[0-9]+)"
                            + "\\b|\\b\\d+\\s*(?:st|nd|rd|th)"
                            + "\\s+season\\b|\\b\\d+기\\b|"
                            + "\\b시즌\\s*\\d+\\b|\\b제\\s*\\d+\\s*기\\b|"
                            + "\\b第\\s*\\d+\\s*期\\b|\\b第\\s*\\d+\\s*季\\b)"
                    ),
                    " "
                )
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim()

        addCandidate(seasonless)

        addCandidate(
            cleaned.substringBefore(" - ")
        )

        addCandidate(
            cleaned.substringBefore(" | ")
        )

        Regex(
            "[A-Za-z][A-Za-z0-9À-ÿ'’:&.,!? -]{3,}"
        )
            .findAll(cleaned)
            .map {
                it.value.trim()
            }
            .filter {
                it.length >= 4
            }
            .forEach {
                addCandidate(it)
            }

        Regex(
            "(?i)(?:anime|title)?\\s*[:：]\\s*"
                + "([A-Za-z][A-Za-z0-9'’:&.,!? -]{3,})"
        )
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::addCandidate)

        return candidates.toList()
    }

    private fun extractRequestedSeason(
        title: String
    ): Int? {
        val patterns =
            listOf(
                Regex(
                    "(?i)\\b(?:season|part|cour)\\s*(\\d+)\\b"
                ),
                Regex(
                    "(?i)\\b(\\d+)(?:st|nd|rd|th)\\s+season\\b"
                ),
                Regex(
                    "(?i)\\b(\\d+)\\s*기\\b"
                ),
                Regex(
                    "(?i)\\b시즌\\s*(\\d+)\\b"
                ),
                Regex(
                    "(?i)\\b제\\s*(\\d+)\\s*기\\b"
                ),
                Regex(
                    "(?i)\\b第\\s*(\\d+)\\s*期\\b"
                ),
                Regex(
                    "(?i)\\b第\\s*(\\d+)\\s*季\\b"
                )
            )

        for (pattern in patterns) {
            pattern.find(title)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.let { return it }
        }

        return null
    }

    private fun extractSeasonNumber(
        value: String
    ): Int? {
        val patterns =
            listOf(
                Regex(
                    "(?i)\\bseason\\s*(\\d+)\\b"
                ),
                Regex(
                    "(?i)\\bpart\\s*(\\d+)\\b"
                ),
                Regex(
                    "(?i)\\b(\\d+)(?:st|nd|rd|th)\\s+season\\b"
                ),
                Regex(
                    "(?i)\\b(\\d+)\\s*기\\b"
                ),
                Regex(
                    "(?i)\\b시즌\\s*(\\d+)\\b"
                ),
                Regex(
                    "(?i)\\b제\\s*(\\d+)\\s*기\\b"
                ),
                Regex(
                    "(?i)\\b第\\s*(\\d+)\\s*期\\b"
                ),
                Regex(
                    "(?i)\\b第\\s*(\\d+)\\s*季\\b"
                ),
                Regex(
                    "(?i)\\bS(\\d+)\\b"
                )
            )

        for (pattern in patterns) {
            pattern.find(value)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.let { return it }
        }

        return null
    }

    private fun normalizeTitle(
        value: String
    ): String =
        value
            .lowercase(Locale.ROOT)
            .replace(
                Regex("\\[[^]]*]"),
                " "
            )
            .replace(
                Regex("\\([^)]*\\)"),
                " "
            )
            .replace(
                Regex("[^\\p{L}\\p{N}]+"),
                " "
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()

    private fun compareTitles(
        a: String,
        b: String
    ): Int {
        if (
            a.isBlank() ||
            b.isBlank()
        ) {
            return 0
        }

        if (a == b) {
            return 10000
        }

        if (
            a.contains(b) ||
            b.contains(a)
        ) {
            return 8000
        }

        val left =
            a.split(' ')
                .filter {
                    it.length >= 2
                }
                .toSet()

        val right =
            b.split(' ')
                .filter {
                    it.length >= 2
                }
                .toSet()

        if (
            left.isEmpty() ||
            right.isEmpty()
        ) {
            return 0
        }

        val overlap =
            left.intersect(right).size.toDouble() /
                maxOf(
                    left.size,
                    right.size
                ).toDouble()

        return (
            overlap * 6000.0
        ).toInt()
    }
}

// ============================================================
// MAIN ACTIVITY
// ============================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: AnimeViewModel = viewModel()
            val context = LocalContext.current
            
            LaunchedEffect(Unit) {
                viewModel.monitorNetwork(context)
                viewModel.loadAnime(context)
            }
            
            LilacApp(vm = viewModel)
        }
    }
}

// ============================================================
// VIEW MODEL
// ============================================================

class AnimeViewModel : ViewModel() {
    private val repository = AnimeRepository()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline

    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedIds: StateFlow<Set<String>> = _downloadedIds

    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap

    var homeAnime by mutableStateOf<List<Anime>>(emptyList())
        private set

    var allAnime by mutableStateOf<List<Anime>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    var isAllAnimeLoading by mutableStateOf(false)
        private set

    private var isAllAnimeFullyLoaded = false

    var error by mutableStateOf<String?>(null)
        private set

    var playerSettings by mutableStateOf(PlayerSettings())
        private set

    private val detailCache = mutableStateMapOf<String, Anime>()
    private val animeCache = mutableMapOf<String, Anime>()

    private val episodeCache = mutableStateMapOf<String, List<Episode>>()
    private val dubEpisodeCache = mutableStateMapOf<String, List<Episode>>()
    private val episodeLoading = mutableStateMapOf<String, Boolean>()
    
    private val isOfflineOnlyCache = mutableStateMapOf<String, Boolean>()

    var library by mutableStateOf<Set<String>>(emptySet())
        private set

    var watchHistory by mutableStateOf<List<WatchProgress>>(emptyList())
        private set

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        startProgressTracking()
    }

    @OptIn(UnstableApi::class)
    private fun startProgressTracking() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val progressMap = mutableMapOf<String, Float>()
                var hasActiveDownloads = false

                try {
                    val cursor = LilacApplication.downloadManager.downloadIndex.getDownloads()
                    while (cursor.moveToNext()) {
                        val download = cursor.download
                        if (download.state == Download.STATE_DOWNLOADING || download.state == Download.STATE_QUEUED) {
                            hasActiveDownloads = true
                            val percent = if (download.percentDownloaded != C.PERCENTAGE_UNSET.toFloat()) {
                                (download.percentDownloaded / 100f).coerceAtLeast(0f)
                            } else 0f
                            
                            progressMap[download.request.id] = percent
                        }
                    }
                    cursor.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                _downloadProgressMap.value = progressMap
                _downloadedIds.value = fetchDownloadedIdsInternal()

                delay(if (hasActiveDownloads) 300L else 1000L)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun fetchDownloadedIdsInternal(): Set<String> {
        val ids = mutableSetOf<String>()
        try {
            val cursor = LilacApplication.downloadManager.downloadIndex.getDownloads()
            while (cursor.moveToNext()) {
                val download = cursor.download
                if (download.state == Download.STATE_COMPLETED) {
                    ids.add(download.request.id)
                }
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ids
    }

    fun monitorNetwork(context: Context) {
        if (networkCallback != null) return
        
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(activeNetwork)
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _isOffline.value = !isConnected

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOffline.value = false
            }

            override fun onLost(network: Network) {
                _isOffline.value = true
            }
        }
        
        networkCallback = callback
        cm.registerNetworkCallback(request, callback)

        refreshDownloads()
    }

    fun refreshDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            _downloadedIds.value = fetchDownloadedIdsInternal()
        }
    }

    fun isEpisodeDownloaded(animeId: String, episodeNumber: Int): Boolean {
        return _downloadedIds.value.contains("${animeId}_${episodeNumber}")
    }

    fun loadAnime(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val lib = OfflineStore.getLibrary(context)
            val history = OfflineStore.getWatchHistory(context)
            val settings = OfflineStore.getPlayerSettings(context)
            val cachedList = OfflineStore.getSavedAnimeList(context)

            withContext(Dispatchers.Main) {
                library = lib
                watchHistory = history
                playerSettings = settings
                if (cachedList.isNotEmpty() && homeAnime.isEmpty()) {
                    homeAnime = cachedList.take(10)
                    allAnime = cachedList
                    cachedList.forEach { animeCache[it.id] = it }
                    loading = false
                } else if (homeAnime.isEmpty()) {
                    loading = true
                }
                error = null
            }

            if (!_isOffline.value) {
                try {
                    val firstPageList = repository.getHomeAnimeList()
                    if (firstPageList.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            homeAnime = firstPageList.take(10)
                            if (allAnime.isEmpty()) allAnime = firstPageList
                            firstPageList.forEach { animeCache[it.id] = it }
                        }
                        OfflineStore.saveAnimeList(context, firstPageList)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        if (homeAnime.isEmpty()) {
                            error = e.message ?: "목록을 불러오지 못했습니다."
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        loading = false
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    loading = false
                }
            }

            // Load the complete catalog in the background so SearchScreen does
            // not depend on the user visiting the All Anime tab first.
            if (!_isOffline.value) {
                loadAllAnime()
            }
        }
    }

    fun updatePlayerSettings(context: Context, newSettings: PlayerSettings) {
        playerSettings = newSettings
        viewModelScope.launch(Dispatchers.IO) {
            OfflineStore.savePlayerSettings(context, newSettings)
        }
    }

    fun loadAllAnime() {
        if (isAllAnimeLoading || isAllAnimeFullyLoaded) return
        isAllAnimeLoading = true
        
        viewModelScope.launch {
            try {
                repository.getAllAnimeListFlow().collect { list ->
                    allAnime = list
                    list.forEach { animeCache[it.id] = it }
                }
                isAllAnimeFullyLoaded = true
            } catch (_: Exception) {
            } finally {
                isAllAnimeLoading = false
            }
        }
    }

    fun loadAnimeDetail(target: Anime) {
        if (detailCache.containsKey(target.id)) return

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getAnimeDetail(target)
                }
                detailCache[result.id] = result
                animeCache[result.id] = result
                homeAnime = homeAnime.map { if (it.id == result.id) result else it }
                allAnime = allAnime.map { if (it.id == result.id) result else it }
            } catch (_: Exception) {}
        }
    }

    suspend fun getDownloadedAnimeList(context: Context): List<Anime> = withContext(Dispatchers.IO) {
        val downloadedAnimeIds = _downloadedIds.value.mapNotNull { id ->
            id.split("_").firstOrNull()
        }.toSet()

        val allAvailableAnime = (homeAnime + allAnime + detailCache.values + animeCache.values).distinctBy { it.id }
        val resultMap = allAvailableAnime.filter { it.id in downloadedAnimeIds }.associateBy { it.id }.toMutableMap()

        for (animeId in downloadedAnimeIds) {
            if (!resultMap.containsKey(animeId)) {
                val storedAnime = OfflineStore.getAnime(context, animeId)
                if (storedAnime != null) {
                    resultMap[animeId] = storedAnime
                } else {
                    resultMap[animeId] = Anime(
                        id = animeId,
                        title = "오프라인 저장 항목 ($animeId)",
                        poster = "",
                        description = "오프라인 상태에서 다운로드된 콘텐츠입니다.",
                        genres = listOf("오프라인")
                    )
                }
            }
        }

        resultMap.values.toList()
    }

    fun getAnime(context: Context, id: String): Anime? {
        return detailCache[id] 
            ?: animeCache[id] 
            ?: homeAnime.firstOrNull { it.id == id }
            ?: allAnime.firstOrNull { it.id == id }
    }

    fun episodes(anime: Anime): List<Episode> {
        return episodeCache[anime.id] ?: emptyList()
    }

    fun dubEpisodes(anime: Anime): List<Episode> {
        return dubEpisodeCache[anime.id] ?: emptyList()
    }

    fun isEpisodesLoading(anime: Anime): Boolean {
        return episodeLoading[anime.id] == true
    }

    fun loadEpisodes(context: Context, anime: Anime) {
        val currentList = episodeCache[anime.id]
        val isOfflineOnly = isOfflineOnlyCache[anime.id] ?: false

        if (_isOffline.value) {
            loadOfflineEpisodes(context, anime)
            return
        }

        if (!currentList.isNullOrEmpty() && !isOfflineOnly) {
            return
        }

        episodeLoading[anime.id] = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val targetAnime = detailCache[anime.id] ?: repository.getAnimeDetail(anime).also {
                    withContext(Dispatchers.Main) {
                        detailCache[it.id] = it
                        animeCache[it.id] = it
                    }
                }

                val result = repository.getEpisodes(targetAnime)
                
                withContext(Dispatchers.Main) {
                    if (result.isNotEmpty()) {
                        episodeCache[anime.id] = result
                        isOfflineOnlyCache[anime.id] = false
                    } else {
                        loadOfflineEpisodes(context, anime)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadOfflineEpisodes(context, anime)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    episodeLoading[anime.id] = false
                }
            }
        }
    }

    fun loadOfflineEpisodes(context: Context, anime: Anime) {
        viewModelScope.launch(Dispatchers.IO) {
            val storedEpisodes = OfflineStore.getEpisodesForAnime(context, anime.id)
            if (storedEpisodes.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    episodeCache[anime.id] = storedEpisodes
                    isOfflineOnlyCache[anime.id] = true
                }
                return@launch
            }

            val downloadedEpNumbers = _downloadedIds.value
                .filter { it.startsWith("${anime.id}_") }
                .mapNotNull { it.substringAfter("${anime.id}_").toIntOrNull() }
                .sorted()

            val offlineList = downloadedEpNumbers.map { epNum ->
                OfflineStore.getEpisode(context, anime.id, epNum) ?: Episode(
                    id = "${anime.id}_$epNum",
                    number = epNum,
                    title = "${epNum}화"
                )
            }

            withContext(Dispatchers.Main) {
                episodeCache[anime.id] = offlineList
                isOfflineOnlyCache[anime.id] = true
            }
        }
    }

    fun loadDubEpisodes(anime: Anime) {
        if (dubEpisodeCache.containsKey(anime.id)) return

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getDubEpisodes(anime)
                }
                dubEpisodeCache[anime.id] = result
            } catch (_: Exception) {
                dubEpisodeCache[anime.id] = emptyList()
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun deleteDownload(context: Context, anime: Anime, episodeNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val downloadId = "${anime.id}_${episodeNumber}"
            
            val ep = OfflineStore.getEpisode(context, anime.id, episodeNumber)
            ep?.vttUrl?.let { path ->
                if (path.startsWith("/")) {
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
            }
            
            DownloadService.sendRemoveDownload(
                context,
                LilacDownloadService::class.java,
                downloadId,
                false
            )

            OfflineStore.removeEpisode(context, anime.id, episodeNumber)

            refreshDownloads()
            if (_isOffline.value) {
                loadOfflineEpisodes(context, anime)
            }
        }
    }

    fun toggleLibrary(context: Context, animeId: String) {
        library = if (animeId in library) library - animeId else library + animeId
        val updated = library
        viewModelScope.launch(Dispatchers.IO) {
            OfflineStore.saveLibrary(context, updated)
        }
    }

    fun isInLibrary(animeId: String): Boolean {
        return animeId in library
    }

    fun updateProgress(context: Context, animeId: String, episodeNumber: Int, progress: Float) {
        val filtered = watchHistory.filterNot { it.animeId == animeId && it.episodeNumber == episodeNumber }
        val updatedItem = WatchProgress(animeId = animeId, episodeNumber = episodeNumber, progress = progress)
        val newList = listOf(updatedItem) + filtered
        watchHistory = newList
        viewModelScope.launch(Dispatchers.IO) {
            OfflineStore.saveWatchHistory(context, newList)
        }
    }

    fun getLatestProgress(animeId: String): WatchProgress? {
        return watchHistory.firstOrNull { it.animeId == animeId }
    }

    fun getProgress(animeId: String, episodeNumber: Int): WatchProgress? {
        return watchHistory.firstOrNull { it.animeId == animeId && it.episodeNumber == episodeNumber }
    }
    // 다운로드 취소 및 상태/파일 정리
fun cancelDownload(context: Context, animeId: String, episodeNumber: Int) {
    val downloadKey = "${animeId}_${episodeNumber}"
    
    // 1. 진행 중인 서비스/Worker 작업 중단 요청
    val intent = Intent(context, DownloadService::class.java).apply {
        action = "ACTION_CANCEL_DOWNLOAD"
        putExtra("EXTRA_ANIME_ID", animeId)
        putExtra("EXTRA_EPISODE_NUMBER", episodeNumber)
    }
    context.startService(intent)

    // 2. ViewModel 진행률 맵에서 제거
    _downloadProgressMap.update { currentMap ->
        currentMap - downloadKey
    }
    
    // 3. DB 오프라인 데이터가 존재할 경우 제거
    deleteDownload(context, Anime(id = animeId, title = "", poster = "", backdrop = "", description = ""), episodeNumber)
}
}

// ==========================================
// 1. DataStore 싱글톤 선언 및 테마 저장 키
// ==========================================
private val Context.dataStore by preferencesDataStore(name = "theme_settings")
private val THEME_KEY = stringPreferencesKey("theme_mode")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

// 색상 값 정의
val Lilac = Color(0xFFC8A2C8)
val LilacDark = Color(0xFF9A7B9A)
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF5F5F5)

@Composable
fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}

// ==========================================
// 2. 메인 App Composable (DataStore 연동 완료)
// ==========================================
@Composable
fun LilacApp(vm: AnimeViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. DataStore에서 테마 상태 읽기
    val themeModeFlow = remember {
        context.dataStore.data.map { prefs ->
            val savedName = prefs[THEME_KEY] ?: ThemeMode.LIGHT.name
            runCatching { ThemeMode.valueOf(savedName) }.getOrDefault(ThemeMode.LIGHT)
        }
    }
    // State 수집 (by 키워드로 값 가져오기)
    val themeMode by themeModeFlow.collectAsState(initial = ThemeMode.LIGHT)

    // 2. DataStore에 테마 저장하는 콜백 함수
    val onThemeChange: (ThemeMode) -> Unit = { newMode ->
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[THEME_KEY] = newMode.name
            }
        }
    }

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Lilac,
            onPrimary = Color.White,
            secondary = LilacDark,
            onSecondary = Color.White,
            background = DarkBackground,
            onBackground = Color(0xFFF0EDF6),
            surface = DarkSurface,
            onSurface = Color(0xFFF0EDF6)
        )
    } else {
        lightColorScheme(
            primary = Lilac,
            onPrimary = Color.White,
            secondary = LilacDark,
            onSecondary = Color.White,
            background = LightBackground,
            onBackground = Color(0xFF1C1B1F),
            surface = LightSurface,
            onSurface = Color(0xFF1C1B1F)
        )
    }

    val nav = rememberNavController()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "알림 권한이 없어 다운로드 진행 상태가 표시되지 않을 수 있습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    MaterialTheme(colorScheme = colors) {
        NavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    vm = vm,
                    openDetail = { nav.navigate("detail/${it.id}") },
                    onNavigate = { nav.navigate(it) }
                )
            }

            composable("all") {
                LaunchedEffect(Unit) {
                    vm.loadAllAnime()
                }
                AllAnimeScreen(
                    vm = vm,
                    openDetail = { nav.navigate("detail/${it.id}") },
                    onNavigate = { nav.navigate(it) }
                )
            }

            composable("search") {
                SearchScreen(
                    vm = vm,
                    open = { nav.navigate("detail/${it.id}") },
                    onNavigate = { nav.navigate(it) }
                )
            }

            composable("library") {
                LibraryScreen(
                    vm = vm,
                    open = { nav.navigate("detail/${it.id}") },
                    onNavigate = { nav.navigate(it) }
                )
            }

            composable("settings") {
                SettingsScreen(
                    vm = vm,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange, // DataStore 저장 함수 연결
                    onNavigate = { nav.navigate(it) }
                )
            }

            composable("detail/{id}") { backStack ->
                val id = backStack.arguments?.getString("id")
                var item by remember { mutableStateOf(id?.let { vm.getAnime(context, it) }) }

                LaunchedEffect(id) {
                    if (item == null && id != null) {
                        item = OfflineStore.getAnime(context, id)
                    }
                }

                val currentItem = item
                when {
                    currentItem != null -> {
                        DetailScreen(
                            vm = vm,
                            anime = currentItem,
                            back = { nav.popBackStack() },
                            playEpisode = { ep ->
                                nav.navigate("player/${currentItem.id}/${ep.number}")
                            }
                        )
                    }
                    vm.loading -> {
                        FullScreenState(message = "불러오는 중...", isLoading = true)
                    }
                    else -> {
                        FullScreenState(
                            message = "해당 작품을 찾을 수 없습니다.",
                            isLoading = false,
                            onRetry = { vm.loadAnime(context) },
                            onBack = { nav.popBackStack() }
                        )
                    }
                }
            }

            composable("player/{animeId}/{episodeNumber}") { backStack ->
                val animeId = backStack.arguments?.getString("animeId")
                val episodeNumber = backStack.arguments?.getString("episodeNumber")?.toIntOrNull()
                var anime by remember { mutableStateOf(animeId?.let { vm.getAnime(context, it) }) }
                var episode by remember { mutableStateOf<Episode?>(null) }

                LaunchedEffect(animeId, episodeNumber) {
                    if (anime == null && animeId != null) {
                        anime = OfflineStore.getAnime(context, animeId)
                    }
                    val targetAnime = anime
                    if (targetAnime != null && episodeNumber != null) {
                        val epFromCache = vm.episodes(targetAnime).firstOrNull { it.number == episodeNumber }
                            ?: vm.dubEpisodes(targetAnime).firstOrNull { it.number == episodeNumber }
                        if (epFromCache != null) {
                            episode = epFromCache
                        } else {
                            val stored = OfflineStore.getEpisode(context, targetAnime.id, episodeNumber)
                            episode = stored ?: Episode(
                                id = "${targetAnime.id}_ep_$episodeNumber",
                                number = episodeNumber,
                                title = "${episodeNumber}화"
                            )
                        }
                    }
                }

                val currentAnime = anime
                val currentEpisode = episode

                when {
                    currentAnime != null && currentEpisode != null -> {
                        PlayerScreen(
                            anime = currentAnime,
                            episode = currentEpisode,
                            vm = vm,
                            back = { nav.popBackStack() }
                        )
                    }
                    vm.loading -> {
                        FullScreenState(message = "불러오는 중...", isLoading = true)
                    }
                    else -> {
                        FullScreenState(
                            message = "재생할 항목을 찾을 수 없습니다.",
                            isLoading = false,
                            onRetry = { vm.loadAnime(context) },
                            onBack = { nav.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// FULL SCREEN STATE & SCAFFOLD
// ============================================================

@Composable
fun FullScreenState(
    message: String,
    isLoading: Boolean,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isLoading) {
                CircularProgressIndicator(color = Lilac)
            } else {
                Icon(Icons.Default.ErrorOutline, null, tint = LilacDark, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(message, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onRetry != null) {
                    Button(onClick = onRetry) { Text("다시 시도", color = Color.White) }
                }
                if (onBack != null) {
                    OutlinedButton(onClick = onBack) { Text("뒤로", color = MaterialTheme.colorScheme.onBackground) }
                }
            }
        }
    }
}

@Composable
fun AppScaffold(
    selected: String,
    onSelect: (String) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val items = listOf(
        Triple("home", "홈", Icons.Default.Home),
        Triple("all", "전체", Icons.Default.GridView),
        Triple("search", "검색", Icons.Default.Search),
        Triple("library", "내 목록", Icons.Default.Favorite),
        Triple("settings", "설정", Icons.Default.Settings)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                items.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = selected == route,
                        onClick = { onSelect(route) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        },
        content = content
    )
}

// ============================================================
// HOME
// ============================================================

@Composable
fun HomeScreen(
    vm: AnimeViewModel,
    openDetail: (Anime) -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    AppScaffold(
        selected = "home",
        onSelect = onNavigate
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "안녕하세요",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Text("오늘은 무엇을 볼까요?", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { onNavigate("search") }) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            item {
                if (vm.loading && vm.homeAnime.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Lilac)
                    }
                } else if (vm.error != null && vm.homeAnime.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = vm.error ?: "오류가 발생했습니다.", color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.loadAnime(context) }) { Text("다시 시도", color = Color.White) }
                    }
                } else if (vm.homeAnime.isNotEmpty()) {
                    HeroCard(anime = vm.homeAnime.first(), open = openDetail)
                } else {
                    Text("등록된 애니메이션이 없습니다.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onBackground)
                }
            }

            if (vm.watchHistory.isNotEmpty()) {
                item { RailTitle("계속 시청하기") }
                item { ContinueWatchingRail(vm = vm, open = openDetail) }
            }

            if (vm.homeAnime.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    RailTitle("최신 애니메이션")
                }
                item { AnimeRail(vm.homeAnime.take(10), openDetail) }

                item {
                    Spacer(Modifier.height(20.dp))
                    RailTitle("추천 애니메이션")
                }
                item { AnimeRail(vm.homeAnime.reversed().take(10), openDetail) }
            }
        }
    }
}

// ============================================================
// ALL ANIME
// ============================================================

@Composable
fun AllAnimeScreen(
    vm: AnimeViewModel,
    openDetail: (Anime) -> Unit,
    onNavigate: (String) -> Unit
) {
    LaunchedEffect(Unit) {
        vm.loadAllAnime()
    }

    AppScaffold(selected = "all", onSelect = onNavigate) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("전체 애니메이션", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                if (vm.isAllAnimeLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Lilac,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("${vm.allAnime.size}개", color = LilacDark, fontSize = 14.sp)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                // AllAnimeScreen 내의 items 항목 수정
items(
    items = vm.allAnime,
    key = { anime -> anime.id }
) { anime ->
    Column(
        modifier = Modifier.clickableNoIndication { openDetail(anime) }
    ) {
        AsyncImage(
            model = anime.backdrop.ifEmpty { anime.poster }, // backdrop을 사용하고, 없을 경우 poster로 대체
            contentDescription = anime.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f) // 가로 16:9 비율 설정
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(6.dp))
        Text(
            anime.title,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            anime.genres.joinToString(" · "),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
            }
        }
    }
}

// ============================================================
// HOME RAILS & CARDS
// ============================================================

@Composable
fun ContinueWatchingRail(
    vm: AnimeViewModel,
    open: (Anime) -> Unit
) {
    val allAvailableAnime = (vm.homeAnime + vm.allAnime).distinctBy { it.id }
    val watchingAnime = vm.watchHistory
        .map { it.animeId }
        .distinct()
        .mapNotNull { id -> allAvailableAnime.firstOrNull { it.id == id } }

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        watchingAnime.forEach { anime ->
            val latestProgress = vm.getLatestProgress(anime.id)
            if (latestProgress != null) {
                Column(
                    modifier = Modifier.width(190.dp).clickableNoIndication { open(anime) }
                ) {
                    Box {
                        AsyncImage(
                            model = anime.backdrop,
                            contentDescription = anime.title,
                            modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Icon(
                            Icons.Default.PlayArrow,
                            null,
                            modifier = Modifier.align(Alignment.Center).size(42.dp),
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(anime.title, fontWeight = FontWeight.SemiBold, maxLines = 1, color = MaterialTheme.colorScheme.onBackground)
                    Text("EP.${latestProgress.episodeNumber}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { latestProgress.progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Lilac
                    )
                }
            }
        }
    }
}

@Composable
fun HeroCard(anime: Anime, open: (Anime) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(300.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickableNoIndication { open(anime) }
    ) {
        AsyncImage(
            model = anime.backdrop,
            contentDescription = anime.title,
            // height(200.dp)를 fillMaxSize()로 변경하여 부모 Box(300.dp)를 꽉 채우도록 수정했습니다.
            modifier = Modifier.fillMaxSize(), 
            contentScale = ContentScale.Crop // 여백 없이 꽉 채우고 싶다면 Crop 유지
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)))
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
            Text("FEATURED", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(anime.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { open(anime) },
                colors = ButtonDefaults.buttonColors(containerColor = Lilac, contentColor = Color.White)
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("지금 보기", color = Color.White)
            }
        }
    }
}

@Composable
fun RailTitle(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun AnimeRail(list: List<Anime>, openDetail: (Anime) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(list) { anime ->
            Column(
                modifier = Modifier
                    .width(190.dp) // 계속 시청하기와 동일한 가로 너비
                    .clickableNoIndication { openDetail(anime) }
            ) {
                AsyncImage(
                    model = anime.backdrop.ifEmpty { anime.poster }, // backdrop 사용[cite: 8]
                    contentDescription = anime.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp) // 계속 시청하기와 동일한 가로 높이[cite: 8]
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    anime.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

// ============================================================
// SEARCH & LIBRARY
// ============================================================

@Composable
fun SearchScreen(
    vm: AnimeViewModel,
    open: (Anime) -> Unit,
    onNavigate: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val searchList = remember(vm.homeAnime, vm.allAnime) {
        (vm.allAnime + vm.homeAnime).distinctBy { it.id }
    }

    AppScaffold(selected = "search", onSelect = onNavigate) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text("검색", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("작품명, 장르 검색", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = Lilac,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(18.dp),
                singleLine = true
            )

            Spacer(Modifier.height(20.dp))

            val results = searchList.filter {
                query.isBlank() || it.title.contains(query, true) || it.genres.any { genre -> genre.contains(query, true) }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(results) { anime ->
                    Row(
                        Modifier.fillMaxWidth().clickableNoIndication { open(anime) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = anime.poster,
                            contentDescription = anime.title,
                            modifier = Modifier.size(width = 78.dp, height = 110.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(anime.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                anime.genres.joinToString(" · "),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    vm: AnimeViewModel,
    open: (Anime) -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val isOffline by vm.isOffline.collectAsState()
    val downloadedIds by vm.downloadedIds.collectAsState()
    
    LaunchedEffect(Unit) {
        vm.refreshDownloads()
    }

    var downloadedAnimeList by remember { mutableStateOf<List<Anime>>(emptyList()) }
    LaunchedEffect(downloadedIds, vm.homeAnime, vm.allAnime) {
        downloadedAnimeList = vm.getDownloadedAnimeList(context)
    }

    val allAnimeList = (vm.allAnime + vm.homeAnime).distinctBy { it.id }
    val savedAnime = allAnimeList.filter { it.id in vm.library }

    var selectedTab by remember { mutableIntStateOf(if (isOffline) 1 else 0) }

    AppScaffold(selected = "library", onSelect = onNavigate) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = "보관함", 
                fontSize = 28.sp, 
                fontWeight = FontWeight.Bold, 
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp)
            )

            Spacer(Modifier.height(12.dp))

            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("내 목록", color = MaterialTheme.colorScheme.onSurface) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("다운로드 완료 (${downloadedAnimeList.size})", color = MaterialTheme.colorScheme.onSurface) }
                )
            }

            Spacer(Modifier.height(16.dp))

            val currentList = if (selectedTab == 0) savedAnime else downloadedAnimeList

            if (currentList.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (selectedTab == 0) Icons.Default.FavoriteBorder else Icons.Default.DownloadDone,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Lilac
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (selectedTab == 0) "내 목록이 비어 있습니다" else "다운로드된 애니메이션이 없습니다",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(currentList) { anime ->
                        Row(modifier = Modifier.fillMaxWidth().clickableNoIndication { open(anime) }) {
                            AsyncImage(
                                model = anime.poster,
                                contentDescription = anime.title,
                                modifier = Modifier.size(width = 90.dp, height = 130.dp).clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(anime.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Spacer(Modifier.height(6.dp))
                                Text(anime.genres.joinToString(" · "), color = LilacDark)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    anime.description,
                                    maxLines = 2,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// SETTINGS
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AnimeViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val settings = vm.playerSettings

    AppScaffold(selected = "settings", onSelect = onNavigate) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("설정", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            
            Spacer(Modifier.height(24.dp))
            Text("테마", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(12.dp))

            ThemeOption("시스템 설정", themeMode == ThemeMode.SYSTEM) { onThemeChange(ThemeMode.SYSTEM) }
            ThemeOption("라이트 모드", themeMode == ThemeMode.LIGHT) { onThemeChange(ThemeMode.LIGHT) }
            ThemeOption("다크 모드", themeMode == ThemeMode.DARK) { onThemeChange(ThemeMode.DARK) }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            Text("재생 및 자막 설정", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))

            Text("기본 화질", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Auto", "720p", "1080p").forEach { quality ->
                    FilterChip(
                        selected = settings.defaultQuality == quality,
                        onClick = { vm.updatePlayerSettings(context, settings.copy(defaultQuality = quality)) },
                        label = { Text(quality) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("기본 자막 크기 (${settings.subtitleSize.toInt()}%)", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            Slider(
                value = settings.subtitleSize,
                onValueChange = { vm.updatePlayerSettings(context, settings.copy(subtitleSize = it)) },
                valueRange = 50f..300f,
                steps = 10
            )

            Spacer(Modifier.height(16.dp))

            Text("자막 싱크 미세 조정 (${settings.syncOffsetMs} ms)", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { 
                    vm.updatePlayerSettings(context, settings.copy(syncOffsetMs = settings.syncOffsetMs - 250L)) 
                }) {
                    Text("-250ms")
                }
                OutlinedButton(onClick = { 
                    vm.updatePlayerSettings(context, settings.copy(syncOffsetMs = 0L)) 
                }) {
                    Text("초기화")
                }
                OutlinedButton(onClick = { 
                    vm.updatePlayerSettings(context, settings.copy(syncOffsetMs = settings.syncOffsetMs + 250L)) 
                }) {
                    Text("+250ms")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("기본 자막 폰트", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("기본체", "나눔고딕", "명조체").forEach { font ->
                    FilterChip(
                        selected = settings.subtitleFont == font,
                        onClick = { vm.updatePlayerSettings(context, settings.copy(subtitleFont = font)) },
                        label = { Text(font) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            Text("Lilac Anime", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text("Version 0.1.0", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun ThemeOption(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickableNoIndication { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Lilac.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                null,
                tint = if (selected) Lilac else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(14.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

// ============================================================
// DETAIL
// ============================================================

@OptIn(UnstableApi::class)
@Composable
fun DetailScreen(
    vm: AnimeViewModel,
    anime: Anime,
    back: () -> Unit,
    playEpisode: (Episode) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isOffline by vm.isOffline.collectAsState()

    LaunchedEffect(anime.id, isOffline) {
        vm.loadAnimeDetail(anime)
        vm.loadEpisodes(context, anime)
    }

    val saved = vm.isInLibrary(anime.id)
    val episodes = vm.episodes(anime)
    val episodesLoading = vm.isEpisodesLoading(anime)

    val downloadProgressMap by vm.downloadProgressMap.collectAsState()

    // 배치 다운로드 상태 관리
    var isBatchDownloading by remember { mutableStateOf(false) }
    var batchTotalCount by remember { mutableIntStateOf(0) }
    var batchCurrentIndex by remember { mutableIntStateOf(0) }

    // 추출 UI 제어용 에피소드 저장 상태
    var activeExtractEpisode by remember { mutableStateOf<Episode?>(null) }

    // 비동기 URL 추출용 Deferred 관리
    var currentExtractDeferred by remember { mutableStateOf<CompletableDeferred<Pair<List<StreamQuality>, String?>>?>(null) }

    // 비동기 URL 추출 함수
    suspend fun extractEpisodeInfo(ep: Episode): Pair<List<StreamQuality>, String?> {
        val deferred = CompletableDeferred<Pair<List<StreamQuality>, String?>>()
        currentExtractDeferred = deferred
        activeExtractEpisode = ep

        val result = deferred.await()

        activeExtractEpisode = null
        currentExtractDeferred = null
        return result
    }

    // 화질 선택 대기용 Deferred 및 Dialog 상태 관리
    var pendingQualitiesDialog by remember { mutableStateOf<List<StreamQuality>?>(null) }
    var pendingDialogTargetEpisode by remember { mutableStateOf<Episode?>(null) }
    var currentQualityDeferred by remember { mutableStateOf<CompletableDeferred<StreamQuality>?>(null) }

    // 화질 선택 대기 함수 (2개 이상 화질 감지 시 호출)
    suspend fun awaitQualitySelection(ep: Episode, qualities: List<StreamQuality>): StreamQuality {
        val deferred = CompletableDeferred<StreamQuality>()
        currentQualityDeferred = deferred
        pendingDialogTargetEpisode = ep
        pendingQualitiesDialog = qualities

        val selected = deferred.await()

        pendingQualitiesDialog = null
        pendingDialogTargetEpisode = null
        currentQualityDeferred = null
        return selected
    }

    // 단일 에피소드 다운로드 프로세스
    fun processSingleDownload(ep: Episode) {
        scope.launch(Dispatchers.Main) {
            try {
                val (qualities, vttUrl) = extractEpisodeInfo(ep)
                if (qualities.isEmpty()) {
                    Toast.makeText(context, "${ep.number}화의 다운로드 주소를 찾지 못했습니다.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val selectedQuality = if (qualities.size > 1) {
                    awaitQualitySelection(ep, qualities)
                } else {
                    qualities.first()
                }

                withContext(Dispatchers.IO) {
                    // 오프라인 재생에서도 원본 ASS 스타일을 유지할 수 있도록
                    // 다운로드 시 Kairan ASS를 먼저 찾아 앱 내부 영구 저장소에 보관한다.
                    val kairanSubtitlePath = try {
                        when (val result = KairanSubtitleService.findSubtitle(context, anime.title, ep.number)) {
                            is KairanSubtitleResult.DirectFile -> result.path
                            null -> null
                        }
                    } catch (e: Exception) {
                        Log.w("Kairan", "OFFLINE_ASS_PRELOAD_FAILED episode=${ep.number}", e)
                        null
                    }

                    val localVttPath = if (kairanSubtitlePath == null) {
                        downloadSubtitleFile(context, anime.id, ep.number, vttUrl)
                    } else {
                        null
                    }

                    startEpisodeDownload(context, anime.id, anime.title, ep, selectedQuality.url)

                    OfflineStore.saveAnime(context, anime)
                    OfflineStore.saveEpisode(
                        context = context,
                        animeId = anime.id,
                        episode = ep.copy(
                            videoUrl = selectedQuality.url,
                            vttUrl = kairanSubtitlePath ?: localVttPath ?: vttUrl
                        )
                    )
                }
                Toast.makeText(context, "${ep.number}화 (${selectedQuality.label}) 다운로드를 시작합니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 전체 에피소드 다운로드 프로세스 (순차적 URL 추출 및 순차 다운로드 실행)
    fun processBatchDownload(targetEpisodes: List<Episode>) {
        if (targetEpisodes.isEmpty()) return
        isBatchDownloading = true
        batchTotalCount = targetEpisodes.size
        batchCurrentIndex = 0

        scope.launch(Dispatchers.Main) {
            for ((index, ep) in targetEpisodes.withIndex()) {
                if (!isBatchDownloading) break // 다운로드 중단 플래그 체크

                batchCurrentIndex = index + 1
                try {
                    val (qualities, vttUrl) = extractEpisodeInfo(ep)
                    if (qualities.isNotEmpty() && isBatchDownloading) {
                        val selectedQuality = if (qualities.size > 1) {
                            awaitQualitySelection(ep, qualities)
                        } else {
                            qualities.first()
                        }

                        withContext(Dispatchers.IO) {
                            // 배치 다운로드도 동일하게 Kairan ASS를 우선 저장한다.
                            val kairanSubtitlePath = try {
                                when (val result = KairanSubtitleService.findSubtitle(context, anime.title, ep.number)) {
                                    is KairanSubtitleResult.DirectFile -> result.path
                                    null -> null
                                }
                            } catch (e: Exception) {
                                Log.w("Kairan", "OFFLINE_ASS_PRELOAD_FAILED episode=${ep.number}", e)
                                null
                            }

                            val localVttPath = if (kairanSubtitlePath == null) {
                                downloadSubtitleFile(context, anime.id, ep.number, vttUrl)
                            } else {
                                null
                            }

                            startEpisodeDownload(context, anime.id, anime.title, ep, selectedQuality.url)

                            OfflineStore.saveAnime(context, anime)
                            OfflineStore.saveEpisode(
                                context = context,
                                animeId = anime.id,
                                episode = ep.copy(
                                    videoUrl = selectedQuality.url,
                                    vttUrl = kairanSubtitlePath ?: localVttPath ?: vttUrl
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isBatchDownloading = false
            Toast.makeText(context, "전체 다운로드 요청 처리가 완료되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Box(Modifier.fillMaxWidth().height(280.dp)) {
                    AsyncImage(
                        model = anime.backdrop,
                        contentDescription = anime.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                    IconButton(onClick = back, modifier = Modifier.padding(top = 12.dp, start = 8.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = Color.White)
                    }
                }
            }

            item {
                Column(Modifier.padding(20.dp)) {
                    Row {
                        AsyncImage(
                            model = anime.poster,
                            contentDescription = anime.title,
                            modifier = Modifier.size(width = 110.dp, height = 160.dp).clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(anime.title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.height(8.dp))
                            Text("${episodes.size}화", color = LilacDark)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                anime.genres.joinToString(" · "),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            enabled = episodes.isNotEmpty() && (!isOffline || vm.isEpisodeDownloaded(anime.id, episodes.first().number)),
                            onClick = {
                                val latestProgress = vm.getLatestProgress(anime.id)
                                val episodeNumber = latestProgress?.episodeNumber ?: episodes.first().number
                                val episode = episodes.firstOrNull { it.number == episodeNumber } ?: episodes.first()
                                playEpisode(episode)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Lilac, contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(5.dp))
                            Text("재생", color = Color.White)
                        }

                        OutlinedButton(onClick = { vm.toggleLibrary(context, anime.id) }) {
                            Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.width(5.dp))
                            Text(if (saved) "저장됨" else "내 목록", color = MaterialTheme.colorScheme.onBackground)
                        }

                        if (!isOffline && episodes.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    if (isBatchDownloading) {
                                        // 전체 다운로드 진행 중 클릭 시 취소
                                        isBatchDownloading = false
                                        activeExtractEpisode = null
                                        currentExtractDeferred?.cancel()
                                        Toast.makeText(context, "전체 다운로드가 중단되었습니다.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val notDownloaded = episodes.filter { !vm.isEpisodeDownloaded(anime.id, it.number) }
                                        if (notDownloaded.isNotEmpty()) {
                                            processBatchDownload(notDownloaded)
                                        } else {
                                            Toast.makeText(context, "모든 에피소드가 이미 다운로드되었습니다.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    if (isBatchDownloading) Icons.Default.Close else Icons.Default.DownloadForOffline,
                                    contentDescription = null,
                                    tint = if (isBatchDownloading) Color.Red else Lilac
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    if (isBatchDownloading) "취소 ($batchCurrentIndex/$batchTotalCount)" else "전체 저장",
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Text(anime.description, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                }
            }

            if (episodesLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Lilac)
                    }
                }
            } else if (episodes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("에피소드", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                }

                items(episodes) { ep ->
                    val downloadKey = "${anime.id}_${ep.number}"
                    val isDownloaded = vm.isEpisodeDownloaded(anime.id, ep.number)
                    val downloadingProgress = downloadProgressMap[downloadKey]
                    val epProgress = vm.getProgress(anime.id, ep.number)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDownloaded) Lilac.copy(alpha = 0.15f) else Color.Transparent)
                            .clickableNoIndication {
                                if (isDownloaded || !isOffline) playEpisode(ep)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Lilac.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(ep.number.toString(), color = Lilac, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(ep.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            if (isDownloaded) {
                                Text("오프라인 시청 가능", color = LilacDark, fontSize = 12.sp)
                            } else if (downloadingProgress != null) {
                                val percentText = (downloadingProgress * 100).toInt()
                                Text("다운로드 중... $percentText%", color = Lilac, fontSize = 12.sp)
                            }

                            if (epProgress != null && epProgress.progress > 0f) {
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { epProgress.progress },
                                    modifier = Modifier.fillMaxWidth(0.8f).height(3.dp),
                                    color = Lilac
                                )
                            }
                        }

                        when {
                            isDownloaded -> {
                                IconButton(
                                    onClick = { vm.deleteDownload(context, anime, ep.number) }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "삭제", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            downloadingProgress != null -> {
                                // 다운로드 진행 중: Circular Progress + 클릭 시 다운로드 취소
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable {
                                            vm.cancelDownload(context, anime.id, ep.number)
                                            Toast.makeText(context, "${ep.number}화 다운로드가 취소되었습니다.", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    CircularProgressIndicator(
                                        progress = { downloadingProgress ?: 0f },
                                        modifier = Modifier.fillMaxSize(),
                                        color = Lilac,
                                        strokeWidth = 3.dp
                                    )
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "취소",
                                        tint = Lilac,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            !isOffline -> {
                                IconButton(
                                    enabled = !isBatchDownloading && activeExtractEpisode == null,
                                    onClick = { processSingleDownload(ep) }
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "다운로드", tint = Lilac)
                                }
                            }
                            else -> {
                                Icon(Icons.Default.CloudOff, contentDescription = "오프라인", tint = Color.Gray)
                            }
                        }
                    }
                }
            } else {
                item {
                    Text("에피소드가 없습니다.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        // 실제 추출기(StreamUrlExtractor) 호출 브릿지
        activeExtractEpisode?.let { ep ->
            val target = ep.videoUrl
            if (!target.isNullOrBlank()) {
                var extractedVtt: String? = null
                Box(modifier = Modifier.size(1.dp).alpha(0f)) {
                    StreamUrlExtractor(
                        targetUrl = target,
                        onSubtitleFound = { vttUrl -> extractedVtt = vttUrl },
                        onQualitiesFound = { qualities ->
                            currentExtractDeferred?.complete(Pair(qualities, extractedVtt))
                        }
                    )
                }
            } else {
                currentExtractDeferred?.complete(Pair(emptyList(), null))
            }
        }

        // 2개 이상의 화질(m3u8)이 검색된 경우 띄우는 화질 선택 다이얼로그
        val qualitiesForDialog = pendingQualitiesDialog
        if (qualitiesForDialog != null) {
            AlertDialog(
                onDismissRequest = {
                    currentQualityDeferred?.complete(qualitiesForDialog.first())
                },
                title = { Text("다운로드 화질 선택 (${pendingDialogTargetEpisode?.number ?: ""}화)") },
                text = {
                    Column {
                        qualitiesForDialog.forEach { quality ->
                            Button(
                                onClick = {
                                    currentQualityDeferred?.complete(quality)
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Lilac)
                            ) {
                                Text("${quality.label} 선택", color = Color.White)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        currentQualityDeferred?.complete(qualitiesForDialog.first())
                    }) {
                        Text("기본 화질 선택")
                    }
                }
            )
        }
    }
}

// ============================================================
// PLAYER
// ============================================================


private const val USER_SUBTITLE_DIR = "user_subtitles"

private fun isLocalUserSubtitlePath(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    val file = File(path)
    if (!file.isFile) return false
    val normalized = file.absolutePath.replace('\\', '/')
    return normalized.contains("/${USER_SUBTITLE_DIR}/") &&
        (normalized.endsWith(".ass", true) || normalized.endsWith(".ssa", true) ||
         normalized.endsWith(".srt", true) || normalized.endsWith(".vtt", true))
}

private fun userSubtitleDirectory(context: Context): File =
    File(context.filesDir, USER_SUBTITLE_DIR).apply { mkdirs() }

private fun userSubtitleFile(context: Context, animeId: String, episodeNumber: Int, extension: String): File =
    File(userSubtitleDirectory(context), "${animeId}_${episodeNumber}.${extension.lowercase(Locale.ROOT)}")

private fun findLocalKairanAssSubtitle(
    context: Context,
    title: String,
    episodeNumber: Int,
    storedPath: String? = null
): String? {
    fun isAss(path: String): Boolean {
        val lower = path.lowercase(Locale.ROOT)
        return (lower.endsWith(".ass") || lower.endsWith(".ssa")) && File(path).isFile
    }

    if (!storedPath.isNullOrBlank() && isAss(storedPath)) {
        return storedPath
    }

    val dir = File(context.filesDir, "kairan_subtitles")
    if (!dir.isDirectory) return null

    val safe = KairanSubtitleService.normalizeTitleForFile(title)
        .replace(' ', '_')
        .ifBlank { "subtitle" }
        .take(60)

    val exactNames = listOf(
        "${safe}_${episodeNumber}.ass",
        "${safe}_${episodeNumber}.ssa"
    )
    for (name in exactNames) {
        val file = File(dir, name)
        if (file.isFile) return file.absolutePath
    }

    return dir.listFiles()?.firstOrNull { file ->
        if (!file.isFile) return@firstOrNull false
        val n = file.name.lowercase(Locale.ROOT)
        (n.endsWith(".ass") || n.endsWith(".ssa")) &&
            n.contains("_${episodeNumber}") &&
            n.startsWith(safe.lowercase(Locale.ROOT))
    }?.absolutePath
}

@SuppressLint("SourceLockedOrientationActivity")
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    anime: Anime,
    episode: Episode,
    vm: AnimeViewModel,
    back: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity
    val isOffline by vm.isOffline.collectAsState()
    
    var currentEpisode by remember(episode) { mutableStateOf(episode) }
    
    var isFullScreen by remember { mutableStateOf(false) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var subtitlesUrl by remember { mutableStateOf<String?>(null) }
    var subtitleSource by remember { mutableStateOf("none") }
    var kairanSubtitleResolved by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    
    var isAutoPlayEnabled by rememberSaveable { mutableStateOf(true) }
    var isAutoSkipEnabled by rememberSaveable { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var aniSkipSegments by remember { mutableStateOf<List<AniSkipSegment>>(emptyList()) }
    var activeAniSkipSegment by remember { mutableStateOf<AniSkipSegment?>(null) }
    var buttonAniSkipSegment by remember { mutableStateOf<AniSkipSegment?>(null) }
    var aniSkipEnteredAtMs by remember { mutableLongStateOf(-1L) }
    var skippedAniSkipKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var skipEpisodeKey by remember { mutableStateOf<String?>(null) }
    var suppressProgressSaveForEpisode by remember { mutableStateOf<Int?>(null) }

    var subtitleSizePercent by rememberSaveable { mutableFloatStateOf(vm.playerSettings.subtitleSize) }
    var subtitleSizeText by rememberSaveable { mutableStateOf(vm.playerSettings.subtitleSize.toInt().toString()) }
    var syncOffsetMs by rememberSaveable { mutableLongStateOf(vm.playerSettings.syncOffsetMs) }
    var isVttStyleEnabled by rememberSaveable { mutableStateOf(true) }
    var customTypeface by remember { mutableStateOf<Typeface?>(null) }
    var customFontName by remember { mutableStateOf<String?>(null) }

    var parsedStreamingQualities by remember { mutableStateOf<List<StreamQuality>>(emptyList()) }
    var selectedStreamingQuality by remember { mutableStateOf<StreamQuality?>(null) }
    var pendingSeekPositionMs by remember { mutableLongStateOf(-1L) }

    var exoQualities by remember { mutableStateOf<List<ExoVideoQualityOption>>(emptyList()) }
    var selectedQualityOption by remember { mutableStateOf<ExoVideoQualityOption?>(null) }
    var showPlayerSettingsDialog by remember { mutableStateOf(false) }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val tempFile = File.createTempFile("custom_font", ".ttf", context.cacheDir)
                FileOutputStream(tempFile).use { output -> inputStream?.copyTo(output) }
                customTypeface = Typeface.createFromFile(tempFile)
                customFontName = "커스텀 폰트 적용됨"
                Toast.makeText(context, "폰트가 적용되었습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "폰트를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val subtitleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val displayName = runCatching {
                    context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull() ?: "subtitle"

                val lowerName = displayName.lowercase(Locale.ROOT)
                val extension = when {
                    lowerName.endsWith(".ass") -> "ass"
                    lowerName.endsWith(".ssa") -> "ssa"
                    lowerName.endsWith(".srt") -> "srt"
                    lowerName.endsWith(".vtt") -> "vtt"
                    else -> null
                }

                if (extension == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "ASS, SSA, SRT, VTT 자막만 사용할 수 있습니다.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val target = userSubtitleFile(context, anime.id, currentEpisode.number, extension)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("자막 파일을 열 수 없습니다.")

                val updatedEpisode = currentEpisode.copy(vttUrl = target.absolutePath)
                OfflineStore.saveEpisode(
                    context = context,
                    animeId = anime.id,
                    episode = updatedEpisode
                )
                withContext(Dispatchers.Main) {
                    currentEpisode = updatedEpisode
                    subtitlesUrl = target.absolutePath
                    subtitleSource = "user"
                    kairanSubtitleResolved = true
                    Toast.makeText(
                        context,
                        "${currentEpisode.number}화 사용자 자막을 적용했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("Subtitle", "USER_SUBTITLE_IMPORT_FAILED", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "자막 파일을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val episodeList = remember(anime.id) { 
        vm.episodes(anime).sortedBy { it.number } 
    }
    val currentIndex = remember(episodeList, currentEpisode.number) {
        episodeList.indexOfFirst { it.number == currentEpisode.number }
    }
    val prevEpisode = remember(episodeList, currentIndex) {
        if (currentIndex > 0) episodeList.getOrNull(currentIndex - 1) else null
    }
    val nextEpisode = remember(episodeList, currentIndex) {
        if (currentIndex >= 0 && currentIndex < episodeList.size - 1) episodeList.getOrNull(currentIndex + 1) else null
    }

    val currentNextEpisode by rememberUpdatedState(nextEpisode)
    val currentAutoPlay by rememberUpdatedState(isAutoPlayEnabled)
    val currentEpisodeState by rememberUpdatedState(currentEpisode)

    val isDownloaded = remember(anime.id, currentEpisode.number) {
        vm.isEpisodeDownloaded(anime.id, currentEpisode.number)
    }

    var offlineEp by remember { mutableStateOf<Episode?>(null) }
    LaunchedEffect(anime.id, currentEpisode.number) {
        offlineEp = OfflineStore.getEpisode(context, anime.id, currentEpisode.number)
    }

    LaunchedEffect(isFullScreen) {
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullScreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        if (isFullScreen) {
            isFullScreen = false
        } else {
            back()
        }
    }

    // Restore a user-imported subtitle saved in OfflineStore even when the
    // current episode itself is not downloaded. This makes the custom subtitle
    // available again after leaving/reopening the player and also during offline playback.
    LaunchedEffect(anime.id, currentEpisode.number) {
        val stored = withContext(Dispatchers.IO) {
            OfflineStore.getEpisode(context, anime.id, currentEpisode.number)
        }
        val storedSubtitle = stored?.vttUrl
        if (isLocalUserSubtitlePath(storedSubtitle)) {
            currentEpisode = currentEpisode.copy(vttUrl = storedSubtitle)
            subtitlesUrl = storedSubtitle
            subtitleSource = "user"
            Log.d("Subtitle", "RESTORE_USER_SUBTITLE path=$storedSubtitle episode=${currentEpisode.number}")
        }
    }

    LaunchedEffect(currentEpisode, isDownloaded, offlineEp) {
        isLoading = true
        streamUrl = null
        subtitlesUrl = null
        subtitleSource = "none"
        kairanSubtitleResolved = false
        parsedStreamingQualities = emptyList()
        selectedStreamingQuality = null
        exoQualities = emptyList()
        selectedQualityOption = null
        pendingSeekPositionMs = -1L
        aniSkipSegments = emptyList()
        activeAniSkipSegment = null
        buttonAniSkipSegment = null
        aniSkipEnteredAtMs = -1L
        skippedAniSkipKeys = emptySet()
        skipEpisodeKey = null
        suppressProgressSaveForEpisode = null
        
        val targetUrl = if (isDownloaded) {
            offlineEp?.videoUrl ?: currentEpisode.videoUrl
        } else if (!isOffline) {
            currentEpisode.videoUrl
        } else {
            null
        }

        if (!targetUrl.isNullOrBlank()) {
            if (targetUrl.contains(".m3u8") || targetUrl.contains(".mp4") || isDownloaded) {
                streamUrl = targetUrl

                val storedSubtitle = offlineEp?.vttUrl ?: currentEpisode.vttUrl
                val localStoredSubtitle = storedSubtitle?.takeIf { path ->
                    path.startsWith("/") && File(path).isFile
                }
                val localUserSubtitle = localStoredSubtitle?.takeIf { isLocalUserSubtitlePath(it) }

                // User-imported subtitles always have the highest priority.
                // This prevents Kairan from replacing a subtitle the user explicitly selected.
                val localAssSubtitle = if (localUserSubtitle == null) {
                    findLocalKairanAssSubtitle(
                        context = context,
                        title = anime.title,
                        episodeNumber = currentEpisode.number,
                        storedPath = localStoredSubtitle
                    )
                } else null

                subtitlesUrl = when {
                    localUserSubtitle != null -> localUserSubtitle
                    localAssSubtitle != null -> localAssSubtitle
                    isDownloaded || isOffline -> localStoredSubtitle
                    else -> storedSubtitle
                }
                subtitleSource = when {
                    localUserSubtitle != null -> "user"
                    subtitlesUrl != null -> "offline"
                    else -> "none"
                }
                kairanSubtitleResolved = true
                isLoading = false
            }
        } else if (isOffline) {
            isLoading = false
        }
    }

    LaunchedEffect(anime.id, anime.title, currentEpisode.number, isOffline, isDownloaded, currentEpisode.vttUrl) {
        kairanSubtitleResolved = false

        // A user-imported subtitle always wins over Kairan and streaming fallback.
        val userSubtitle = currentEpisode.vttUrl?.takeIf { isLocalUserSubtitlePath(it) }
        if (userSubtitle != null) {
            subtitlesUrl = userSubtitle
            subtitleSource = "user"
            kairanSubtitleResolved = true
            Log.d("Subtitle", "USE_USER_SUBTITLE path=$userSubtitle episode=${currentEpisode.number}")
            return@LaunchedEffect
        }

        // First, prefer any ASS that is already stored locally. This also fixes
        // older downloaded episodes whose OfflineStore entry still points at VTT.
        val existingAss = withContext(Dispatchers.IO) {
            findLocalKairanAssSubtitle(
                context = context,
                title = anime.title,
                episodeNumber = currentEpisode.number,
                storedPath = subtitlesUrl
            )
        }

        if (existingAss != null) {
            subtitlesUrl = existingAss
            subtitleSource = "kairan"
            Log.d("Kairan", "PREFER_LOCAL_ASS path=$existingAss episode=${currentEpisode.number}")

            if (isDownloaded) {
                OfflineStore.saveEpisode(
                    context = context,
                    animeId = anime.id,
                    episode = (offlineEp ?: currentEpisode).copy(vttUrl = existingAss)
                )
            }
            kairanSubtitleResolved = true
            return@LaunchedEffect
        }

        // A downloaded episode can be upgraded to ASS automatically when the
        // device is online. The video itself is never downloaded again.
        if (!isOffline) {
            val result: KairanSubtitleResult? = try {
                withContext(Dispatchers.IO) {
                    KairanSubtitleService.findSubtitle(context, anime.title, currentEpisode.number)
                }
            } catch (e: Exception) {
                Log.w("Kairan", "AUTO_ASS_SEARCH_FAILED episode=${currentEpisode.number}", e)
                null
            }

            when (result) {
                is KairanSubtitleResult.DirectFile -> {
                    subtitlesUrl = result.path
                    subtitleSource = "kairan"
                    Log.d("Kairan", "PREFER_KAIRAN_ASS path=${result.path} episode=${currentEpisode.number}")

                    if (isDownloaded) {
                        OfflineStore.saveEpisode(
                            context = context,
                            animeId = anime.id,
                            episode = (offlineEp ?: currentEpisode).copy(vttUrl = result.path)
                        )
                    }
                }
                null -> {
                    // Kairan ASS가 없거나 검색에 실패하면 기존 VTT/SRT 자막으로
                    // 반드시 되돌아간다. Kairan 검색 때문에 원래 있던 자막이
                    // 사라지지 않도록 현재/오프라인 저장 자막을 그대로 유지한다.
                    val fallbackSubtitle = currentEpisode.vttUrl
                        ?: offlineEp?.vttUrl
                        ?: subtitlesUrl

                    subtitlesUrl = fallbackSubtitle
                    subtitleSource = when {
                        fallbackSubtitle.isNullOrBlank() -> "none"
                        isLocalUserSubtitlePath(fallbackSubtitle) -> "user"
                        else -> "vtt"
                    }
                    Log.w(
                        "Kairan",
                        "ASS_NOT_FOUND episode=${currentEpisode.number}; FALLBACK_VTT path=$fallbackSubtitle"
                    )
                }
            }
        } else {
            Log.d("Kairan", "OFFLINE_ASS_NOT_FOUND episode=${currentEpisode.number}; keeping existing local subtitle")
        }

        kairanSubtitleResolved = true
    }

    // ASS/SSA is rendered by libass instead of Media3's normal SubtitleView.
    // OVERLAY_OPEN_GL keeps the libass bitmap on a dedicated overlay path so
    // positioning, styles, animations, karaoke, borders, shadows, etc. stay
    // faithful to the original ASS script.
    val assHandler = remember(context) {
        AssHandler(
            renderType = AssRenderType.OVERLAY_OPEN_GL,
            config = AssHandlerConfig(
                maxRenderPixels = 0
            )
        )
    }

    val assSubtitleParserFactory = remember(assHandler) {
        AssSubtitleParserFactory(assHandler)
    }

    val trackSelector = remember(context) {
        DefaultTrackSelector(context)
    }

    val exoPlayer = remember(context, trackSelector, assHandler) {
        val defaultRenderersFactory = DefaultRenderersFactory(context)
        val assRenderersFactory = AssRenderersFactory(
            assHandler = assHandler,
            renderersFactory = defaultRenderersFactory
        )

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setRenderersFactory(assRenderersFactory)
            .build()
    }

    DisposableEffect(exoPlayer, assHandler) {
        Log.d("Subtitle", "LIBASS_INIT renderType=OVERLAY_OPEN_GL maxRenderPixels=0")
        assHandler.init(exoPlayer)
        onDispose {
            Log.d("Subtitle", "LIBASS_RELEASE")
            assHandler.release()
        }
    }

    val forwardingPlayer = remember(exoPlayer, prevEpisode, nextEpisode) {
        object : ForwardingPlayer(exoPlayer) {
            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> nextEpisode != null
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> prevEpisode != null
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun hasNextMediaItem(): Boolean = nextEpisode != null
            override fun hasPreviousMediaItem(): Boolean = prevEpisode != null

            override fun seekToNext() { nextEpisode?.let { currentEpisode = it } }
            override fun seekToPrevious() { prevEpisode?.let { currentEpisode = it } }
            override fun seekToNextMediaItem() { nextEpisode?.let { currentEpisode = it } }
            override fun seekToPreviousMediaItem() { prevEpisode?.let { currentEpisode = it } }
        }
    }

    DisposableEffect(currentEpisode) {
        val episodeNumberForSave = currentEpisode.number
        onDispose {
            if (suppressProgressSaveForEpisode == episodeNumberForSave) return@onDispose
            val duration = exoPlayer.duration
            if (duration > 0 && duration != C.TIME_UNSET) {
                val progress = (exoPlayer.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                vm.updateProgress(
                    context = context,
                    animeId = anime.id,
                    episodeNumber = episodeNumberForSave,
                    progress = progress
                )
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val completedEpisode = currentEpisodeState.number
                    suppressProgressSaveForEpisode = completedEpisode
                    vm.updateProgress(
                        context = context,
                        animeId = anime.id,
                        episodeNumber = completedEpisode,
                        progress = 0f
                    )
                    if (currentAutoPlay && currentNextEpisode != null) {
                        currentEpisode = currentNextEpisode!!
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val qualityList = mutableListOf<ExoVideoQualityOption>()
                qualityList.add(ExoVideoQualityOption("자동 (Auto)", 0, 0, isAuto = true))

                for (groupIndex in 0 until tracks.groups.size) {
                    val trackGroup = tracks.groups[groupIndex]
                    if (trackGroup.type == C.TRACK_TYPE_VIDEO) {
                        for (trackIndex in 0 until trackGroup.length) {
                            val format = trackGroup.getTrackFormat(trackIndex)
                            val height = format.height
                            val width = format.width

                            if (height > 0) {
                                val label = when {
                                    height >= 2160 -> "4K (2160p)"
                                    height >= 1440 -> "QHD (1440p)"
                                    height >= 1080 -> "1080p"
                                    height >= 720 -> "720p"
                                    else -> "${height}p"
                                }
                                qualityList.add(ExoVideoQualityOption(label, width, height))
                            }
                        }
                    }
                }
                exoQualities = qualityList.distinctBy { it.label }

                val prefQuality = vm.playerSettings.defaultQuality
                if (prefQuality != "Auto") {
                    val targetOpt = exoQualities.firstOrNull { it.label.contains(prefQuality) }
                    if (targetOpt != null && selectedQualityOption == null) {
                        selectedQualityOption = targetOpt
                        val builder = trackSelector.buildUponParameters()
                        builder.setMaxVideoSize(targetOpt.width, targetOpt.height)
                            .setMinVideoSize(targetOpt.width, targetOpt.height)
                        trackSelector.setParameters(builder)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(context, "재생 오류: ${error.errorCodeName}", Toast.LENGTH_SHORT).show()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(streamUrl, subtitlesUrl, syncOffsetMs, isOffline) {
        val url = streamUrl ?: return@LaunchedEffect
        val isLocalFile = url.startsWith("file://") || url.startsWith("/")

        val mediaSourceFactory = if (isLocalFile) {
            val localDataSourceFactory = DefaultDataSource.Factory(context)
            DefaultMediaSourceFactory(context).setDataSourceFactory(localDataSourceFactory)
        } else {
            val parsedUri = Uri.parse(url)
            val refererHost = if (!parsedUri.host.isNullOrEmpty()) {
                "${parsedUri.scheme ?: "https"}://${parsedUri.host}/"
            } else {
                "https://linkkf.tv/"
            }
            val upstreamFactory = if (isOffline) {
                null
            } else {
                DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(
                        mapOf(
                            "Referer" to "https://play.sub3.top/",
                            "Origin" to "https://play.sub3.top"
                        )
                    )
            }
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(LilacApplication.downloadCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheReadDataSourceFactory(FileDataSource.Factory())
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            val dataSourceFactory = DefaultDataSource.Factory(context, cacheDataSourceFactory)
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)
                .setSubtitleParserFactory(assSubtitleParserFactory)
        }

        val mediaItemUri = if (isLocalFile && !url.startsWith("file://")) {
            Uri.fromFile(File(url))
        } else {
            Uri.parse(url)
        }

        val mimeType = if (url.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else null
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(mediaItemUri)
            .apply {
                if (mimeType != null) {
                    setMimeType(mimeType)
                }
            }

        if (!subtitlesUrl.isNullOrEmpty()) {
            val subPath = subtitlesUrl!!
            val subUri = when {
                subPath.startsWith("http://") || subPath.startsWith("https://") -> Uri.parse(subPath)
                subPath.startsWith("file://") -> Uri.parse(subPath)
                else -> Uri.fromFile(File(subPath))
            }
            val lowerSubPath = subPath.lowercase(Locale.ROOT)
            val subtitleMimeType = when {
                lowerSubPath.contains(".ass") || lowerSubPath.contains(".ssa") -> MimeTypes.TEXT_SSA
                lowerSubPath.contains(".srt") -> MimeTypes.APPLICATION_SUBRIP
                else -> MimeTypes.TEXT_VTT
            }
            Log.d("Subtitle", "LOAD source=$subtitleSource path=$subPath mime=$subtitleMimeType")
            val subtitleId = if (subtitleMimeType == MimeTypes.TEXT_SSA) {
                // libass-android uses the external track id to bind the ASS
                // stream to AssHandler. Keep it stable and well above the
                // media track ids used by ExoPlayer.
                "kairan-ass-${anime.id}-${currentEpisode.number}"
            } else {
                "kairan-subtitle-${anime.id}-${currentEpisode.number}"
            }

            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
                .setId(subtitleId)
                .setMimeType(subtitleMimeType)
                .setLanguage("ko")
                .setLabel(if (subtitleMimeType == MimeTypes.TEXT_SSA) "Kairan ASS" else "Kairan Subtitle")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        var initialSeekDone = false
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !initialSeekDone) {
                    initialSeekDone = true
                    if (pendingSeekPositionMs >= 0L) {
                        exoPlayer.seekTo(pendingSeekPositionMs)
                        pendingSeekPositionMs = -1L
                    } else {
                        val savedProgress = vm.getProgress(anime.id, currentEpisode.number)
                        if (savedProgress != null) {
                            val duration = exoPlayer.duration
                            if (duration > 0 && duration != C.TIME_UNSET) {
                                val seekPos = (savedProgress.progress * duration).toLong().coerceAtLeast(0)
                                exoPlayer.seekTo(seekPos)
                            }
                        }
                    }

                }
            }
        }
        exoPlayer.addListener(listener)

        exoPlayer.setMediaSource(mediaSourceFactory.createMediaSource(mediaItemBuilder.build()))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(streamUrl, currentEpisode.number) {
        val currentStreamUrl = streamUrl ?: return@LaunchedEffect

        aniSkipSegments = emptyList()
        activeAniSkipSegment = null
        buttonAniSkipSegment = null
        aniSkipEnteredAtMs = -1L
        skippedAniSkipKeys = emptySet()

        while (isActive) {
            val duration = exoPlayer.duration
            if (
                exoPlayer.playbackState == Player.STATE_READY &&
                duration > 0L &&
                duration != C.TIME_UNSET
            ) {
                val durationSeconds = kotlin.math.round(duration / 1000.0).toInt().coerceAtLeast(1)
                Log.d(
                    "AniSkip",
                    "PLAYER_READY_FOR_ANISKIP episode=${currentEpisode.number} duration=$durationSeconds"
                )

                val segments = AniSkipService.getSkipTimes(
                    anime.title,
                    currentEpisode.number,
                    durationSeconds
                )

                aniSkipSegments = segments.mapNotNull { segment ->
                    val source = segment.episodeLength
                    val local = durationSeconds.toDouble()

                    if (source <= 0.0 || local <= 0.0) {
                        Log.w(
                            "AniSkip",
                            "MAPPED_REJECT type=${segment.type} invalidLengths " +
                                "source=$source local=$local"
                        )
                        return@mapNotNull null
                    }

                    val diff = local - source

                    // AniSkip의 타임스탬프는 원본 영상의 실제 타임라인이다.
                    // 길이가 크게 다른 영상에 비율을 곱하면 OP/ED가 전혀 다른 위치로 이동한다.
                    // 따라서 가까운 길이만 offset 보정하고, 크게 다르면 원본 시간을 그대로 사용한다.
                    val (start, end, mode) = if (kotlin.math.abs(diff) <= 30.0) {
                        Triple(
                            (segment.startTime + diff).coerceIn(0.0, local),
                            (segment.endTime + diff).coerceIn(0.0, local),
                            "offset"
                        )
                    } else {
                        Triple(
                            segment.startTime.coerceIn(0.0, local),
                            segment.endTime.coerceIn(0.0, local),
                            "raw_mismatch"
                        )
                    }

                    val safeStart = minOf(start, end)
                    val safeEnd = maxOf(start, end)

                    if (safeEnd <= safeStart) {
                        Log.w(
                            "AniSkip",
                            "MAPPED_REJECT type=${segment.type} invalidMappedRange"
                        )
                        return@mapNotNull null
                    }

                    Log.d(
                        "AniSkip",
                        "MAPPED type=${segment.type} " +
                            "raw=${segment.startTime}-${segment.endTime} " +
                            "source=$source local=$local diff=$diff " +
                            "mode=$mode mapped=$safeStart-$safeEnd"
                    )

                    segment.copy(
                        startTime = safeStart,
                        endTime = safeEnd
                    )
                }

                skipEpisodeKey = "${anime.id}_${currentEpisode.number}"

                Log.d(
                    "AniSkip",
                    "LOADED episode=${currentEpisode.number} count=${aniSkipSegments.size}"
                )
                break
            }
            delay(250L)
        }
    }

    LaunchedEffect(exoPlayer, currentEpisode.number, aniSkipSegments, isAutoSkipEnabled) {
        val segments = aniSkipSegments
        if (segments.isEmpty()) {
            activeAniSkipSegment = null
            return@LaunchedEffect
        }

        while (isActive) {
            val positionSeconds = exoPlayer.currentPosition / 1000.0
            val active = segments.firstOrNull {
                positionSeconds >= it.startTime && positionSeconds < it.endTime
            }

            if (active != activeAniSkipSegment) {
                activeAniSkipSegment = active
                buttonAniSkipSegment = active
                aniSkipEnteredAtMs = if (active != null) System.currentTimeMillis() else -1L

                if (active != null) {
                    Log.d(
                        "AniSkip",
                        "ENTER type=${active.type} position=$positionSeconds range=${active.startTime}-${active.endTime}"
                    )
                }
            }

            if (active == null) {
                buttonAniSkipSegment = null
                aniSkipEnteredAtMs = -1L
            } else if (isAutoSkipEnabled) {
                val key = "${active.type}:${active.startTime}:${active.endTime}"
                val elapsedMs = if (aniSkipEnteredAtMs >= 0L) {
                    System.currentTimeMillis() - aniSkipEnteredAtMs
                } else {
                    0L
                }

                // 버튼이 잠깐 보인 뒤 자동 스킵되도록 한다. 자동 스킵을 끄면
                // 구간 전체에서 버튼으로 직접 넘길 수 있다.
                if (key !in skippedAniSkipKeys && elapsedMs >= 1200L) {
                    val duration = exoPlayer.duration
                    val targetSeconds = if (duration > 0L && duration != C.TIME_UNSET) {
                        minOf(active.endTime, duration / 1000.0 - 0.5)
                    } else {
                        active.endTime
                    }

                    if (targetSeconds > positionSeconds + 0.25) {
                        Log.d(
                            "AniSkip",
                            "AUTO_SKIP type=${active.type} position=$positionSeconds target=$targetSeconds elapsedMs=$elapsedMs"
                        )
                        skippedAniSkipKeys = skippedAniSkipKeys + key
                        exoPlayer.seekTo((targetSeconds * 1000.0).toLong().coerceAtLeast(0L))
                        activeAniSkipSegment = null
                        buttonAniSkipSegment = null
                        aniSkipEnteredAtMs = -1L
                    }
                }
            }

            delay(200L)
        }
    }

    fun applySubtitleSettingsToView(playerView: PlayerView) {
        val subView = playerView.subtitleView ?: return

        val isAss = subtitlesUrl?.lowercase(Locale.ROOT)?.let {
            it.endsWith(".ass") || it.endsWith(".ssa") ||
                it.contains(".ass?") || it.contains(".ssa?")
        } == true

        if (isAss) {
            // ASS is rendered by libass through AssSubtitleView. Hide Media3's
            // normal SubtitleView so the same ASS track is not drawn twice.
            subView.visibility = View.INVISIBLE
            Log.d(
                "Subtitle",
                "ASS_VIEW libass=true renderType=OVERLAY_OPEN_GL media3SubtitleView=hidden"
            )
            return
        }

        // VTT/SRT: use the app's normal subtitle appearance.
        subView.visibility = View.VISIBLE
        subView.setApplyEmbeddedStyles(isVttStyleEnabled)
        subView.setApplyEmbeddedFontSizes(isVttStyleEnabled)

        val calculatedSp = 18f * (subtitleSizePercent / 100f)
        subView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, calculatedSp)
        subView.setBottomPaddingFraction(0.09f)

        val transparentStyle = CaptionStyleCompat(
            vm.playerSettings.textColor,
            vm.playerSettings.backgroundColor,
            android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            vm.playerSettings.strokeColor,
            if (isVttStyleEnabled) Typeface.DEFAULT_BOLD else (customTypeface ?: Typeface.DEFAULT_BOLD)
        )
        subView.setStyle(transparentStyle)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val videoUrl = currentEpisode.videoUrl

        when {
            streamUrl != null -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = forwardingPlayer
                            useController = true
                            controllerShowTimeoutMs = 2000
                            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                                isControlsVisible = (visibility == View.VISIBLE)
                            })
                            applySubtitleSettingsToView(this)

                            // libass renderer overlay. It is transparent unless an
                            // ASS track is active, and it follows the PlayerView
                            // surface size automatically.
                            val libassOverlay = AssSubtitleView(ctx, assHandler).apply {
                                tag = "kairan_libass_overlay"
                                isClickable = false
                                isFocusable = false
                            }
                            addView(
                                libassOverlay,
                                android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                )
                            )
                            Log.d("Subtitle", "LIBASS_OVERLAY_ATTACHED")
                        }
                    },
                    update = { playerView ->
                        playerView.player = forwardingPlayer
                        applySubtitleSettingsToView(playerView)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            !isOffline && !videoUrl.isNullOrBlank() -> {
                StreamUrlExtractor(
                    targetUrl = videoUrl,
                    onSubtitleFound = {
                        if ((subtitleSource == "none" || subtitleSource == "vtt") && kairanSubtitleResolved) {
                            // Kairan ASS가 없을 때는 스트림에서 제공하는 원래 VTT를 그대로 사용한다.
                            if (subtitleSource == "none") {
                                subtitlesUrl = it
                                subtitleSource = "linkkf-vtt"
                                Log.d("Subtitle", "USE_LINKKF_VTT url=$it")
                            }
                        }
                    },
                    onQualitiesFound = { qualities ->
                        parsedStreamingQualities = qualities
                        if (streamUrl == null && qualities.isNotEmpty()) {
                            val selected = qualities.first()
                            selectedStreamingQuality = selected
                            streamUrl = selected.url
                            isLoading = false
                        }
                    }
                )
                CircularProgressIndicator(color = Lilac)
            }
            else -> {
                Text(
                    text = if (isOffline) "오프라인 상태이며 다운로드된 영상이 없습니다." else "영상을 불러올 수 없습니다.",
                    color = Color.White
                )
            }
        }

        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isFullScreen) isFullScreen = false else back()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로가기",
                        tint = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showPlayerSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "설정",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { isAutoPlayEnabled = !isAutoPlayEnabled }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "자동재생",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isAutoPlayEnabled,
                            onCheckedChange = { isAutoPlayEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Lilac,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { isAutoSkipEnabled = !isAutoSkipEnabled }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "OP/ED",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isAutoSkipEnabled,
                            onCheckedChange = { isAutoSkipEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Lilac,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                }
            }
        }

        buttonAniSkipSegment?.let { segment ->
            val label = if (segment.type == "op" || segment.type == "mixed-op") {
                "OP 스킵"
            } else {
                "ED 스킵"
            }

            Button(
                onClick = {
                    val positionSeconds = exoPlayer.currentPosition / 1000.0
                    val duration = exoPlayer.duration
                    val targetSeconds = if (duration > 0L && duration != C.TIME_UNSET) {
                        minOf(segment.endTime, duration / 1000.0 - 0.5)
                    } else {
                        segment.endTime
                    }

                    Log.d(
                        "AniSkip",
                        "BUTTON_SKIP type=${segment.type} position=$positionSeconds target=$targetSeconds"
                    )

                    skippedAniSkipKeys = skippedAniSkipKeys + "${segment.type}:${segment.startTime}:${segment.endTime}"
                    activeAniSkipSegment = null
                    buttonAniSkipSegment = null
                    aniSkipEnteredAtMs = -1L

                    if (targetSeconds > positionSeconds) {
                        exoPlayer.seekTo((targetSeconds * 1000.0).toLong().coerceAtLeast(0L))
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 72.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(label)
            }
        }

        if (showPlayerSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showPlayerSettingsDialog = false },
                title = { Text("플레이어 설정", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("M3U8 화질 직접 선택", fontWeight = FontWeight.Bold, color = LilacDark)
                        Spacer(Modifier.height(6.dp))

                        if (parsedStreamingQualities.isNotEmpty()) {
                            parsedStreamingQualities.forEach { quality ->
                                val isSelected = (selectedStreamingQuality?.url == quality.url) || 
                                                 (selectedStreamingQuality == null && streamUrl == quality.url)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!isSelected) {
                                                pendingSeekPositionMs = exoPlayer.currentPosition
                                                selectedStreamingQuality = quality
                                                streamUrl = quality.url
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(quality.label, fontSize = 14.sp)
                                }
                            }
                        } else if (exoQualities.isNotEmpty()) {
                            Text("ExoPlayer 내장 트랙 목록", fontSize = 12.sp, color = Color.Gray)
                            exoQualities.forEach { option ->
                                val isSelected = (selectedQualityOption == option) || (selectedQualityOption == null && option.isAuto)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedQualityOption = option
                                            val builder = trackSelector.buildUponParameters()
                                            if (option.isAuto) {
                                                builder.clearVideoSizeConstraints()
                                            } else {
                                                builder
                                                    .setMaxVideoSize(option.width, option.height)
                                                    .setMinVideoSize(option.width, option.height)
                                            }
                                            trackSelector.setParameters(builder)
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(option.label, fontSize = 14.sp)
                                }
                            }
                        } else {
                            Text("m3u8 화질 정보를 불러오는 중...", fontSize = 12.sp, color = Color.Gray)
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        Text("자막 설정", fontWeight = FontWeight.Bold, color = LilacDark)
                        Spacer(Modifier.height(10.dp))

                        val currentUserSubtitle = currentEpisode.vttUrl?.takeIf { isLocalUserSubtitlePath(it) }
                        OutlinedButton(
                            onClick = {
                                subtitleFilePickerLauncher.launch(
                                    arrayOf("text/*", "application/x-subrip", "application/x-ass", "application/octet-stream")
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Subtitles, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (currentUserSubtitle != null) "사용자 자막 변경" else "자막 파일 불러오기")
                        }
                        if (currentUserSubtitle != null) {
                            Text(
                                "사용자 자막이 우선 적용됩니다. 오프라인 재생에도 유지됩니다.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("VTT 원본 색상/스타일 유지", fontSize = 13.sp)
                            Switch(
                                checked = isVttStyleEnabled,
                                onCheckedChange = { isVttStyleEnabled = it }
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Text("자막 싱크 미세 조정 (${syncOffsetMs}ms)", fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { syncOffsetMs -= 250L }, modifier = Modifier.weight(1f)) {
                                Text("-250ms", fontSize = 11.sp)
                            }
                            OutlinedButton(onClick = { syncOffsetMs = 0L }, modifier = Modifier.weight(1f)) {
                                Text("초기화", fontSize = 11.sp)
                            }
                            OutlinedButton(onClick = { syncOffsetMs += 250L }, modifier = Modifier.weight(1f)) {
                                Text("+250ms", fontSize = 11.sp)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Text("자막 크기 조절", fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Slider(
                                value = subtitleSizePercent,
                                onValueChange = { valValue ->
                                    subtitleSizePercent = valValue
                                    subtitleSizeText = valValue.toInt().toString()
                                },
                                valueRange = 50f..300f,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = subtitleSizeText,
                                onValueChange = { text ->
                                    subtitleSizeText = text
                                    text.toFloatOrNull()?.let { parsed ->
                                        subtitleSizePercent = parsed.coerceIn(50f, 300f)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(65.dp),
                                singleLine = true
                            )
                            Text("%", modifier = Modifier.padding(start = 4.dp), fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("커스텀 폰트", fontSize = 13.sp)
                                Text(
                                    customFontName ?: "기본 폰트 사용 중", 
                                    fontSize = 11.sp, 
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { fontPickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Lilac)
                            ) {
                                Text("폰트 불러오기", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { 
                        vm.updatePlayerSettings(context, vm.playerSettings.copy(syncOffsetMs = syncOffsetMs, subtitleSize = subtitleSizePercent))
                        showPlayerSettingsDialog = false 
                    }) {
                        Text("확인")
                    }
                }
            )
        }
    }
}

// ============================================================
// KAIRAN03 BLOGGER + GOOGLE DRIVE SUBTITLE
// ============================================================

sealed class KairanSubtitleResult {
    data class DirectFile(val path: String) : KairanSubtitleResult()
}

object KairanSubtitleService {
    private const val BLOG_URL = "https://kairan03.blogspot.com"
    private const val CACHE_DIR = "kairan_subtitles"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    suspend fun findSubtitle(context: Context, title: String, episodeNumber: Int): KairanSubtitleResult? =
        withContext(Dispatchers.IO) {
            try {
                Log.d("Kairan", "START_SEARCH title=[$title] episode=$episodeNumber")

                val postUrl = findBlogPost(title, episodeNumber)
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

    private suspend fun findBlogPost(title: String, episode: Int): String? {
        // Search every meaningful title word, but run the independent Blogger
        // requests concurrently. This keeps the word-by-word search behavior
        // while removing the serial network wait between words.
        val normalizedTitle = kairanSearchTitle(title)
        val words = normalizedTitle
            .split(' ')
            .filter { it.length >= 2 }
            .distinct()
            .sortedByDescending { it.length }

        if (words.isEmpty()) return null

        val searchQueries = words.toMutableList()
        // Also keep the compact normalized title as one fallback query.
        if (normalizedTitle.isNotBlank() && normalizedTitle !in searchQueries) {
            searchQueries += normalizedTitle
        }

        val resultsByQuery = coroutineScope {
            searchQueries.map { query ->
                async(Dispatchers.IO) {
                    query to runCatching { searchKairanBlog(query) }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }

        var bestUrl: String? = null
        var bestScore = Int.MIN_VALUE
        val seenUrls = HashSet<String>()

        for ((query, results) in resultsByQuery) {
            Log.d(
                "Kairan",
                "BLOG_SEARCH_RESULTS query=[$query] start=0 count=${results.size}"
            )

            for (result in results) {
                if (!seenUrls.add(result.url)) continue

                val titleScore = scoreKairanSearchTitle(title, result.title)
                val episodeMatch = hasKairanEpisode(result.title, result.url, episode)

                if (titleScore <= 0 || !episodeMatch) {
                    continue
                }

                if (titleScore > bestScore) {
                    bestScore = titleScore
                    bestUrl = result.url
                    Log.d(
                        "Kairan",
                        "BLOG_SEARCH_BEST_UPDATE score=$bestScore query=[$query] " +
                            "title=[${result.title}] url=${result.url}"
                    )
                }
            }
        }

        Log.d("Kairan", "BLOG_SEARCH_BEST score=$bestScore url=$bestUrl")
        return bestUrl
    }


    private fun searchKairanBlog(query: String): List<KairanSearchResult> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$BLOG_URL/search?q=$encoded"
            Log.d("Kairan", "BLOG_SEARCH_REQUEST url=$url")

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 20000
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

// ============================================================
// EXTRACTOR, OFFLINE STORE & HELPER
// ============================================================

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StreamUrlExtractor(
    targetUrl: String,
    onQualitiesFound: (List<StreamQuality>) -> Unit,
    onSubtitleFound: (String) -> Unit
) {
    val detectedUrls = remember { mutableListOf<String>() }
    var isSubtitleFound by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?, 
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

                        if (!isSubtitleFound && url.contains(".vtt")) {
                            isSubtitleFound = true
                            Handler(Looper.getMainLooper()).post {
                                onSubtitleFound(url)
                            }
                        }

                        if (url.contains(".m3u8") && !url.contains("ad")) {
                            if (!detectedUrls.contains(url)) {
                                detectedUrls.add(url)
                                
                                Handler(Looper.getMainLooper()).post {
                                    // 경로 문자열로 화질 1차 유추
                                    val qualityList = detectedUrls.map { u ->
                                        val label = if (u.contains("/sd/")) "720p" else "1080p"
                                        StreamQuality(label, u)
                                    }
                                    
                                    // 1080p나 720p 라벨이 여러 개일 경우 겹치지 않게 #1, #2 넘버링 처리
                                    val finalQualityList = qualityList.groupBy { it.label }.flatMap { (lbl, streams) ->
                                        if (streams.size > 1) {
                                            streams.mapIndexed { idx, sq -> StreamQuality("$lbl #${idx + 1}", sq.url) }
                                        } else {
                                            streams
                                        }
                                    }
                                    
                                    onQualitiesFound(finalQualityList)
                                }
                            }
                        }
                        
                        return super.shouldInterceptRequest(view, request)
                    }
                }
                loadUrl(targetUrl)
            }
        },
        modifier = Modifier.size(0.dp)
    )
}

suspend fun downloadSubtitleFile(
    context: Context, 
    animeId: String, 
    episodeNumber: Int, 
    vttUrl: String?
): String? {
    if (vttUrl.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(vttUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode == 200) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val file = File(context.filesDir, "sub_${animeId}_${episodeNumber}.vtt")
                file.writeText(text)
                file.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

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
            putString("pref_custom_font_path", settings.customFontPath)
            apply()
        }
    }

    suspend fun getPlayerSettings(context: Context): PlayerSettings = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        PlayerSettings(
            defaultQuality = prefs.getString("pref_default_quality", "1080p") ?: "1080p",
            subtitleFont = prefs.getString("pref_subtitle_font", "기본체") ?: "기본체",
            subtitleSize = prefs.getFloat("pref_subtitle_size", 100f),
            textColor = prefs.getInt("pref_text_color", android.graphics.Color.WHITE),
            backgroundColor = prefs.getInt("pref_background_color", android.graphics.Color.TRANSPARENT),
            strokeColor = prefs.getInt("pref_stroke_color", android.graphics.Color.BLACK),
            syncOffsetMs = prefs.getLong("pref_sync_offset_ms", 0L),
            customFontPath = prefs.getString("pref_custom_font_path", null)
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

@OptIn(UnstableApi::class)
fun startEpisodeDownload(
    context: Context, 
    animeId: String, 
    animeTitle: String, 
    episode: Episode, 
    streamUrl: String
) {
    val mimeType = if (streamUrl.contains(".m3u8")) {
        MimeTypes.APPLICATION_M3U8
    } else {
        MimeTypes.VIDEO_MP4
    }

    val displayTitle = "$animeTitle - ${episode.number}화"
    val customData = displayTitle.toByteArray(Charsets.UTF_8)
    val downloadId = "${animeId}_${episode.number}"

    val downloadRequest = DownloadRequest.Builder(
        downloadId,
        Uri.parse(streamUrl)
    )
    .setMimeType(mimeType)
    .setData(customData)
    .build()

    DownloadService.sendAddDownload(
        context,
        LilacDownloadService::class.java,
        downloadRequest,
        false
    )
}