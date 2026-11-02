# S-011: выбор UI на этапе постепенной миграции

## Подтверждённое состояние

Engine ExoPlayer2.19.1, не Media3. Mobile XML f_player_view.xml использует сторонние DoubleTapPlayerView и YouTubeOverlay; TV — StyledPlayerView. PlayerViewFragment содержит управление ориентацией, resize, PiP, контролами и треками. PlaybackSession введён c65, reuse/release покрыты unit-тестами; c66 и последующая session-resume проверка подтвердили кадр до и после Home.

## Сравнение вариантов (архитектурное, не runtime A/B)

| Критерий | View-based вариант | Новый Compose Player |
|---|---|---|
| Стабильность существующего UI | Сохраняет Fragment/XML lifecycle и известные контролы | Требует переноса состояния/жестов/оверлеев; совместимость текущего Compose BOM2024.06.00 не проверена |
| Fullscreen/ориентация | Существующие обработчики можно сохранить | Нужна отдельная интеграция с Activity/insets, не доказана |
| Subtitles | Существующий engine/track-selector путь сохраняется до миграции | API и отображение не проверены |
| PiP | Интеграция Fragment/Activity уже есть; runtime после адаптера ещё не проверен | Нельзя считать замену composable готовой PiP интеграцией |
| Поддержка потоков | HLS/MP4 подтверждены на текущем engine | Зависит от engine/source factory, не от Compose как такового |
| Риск миграции | Третьесторонний DoubleTapPlayerView типизирован старым ExoPlayer; Media3 не drop-in замена | Одновременно меняет engine и UI; выше объём регрессии |

Решение: на первом этапе сохранять View-based UI, постепенно заменить engine и совместимые view/overlay-границы за PlaybackSession. Не вводить Compose Player одновременно с engine. Это выбор по рискам проекта, НЕ утверждение, что Compose Player хуже или медленнее.

## Что не подтверждено

Попытка прочитать официальную страницу https://developer.android.com/media/media3/ui/compose завершилась ошибкой инструмента (axios/browser extraction failed). Поэтому наличие/точная сигнатура media3-ui-compose-material3:Player, актуальная версия и требования к SDK/Compose здесь не утверждаются. Зависимость не добавлялась и не разрешалась; runtime сравнения двух Media3 UI нет. Перед добавлением любой новой зависимости нужны resolution+минимальная сборка.

S-011 остаётся открытым: Media3 engine ещё не подключён, TV compatibility, PiP, position preservation при lifecycle переходах и ads не подтверждены. Данный документ не заменяет эти проверки и не является вторым планом.
