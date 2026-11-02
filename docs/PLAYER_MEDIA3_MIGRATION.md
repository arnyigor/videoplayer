# S-011: приложение переведено на Media3 (мобильный плеер)

## Что изменено в этом цикле

- Зависимости: убраны артефакты `com.google.android.exoplayer:exoplayer*` (включая UI, mediasession,
  hls/dash/datasource) и JitPack-версия `com.github.vkay94:DoubleTapPlayerView`; добавлены
  `androidx.media3:media3-exoplayer|ui|session|exoplayer-hls|exoplayer-dash:1.4.1` и локальный модуль
  `:doubletapplayerview-media3` (MIT-форк с поддержкой Media3, те же классы `DoubleTapPlayerView` /
  `YouTubeOverlay`, поэтому XML `f_player_view.xml` не менялся).
- Код: механическая миграция пакетов `com.google.android.exoplayer2.*` → `androidx.media3.*`
  в 9 файлах (PlayerSource, PlayerViewFragment, TvPlayerFragment, VideoCache, MovieDownloadService,
  PlayerExtentions, ApiUtils, интеграционные тесты, layout для TV).
- Точечные адаптации API, которые нельзя сделать заменой импорта:
  - `isControllerVisible()` → свойство `isControllerFullyVisible`;
  - `setControllerVisibilityListener` → `PlayerView.ControllerVisibilityListener`;
  - `MediaSessionConnector` (exoplayer2.ext.mediasession) → `androidx.media3.session.MediaSession`;
  - `ExoPlaybackException.sourceException` → `cause`-цепочка; `SubtitleDecoderFactory` переехал
    в `androidx.media3.exoplayer.text`; `TrackSelectionOverride/Parameters` — в `media3.common`;
    `DecoderReuseEvaluation`/`SeekParameters` — в `androidx.media3.exoplayer`.
- В `app/build.gradle.kts` добавлен opt-in `androidx.media3.common.util.UnstableApi`.

## Проверено

| Проверка | Результат |
|---|---|
| `:app:assembleDebug` (perfIsolated) | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest` | зелёные |
| Один instrumentation-прогон по пакету playerview | **14/14 PASS** (`build/perf/c69/player-tests.log`) |
| Реальный запуск приложения на CPU-эмуляторе | `ExoPlayerImpl [AndroidXMedia3/1.4.1]`, `MediaSessionImpl Init`, кадр (`playbackStarted`), `state=3` (`build/perf/c69/play.log`) |

То есть в приложении теперь **работает Media3**, а жесты берутся из Media3-версии той же
библиотеки: `Media3GesturesTest` (2/2) подтверждает двойной тап вперёд/назад на Media3-движке.

## Что НЕ подтверждено (важно)

1. **Двойной тап на живом экране.** Через `adb shell input tap` (две команды подряд) перемотка
   не измерилась: `dumpsys media_session` показывал только естественный рост позиции, а пауза
   по `KEYCODE_MEDIA_PAUSE` не применялась. Это может быть и ограничением adb-тапов, и тем, что
   существующий `setOnTouchListener`/контроллер экрана перехватывает события. Нужна проверка
   руками или инструментальный тест на самом фрагменте — до этого нельзя утверждать, что жест
   работает именно в экране приложения.
2. `YouTubeOverlay` (анимация круга и «+10 сек») на экране после миграции не проверялся.
3. Не проверено: PiP, смена ориентации/resize, субтитры, аудиодорожки, автопереход серий, ads.
4. TV-экран мигрирован механически (`f_tv_player.xml` → `androidx.media3.ui.PlayerView`), но на
   TV-устройстве/эмуляторе не запускался.
5. Откат: если понадобится вернуть ExoPlayer2, исходники до миграции лежат в `/tmp/backup-main-java`
   (временная копия вне репозитория — для постоянного отката нужен git).
