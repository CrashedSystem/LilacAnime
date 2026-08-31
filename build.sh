#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# LilacAnime 빌드 + 다운로드 폴더로 자동 복사 스크립트
# 사용법: ./build.sh

export ANDROID_HOME=/data/data/com.termux/files/home/android-sdk

APP_NAME="LilacAnime"
APK="app/build/outputs/apk/debug/app-debug.apk"
DEST_DIR="$HOME/storage/downloads"
VERSION="0.2.1"

echo "==>[1/2] APK 빌드 시작"
./gradlew :app:assembleDebug --no-daemon

if [ ! -f "$APK" ]; then
    echo "!! APK 생성 실패: $APK 없음" >&2
    exit 1
fi

echo "==>[2/2] 다운로드 폴더로 복사"

if [ ! -d "$DEST_DIR" ]; then
    echo "!! 공유 저장소 접근 불가. termux-setup-storage 실행 후 재시도" >&2
    exit 1
fi

DEST="$DEST_DIR/${APP_NAME}-v${VERSION}-debug.apk"
cp "$APK" "$DEST"
echo "==> 완료: $DEST"
echo "==> 크기: $(du -h "$DEST" | cut -f1)"
