# S-008: цепочка одного клика «Смотреть» (проверено на эмуляторе)

Устройство: `emulator-5554` (AVD Pixel_5_API_30_ARM_Compat, Android 11/API 30, x86_64).
Сборка: `com.arny.mobilecinema.perfdebug` (debug + фикстуры, `-PperfIsolated=true`),
сервер фикстур `127.0.0.1:8765` через `adb reverse tcp:8765 tcp:8765`.
Данные: 1 фильм фикстуры, надпись `2024 • Фильм • Тест`.

Артефакты: `build/manual/chain-home.xml` (каталог), `build/manual/chain-details.xml` (детали),
`build/manual/chain-logcat.txt` (полный лог), `build/manual/chain-stages.txt` (только этапы),
`build/manual/chain-player.png` (кадр на экране).

## Этапы одного клика

| # | Этап | Подтверждение |
|---|---|---|
| 1 | Pointer click по «СМОТРЕТЬ» (`[44,2010][1036,2072]`) | `input tap 540 2040`, кнопка найдена в дампе деталей как clickable |
| 2 | `Button.onClick` → `DetailsFragment.playMovie()` | строка лога через 0 мс после тапа |
| 3 | ViewModel intent | **этапа нет по архитектуре**: кнопка деталей зовёт резолвер напрямую в фрагменте; выдумывать его не нужно |
| 4 | Выбранный источник / качество / `playableId` | `playMovie: ready (movieId=999, quality=HD, season=0, episode=0)` |
| 5 | `resolve URL` | резолвер вернул `PlayStart.Ready`; URL ушёл bunde-ключом `path` в навигацию |
| 6 | `navigate(PlayerRoute)` | `MediaFocusControl: requestAudioFocus() ... PlayerViewFragment`, затем `ExoPlayerImpl: Init 2.19.1` в том же pid 15767 |
| 7 | Player init / MediaItem | `Init 22fbe16 [ExoPlayerLib/2.19.1] [generic_x86_64_arm64, sdk_gphone_x86_64, Google, 30]` |
| 8 | `prepare` → BUFFERING | `state=2` (STATE_BUFFERING) |
| 9 | Декодер видео | `videoFormat 1 mime=video/avc` |
| 10 | Первый кадр | `firstFrame` (новый лог `PlayChain`), на экране — синий кадр фикстуры |
| 11 | READY | `state=3` (STATE_READY) |

Тайминги (настенные, из logcat): клик → `playMovie: ready` 0 мс, → init плеера 0.20 с,
→ BUFFERING 0.61 с, → формат 1.23 с, → первый кадр 1.36 с, → READY 1.55 с.

Примечание: в логе `firstFrame after 10856160 ms` — это `renderTimeMs` (медиа-таймстемп
из ExoPlayer), а не задержка; латентность считается по разнице настенных времён выше.

## Что добавлено в код для проверки

`PlayerViewFragment.kt`: в `AnalyticsListener` добавлены `onRenderedFirstFrame` и
`onVideoInputFormatChanged`, в `Player.Listener` — лог `state=%d`. Всё через `Timber` тегом
`PlayChain`, только для диагностики; поведение не меняется.

## Что этим НЕ установлено

- Исходный пользовательский дефект «кнопка не работает» **не воспроизведён** ни здесь, ни ранее;
  причиной он не доказан. Проверено, что в текущем коде цепочка проходит целиком.
- Проверен один сценарий: один фильм, один источник, HD, Wi-Fi эмулятора. Сериалы (выбор
  сезона/серии), несколько источников, отсутствие источника и мобильная сеть — не в этом прогоне.
- `am_anr` за прогон: 0.
