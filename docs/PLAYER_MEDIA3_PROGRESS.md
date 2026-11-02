# S-011: Media3 — что уже работает и как проверено

## Готово и проверено на CPU-эмуляторе (SwiftShader, emulator-5554)

| Что | Проверка | Итог |
|---|---|---|
| Локальный Media3-форк жестов `:doubletapplayerview-media3` (MIT, коммит `507a72b`) | `assembleDebug` | BUILD SUCCESSFUL |
| Media3-зависимости в приложении (`media3-exoplayer/ui/session/hls/dash` 1.4.1) | `:app:assembleDebug`, `:app:dependencies` | резолвятся, сборка зелёная |
| Адаптер источников `PlayerSourceMedia3` | `Media3AdapterPlaybackTest` | 4/4: progressive-кадр, HLS-кадр, 403 → ошибка, метаданные серии (season/episode/playableId) |
| **YouTube-жест на Media3**: двойной тап вправо/влево | `Media3GesturesTest` | 2/2: перемотка вперёд ≥10 с и назад на 10 с от 20 с |
| Регрессия юнит-тестов | `:app:testDebugUnitTest` | зелёные |

Логи: `build/perf/c68/adapter-tests2.log`, `build/perf/c68/gestures3.log`, `build/perf/c68/unit.log`.
Ранняя версия жестового теста падала из-за требования декодированного кадра (`video did not start`) —
жесту кадр не нужен, тест переведён на ожидание STATE_READY; это изменение теста, не кода приложения.

## Что ещё не сделано внутри S-011

1. Сам экран (`PlayerViewFragment`) и `PlayerSource`/`VideoCache`/`MediaDownloadService`/`PlayerExtentions`
   всё ещё на ExoPlayer2 — миграция экрана не выполнена, поэтому в приложении пока работает старый путь.
2. `YouTubeOverlay` проверен только по исходникам форка: его анимационный layout потребовал
   ресурсов библиотеки, и в тесте оверлей заменён прямым контроллером перемотки. Анимацию надо
   проверить уже в приложении после миграции экрана.
3. TV (`TvPlayerFragment`, `f_tv_player.xml`) — отдельный этап.
4. Не проверено: PiP, ориентация/resize, субтитры, аудиодорожки, автопереход серий, ads.
