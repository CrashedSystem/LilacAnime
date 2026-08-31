package com.lilac.anime

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lilac.anime.data.*
import java.text.Collator
import java.util.Locale

@Composable
fun AllAnimeScreen(
    vm: AnimeViewModel,
    openDetail: (Anime) -> Unit,
    onNavigate: (String) -> Unit
) {
    LaunchedEffect(Unit) {
        vm.loadAllAnime()
    }

    // 오름차순/내림차순 정렬 상태 (기본 오름차순)
    var ascending by remember { mutableStateOf(true) }
    // 게시판처럼 한 페이지에 보여줄 개수 (10 ~ 50)
    var pageSize by remember { mutableIntStateOf(20) }
    // 현재 페이지 (1부터 시작)
    var currentPage by remember { mutableIntStateOf(1) }

    // 전체 목록을 제목 기준으로 정렬한다. 한글/영문/숫자가 섞여 있어
    // Collator(한국어 로케일)로 가나다+알파벳 순을 자연스럽게 반영한다.
    val sortedAllAnime = remember(vm.allAnime, ascending) {
        val list = vm.allAnime.toMutableList()
        val collator = runCatching { Collator.getInstance(Locale.KOREAN) }.getOrNull()
            ?.apply { strength = Collator.PRIMARY }
        if (collator != null) {
            list.sortWith { a, b -> collator.compare(a.title, b.title) }
        } else {
            list.sortBy { it.title.lowercase(Locale.ROOT) }
        }
        if (!ascending) list.reverse()
        list
    }

    val totalCount = sortedAllAnime.size
    val totalPages = maxOf(1, (totalCount + pageSize - 1) / pageSize)

    // 현재 페이지가 범위를 벗어나지 않도록 보정 (목록이 줄어들 때 대비)
    if (currentPage > totalPages) {
        currentPage = totalPages
    }

    // 현재 페이지 항목만 잘라 그리드에 보여준다.
    val pageItems = remember(sortedAllAnime, currentPage, pageSize) {
        val from = (currentPage - 1) * pageSize
        if (from >= totalCount) emptyList() else sortedAllAnime.subList(from, minOf(from + pageSize, totalCount))
    }

    AppScaffold(selected = "all", onSelect = onNavigate) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 상단 바: 제목 + 정렬 토글
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
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
                    Text("${totalCount}개", color = LilacDark, fontSize = 14.sp)
                }
            }

            // 두 번째 줄: 정렬 방향 + 페이지 크기 선택
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 정렬 방향 토글 (오름차순 / 내림차순)
                val sortLabel = if (ascending) "가나다순 ↑" else "가나다 역순 ↓"
                OutlinedButton(
                    onClick = {
                        ascending = !ascending
                        currentPage = 1
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(sortLabel, fontSize = 13.sp)
                }

                // 페이지 크기 선택 (10 / 20 / 30 / 50)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(10, 20, 30, 50).forEach { size ->
                        val selected = pageSize == size
                        Surface(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickableNoIndication {
                                    pageSize = size
                                    currentPage = 1
                                },
                            color = if (selected) Lilac.copy(alpha = 0.25f) else Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$size",
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Lilac else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(
                    items = pageItems,
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

            // 하단 페이지네이션 바 (게시판 스타일)
            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (currentPage > 1) currentPage-- },
                        enabled = currentPage > 1
                    ) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "이전 페이지")
                    }

                    Text(
                        "$currentPage / $totalPages",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    IconButton(
                        onClick = { if (currentPage < totalPages) currentPage++ },
                        enabled = currentPage < totalPages
                    ) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "다음 페이지")
                    }
                }
            }
        }
    }
}

// ============================================================
// HOME RAILS & CARDS
// ============================================================
