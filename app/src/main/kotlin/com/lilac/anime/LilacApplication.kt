package com.lilac.anime // 본인의 패키지명에 맞게 수정하세요

import android.app.Application
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
class LilacApplication : Application() {

    // 앱 어디서든 다운로드 매니저와 캐시에 접근할 수 있도록 Companion Object로 선언
    companion object {
        lateinit var databaseProvider: DatabaseProvider
        lateinit var downloadCache: Cache
        lateinit var streamingCache: Cache
        lateinit var dataSourceFactory: HttpDataSource.Factory
        lateinit var downloadManager: DownloadManager
    }

    override fun onCreate() {
        super.onCreate()

        // 각 초기화는 독립적으로 try/catch로 감싼다. 어느 단계가 실패해도
        // 다음 단계로 진행해 모든 접근자를 항상 초기화 상태로 유지한다.
        // 이렇게 하지 않으면 초기화 도중 예외가 발생했을 때 나머지 lateinit
        // 필드가 미초기화로 남아 UninitializedPropertyAccessException을 일으킨다.

        // 1. 다운로드 정보 저장을 위한 데이터베이스
        databaseProvider = runCatching { StandaloneDatabaseProvider(this) }
            .getOrElse { StandaloneDatabaseProvider(applicationContext) }

        // 외부 저장소를 사용할 수 없으면 내부 저장소로 폴백해 시작 시 크래시를 막는다.
        val baseDir = getExternalFilesDir(null) ?: filesDir
        val cacheDir = File(baseDir, "lilac_downloads")

        // 2. 영상 조각들이 실제로 저장될 캐시 폴더 설정
        downloadCache = try {
            SimpleCache(
                cacheDir,
                NoOpCacheEvictor(), // 다운로드된 파일이 자동으로 삭제되지 않도록 설정
                databaseProvider
            )
        } catch (e: Exception) {
            Log.w("LilacApp", "DOWNLOAD_CACHE_INIT_FAILED", e)
            SimpleCache(
                File(baseDir, "lilac_downloads_" + System.currentTimeMillis()),
                NoOpCacheEvictor(),
                databaseProvider
            )
        }

        // 2-1. 스트리밍 재생용 별도 캐시. 재생은 LRU로 오래된 조각을 자동 정리해
        //     임시 재생 데이터가 디스크를 무한히 채우지 않도록 한다.
        val streamingCacheDir = File(baseDir, "lilac_streaming_cache")
        streamingCache = try {
            SimpleCache(
                streamingCacheDir,
                LeastRecentlyUsedCacheEvictor(512L * 1024L * 1024L), // 최대 512MB
                databaseProvider
            )
        } catch (e: Exception) {
            Log.w("LilacApp", "STREAMING_CACHE_INIT_FAILED", e)
            SimpleCache(
                File(baseDir, "lilac_streaming_cache_" + System.currentTimeMillis()),
                LeastRecentlyUsedCacheEvictor(512L * 1024L * 1024L),
                databaseProvider
            )
        }

        // 3. 403 에러 우회를 위한 HTTP 헤더 설정 (기존 플레이어에 넣었던 것과 동일)
        dataSourceFactory = try {
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(
                    mapOf(
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
                        "Referer" to "https://play.sub3.top/",
                        "Origin" to "https://play.sub3.top"
                    )
                )
        } catch (e: Exception) {
            Log.w("LilacApp", "DATASOURCE_INIT_FAILED", e)
            DefaultHttpDataSource.Factory()
        }

        // 4. 다운로드 매니저 초기화
        downloadManager = try {
            DownloadManager(
                this,
                databaseProvider,
                downloadCache,
                dataSourceFactory,
                Executors.newFixedThreadPool(6) // 동시 다운로드 스레드 수
            )
        } catch (e: Exception) {
            Log.w("LilacApp", "DOWNLOAD_MANAGER_INIT_FAILED", e)
            DownloadManager(
                this,
                databaseProvider,
                downloadCache,
                dataSourceFactory,
                Executors.newFixedThreadPool(1)
            )
        }
    }
}