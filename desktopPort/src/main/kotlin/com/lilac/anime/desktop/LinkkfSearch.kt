package com.lilac.anime.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lilac.anime.portdata.Anime
import com.lilac.anime.portdata.AnimeRepository
import com.lilac.anime.stream.BrowserEpisodeStreamExtractor
import com.lilac.anime.stream.EpisodeStreamInfo
import com.lilac.anime.stream.StreamQuality
import com.lilac.anime.stream.SubtitleDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Linkkf-based search flow for Desktop.
 *
 * 1. Type a title -> filter the full Linkkf catalogue (same client-side search
 *    the Android app uses, no server search endpoint needed).
 * 2. Pick a title -> load detail + episode/dub lists from Linkkf.
 * 3. Pick an episode -> extract the real m3u8 (+ VTT) with
 *    [BrowserEpisodeStreamExtractor] (Playwright/Chromium), then play in mpv.
 */
@Composable
fun LinkkfSearch(
    modifier: Modifier = Modifier,
    onPlay: (url: String, headers: Map<String, String>, subtitlePath: Path?) -> Unit,
    onBusy: (Boolean) -> Unit,
    onStatus: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = remember { AnimeRepository() }
    val extractor = remember { BrowserEpisodeStreamExtractor() }

    var query by remember { mutableStateOf("") }
    var shouldSearch by remember { mutableStateOf(false) }
    var catalogue by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    var selectedAnime by remember { mutableStateOf<Anime?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var subtitle by remember { mutableStateOf<Path?>(null) }

    var dubMode by remember { mutableStateOf(false) }
    var currentInfo by remember { mutableStateOf<EpisodeStreamInfo?>(null) }
    var currentQuality by remember { mutableStateOf<StreamQuality?>(null) }

    suspend fun playEpisode(anime: Anime, pageUrl: String, isDub: Boolean) {
        onBusy(true)
        onStatus("스트림 추출 중... $pageUrl")
        try {
            val info = withContext(Dispatchers.IO) { extractor.extract(pageUrl) }
            currentInfo = info
            currentQuality = info.qualities.firstOrNull()
            val localSubtitle = if (info.subtitleUrl != null) {
                withContext(Dispatchers.IO) {
                    SubtitleDownloader.download(
                        info.subtitleUrl,
                        Path.of(System.getProperty("user.home"), ".lilacanime", "subtitles"),
                        fileName = "${anime.id}_${if (isDub) "dub" else "sub"}.vtt",
                        headers = info.subtitleHeaders
                    )
                }
            } else null
            subtitle = localSubtitle

            val quality = currentQuality
            if (quality != null) {
                onPlay(quality.url, quality.headers, localSubtitle)
                onStatus("재생 시작: ${anime.title} ${if (isDub) "(더빙)" else ""}")
            } else {
                onStatus("m3u8 요청을 찾지 못했습니다: $pageUrl")
            }
        } catch (e: Exception) {
            onStatus("추출 실패: ${e.message ?: e::class.simpleName}")
        } finally {
            onBusy(false)
        }
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🔎 Linkkf 검색", style = MaterialTheme.typography.titleMedium)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            shouldSearch = true
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("작품명") },
                        singleLine = true
                    )
                    Button(
                        enabled = query.isNotBlank() && !loading,
                        onClick = {
                            loading = true
                            onStatus("카탈로그 로드 중...")
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) { repository.getAllAnimeList() }
                                }.onSuccess { list ->
                                    catalogue = list
                                    onStatus("카탈로그 ${list.size}개 로드 완료")
                                }.onFailure {
                                    onStatus("로드 실패: ${it.message}")
                                }
                                loading = false
                            }
                        }
                    ) { Text("카탈로그 로드") }
                }
            }
        }

        val results = remember(query, catalogue, shouldSearch) {
            if (query.isBlank()) catalogue
            else catalogue.filter {
                it.title.contains(query.trim(), ignoreCase = true)
            }
        }

        if (loading || detailLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.width(28.dp).height(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (loading) "카탈로그 로드 중..." else "상세 로드 중...")
            }
        }

        if (selectedAnime == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(results, key = { it.id }) { anime ->
                    AnimeRow(
                        anime = anime,
                        onClick = {
                            selectedAnime = anime
                            detailLoading = true
                            onStatus("상세 로드 중: ${anime.title}")
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) { repository.getAnimeDetail(anime) }
                                }.onSuccess { detail ->
                                    selectedAnime = detail
                                    onStatus("${detail.title} · ${detail.episodes.size}화")
                                }.onFailure {
                                    selectedAnime = anime
                                    onStatus("상세 로드 실패: ${it.message}")
                                }
                                detailLoading = false
                            }
                        }
                    )
                }
            }
        } else {
            val anime = selectedAnime ?: return@LinkkfSearch
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Column {
                        Text(anime.title, style = MaterialTheme.typography.titleLarge)
                        Text(anime.genres.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        if (anime.description.isNotBlank()) {
                            Text(anime.description, style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = !dubMode, onClick = { dubMode = false }, label = { Text("일반") })
                            Spacer(Modifier.width(6.dp))
                            FilterChip(selected = dubMode, onClick = { dubMode = true }, label = { Text("더빙") })
                            Spacer(Modifier.width(8.dp))
                            Text("${(if (dubMode) anime.dubEpisodes else anime.episodes).size}화")
                        }
                    }
                }

                val eps = if (dubMode) anime.dubEpisodes else anime.episodes
                items(eps, key = { it.id }) { ep ->
                    val pageUrl = ep.videoUrl ?: return@items
                    Card(Modifier.fillMaxWidth().clickable {
                        scope.launch { playEpisode(anime, pageUrl, dubMode) }
                    }) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${ep.number}화", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            Text("▶ 재생", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                item {
                    Button(onClick = {
                        selectedAnime = null
                        currentInfo = null
                        currentQuality = null
                    }) { Text("← 목록으로") }
                }
            }
        }
    }
}

@Composable
private fun AnimeRow(anime: Anime, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(anime.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("▶", color = MaterialTheme.colorScheme.primary)
        }
    }
}
