package com.lilac.anime.data.matcher

import java.util.Locale

/**
 * 검색 매커니즘 개선 매처.
 * 사용자는 '전생슬', 'bleach', '무직전생', '황천의 츠카이' 같은
 * 약어/이명/영문 표기로 검색하지만 서버 제목(Anime.title)은 정식 한글 제목이라
 * 단순 부분일치로는 안 잡히는 문제를 해결한다.
 *
 *  - normalize : 기호(콜론/하이픈/마침표 등)와 공백 제거 + 소문자화
 *  - acronym   : 제목 단어 이니셜 축약어 대응
 *  - chosung   : 자음 초성 검색어 대응
 *  - alias     : 대표 인기작의 약어/이명/영문제목 사전
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
        // 조인 길이가 짧고 단어가 여러 개면 그대로 축약 후보
        if (cleaned.size >= 2 && compact.length <= 8) {
            append(compact)
        }
        append(' ')
        // 단어별 첫 글자 (한글 완성자는 제외해 영문/일본어 이니셜만)
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

    // ---------- 이명/약어 사전 ----------
    // 키: normalize(정식 제목), 값: 매칭용 별칭
    private val aliasByTitle = mapOf(
        "전생했더니슬라임이었던건에대하여" to listOf("전생슬", "전생슬라임", "tensura", "tensuresuraimu", "reincarnated as a slime"),
        "블리치" to listOf("bleach", "bleach the", "bleach final"),
        "무직전생이세계에가면진심을낸다" to listOf("무직전생", "mushoku tensei", "mushokutensei", "jobless reincarnation"),
        "주술회전" to listOf("황천의 츠가이", "황천의츠가이", "황천의 츠카이", "황천의츠카이", "주술회", "jujutsu kaisen", "jujutsukaisen", "jjk"),
        "re제로부터시작하는이세계생활" to listOf("re제로", "re제로부터", "rezero", "re zero", "re0", "리제로"),
        "소드아트온라인" to listOf("소아온", "sao", "sword art online"),
        "진격의거인" to listOf("진격거", "attack on titan", "aot"),
        "원피스" to listOf("one piece", "onepiece"),
        "나루토" to listOf("naruto", "naruto shippuden"),
        "드래곤볼" to listOf("dragonball", "dragon ball", "dbz"),
        "귀멸의칼날" to listOf("귀칼", "demon slayer", "kimetsu"),
        "스파이패밀리" to listOf("spy x family", "spyfamily", "스파이"),
        "체인소맨" to listOf("chainsaw man", "chainsawman", "chainsaw"),
        "나의히어로아카데미아" to listOf("히로아카", "my hero academia", "boku no hero"),
        "호로미야" to listOf("호로미야", "horimiya"),
        "달링인더프랑키스" to listOf("달링", "darling in the franxx", "ditf"),
        "코드기어스" to listOf("code geass", "codegeass"),
        "스타게이트" to listOf("stargate"),
        "아이실드21" to listOf("eyesheild", "eyesheild 21"),
        "원펀맨" to listOf("one punch man", "onepunchman", "opm"),
        "내일의조" to listOf("ashita no joe", "tomorrows joe"),
        "엔드오브에반게리온" to listOf("eva", "neon genesis", "end of eva"),
        "우르세이야츠라" to listOf("urusei yatsura"),
        "시티헌터" to listOf("city hunter"),
        "스튜디오지브리" to listOf("지브리", "ghibli"),
        "슬램덩크" to listOf("slam dunk", "slamdunk"),
        "배가본드" to listOf("vagabond"),
        "이누야샤" to listOf("inuyasha"),
        "하이큐" to listOf("하이큐!!", "haikyu", "haikyuu"),
        "블루록" to listOf("blue lock", "bluelock"),
        "고스트버스터즈" to listOf("ghostbusters"),
        "월희" to listOf("tsukihime"),
        "바람의검심" to listOf("rurouni kenshin", "켄신"),
        "중2병이라도사랑이하고싶어" to listOf("chuunibyou"),
        "로미오와줄리엣" to listOf("romeo and juliet"),
        "사이코패스" to listOf("psycho pass"),
        "마모루동" to listOf("mamorudo"),
        "아기공룡둘리" to listOf("둘리"),
        "진월담월희" to listOf("shingetsutan tsukihime"),
    )

    /**
     * 사전에서 제목에 맞는 별칭 목록을 찾는다.
     * 정규화된 제목이 사전 키 또는 어느 사전의 별칭과 일치/포함되면
     * 그 작품의 모든 별칭(키 포함)을 반환한다.
     * -> 제목이 "Bleach"(영문)든 "블리치"(한글)든 상대 표기로 검색돼도 매칭된다.
     */
    fun aliasesOf(title: String): List<String> {
        val nTitle = normalize(title)
        if (nTitle.isEmpty()) return emptyList()

        for (entry in aliasByTitle) {
            val key = entry.key
            val values = entry.value

            // 제목이 키이거나 키를 포함하거나, 제목이 어느 별칭과 일치/포함하면 이 작품 사전을 적용
            val titleIsEntry =
                nTitle == key || (key.isNotEmpty() && nTitle.contains(key)) ||
                    values.any { v ->
                        val nv = normalize(v)
                        nv.isNotEmpty() && (nTitle.contains(nv) || nv.contains(nTitle))
                    }

            if (titleIsEntry) {
                val merged = buildList {
                    add(key)
                    addAll(values)
                }
                return merged
            }
        }
        return emptyList()
    }

    /** 검색어가 제목에 매칭되는지. */
    fun matches(title: String, rawQuery: String): Boolean {
        val query = rawQuery.trim()
        if (query.isEmpty()) return true

        val nQuery = normalize(query)
        if (nQuery.isEmpty()) return false

        val nTitle = normalize(title)
        if (nTitle.isEmpty()) return false

        // 1) 정규화 부분일치 (대소문자/기호 무시)
        if (nTitle.contains(nQuery) || nQuery.contains(nTitle)) return true

        // 2) 제목 축약어 대응
        val titleAcro = normalize(acronym(title))
        if (titleAcro.isNotEmpty() && (titleAcro.contains(nQuery) || nQuery.contains(titleAcro))) return true

        // 3) 초성 검색
        if (isChosungOnly(nQuery) && nQuery.length >= 2) {
            val titleCho = chosung(title)
            if (titleCho.isNotEmpty() &&
                (titleCho.contains(nQuery) || titleCho.startsWith(nQuery) || nQuery == titleCho.take(nQuery.length))
            ) return true
        }

        // 4) 이명/약어 사전 대조
        for (alias in aliasesOf(title)) {
            val nAlias = normalize(alias)
            if (nAlias.isNotEmpty() && (nAlias.contains(nQuery) || nQuery.contains(nAlias))) return true
        }

        return false
    }
}
