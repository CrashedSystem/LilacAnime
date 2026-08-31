# LilacAnime Desktop Port (:desktopPort)

`desktopApp`과 별도 디렉터리. 앱 전체 완성 전, 기존 골격을 복사해
**Linkkf 검색 → 에피소드 선택 → 스트림 추출 → mpv 재생** 흐름을 추가한 포팅본.

## 추가된 것 (기존 desktopApp 대비)

- `LinkkfSearch.kt` — 작품 검색 / 상세 / 에피소드(일반·더빙) 선택 UI
- `portdata/` — app/ 의 Android 의존을 제거한 순수 JVM 로직 이식
  - `AnimeModels.kt` (Anime/Episode)
  - `LinkkfClient.kt` (okhttp/jsoup, 재시도 포함)
  - `LinkkfParser.kt` (목록/상세/에피소드 파싱)
  - `AnimeRepository.kt` (카탈로그·상세 조회)

## 실행

```bash
export LILAC_CHROMIUM_PATH=/path/to/chrome   # 없으면 Playwright chromium 설치
mpv            # PATH에 필요
./gradlew :desktopPort:run
```

## 흐름

1. "카탈로그 로드" → Linkkf 전체 목록(최대 25페이지)
2. 작품명 검색(클라이언트 사이드 필터)
3. 작품 선택 → 에피소드 목록 (일반/더빙 탭)
4. 에피소드 클릭 → Playwright로 m3u8+VTT 추출 → mpv 재생

## 미포팅

- Kairan/Csora 자막 (Android Context·DataStore·Zip폰트 결합 심함 → 추후)
- 설정 화면, 즐겨찾기/이어보기 등 (Android 저장 의존)
