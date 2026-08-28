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
import android.util.TypedValue
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
import com.lilac.anime.data.*
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import android.content.Intent
import kotlinx.coroutines.flow.update

// ============================================================
// DATA MODELS
// ============================================================

data class StreamQuality(
    val label: String,
    val url: String
)

data class ExoVideoQualityOption(
    val label: String,
    val width: Int,
    val height: Int,
    val isAuto: Boolean = false
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

object SubtitleColorPresets {
    val TextColors = mapOf(
        "흰색" to android.graphics.Color.WHITE,
        "노란색" to android.graphics.Color.YELLOW,
        "연두색" to android.graphics.Color.GREEN,
        "하늘색" to android.graphics.Color.CYAN
    )

    val StrokeColors = mapOf(
        "검은색" to android.graphics.Color.BLACK,
        "투명(없음)" to android.graphics.Color.TRANSPARENT,
        "붉은색" to android.graphics.Color.RED
    )

    val BackgroundColors = mapOf(
        "투명" to android.graphics.Color.TRANSPARENT,
        "반투명 검정" to android.graphics.Color.parseColor("#80000000"),
        "검정" to android.graphics.Color.BLACK
    )
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
                    val localVttPath = downloadSubtitleFile(context, anime.id, ep.number, vttUrl)
                    startEpisodeDownload(context, anime.id, anime.title, ep, selectedQuality.url)

                    OfflineStore.saveAnime(context, anime)
                    OfflineStore.saveEpisode(
                        context = context,
                        animeId = anime.id,
                        episode = ep.copy(videoUrl = selectedQuality.url, vttUrl = localVttPath ?: vttUrl)
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
                            val localVttPath = downloadSubtitleFile(context, anime.id, ep.number, vttUrl)
                            startEpisodeDownload(context, anime.id, anime.title, ep, selectedQuality.url)

                            OfflineStore.saveAnime(context, anime)
                            OfflineStore.saveEpisode(
                                context = context,
                                animeId = anime.id,
                                episode = ep.copy(videoUrl = selectedQuality.url, vttUrl = localVttPath ?: vttUrl)
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
    val activity = context as? Activity
    val isOffline by vm.isOffline.collectAsState()
    
    var currentEpisode by remember(episode) { mutableStateOf(episode) }
    
    var isFullScreen by remember { mutableStateOf(false) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var subtitlesUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    var isAutoPlayEnabled by rememberSaveable { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(true) }

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

    LaunchedEffect(currentEpisode, isDownloaded, offlineEp) {
        isLoading = true
        streamUrl = null
        subtitlesUrl = null
        parsedStreamingQualities = emptyList()
        selectedStreamingQuality = null
        exoQualities = emptyList()
        selectedQualityOption = null
        pendingSeekPositionMs = -1L
        
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
                subtitlesUrl = offlineEp?.vttUrl ?: currentEpisode.vttUrl
                isLoading = false
            }
        } else if (isOffline) {
            isLoading = false
        }
    }

    val trackSelector = remember(context) { DefaultTrackSelector(context) }
    val exoPlayer = remember(context) { 
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build() 
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
        onDispose {
            val duration = exoPlayer.duration
            if (duration > 0 && duration != C.TIME_UNSET) {
                val progress = (exoPlayer.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                vm.updateProgress(
                    context = context,
                    animeId = anime.id,
                    episodeNumber = currentEpisode.number,
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
            DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
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
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("ko")
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

    fun applySubtitleSettingsToView(playerView: PlayerView) {
        val subView = playerView.subtitleView ?: return
        
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
                    onSubtitleFound = { subtitlesUrl = it },
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
                }
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