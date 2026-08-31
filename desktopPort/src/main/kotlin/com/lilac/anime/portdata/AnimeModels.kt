package com.lilac.anime.portdata

data class Anime(
    val id: String = "",
    val title: String = "",
    val poster: String = "",
    val backdrop: String = "",
    val genres: List<String> = emptyList(),
    val description: String = "",
    val detailUrl: String = "",
    val episodes: List<Episode> = emptyList(),
    val dubEpisodes: List<Episode> = emptyList()
)

data class Episode(
    val id: String,
    val number: Int,
    val title: String,
    val description: String = "", // <-- 이 줄을 추가해 주세요!
    val videoUrl: String? = null,
    val vttUrl: String? = null
)

data class WatchProgress(
    val animeId: String,
    val episodeNumber: Int,
    val progress: Float
)