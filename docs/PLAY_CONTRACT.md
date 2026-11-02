# S-009: контракт ошибок запуска воспроизведения

## Типизированные результаты по этапам

| Этап цепочки | Тип | Где формируется | Что видит пользователь |
|---|---|---|---|
| Карточка не загружена | `PlayStartFailure.NO_MOVIE` | `PlayStartResolver.resolve` | тост «Данные тайтла ещё не загружены…» |
| Нет источников | `PlayStartFailure.NO_SOURCE` | `PlayStartResolver.resolve` | тост «У этого тайтла нет доступного источника…» |
| Адрес есть, но не абсолютный http(s) | `PlayStartFailure.INVALID_SOURCE` (новое) | `PlayStartResolver.resolve` — проверка `PlayTrace.isPlayableUrl` | тост «Адрес источника некорректен…» |
| Источник выбран | `PlayStart.Ready(source)` | `PlayStartResolver.resolve` | кнопка ведёт в плеер |
| Навигация упала | исключение в `navigateToPlayer` | `DetailsFragment.navigateToPlayer` | тост «Не удалось открыть плеер» + лог `navigation failed` |
| Плеер отдал ошибку | `PlaybackException` | `PlayerViewFragment.onPlayerError` | тост с причиной/типом сети, затем `retryOpenCinema` |
| Воспроизведение пошло | `PlaybackStarted` | `PlayerViewFragment.onRenderedFirstFrame` (лог `playbackStarted`) | кадр на экране |

Молчаливых ветвей нет: каждый `return` из обработчика клика предваряется тостом, а ошибки
плеера и навигации логируются как ошибки.

## Correlation ID и редактирование URL

- `PlayTrace.nextId()` — идентификатор клика (`p` + время + счётчик), передаётся в плеер
  аргументом `playId`, поэтому все этапы одного клика видны в логах по одному префиксу.
- `PlayTrace.redactUrl` вырезает query, fragment и userinfo; неизвестная схема превращается
  в `<no-scheme>`, пустая строка — в `<empty>`. Токены и cookie-подписи в логи не попадают.
- Тег логов — `PlayChain`, уровень debug для этапов и error для отказов.

## Проверка (эмулятор, perfdebug, фикстуры)

Реальный клик дал непрерывную цепочку с одним ID:

```
psora49 click
psora49 resolve ready (movieId=999, quality=HD, season=0, episode=0)
psora49 navigate playableId=999 quality=HD url=http://127.0.0.1:8765/test.mp4
psora49 state=2
psora49 videoFormat 1 mime=video/avc
psora49 playbackStarted (render 11409858 ms)
psora49 state=3
```

Полный лог — `build/manual/chain2-logcat.txt`, выжимка — `build/manual/chain2-stages.txt`.
ANR за прогон: 0.

Юнит-тесты контракта: `PlayTraceTest` (6 тестов: редактирование query/userinfo/пустых/
без-схемы, `isPlayableUrl`, уникальность ID) и `PlayStartResolverTest` (добавлен случай
относительного адреса → `INVALID_SOURCE`). Всего 198 тестов, 0 падений.

## Что не проверено

- Ветки `NO_MOVIE`/`NO_SOURCE`/`INVALID_SOURCE` подтверждены юнит-тестами, но не живым
  кликом на устройстве: в фикстурах нет тайтла без источников и с битым адресом.
- Ошибка плеера (`PlayerError`) не воспроизводилась: фикстурный поток исправен.
- Редактирование URL в логах проверено тестами; в живом логе query отсутствовал.
