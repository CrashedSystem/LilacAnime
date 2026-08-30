package com.lilac.anime

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.factory.AssRenderersFactory
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import io.github.peerless2012.ass.media.widget.AssSubtitleView
import com.lilac.anime.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
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

fun findLocalKairanAssSubtitle(
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
    
    var isFullScreen by rememberSaveable { mutableStateOf(true) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var subtitlesUrl by remember { mutableStateOf<String?>(null) }
    // Linkkf VTT 주소는 한 번 발견되면 자막 소스를 Kairan으로 바꿔도 유지한다.
    // 그래야 다시 Linkkf VTT를 선택했을 때 재탐색 없이 즉시 전환할 수 있다.
    var linkkfSubtitleUrl by remember(anime.id, currentEpisode.number) { mutableStateOf<String?>(currentEpisode.vttUrl) }
    var subtitleSource by remember { mutableStateOf("none") }
    var kairanSubtitleResolved by remember { mutableStateOf(false) }
    // 재생 중 백그라운드에서 Kairan ASS를 찾았을 때만 조용히 표시하는 안내창 상태
    var discoveredKairanAssPath by remember(anime.id, currentEpisode.number) { mutableStateOf<String?>(null) }
    var showKairanAssPrompt by remember(anime.id, currentEpisode.number) { mutableStateOf(false) }
    var kairanAssPromptHandled by remember(anime.id, currentEpisode.number) { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    
    var isAutoPlayEnabled by rememberSaveable { mutableStateOf(true) }
    var isAutoSkipEnabled by rememberSaveable { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isPlayerLocked by rememberSaveable { mutableStateOf(false) }
    var showLockedButton by remember { mutableStateOf(false) }
    var lockedButtonRequest by remember { mutableIntStateOf(0) }
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
    var subtitleBottomPaddingFraction by rememberSaveable {
        mutableFloatStateOf(vm.playerSettings.subtitleBottomPaddingFraction)
    }
    var subtitleSourcePreference by rememberSaveable {
        mutableStateOf(vm.playerSettings.subtitleSourcePreference)
    }
    var syncOffsetText by rememberSaveable {
        mutableStateOf(vm.playerSettings.syncOffsetMs.toString())
    }
    var isVttStyleEnabled by rememberSaveable { mutableStateOf(true) }
    var customTypeface by remember { mutableStateOf<Typeface?>(null) }
    var customFontName by remember { mutableStateOf<String?>(null) }

    var parsedStreamingQualities by remember { mutableStateOf<List<StreamQuality>>(emptyList()) }
    var selectedStreamingQuality by remember { mutableStateOf<StreamQuality?>(null) }
    var pendingSeekPositionMs by remember { mutableLongStateOf(-1L) }

    var exoQualities by remember { mutableStateOf<List<ExoVideoQualityOption>>(emptyList()) }
    var selectedQualityOption by remember { mutableStateOf<ExoVideoQualityOption?>(null) }
    var showPlayerSettingsDialog by remember { mutableStateOf(false) }

    // 재생 속도는 플레이어 화면 안에서 유지하고, 설정 메뉴에서 변경한다.
    var playbackSpeed by rememberSaveable { mutableFloatStateOf(1.0f) }
    val playbackSpeedOptions = remember {
        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    }

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

    // PlayerScreen에서는 뒤로가기 한 번으로 즉시 이전 화면으로 돌아간다.
    BackHandler {
        back()
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
        linkkfSubtitleUrl = currentEpisode.vttUrl
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
                val localStoredSubtitle = storedSubtitle?.takeIf { path -> path.startsWith("/") && File(path).isFile }
                val localUserSubtitle = localStoredSubtitle?.takeIf { isLocalUserSubtitlePath(it) }
                val localLinkkf = withContext(Dispatchers.IO) {
                    SubtitleStore.get(context, anime.id, currentEpisode.number, "linkkf")
                } ?: localStoredSubtitle?.takeIf { it.endsWith(".vtt", true) || it.endsWith(".srt", true) }
                val localKairan = withContext(Dispatchers.IO) {
                    SubtitleStore.get(context, anime.id, currentEpisode.number, "kairan")
                } ?: findLocalKairanAssSubtitle(context, anime.title, currentEpisode.number, localStoredSubtitle)
                subtitlesUrl = when {
                    localUserSubtitle != null -> localUserSubtitle
                    subtitleSourcePreference == "kairan" -> localKairan ?: localLinkkf
                    else -> localLinkkf ?: localKairan
                }
                subtitleSource = when {
                    localUserSubtitle != null -> "user"
                    subtitlesUrl == localKairan && localKairan != null -> "kairan"
                    subtitlesUrl != null -> "linkkf-vtt"
                    else -> "none"
                }
                kairanSubtitleResolved = true
                isLoading = false
            }
        } else if (isOffline) {
            isLoading = false
        }
    }

    LaunchedEffect(anime.id, anime.title, currentEpisode.number, isOffline, isDownloaded, currentEpisode.vttUrl, linkkfSubtitleUrl, subtitleSourcePreference) {
        kairanSubtitleResolved = false

        val userSubtitle = currentEpisode.vttUrl?.takeIf { isLocalUserSubtitlePath(it) }
        if (userSubtitle != null) {
            subtitlesUrl = userSubtitle
            subtitleSource = "user"
            kairanSubtitleResolved = true
            Log.d("Subtitle", "USE_USER_SUBTITLE path=$userSubtitle episode=${currentEpisode.number}")
            return@LaunchedEffect
        }

        val storedSubtitle = offlineEp?.vttUrl ?: currentEpisode.vttUrl
        val localStoredSubtitle = storedSubtitle?.takeIf { path -> path.startsWith("/") && File(path).isFile }

        if (isOffline || isDownloaded) {
            val localLinkkf = withContext(Dispatchers.IO) {
                SubtitleStore.get(context, anime.id, currentEpisode.number, "linkkf")
            } ?: localStoredSubtitle?.takeIf {
                it.endsWith(".vtt", true) || it.endsWith(".srt", true) || it.contains("/sub_${anime.id}_${currentEpisode.number}.")
            }
            val localKairan = withContext(Dispatchers.IO) {
                SubtitleStore.get(context, anime.id, currentEpisode.number, "kairan")
            } ?: findLocalKairanAssSubtitle(context, anime.title, currentEpisode.number, localStoredSubtitle)
            when (subtitleSourcePreference) {
                "kairan" -> {
                    subtitlesUrl = localKairan ?: localLinkkf
                    subtitleSource = if (localKairan != null) "kairan" else if (localLinkkf != null) "linkkf-vtt" else "none"
                }
                else -> {
                    subtitlesUrl = localLinkkf ?: localKairan
                    subtitleSource = if (localLinkkf != null) "linkkf-vtt" else if (localKairan != null) "kairan" else "none"
                }
            }
            kairanSubtitleResolved = true
            return@LaunchedEffect
        }

        // 스트리밍에서는 선택한 소스를 사용한다. Kairan은 필요할 때 캐시하고,
        // Linkkf는 StreamUrlExtractor가 발견한 VTT 주소를 사용한다.
        if (subtitleSourcePreference == "kairan") {
            val result = try {
                withContext(Dispatchers.IO) {
                    KairanSubtitleService.findSubtitle(context, anime.title, currentEpisode.number)
                }
            } catch (e: Exception) {
                Log.w("Kairan", "SUBTITLE_SEARCH_FAILED episode=${currentEpisode.number}", e)
                null
            }

            when (result) {
                is KairanSubtitleResult.DirectFile -> {
                    subtitlesUrl = result.path
                    subtitleSource = "kairan"
                    Log.d("Kairan", "USE_KAIRAN path=${result.path} episode=${currentEpisode.number}")
                }
                null -> {
                    subtitlesUrl = null
                    subtitleSource = "none"
                    Log.d("Kairan", "NO_KAIRAN_SUBTITLE episode=${currentEpisode.number}; waiting for Linkkf VTT fallback")
                }
            }
        } else {
            val linkkf = linkkfSubtitleUrl ?: currentEpisode.vttUrl
            subtitlesUrl = linkkf
            subtitleSource = if (!linkkf.isNullOrBlank()) "linkkf-vtt" else "none"
        }

        kairanSubtitleResolved = true
    }

    // 기본 자막이 Linkkf일 때도 재생을 막지 않고 Kairan ASS를 백그라운드에서 찾는다.
    // 발견되면 로컬 캐시에 저장된 경로를 유지하고, 사용자에게만 짧게 전환 여부를 묻는다.
    LaunchedEffect(anime.id, anime.title, currentEpisode.number, isOffline, isDownloaded) {
        if (isOffline || isDownloaded || kairanAssPromptHandled) return@LaunchedEffect
        if (subtitleSource == "user" || subtitleSourcePreference == "kairan") return@LaunchedEffect

        val result = try {
            withContext(Dispatchers.IO) {
                KairanSubtitleService.findSubtitle(context, anime.title, currentEpisode.number)
            }
        } catch (e: Exception) {
            Log.w("Kairan", "BACKGROUND_ASS_SEARCH_FAILED episode=${currentEpisode.number}", e)
            null
        }

        if (result is KairanSubtitleResult.DirectFile) {
            discoveredKairanAssPath = result.path
            kairanAssPromptHandled = true
            if (subtitleSource != "kairan") {
                showKairanAssPrompt = true
                Log.d("Kairan", "BACKGROUND_ASS_FOUND path=${result.path} episode=${currentEpisode.number}")
            }
        }
    }

    // 3초 동안만 표시하고 사용자가 선택하지 않으면 현재 자막을 그대로 유지한다.
    LaunchedEffect(showKairanAssPrompt) {
        if (showKairanAssPrompt) {
            delay(3000L)
            showKairanAssPrompt = false
        }
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

    // 속도 변경은 Media3/ExoPlayer에 즉시 반영한다.
    LaunchedEffect(exoPlayer, playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
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

    // PlayerScreen이 실제로 종료될 때만 화면 방향과 시스템 바를 복원한다.
    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(
                    window,
                    window.decorView
                ).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // ExoPlayer lifecycle은 화면 방향과 분리해서 관리한다.
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
            val lowerSubPath = subPath.lowercase(Locale.ROOT)
            val subtitleMimeType = when {
                lowerSubPath.contains(".ass") || lowerSubPath.contains(".ssa") -> MimeTypes.TEXT_SSA
                lowerSubPath.contains(".srt") -> MimeTypes.APPLICATION_SUBRIP
                else -> MimeTypes.TEXT_VTT
            }

            val effectiveSubtitlePath = if (
                syncOffsetMs != 0L &&
                subtitleMimeType != MimeTypes.TEXT_SSA
            ) {
                withContext(Dispatchers.IO) {
                    prepareSyncedSubtitleFile(
                        context = context,
                        subtitlePath = subPath,
                        animeId = anime.id,
                        episodeNumber = currentEpisode.number,
                        offsetMs = syncOffsetMs
                    )
                } ?: subPath
            } else subPath

            val subUri = when {
                effectiveSubtitlePath.startsWith("http://") || effectiveSubtitlePath.startsWith("https://") ->
                    Uri.parse(effectiveSubtitlePath)
                effectiveSubtitlePath.startsWith("file://") ->
                    Uri.parse(effectiveSubtitlePath)
                else -> Uri.fromFile(File(effectiveSubtitlePath))
            }

            Log.d(
                "Subtitle",
                "LOAD source=$subtitleSource path=$subPath effective=$effectiveSubtitlePath " +
                    "mime=$subtitleMimeType syncOffsetMs=$syncOffsetMs"
            )

            val subtitleId = if (subtitleMimeType == MimeTypes.TEXT_SSA) {
                "kairan-ass-${anime.id}-${currentEpisode.number}"
            } else {
                "${subtitleSource}-subtitle-${anime.id}-${currentEpisode.number}-${syncOffsetMs}"
            }

            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
                .setId(subtitleId)
                .setMimeType(subtitleMimeType)
                .setLanguage("ko")
                .setLabel(
                    when {
                        subtitleSource == "kairan" -> "Kairan ASS"
                        subtitleSource == "linkkf-vtt" -> "Linkkf VTT"
                        else -> "Subtitle"
                    }
                )
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
        val positionFraction = subtitleBottomPaddingFraction.coerceIn(0.03f, 0.45f)
        subView.setBottomPaddingFraction(positionFraction)

        // Media3 버전에 따라 bottomPaddingFraction이 재측정 시 되돌아가는 경우가 있어
        // 실제 padding도 함께 갱신하고 즉시 invalidate/requestLayout 한다.
        fun applyPositionAfterLayout() {
            subView.setBottomPaddingFraction(positionFraction)
            val bottomPx = (subView.height * positionFraction).toInt().coerceAtLeast(0)
            subView.setPadding(
                subView.paddingLeft,
                subView.paddingTop,
                subView.paddingRight,
                bottomPx
            )
            subView.invalidate()
        }

        // 최초 AndroidView 생성 시에는 SubtitleView 높이가 아직 0일 수 있다.
        // post()로 레이아웃 이후 한 번 더 적용해 처음 표시되는 VTT에도 위치 설정을 반영한다.
        subView.requestLayout()
        subView.post { applyPositionAfterLayout() }
        applyPositionAfterLayout()

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
                            useController = !isPlayerLocked
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
                    val settingsButton = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                    settingsButton?.visibility = View.GONE
                    playerView.player = forwardingPlayer
                    playerView.useController = !isPlayerLocked
                        applySubtitleSettingsToView(playerView)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            !isOffline && !videoUrl.isNullOrBlank() -> {
                StreamUrlExtractor(
                    targetUrl = videoUrl,
                    onSubtitleFound = { foundUrl ->
                        // 발견한 VTT를 항상 보관한다. Kairan을 보고 있는 동안 발견되어도
                        // 나중에 Linkkf VTT를 선택하면 즉시 다시 사용할 수 있어야 한다.
                        linkkfSubtitleUrl = foundUrl
                        if (subtitleSourcePreference == "linkkf") {
                            subtitlesUrl = foundUrl
                            subtitleSource = "linkkf-vtt"
                            Log.d("Subtitle", "USE_LINKKF_VTT url=$foundUrl")
                        } else {
                            Log.d("Subtitle", "CACHE_LINKKF_VTT url=$foundUrl")
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

        // 잠금 상태에서는 PlayerView의 기본 컨트롤을 사용하지 않고,
        // 화면 터치 시 잠금 버튼만 2초 동안 보여준다.
        if (isPlayerLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showLockedButton = true
                                lockedButtonRequest++
                            }
                        )
                    }
            )
        }

        LaunchedEffect(lockedButtonRequest, isPlayerLocked) {
            if (isPlayerLocked && showLockedButton) {
                delay(2000L)
                showLockedButton = false
            }
        }

        // 우측 하단에 3초간 조용히 표시되는 Kairan ASS 전환 안내.
        AnimatedVisibility(
            visible = showKairanAssPrompt && !isPlayerLocked,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ASS 자막을 발견했습니다. 바꾸시겠습니까?",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.widthIn(max = 260.dp)
                    )
                    TextButton(onClick = { showKairanAssPrompt = false }) {
                        Text("아니요")
                    }
                    Button(onClick = {
                        discoveredKairanAssPath?.let { path ->
                            subtitlesUrl = path
                            subtitleSource = "kairan"
                            subtitleSourcePreference = "kairan"
                            vm.updatePlayerSettings(
                                context,
                                vm.playerSettings.copy(subtitleSourcePreference = "kairan")
                            )
                            Log.d("Kairan", "USER_SWITCHED_TO_BACKGROUND_ASS path=$path episode=${currentEpisode.number}")
                        }
                        showKairanAssPrompt = false
                    }) {
                        Text("예")
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isControlsVisible && !isPlayerLocked,
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
                        back()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로가기",
                        tint = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Player settings are consolidated here in the top-right.
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

        if (vm.playerSettings.showAniSkipButton) buttonAniSkipSegment?.let { segment ->
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("재생 속도", fontWeight = FontWeight.Bold, color = LilacDark)
                            Text(
                                "${String.format(java.util.Locale.US, "%.2f", playbackSpeed)}x",
                                fontWeight = FontWeight.Bold,
                                color = Lilac
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Slider(
                            value = playbackSpeedOptions.indexOf(playbackSpeed)
                                .takeIf { it >= 0 }
                                ?.toFloat() ?: 2f,
                            onValueChange = { value ->
                                val index = value.toInt().coerceIn(
                                    0,
                                    playbackSpeedOptions.lastIndex
                                )
                                playbackSpeed = playbackSpeedOptions[index]
                            },
                            valueRange = 0f..playbackSpeedOptions.lastIndex.toFloat(),
                            steps = playbackSpeedOptions.size - 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0.5x", fontSize = 11.sp, color = Color.Gray)
                            Text("1.0x", fontSize = 11.sp, color = Color.Gray)
                            Text("2.0x", fontSize = 11.sp, color = Color.Gray)
                        }

                        Text(
                            "슬라이더를 움직이면 즉시 재생 속도가 변경됩니다.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 6.dp)
                        )

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "OP/ED 스킵 버튼 표시",
                                    fontWeight = FontWeight.Bold,
                                    color = LilacDark
                                )
                                Text(
                                    "재생화면에 나타나는 OP/ED 스킵 버튼을 표시합니다.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = vm.playerSettings.showAniSkipButton,
                                onCheckedChange = { enabled ->
                                    vm.updatePlayerSettings(
                                        context,
                                        vm.playerSettings.copy(showAniSkipButton = enabled)
                                    )
                                }
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        Text("자막 소스", fontWeight = FontWeight.Bold, color = LilacDark)
                        Text(
                            "다운로드 시 Linkkf VTT와 Kairan ASS를 모두 저장하고, 재생할 소스를 여기서 선택합니다.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = subtitleSourcePreference == "linkkf",
                                onClick = { subtitleSourcePreference = "linkkf" },
                                label = { Text("Linkkf VTT") }
                            )
                            FilterChip(
                                selected = subtitleSourcePreference == "kairan",
                                onClick = { subtitleSourcePreference = "kairan" },
                                label = { Text("Kairan ASS") }
                            )
                        }
                        Text(
                            "Kairan은 현재 연결된 원본이 ASS 형식이므로 ASS 렌더러를 사용합니다.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )

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

                        Text("VTT 자막 싱크 입력", fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = syncOffsetText,
                                onValueChange = { value ->
                                    syncOffsetText = value.filter { it.isDigit() || it == '-' }
                                    syncOffsetText.toLongOrNull()?.let { syncOffsetMs = it }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                suffix = { Text("ms") }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "양수 = 자막을 늦춤\n음수 = 자막을 앞당김",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            "VTT 자막 위치 (${(subtitleBottomPaddingFraction * 100).toInt()}%)",
                            fontSize = 13.sp
                        )
                        Slider(
                            value = subtitleBottomPaddingFraction,
                            onValueChange = { subtitleBottomPaddingFraction = it },
                            valueRange = 0.03f..0.30f,
                            steps = 26
                        )
                        Text(
                            "값이 클수록 자막이 조금 더 위로 올라갑니다.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )

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
                        vm.updatePlayerSettings(
                            context,
                            vm.playerSettings.copy(
                                syncOffsetMs = syncOffsetText.toLongOrNull() ?: syncOffsetMs,
                                subtitleSize = subtitleSizePercent,
                                subtitleBottomPaddingFraction = subtitleBottomPaddingFraction,
                                subtitleSourcePreference = subtitleSourcePreference
                            )
                        )
                        showPlayerSettingsDialog = false 
                    }) {
                        Text("확인")
                    }
                }
            )
        }
        AnimatedVisibility(
            visible = if (isPlayerLocked) showLockedButton else isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
                .size(48.dp)
        ) {
            IconButton(
                onClick = {
                    if (isPlayerLocked) {
                        // 잠금 해제 후에는 일반 플레이어 컨트롤을 다시 사용할 수 있게 한다.
                        isPlayerLocked = false
                        showLockedButton = true
                        isControlsVisible = true
                    } else {
                        // 잠금 상태에서는 잠금 버튼만 잠시 남긴다.
                        isPlayerLocked = true
                        showLockedButton = true
                        lockedButtonRequest++
                    }
                }
            ) {
                Icon(
                    imageVector = if (isPlayerLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isPlayerLocked) "잠금 해제" else "플레이어 잠금",
                    tint = Color.White
                )
            }
        }
    }
}

// ============================================================
// KAIRAN03 BLOGGER + GOOGLE DRIVE SUBTITLE
// ============================================================

sealed class KairanSubtitleResult {
    data class DirectFile(val path: String) : KairanSubtitleResult()
}

