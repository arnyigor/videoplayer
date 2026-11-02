# S-011: автопереход на следующую серию после миграции на Media3

## Проверка

`PlayerAutoplayNextTest` на CPU-эмуляторе (SwiftShader):

1. Настоящая `MainActivity`, экран плеера открывается через `NavController` с сериалом из двух
   серий (обе — фикстурный HLS `http://127.0.0.1:8765/test.m3u8`), `seasonIndex=0`, `episodeIndex=0`.
2. Подтверждено: плеер стартует на серии 0 и получает **очередь из 2 MediaItem**
   (`mediaItemCount >= 2`) — то есть серии ставятся как плейлист, а не одна.
3. Первая серия проматывается к концу (`duration - 1200 мс`), и тест ждёт смену
   `currentMediaItemIndex` на 1.
4. Результат: индекс стал **1** — автопереход работает после миграции.

Замечания по ходу проверки (исправления только в тесте, не в коде приложения):
- без ключа `path` в бандле Safe Args падает — ключ добавлен `putString("path", null)`;
- обращения к `Player` вынесены на main-поток, иначе Media3 бросает
  `IllegalStateException: Player is accessed on the wrong thread`.

## Результаты

| Прогон | Итог |
|---|---|
| `PlayerAutoplayNextTest` | PASS (`build/perf/c72-autoplay3.log`) |
| Пакет `presentation.playerview` | **17/17 PASS**, 0 failed (`build/perf/c72-all.log`) |
| Юнит-тесты | BUILD SUCCESSFUL (`build/perf/c72-unit.log`) |

## Остаток по S-011

Не проверены: визуальная анимация оверлея «+10 с», смена ориентации/resize, субтитры,
аудиодорожки, ads, TV-экран после механической миграции.
