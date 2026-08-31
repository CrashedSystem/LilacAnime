package com.lilac.anime.data.matcher

import java.util.Locale

/**
 * 검색 매커니즘 개선 매처.
 * 사용자는 '전생슬', 'bleach', '무직전생' 같은 약어/이명/영문 표기로 검색하지만
 * 서버 제목(Anime.title)은 정식 한글 제목이라 단순 부분일치로는 안 잡히는 문제를 해결한다.
 *
 * 매칭 우선순위:
 *  1. 정확한 제목(정규화)
 *  2. alias 정확 일치
 *  3. 한글 초성
 *  4. prefix 일치
 *  5. 부분 문자열 (짧은 검색어는 오탐이 많아 제외)
 *  6. 단어 이니셜 축약어
 */
object AnimeTitleMatcher {

    /** 기호를 제거하고 소문자화한 검색 정규화 문자열. */
    fun normalize(input: String): String = buildString {
        val lower = input.lowercase(Locale.ROOT)
        for (c in lower) {
            if (c.isLetterOrDigit()) append(c)
        }
    }

    /** 단어 이니셜 축약어. 예: "무직전생 ~이세계에..." -> "무이" 등. */
    fun acronym(input: String): String = buildString {
        val cleaned = input
            .replace(Regex("[~!@#$%^&*()_+|<>?:{}.,-]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        val compact = cleaned.joinToString("")
        if (cleaned.size >= 2 && compact.length <= 8) {
            append(compact)
        }
        append(' ')
        for (w in cleaned) {
            val first = w.first()
            if (first.code < 0xAC00 || first.code > 0xD7A3) append(first)
        }
    }

    private fun chosungChar(c: Char): Char? {
        val code = c.code
        if (code in 0xAC00..0xD7A3) {
            return ('\u1100' + (code - 0xAC00) / (28 * 21))
        }
        return null
    }

    fun chosung(input: String): String = buildString {
        for (c in input) chosungChar(c)?.let { append(it) }
    }

    private fun isChosungOnly(input: String): Boolean {
        if (input.isEmpty()) return false
        for (c in input) {
            if (!(c in '\u1100'..'\u1112') && chosungChar(c) == null) return false
        }
        return true
    }

    // ====================================================================
    // 이명/약어 사전 (검색 로직과 분리된 데이터)
    // 키: normalize(정식 제목), 값: 매칭용 별칭.
    // 별칭은 오직 "그 작품을 지칭하는" 표기만 등재한다.
    // (다른 작품명, 제작사명, 장르명, 비 애니 작품, 검증 불가 값은 금지)
    // ====================================================================
    private val aliasByTitle = mapOf(
        "전생했더니슬라임이었던건에대하여" to listOf("전생슬", "전생슬라임", "tensura", "tensuresuraimu", "reincarnated as a slime"),
        "블리치" to listOf("bleach"),
        "무직전생이세계에가면진심을낸다" to listOf("무직전생", "mushoku tensei", "mushokutensei", "jobless reincarnation"),
        "주술회전" to listOf("jujutsu kaisen", "jujutsukaisen", "jjk"),
        "re제로부터시작하는이세계생활" to listOf("re제로", "rezero", "re zero", "re0", "리제로"),
        "소드아트온라인" to listOf("소아온", "sao", "sword art online"),
        "진격의거인" to listOf("진격거", "attack on titan", "aot"),
        "원피스" to listOf("one piece", "onepiece"),
        "나루토" to listOf("naruto"),
        "드래곤볼" to listOf("dragonball", "dragon ball"),
        "귀멸의칼날" to listOf("귀칼", "demon slayer", "kimetsu no yaiba"),
        "스파이패밀리" to listOf("spy x family", "spyfamily"),
        "체인소맨" to listOf("chainsaw man", "chainsawman", "chainsaw"),
        "나의히어로아카데미아" to listOf("히로아카", "my hero academia", "boku no hero"),
        "호로미야" to listOf("호로미야", "horimiya"),
        "달링인더프랑키스" to listOf("달링", "darling in the franxx", "ditf"),
        "코드기어스" to listOf("code geass", "codegeass"),
        "아이실드21" to listOf("eyeshield", "eyeshield 21"),
        "원펀맨" to listOf("one punch man", "onepunchman", "opm"),
        "내일의조" to listOf("ashita no joe", "tomorrows joe"),
        "엔드오브에반게리온" to listOf("에반게리온", "신세기에반게리온", "evangelion", "eva", "neon genesis", "end of evangelion", "eoe"),
        "우르세이야츠라" to listOf("urusei yatsura"),
        "시티헌터" to listOf("city hunter"),
        "슬램덩크" to listOf("slam dunk", "slamdunk"),
        "배가본드" to listOf("vagabond"),
        "이누야샤" to listOf("inuyasha"),
        "하이큐" to listOf("하이큐", "haikyu", "haikyuu"),
        "블루록" to listOf("blue lock", "bluelock"),
        "월희" to listOf("tsukihime"),
        "바람의검심" to listOf("rurouni kenshin", "켄신"),
        "중2병이라도사랑이하고싶어" to listOf("chuunibyou"),
        "사이코패스" to listOf("psycho pass"),
        "아기공룡둘리" to listOf("둘리"),
        "진월담월희" to listOf("shingetsutan tsukihime"),
    )

    /**
     * 사전에서 제목에 맞는 별칭 목록을 찾는다.
     * 정규화된 제목이 사전 키 또는 별칭과 일치/포함되면 그 작품의 모든 별칭을 반환한다.
     * -> 제목이 "Bleach"(영문)든 "블리치"(한글)든 상대 표기로 검색돼도 매칭된다.
     */
    fun aliasesOf(title: String): List<String> {
        val nTitle = normalize(title)
        if (nTitle.isEmpty()) return emptyList()

        for ((key, values) in aliasByTitle) {
            val titleIsEntry =
                nTitle == key || (key.isNotEmpty() && nTitle.contains(key)) ||
                    values.any { v ->
                        val nv = normalize(v)
                        nv.isNotEmpty() && (nTitle.contains(nv) || nv.contains(nTitle))
                    }
            if (titleIsEntry) {
                return buildList {
                    add(key)
                    addAll(values)
                }
            }
        }
        return emptyList()
    }

    /** 검색어가 제목에 매칭되는지. 우선순위대로 검사하며 짧은 검색어는 초과 매칭을 방지한다. */
    fun matches(title: String, rawQuery: String): Boolean {
        val query = rawQuery.trim()
        if (query.isEmpty()) return true
        val nQuery = normalize(query)
        if (nQuery.isEmpty()) return false
        val nTitle = normalize(title)
        if (nTitle.isEmpty()) return false

        val aliases = aliasesOf(title).map { normalize(it) }.filter { it.isNotEmpty() }

        // 1-2글자처럼 아주 짧은 검색어는 부분 문자열 매칭 시 오탐이 폭증하므로 배제.
        val isTooShort = nQuery.length < 3

        // 1) 정확 일치 (정규화된 제목 / alias)
        if (nTitle == nQuery) return true
        if (aliases.any { it == nQuery }) return true

        // 2) 한글 초성 검색
        if (isChosungOnly(nQuery) && nQuery.length >= 2) {
            val titleCho = chosung(title)
            if (titleCho == nQuery || titleCho.startsWith(nQuery)) return true
            if (aliases.any { a ->
                    val cho = chosung(a)
                    cho == nQuery || (cho.isNotEmpty() && cho.startsWith(nQuery))
                }) return true
        }

        // 3) prefix 일치 (짧은 검색어에도 적용)
        if (nTitle.startsWith(nQuery)) return true
        if (aliases.any { it.startsWith(nQuery) }) return true

        // 4) 부분 문자열 (짧은 검색어 제외)
        if (!isTooShort) {
            if (nTitle.contains(nQuery)) return true
            if (aliases.any { it.contains(nQuery) }) return true
        }

        // 5) 단어 이니셜 축약어
        val titleAcro = normalize(acronym(title))
        if (titleAcro.isNotEmpty() &&
            (titleAcro == nQuery || titleAcro.startsWith(nQuery) || (!isTooShort && titleAcro.contains(nQuery)))
        ) return true

        return false
    }

    /** 매칭 우선순위를 반영한 점수(0이면 비매칭). 검색 결과 정렬에 사용한다. */
    fun score(title: String, rawQuery: String): Int {
        if (!matches(title, rawQuery)) return 0
        val nQuery = normalize(rawQuery)
        val nTitle = normalize(title)
        val aliases = aliasesOf(title).map { normalize(it) }.filter { it.isNotEmpty() }

        if (nTitle == nQuery) return 100
        if (aliases.any { it == nQuery }) return 95
        if (isChosungOnly(nQuery) && nQuery.length >= 2 && chosung(title).startsWith(nQuery)) return 85
        if (nTitle.startsWith(nQuery)) return 80
        if (aliases.any { it.startsWith(nQuery) }) return 75
        if (nTitle.contains(nQuery)) return 65
        if (aliases.any { it.contains(nQuery) }) return 55
        return 40
    }
}
