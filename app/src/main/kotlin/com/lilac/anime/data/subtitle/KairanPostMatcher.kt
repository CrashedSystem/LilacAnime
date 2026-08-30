package com.lilac.anime

import java.util.Locale
import com.lilac.anime.data.matcher.HangulSimilarityMatcher

data class KairanPost(val title: String, val url: String)
data class KairanMatch(val post: KairanPost, val similarity: Double)

object KairanPostMatcher {
    private const val MIN_SIMILARITY = 0.52

    fun findBestMatch(animeTitle: String, episodeNumber: Int, posts: List<KairanPost>): KairanMatch? {
        if (animeTitle.isBlank() || episodeNumber <= 0) return null
        val candidates = posts.asSequence()
            .filter { episodeMatch(it.title, it.url, episodeNumber) }
            .map { post ->
                val candidateTitle = removeEpisodeTokens(post.title, episodeNumber)
                KairanMatch(post, weightedSimilarity(animeTitle, candidateTitle))
            }
            .toList()
        return candidates.maxByOrNull { it.similarity }?.takeIf { it.similarity >= MIN_SIMILARITY }
    }

    fun filterNoise(input: String): String = HangulSimilarityMatcher.filterNoise(input)

    fun weightedEditDistance(first: String, second: String): Double =
        HangulSimilarityMatcher.weightedEditDistance(first, second)

    fun weightedSimilarity(first: String, second: String): Double =
        HangulSimilarityMatcher.similarity(first, second)

    // 기존 서비스의 엄격한 회차 판정을 유지한다. 제목에서 회차를 제거한 뒤 유사도를 계산한다.
    fun episodeMatch(postTitle: String, url: String, episode: Int): Boolean {
        val title = postTitle.lowercase(Locale.ROOT)
        val ep = episode.toString()
        val explicit = listOf(
            Regex("(?:^|\\s|[\\[\\]()._-])0*$ep(?:\\s*(?:화|회|편|話))(?=$|\\s|[\\[\\]()._-])", RegexOption.IGNORE_CASE),
            Regex("(?:^|\\s|[\\[\\]()._-])(?:ep|e|episode|#)\\s*0*$ep(?=$|\\s|[\\[\\]()._-])", RegexOption.IGNORE_CASE),
            Regex("(?:^|\\s|[\\[\\]()._-])0*$ep(?=$|\\s|[\\[\\]()._-])", RegexOption.IGNORE_CASE)
        )
        if (explicit.any { it.containsMatchIn(title) }) return true
        return Regex("(?:-|_)0*$ep\\.html(?:$|[?#])", RegexOption.IGNORE_CASE)
            .containsMatchIn(url.lowercase(Locale.ROOT))
    }

    private fun removeEpisodeTokens(title: String, episode: Int): String {
        val ep = episode.toString()
        return title
            .replace(Regex("(?:ep|episode|e)\\s*0*$ep", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("0*$ep\\s*(?:화|회|편|話)", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("(?<!\\d)0*$ep(?!\\d)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
