# PLAY-01 — кнопка «СМОТРЕТЬ»: что проверено и что изменено

План v3 §4 требует не гадать о причине, а пройти цепочку одного клика и запретить молчаливый
no-op. Ниже — фактически проверенное на эмуляторе и сделанные правки.

## 1. Воспроизведение (эмулятор Pixel_5, API 31, `com.arny.mobilecinema.debug`, 2026-10-22)

| Шаг | Наблюдение |
|---|---|
| Home → «История» → «Матрица» (строка с превью) → play | плеер открывается, `ExoPlayerImpl: Init … [ExoPlayerLib/2.19.1]`, `PlaybackState state=3, position=6 532 110` — продолжение с сохранённой позиции |
| Home → карточка «Кэрри» → «СМОТРЕТЬ» | `DetailsFragment: playMovie: ready (movieId=47718, quality=SD, season=0, episode=0)`, затем `ExoPlayerImpl: Init`, воспроизведение стартует |

Вывод: **на текущем состоянии данных исходный дефект «кнопка не реагирует» не воспроизводится**,
плеер и навигация работают. Утверждение из README архива скриншотов («ни перехода, ни записей
плеера в logcat») на этой сборке не подтвердилось.

## 2. Найденные уязвимые места входа (устранены)

Все они давали ровно тот симптом «нажал — ничего не произошло», поэтому закрыты независимо
от того, воспроизвёлся исходный сбой или нет:

1. `playMovie()` был написан как `currentMovie?.let { … }`: при незагруженной карточке нажатие
   молча игнорировалось.
2. `navigateToPlayer()` брал список ссылок и мог получить `null` (`getOrNull(...) ?: firstOrNull()`).
   В этом случае плеер открывался с `path = null`, сам показывал `path_not_found` и сразу уходил
   назад — со стороны выглядело как «кнопка не работает», а логов плеера не было, потому что
   `MediaItem` не создавался.
3. `btnPlay` оставался `enabled` при отсутствии источников: проверка была только
   `!isAutoUpdateRunning || hasLinks`.
4. `findNavController().navigate(...)` вызывался без обработки исключения.
5. Резерв истории: `nav_player_view` без ключа `path` падал с
   `IllegalArgumentException: Required argument "path" is missing` (нашлось при проверке
   перехода «Продолжить» из истории; ключ теперь передаётся всегда).

## 3. Изменения

- `presentation/details/PlayStartResolver.kt` — новый чистый объект: выбор источника
  (HD → SD для фильма, `dash` → `hls` для серии, сортировка сезонов/серий), результат типизирован
  (`PlayStart.Ready` / `PlayStart.Failed(NO_MOVIE|NO_SOURCE)`).
- `DetailsFragment`:
  - `playMovie()` и `navigateToPlayer()` работают через `PlayStartResolver`, при отказе показывают
    сообщение и пишут `Timber.w` с `movieId`/сезоном/серией, при успехе — `Timber.d: playMovie: ready …`;
  - `navigate` обёрнут в try/catch с сообщением `play_error_navigation`;
  - состояние CTA: `Смотреть` / `Подождите` (идёт обновление и ссылок нет) / `Нет источника`;
  - список источников подписывается как «Источник 1 · HD» вместо `Ссылка 1 HD (host/M3U8)`.
- `HistoryFragment` передаёт `path = null` явно — это и есть фикс падения из п. 2.5.
- Удалены мёртвые `toShortLinkLabel()` и прежняя реализация `hasPlayableLinks()`.

## 4. Тесты

`app/src/test/kotlin/.../presentation/details/PlayStartResolverTest.kt` (8 тестов, зелёные):

- `no movie reports failure instead of silence`
- `film without urls reports no source`
- `film prefers hd sources and keeps order`
- `blank urls are ignored`
- `serial episode is picked by sorted position not declaration order`
- `serial episode without links reports no source`
- `selected index out of range falls back to the first source`
- `episode numbers are parsed for ordering`

## 5. Что осталось непроверенным

- Сценарий из README (кнопка «не реагирует») на исходной сборке 1.6.5 не воспроизведён, поэтому
  нельзя утверждать, что причиной был именно один из пунктов §2 — только что эти пути больше
  не могут «молчать».
- Реальный сетевой отказ (`403/404`, битый HLS, offline) для CTA проверен только по коду:
  сообщения и состояния есть, ручной прогон с подменой источника не делался.
- Долгие потоки и производительность (PERF-01) не измерялись: нужен физический девайс и модуль
  macrobenchmark.
