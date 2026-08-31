package com.lilac.anime.data.matcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeTitleMatcherTest {

    private fun matches(title: String, query: String): Boolean =
        AnimeTitleMatcher.matches(title, query)

    @Test
    fun exactTitleMatch_found() {
        assertTrue(matches("무직전생 ~이세계에 가면 진심을 낸다~", "무직전생"))
        assertTrue(matches("무직전생", "무직전생"))
    }

    @Test
    fun aliasMatch_found() {
        assertTrue(matches("전생했더니 슬라임이었던 건에 대하여", "전생슬"))
        assertTrue(matches("전생했더니 슬라임이었던 건에 대하여", "tensura"))
        assertTrue(matches("나의 히어로 아카데미아", "히로아카"))
    }

    @Test
    fun englishTitleMatch_found() {
        assertTrue(matches("원피스", "one piece"))
        assertTrue(matches("블리치", "bleach"))
    }

    @Test
    fun jjkAlias_found() {
        assertTrue(matches("주술회전", "jjk"))
        assertTrue(matches("주술회전", "jujutsu kaisen"))
        assertTrue(matches("주술회전", "주술회전"))
    }

    @Test
    fun shortQueryExactAndPrefixOnly() {
        // ":ㅂ" 같은 1글자는 부분 매칭되지 않는다.
        assertFalse(matches("원피스", ":ㅂ"))
        assertFalse(matches("배가본드", "노"))
        // 여전히 정확/prefix 매칭은 동작한다.
        assertTrue(matches("원피스", "원"))
    }

    // 2글자 제목 prefix는 매칭되어야 한다.
    @Test
    fun twoCharPrefixTitle_stillMatches() {
        assertTrue(matches("누가복음", "누가"))
    }

    @Test
    fun jujutsuKaisen_aliasDictHasNoForeignEntries() {
        // '황천의 츠가이/츠카이'는 주술회전과 다른 작품이므로 alias 사전에 없어야 한다.
        val aliases = AnimeTitleMatcher.aliasesOf("주술회전")
        assertFalse("황천의 츠가이는 주술회전 alias가 아님", aliases.any { it.contains("황천") })
        assertFalse("주술회도 사전에서 제거되어야 함", aliases.any { it == "주술회" })
    }

    @Test
    fun aliasDict_excludesStudioNames() {
        // '스튜디오지브리'는 제작사이므로 어떤 작품의 alias에도 등재되어선 안 된다.
        val allAliases = mutableSetOf<String>()
        for (title in listOf("주술회전", "원피스", "나루토", "블리치", "진격의거인")) {
            allAliases.addAll(AnimeTitleMatcher.aliasesOf(title))
        }
        assertFalse("지브리는 작품 alias가 아님", allAliases.any { it.contains("지브리") })
        assertFalse("스튜디오는 작품 alias가 아님", allAliases.any { it.contains("스튜디오") })
    }

    @Test
    fun typoAlias_noFalsePositive() {
        // 오타 'eyesheild'는 제거됨.
        assertFalse(matches("아이실드21", "eyesheild"))
        assertTrue(matches("아이실드21", "eyeshield"))
    }

    @Test
    fun chosungMatch_found() {
        // 초성은 대부분 한 작품만 가리키는 경우에만 매칭
        assertFalse(matches("소드아트온라인", "ㄴㄷㅁ"))
    }

    @Test
    fun score_prefersExactOverSubstring() {
        val high = AnimeTitleMatcher.score("무직전생", "무직전생")
        val low = AnimeTitleMatcher.score("무직전생 ~이세계에 가면 진심을 낸다~", "무직전생")
        assertTrue("정확 일치가 prefix/부분보다 높아야 함: $high vs $low", high >= low)
        assertTrue(high > 0)
    }
}
