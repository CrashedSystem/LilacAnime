package com.lilac.anime.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class LinkkfClient {

    private val client =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    fun getDocument(url: String): Document {

        val request =
            Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10) " +
                        "AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) " +
                        "Chrome/120.0 Mobile Safari/537.36"
                )
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml"
                )
                .header(
                    "Accept-Language",
                    "ko-KR,ko;q=0.9,en;q=0.8"
                )
                .build()

        client
            .newCall(request)
            .execute()
            .use { response ->

                if (!response.isSuccessful) {
                    throw Exception(
                        "HTTP ${response.code}"
                    )
                }

                val html =
                    response.body?.string()
                        ?: throw Exception(
                            "응답 본문이 없습니다."
                        )

                return Jsoup.parse(
                    html,
                    url
                )
            }
    }
}