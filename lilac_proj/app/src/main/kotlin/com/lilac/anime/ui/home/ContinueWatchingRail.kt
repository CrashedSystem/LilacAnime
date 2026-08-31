package com.lilac.anime

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lilac.anime.data.*
import kotlinx.coroutines.flow.map

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
