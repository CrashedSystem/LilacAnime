package com.lilac.anime

import java.util.Locale
import kotlin.math.min

data class KairanPost(val title: String, val url: String)
data class KairanMatch(val post: KairanPost, val similarity: Double)
data class HangulVector(val cho: Int, val jung: Int, val jong: Int)

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

    fun filterNoise(input: String): String = input
        .lowercase(Locale.ROOT)
        .replace(Regex("[^가-힣a-zA-Z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun decomposeHangul(c: Char): HangulVector? {
        val code = c.code
        if (code !in 0xAC00..0xD7A3) return null
        val offset = code - 0xAC00
        val jong = offset % 28
        val jung = (offset / 28) % 21
        val cho = offset / (28 * 21)
        return HangulVector(cho, jung, jong)
    }

    private fun substitutionCost(a: Char, b: Char): Double {
        if (a == b) return 0.0
        val va = decomposeHangul(a)
        val vb = decomposeHangul(b)
        if (va != null && vb != null) {
            var cost = 0.0
            if (va.cho != vb.cho) cost += 0.4
            if (va.jung != vb.jung) cost += 0.3
            if (va.jong != vb.jong) cost += 0.3
            return cost
        }
        return 1.0
    }

    fun weightedEditDistance(first: String, second: String): Double {
        val a = filterNoise(first).replace(" ", "")
        val b = filterNoise(second).replace(" ", "")
        if (a.isEmpty()) return b.length.toDouble()
        if (b.isEmpty()) return a.length.toDouble()
        var previous = DoubleArray(b.length + 1) { it.toDouble() }
        var current = DoubleArray(b.length + 1)
        for (i in a.indices) {
            current[0] = (i + 1).toDouble()
            for (j in b.indices) {
                current[j + 1] = min(
                    min(previous[j + 1] + 1.0, current[j] + 1.0),
                    previous[j] + substitutionCost(a[i], b[j])
                )
            }
            val swap = previous; previous = current; current = swap
        }
        return previous[b.length]
    }

    fun weightedSimilarity(first: String, second: String): Double {
        val a = filterNoise(first).replace(" ", "")
        val b = filterNoise(second).replace(" ", "")
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) return 0.90
        return (1.0 - weightedEditDistance(a, b) / maxOf(a.length, b.length).toDouble()).coerceIn(0.0, 1.0)
    }

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
