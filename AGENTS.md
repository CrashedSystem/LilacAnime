# LilacAnime Development Environment

## Build Environment
- **Platform**: Termux (aarch64, Android 15 kernel)
- **Java**: openjdk-17 (`/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk`)
- **Gradle**: 9.5.0 (wrapper)
- **AGP**: 9.3.2
- **Kotlin**: 2.1.0
- **compileSdk**: 37, **targetSdk**: 37, **minSdk**: 23

## SDK Path
```
ANDROID_HOME=/data/data/com.termux/files/home/android-sdk
```

## Key Configuration
- `android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2` (aarch64용 Termux 패키지)
- `android.nonTransitiveRClass=true`
- `org.gradle.daemon=false`
- `buildToolsVersion = "37.0.0"`

## Build Command
```bash
export ANDROID_HOME=/data/data/com.termux/files/home/android-sdk
./gradlew :app:assembleDebug --no-daemon
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Notes
- `app/` 모듈 기준으로 빌드. `lilac_proj/`는 중복 복사본 (무시)
- AGP 9.x에서 `org.jetbrains.kotlin.android` 플러그인 불필요 (AGP 내장)
- `compilerOptions` 사용 (`kotlinOptions` 대신)
- `aapt2` 패키지는 Termux main repo에서 설치 가능
