package com.lilac.anime // 본인의 패키지명에 맞게 수정하세요

import android.app.Application
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
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

        // 1. 다운로드 정보 저장을 위한 데이터베이스.
        //    같은 컨텍스트로 재초기화하는 폴백은 무의미하므로(재시도해도 같은 결과)
        //    단 한 번만 생성한다. 실패 시 원인을 남기고 종료한다.
        databaseProvider = StandaloneDatabaseProvider(this)

        // 외부 저장소를 사용할 수 없으면 내부 저장소로 폴백한다.
        val baseDir = getExternalFilesDir(null) ?: filesDir

        // 2. 다운로드 캐시. NoOpCacheEvictor로 다운로드 파일이 자동 삭제되지 않게 한다.
        downloadCache = buildCache(
            primaryDir = File(baseDir, "lilac_downloads"),
            evictor = NoOpCacheEvictor(),
            dbFactory = { StandaloneDatabaseProvider(this) }
        )

        // 2-1. 스트리밍 재생용 별도 캐시. LRU 512MB로 임시 재생 데이터를 자동 정리한다.
        //      NOTE: media3의 SimpleCache는 캐시당 전용 데이터베이스가 필요하다.
        //      `databaseProvider`를 공유하면 두 캐시가 한 DB의 메타데이터를 서로
        //      덮어써 오염된다. 그래서 스트리밍 캐시는 별도 DatabaseProvider를 쓴다.
        streamingCache = buildCache(
            primaryDir = File(baseDir, "lilac_streaming_cache"),
            evictor = LeastRecentlyUsedCacheEvictor(512L * 1024L * 1024L),
            dbFactory = { StandaloneDatabaseProvider(this) }
        )

        // 3. 403 에러 우회를 위한 HTTP 헤더 설정.
        //    DefaultHttpDataSource.Factory() 생성은 실패하지 않으므로 try/catch가 불필요하다.
        dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
                    "Referer" to "https://play.sub3.top/",
                    "Origin" to "https://play.sub3.top"
                )
            )

        // 4. 다운로드 매니저.
        //    생성 실패는 스레드 수와 무관하게 같은 의존성에서 발생하므로
        //    6->1 스레드 폴백은 아무것도 복구하지 못한다. 실패 시 원인을 남기고 종료한다.
        downloadManager = DownloadManager(
            this,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            Executors.newFixedThreadPool(6) // 동시 다운로드 스레드 수
        )
    }

    /**
     * 캐시 생성. 외부 저장소가 잠시 사용 불가한 경우만 내부 저장소로 재시도한다(복구 가능).
     * 그마저 실패하면 예외를 그대로 던져 실패 상태를 감추지 않고 명확히 드러낸다.
     * 매 SimpleCache는 자체 데이터베이스를 생성해 서로 공유하지 않도록 한다.
     */
    private fun buildCache(
        primaryDir: File,
        evictor: CacheEvictor,
        dbFactory: () -> DatabaseProvider
    ): Cache {
        return try {
            SimpleCache(primaryDir, evictor, dbFactory())
        } catch (e: Exception) {
            Log.w("LilacApp", "CACHE_INIT_FAILED dir=${primaryDir.path}", e)
            SimpleCache(File(filesDir, primaryDir.name), evictor, dbFactory())
        }
    }
}
