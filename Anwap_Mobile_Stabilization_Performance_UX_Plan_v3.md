# Anwap Movies — Mobile stabilization, performance & UX plan v3.0

**Дата:** 2026-10-22  
**Область:** Android Phone и адаптивный мобильный UI на Compose; Android TV не входит в этот этап.  
**Базовое состояние со слов разработчика:** debug 1.6.5, commit `058e1eb` (`origin/master`), `:app:assembleDebug` — OK, 176 unit tests — OK.  
**Материалы:** `Anwap_Mobile_Screens_2026-10-22.zip` — 20 PNG 1080×2340 (примерно 392 dp), Android 13 emulator и README.  
**Ограничение аудита:** скриншоты и отчёт проверены; исходники коммита не получены, runtime-трассы не предоставлены. Причины дефектов ниже — проверяемые гипотезы, а не установленный факт.

---

## 0. Краткий вердикт

1. **P0: кнопка «СМОТРЕТЬ» не реагирует** (экран `19_details.png`): основной сценарий стримингового приложения сломан. Исправить до визуальной полировки.
2. **P0: тормоза интерфейса по всему приложению** (со слов пользователя): измерить в `benchmark` / non-debuggable варианте и на реальном устройстве; затем локализовать точный поток, операцию или компоновку. Не сводить проблему к «Compose медленный».
3. **P1: два входа в поиск** (верхняя иконка с локальным поиском на главной и самостоятельная вкладка «Поиск»), параллельная сортировка на главной и в меню. Убрать дублирование, сохраняя один ViewModel/контракт поиска и сортировки.
4. **P1: смешанная выдача «Везде»** — фильтры работают только по локальной базе, сайт — без них. Разделить состояния источников/секции, пояснить ограничение, не выдавать смешанный список за единообразно отфильтрованный.
5. **P1: история — каталог рейтингов вместо продолжения просмотра**, хотя progress store уже создан: использовать прогресс, номер серии, остаток и последний просмотр.
6. **P1: легаси-экран расширенного поиска зависает, но остаётся в графе**: выяснить обязательность старых deep link / saved state маршрутов, обеспечить редирект, потом удалить неиспользуемые элементы.
7. **P1: несогласованность доказательств**: `10_search_results_all.png` на деле показывает экран настроек. Переснять и автоматизировать проверку screenshot evidence.
8. **P1: огромный коммит**: 150 файлов разной направленности; не делать дальнейших многоцелевых коммитов, выделить независимые задачи и регрессии.

### Что сохранить

- Корневые вкладки: **Главная / Поиск / История / Избранное**.
- Внутри главной: **Все / Фильмы / Сериалы** с полноценным каталогом и пагинацией.
- Отдельный экран поиска с источниками **Везде / В каталоге / На сайте** и bottom sheet фильтров.
- `MobileTheme`, `MobileDimens`, существующие компоненты Compose и общую модель `playableId`.
- Отдельный трек для TV без изменений на текущем этапе.

### Порядок приоритета

`BASE-00` → `PERF-01` + `PLAY-01` (диагностика параллельно) → `PLAY-02` → `PERF-02` → `SRCH-01` → `SORT-01` → `NAV-01` → `HIST-01` → `UI-01` → `MAINT-01` → `QA-01`.

---

## 1. Матрица визуальных наблюдений по 20 скриншотам

| Скриншоты | Подтверждено | Решение |
|---|---|---|
| 01 `home_all` | 4 корневые вкладки; режимы каталога; ряды «Продолжить», «Новинки»; крупные вертикальные карточки продолжения | Сохранить IA, для продолжения сделать 16:9 + реальный прогресс, не терять строку с серией |
| 02 `home_movies` | 2-колоночная сетка; сортировка справа текстом «Сортировка: Новинки»; горизонтальные чипы | Унифицировать иконку/селектор сортировки и жанров, sticky/filter state при возврате |
| 03 `home_series` | Та же схема, отдельный каталог серий | Один `CatalogScreen(mode)` с разными ключами состояния, не копировать UI |
| 04 `home_search_local` | Отдельный search mode в верхнем AppBar, без явного единства с корневым поиском | Оставить одну точку входа — корневую вкладку «Поиск»; при вызове с главной передавать scope=Catalog |
| 05 `home_after_search_collapse` | Возврат переключателя All/Movies/Series после исправления | Регрессионный тест фокуса, состояния и возврата после редиректа поиска |
| 06 `home_overflow` | Белое системное overflow на тёмной теме; Поиск на сайте, Настройки сортировки, Открыть/Импорт, Обновить локальные данные | Убрать поиск и сортировку из overflow, объединить входы URL, обслуживание каталога — в настройки, тёмная тема оставшихся пунктов |
| 07 `add_by_link_invalid` | Локальный HTTP IP заблокирован | Сохранить защиту от SSRF/rebinding/redirect и объяснение ошибки; не ослаблять валидацию для UX |
| 08 `add_by_link_valid` | Предпросмотр «Фильм #1091», кнопки «Сохранить в каталог», «ОТМЕНА», «ОТКРЫТЬ» | Асинхронное разрешение названия и статуса, правильный порядок primary/secondary actions, загрузка/ошибка |
| 09 `search_tab_empty` | Пустой поиск теперь содержит подсказку, три источника и фильтры | Добавить поисковые рекомендации лишь при наличии локальных данных, клавиатура по явному тапу |
| 10 `search_results_all` | **Неправильный файл: вместо поиска сняты настройки** | Переснять, добавить screenshot id в тест evidence |
| 11 `search_source_local` | `matrix` выдаёт локальную «Матрицу» | Сохранить точные title/origTitle и фильтр источника |
| 12 `search_source_remote` | `matrix`: перед несколькими фильмами «Матрица» находится «Секс-файлы: Секс-матрица» | Отдельное ранжирование exact-prefix-token-original, не смешивать простое contains с релевантностью |
| 13 `search_filters` | Bottom sheet: тип, жанр, годы, порядок | Валидация годового диапазона, применение/сброс, сохранение draft отдельно от applied |
| 14 `search_filters_applied` | Значок `Фильтры: 1`; пояснение, что сайт фильтры не поддерживает | В «Везде» разделить локальную и удалённую выдачу с указанием охвата фильтров |
| 15 `history` | Постерная сетка, imdb/thumbs вместо позиции и даты | Новый HistoryRow 16:9, серия, прогресс, lastWatched и Resume |
| 16 `favorites` | Понятное пустое состояние и CTA | Сохранить, добавить корректный маршрут CTA и проверку Back |
| 17–18 `settings` | Debug переключатели Compose и витрины; обновление каталога; PiP, autoplay, amplification | Debug-флаги скрыть в release; update jobs off-main; дифференцировать опасно высокие аудио-усиления |
| 19 `details` | Высокий hero, метаданные, технический URL/«Ссылка 1», кнопка «СМОТРЕТЬ» | Сначала PLAY-01; затем компактный hero, human-readable источник, явный loading/error |
| 20 `sync_check` | Dialog «Обновлений нет» | Учесть error, cancellation, in-progress и последнюю синхронизацию, refresh не подвешивает UI |

---

## 2. BASE-00: сохранить воспроизводимый baseline

**Выполнить перед любым рефакторингом.**

1. Создать рабочую ветку `fix/mobile-stabilization-v3` от `058e1eb`; никогда не делать массовый commit с unrelated файлами.
2. Записать `git status --short`, `git log -3 --oneline`, `git show --stat 058e1eb` и сравнить с предыдущим известным рабочим билдом.
3. Задокументировать текущее сочетание SDK/Kotlin/Compose/Media3/Room/Paging, источники экранов (`Activity`/`Fragment`/`Compose`), навигационные маршруты, кнопку Play и use-case резолва ссылки.
4. Сохранить небольшой **искусственный** тестовый каталог (2 фильма, сериал с 2 сезонами, 4 эпизодами, история/прогресс, дубли по источникам, пустые данные, битые картинки). Не коммитить реальные пользовательские истории, cookies, токены или полные трассы сетевых ответов.
5. Записать зафиксированные сценарии (название фильма, media URI без credentials, device API, user action sequence, состояние сети).
6. Сохранить 20 PNG как CI artifact или внешний архив. Не коммитить 18 МБ изображений в основной Git history.
7. Переснять `10_search_results_all.png` и обновить README — скриншоты должны соответствовать названиям.
8. Отдельно собрать `:app:assembleDebug`, `:app:testDebugUnitTest`, отчёт о фактически пройденных тестах и краткую таблицу ручных проверок.

**Приёмка:** есть один однозначно воспроизводимый baseline, можно сравнить работу приложения до/после каждой правки, незатронутый TV трек не меняется.

---

## 3. PERF-01: инструментировать лаги (P0)

### 3.1. Почему нельзя начинать с «оптимизировать Compose»

На скриншотах нельзя установить частоту кадров или виновный поток. Кроме того, `debug`-билд в эмуляторе имеет повышенные накладные расходы. Нужно сравнить две конфигурации: исходный debug для диагностики и **profileable non-debuggable benchmark** с той же логикой на физическом Android-устройстве. Производительность оценивать прежде всего по второму.

### 3.2. Повторяемые пользовательские сценарии

1. Cold start → главная → первый кадр с текстом → заполнение постерами.
2. Вкладка Главная → Поиск → История → Избранное → Главная (10 циклов).
3. Быстрый scroll 2-колоночного каталога 30 секунд + смена All/Movies/Series.
4. Чередование сортировки и жанров на 3 режимах каталога.
5. Ввод поискового запроса с быстрым изменением `m` → `matrix` → `матрица` и сменой источника.
6. Открыть детали, Back, повторить 10 раз; проверить scroll restoration.
7. Открыть bottom sheet фильтров → изменить → применить → очистить.
8. Запустить update check и проверить, остаётся ли UI отзывчивым.
9. Запуск плеера (после исправления), pause, seek, Back.
10. Повторить с 100 / 1 000 / 10 000 карточек в синтетической базе и с отсутствующими изображениями.

### 3.3. Что измерять и как

| Метрика | Инструмент | Что искать |
|---|---|---|
| FrameOverrun / FrameDuration P50/P90/P95/P99 | Macrobenchmark `FrameTimingMetric` на API31+, Perfetto | Положительный P95/P99 `frameOverrunMs`, ухудшение на прокрутке |
| StartupTime / initial draw | `StartupTimingMetric`, Perfetto | Cold/warm/hot, main thread, decoding |
| Main-thread work | Perfetto, Android Studio System Trace, StrictMode debug | Дисковые операции, Room query, JSON parse, URL decode, bitmap decode, synchronous waits |
| Recomposition | Layout Inspector, Compose Compiler reports | Бесконечные обновления / сверхчастая recomposition крупных деревьев |
| Image decode/cache | image loader events + profiler | Оригиналы загружаются/декодируются в полном разрешении, churn кэша |
| Memory/GC | Memory profiler/Perfetto | Частые GC, memory growth после циклов навигации |
| DB/Paging | временная метрика DAO/SQL/Flow | Full-table sorting, N+1 запросы, повторные запросы каждого row |
| Network | Network Inspector + request IDs | Повторные запросы/парсинг при каждой композиции |
| Navigation latency | `elapsedRealtimeNanos()`/trace section | Задержки onClick→route→visible content |

Рекомендуемые целевые пороги для **выбранного тестового устройства** (проектные, не официальные Android SLA):
- P95 `frameOverrunMs` ≤ 0 мс на главных scroll-сценариях; P99 улучшить минимум относительно baseline, фиксировать выбросы отдельно;
- UI tap → видимая реакция ≤ 100 мс в большинстве сценариев, допускается дальнейшая асинхронная подгрузка;
- нет block main-thread > 50 мс на обычных переходах; отдельные slow tasks фиксировать в Perfetto;
- без систематического роста used heap после 10 циклов навигации и открытия деталей.

**Не использовать абсолютный FPS эмулятора как критерий релиза.** Фиксировать до/после на одинаковом устройстве, температуре, сети, экране и состоянии данных.

### 3.4. Контрольные эксперименты для локализации

На debug-feature-flags, не меняя production архитектуру:

- A: пустой экран без данных; B: экран с синтетическими строками; C: с постерами.
- A: сеть off, база заполнена; B: сеть on; смотреть потери FPS.
- A: обычные изображения; B: заглушки; выявить image path.
- A: исторический старый UI; B: Compose UI — только если обе ветви существуют, без runtime toggle у пользователя.
- A: запрет тяжёлых background sync во время теста; B: обычный режим.
- A: 100 результатов, B: 10 000 — рост времени сортировки и выдачи.

Решение выбирается **по обнаруженному bottleneck**, а не по предположению.

### 3.5. PERF-02: типовые целевые исправления после замеров

- Каталог и поиск: SQL ORDER BY + индекс, page size 20–40, paging; сортировка/фильтры в data/domain слое, не в Composable.
- Lazy layouts: `key = playableId/titleId`, `contentType`, не применять индекс как стабильный ID; однотипные ряды и `LazyVerticalGrid`.
- StateFlow: screen-specific UI state, `stateIn(WhileSubscribed(...))`, `collectAsStateWithLifecycle()`, без повторной коллекции в каждом постере.
- Избавиться от `sort/filter/map` тяжёлого списка непосредственно в композиции; вычислять при изменении данных/фильтра, а не на каждом кадре.
- `remember` / `derivedStateOf` применять там, где доказана нужда; не помечать произвольно все модели `@Immutable` (Strong Skipping уже включён на Kotlin ≥2.0.20).
- Coil: thumbnails по размеру карточки, memory/disk cache, placeholder/error, prefetch умеренный; полноразмерные задники только в деталях.
- Менять объекты состояния, а не мутировать поля без State notification; не пересоздавать большие коллекции на каждом tick прогресса.
- Cancel old coroutines / debounce для запросов и update check; никаких `runBlocking`, парсинга DOM/HTML, синхронного I/O на main.
- Отдельный child composable для интенсивно обновляемого прогресса и таймеров, а не recomposition всего HomeScreen.
- После стабилизации — Baseline Profile на ключевые реальные сценарии.

**Приёмка:** составлен PERF_REPORT с исходными и итоговыми P50/P95/P99, flame/systrace доказательствами причины, фиксируемой разницей на физическом устройстве и отсутствием новых ANR.

---

## 4. PLAY-01: расследование неработающей кнопки «СМОТРЕТЬ» (P0)

### 4.1. Диагностическая цепочка

Нужно проследить каждый этап **одного клика**:

`Pointer click` → `Button.onClick` → `ViewModel intent` → `selected source` → `playableId` → `resolve URL` → `navigate(PlayerRoute)` → `Player init / MediaItem` → `prepare` → `STATE_BUFFERING/READY` → `first frame`.

Приоритет проверки по убыванию:

1. Достигается ли `onClick` (добавить debug-log/trace, проверить enabled, overlay intercept, clickable node semantics)?
2. Приходит ли intent во ViewModel и наблюдается ли event/one-shot effect?
3. Есть ли валидный `playableId` и выбран ли источник; что происходит при одном источнике?
4. Не ранний ли `return` при null URL, отсутствии координат или состоянии `loading`?
5. Не поглощает ли `catch` исключение с пустым телом и без UI error?
6. Если fetch/rewrite URL нужен, почему resolver не возвращает media URI (HLS, redirects, SSL, cookies, expiring links)?
7. Выполняется ли NavController.navigate и поддерживается ли маршрут графом?
8. Плеер инициализирован? Совпадает ли фактический URI с поддерживаемым типом? Не падает ли его initialisation?
9. Нет ли double-click, debounce или stale event collector, который блокирует запуск?

В частности, отсутствие логов плеера **не доказывает**, что проблема в Media3. Она может быть до создания MediaItem и даже до события кнопки.

### 4.2. Минимальный контракт ошибок

Каждый этап должен выдавать типизированный результат: `NoSource`, `InvalidSource`, `ResolveFailed`, `NavigationFailed`, `PlayerError`, `PlaybackStarted`. UI никогда не должен реагировать молчанием.

Для всех переходов в debug использовать correlation ID с **редактированными URL**: не логировать cookies, query tokens и внешние секреты.

```kotlin
// Псевдоконтракт: адаптировать к существующим sealed result / screen effects.
sealed interface PlayStartState {
    data object Idle : PlayStartState
    data object Resolving : PlayStartState
    data class Navigate(val playableId: String) : PlayStartState
    data class Failed(val reason: PlayStartFailure) : PlayStartState
}

enum class PlayStartFailure {
    NO_SOURCE, INVALID_SOURCE, RESOLVE_FAILED,
    NAVIGATION_FAILED, PLAYER_ERROR
}
```

### 4.3. Тесты

- CTA enabled/disabled зависит от валидного playable + source.
- Клик в деталях создаёт **ровно один** PlayRequested effect; двойной тап не открывает два плеера.
- Для фильма и сериала отдельно; серия, сезон, resume из общего стора.
- Один/несколько источников, пустой источник, URL 403/404, redirect, HLS manifest, медленный DNS, offline.
- Navigation: Back возвращает в детали, scroll/detail state сохранён.
- Media3 READY / first frame / error проверять instrumentation fake URL / тестовым медиа в разрешённых условиях.
- Проверить media URL в отладочном окружении, не включать секретные URI в отчёт.

**Приёмка:** нажать «СМОТРЕТЬ» → в течение разумного времени виден loader → открывается плеер и начинается воспроизведение либо появляется понятное сообщение с Retry/Change source. Молчаливый no-op запрещён.

### 4.4. PLAY-02: Media3 без масштабного рефакторинга

1. Сначала устранить дефект входа и подтвердить рабочий end-to-end сценарий.
2. Если текущий плеер уже Media3 — не мигрировать его повторно, а стандартизировать UI и lifecycle.
3. Если не Media3 — завести `PlayerController`/`PlaybackSession` adapter и постепенно заменить engine, сохраняя существующие контракты прогресса и навигации.
4. Для мобильного UI сравнить Media3 `media3-ui-compose-material3:Player` и `PlayerView`-вариант; выбрать по stability, fullscreen, subtitles, PiP, ориентации и поддержке потоков.
5. Player instance управляется жизненным циклом playback session, а не созданием при каждой recomposition.
6. Сохранение прогресса: content position + media/episode canonical id, pause/stop/end; HLS/ads тестировать отдельно.
7. Аудиодорожки, субтитры, seek, autoplay next episode, PiP — после основного рабочего запуска.

---

## 5. SRCH-01: один поиск вместо двух

### 5.1. Новая схема

**Оставить единственный полноценный `SearchScreen`** (корневая вкладка). Верхняя лупа главной должна навигировать туда, с предвыбранным `scope=CATALOG` при необходимости; не создавать отдельный inline search editor поверх главной.

Внутри поиска:
- Одна строка, clear, IME action Search.
- Источник `Везде / В каталоге / На сайте`.
- Быстрые локальные результаты независимо от медленного сайта.
- Bottom sheet «Фильтры» с временным draft и явной кнопкой Apply.
- Понятные Load/Empty/Error/Retry по **каждому** источнику.
- Возврат в ту же вкладку/запрос и предыдущую позицию без обновления при Back.

### 5.2. Неодинаковая поддержка фильтров

Сейчас фильтры относятся к локальному каталогу. В режиме «Везде» не допускается делать вид, что вся выдача одинакова отфильтрована.

Предлагаемое решение: отдельные секции **«В каталоге (фильтры применены)»** и **«На сайте (фильтры недоступны)»**. Если фильтры включены и режим выбран только «На сайте», показывать ненавязчивое пояснение и отключать недоступные параметры, а не silently ignoring.

### 5.3. Релевантность и дедупликация

1. Нормализация Unicode, регистра и пробелов; транслитерация/алиасы только при доказанной необходимости.
2. Приоритет точного title > exact originalTitle > начало названия > совпадение токенов > произвольная подстрока > fuzzy.
3. Для `matrix` проверить оригинальные и локализованные названия, а также `Матрица`, `The Matrix`, `Matrix Reloaded`.
4. Не использовать рейтинг IMDb как главный search rank (он не отражает релевантность запросу).
5. Дедуп только по надёжному каноническому titleId, внешнему ID, нормализованному названию+году+типу с ручной проверкой конфликтов; не удалять разные фильмы с одинаковыми названиями.
6. Отменять предыдущие запросы (`flatMapLatest`/`collectLatest`) и защищаться от устаревших ответов request generation / query token.
7. Серверная пагинация не должна смешивать строки разных запросов/фильтров.

**Тесты:** `matrix`, `матрица`, `The Matrix`, пустая строка, кириллица/латиница, быстрый ввод, сетевой timeout, дубликаты с разными ID, 0/1/1000 результатов, смена scope с сохранением запроса.

### 5.4. Состояние поиска — предлагаемая модель

```kotlin
// Концепт, не привязан к текущим именам классов.
data class UnifiedSearchState(
    val query: String = "",
    val source: SearchSource = SearchSource.ALL,
    val draftFilters: CatalogFilters = CatalogFilters(),
    val appliedFilters: CatalogFilters = CatalogFilters(),
    val local: ResultSection = ResultSection.Idle,
    val remote: ResultSection = ResultSection.Idle,
    val pendingRequestId: Long = 0L,
)
```

Не надо одновременно держать 3 отдельных ViewModel для одного запроса (inline home, local and global), пока нет убедительной причины.

---

## 6. SORT-01: каталог и сортировка

### Сейчас

- На `02/03` сортировка оформлена правой текстовой ссылкой `Сортировка: Новинки`.
- В overflow `06` есть отдельные «Настройки сортировки».
- В поиске уже реализована иконка и отдельный bottom sheet.

### Как сделать

1. Оставить **одну** видимую кнопку с иконкой `sort`, подписью выбранного режима и числом/индикатором применённых фильтров.
2. На `Главная → Фильмы/Сериалы` показывать эту кнопку над сеткой; в `Все` сортировку каталога не показывать.
3. Вызывать **общий `CatalogSortSheet`** (без новой реализации логики). Опции: Новинки, Рейтинг, Название; алгоритмы сортировки должны быть определены однозначно.
4. Сортировку и жанровые фильтры **хранить отдельно по режимам** `MOVIE` и `SERIES`. При возврате из деталей сохранять scroll и selection.
5. Сортировать в одном data/domain слое, желательно SQL `ORDER BY` + индексы, не сортировать весь список на UI thread.
6. Смена сортировки в каталоге сбрасывает только paging позицию каталога, но не глобальную ленту «Все» и не пользовательский прогресс.
7. Убрать пункт «Настройки сортировки» из overflow, заменить им явный вход к каталогу если нужно.

**Приёмка:** три режима сортировки одинаково работают в обоих каталогах, отображается текущая сортировка, она сохраняется при Back, обновления не блокируют UI; `ComposeTestRule` проверяет selection/semantics.

---

## 7. NAV-01: убрать параллельные маршруты и дубли

- Навигация корня не меняется: `Home / Search / History / Favorites`.
- Главная хранит три внутренних режима `All/Movies/Series` с независимым scroll/page/filter state.
- Верхняя лупа → `SearchTab`, не включает другой встроенный режим поиска.
- Детали → Player по типизированному маршруту, а не через поиск URL/ID в интерфейсе.
- Старый `AdvancedSearch` оставить как временный legacy destination **только если** на него есть saved state, internal links или deep links. Вход — redirect на `SearchTab(filtersExpanded=true)`; после тестов совместимости удалить.
- `Open link` и `Import link` → единый диалог с разными явно обозначенными операциями; не менять реальные сценарии импорта без аудита.
- «Обновить локальные данные» и тяжёлые проверки → Настройки → Каталог и обновления.
- Тестовые Compose switches скрыть вне debug Developer Settings; после миграции удалить obsolete UI paths.

**Тест Back-stack:** Home(filter=Series, sort=Rating, scroll=30) → Search(query=matrix) → Details → Back → Search(state retained) → Home(scroll and sort retained) → History → Favorites → Home; ни один экран не переходит неожиданно в старый поиск.

---

## 8. HIST-01: история и продолжение просмотра

Постерная сетка `15_history.png` сохраняет визуал каталога, но не отвечает на главный вопрос: **на чём остановился пользователь?**

- Сменить на `HistoryRow`: горизонтальное превью 16:9; название; `S01E17` для сериалов; осталось минут; последний просмотр; progress bar.
- Канонический ID для резюме — `playableId`, только он определяет эпизод, а не index в текущем списке.
- Позиция/длительность — из общего `PlaybackProgressStore`, а не из разрозненной истории.
- Обновлять прогресс в UI при resume из плеера, без долгой полной перезагрузки экранов.
- Различать «Закончено», «Продолжить», «Ещё не начинали» и неизвестную длительность.
- Удаление из истории не должно безусловно удалять `playback_progress`; требования и UI-кнопки нужно разделить.
- В меню контекста: Продолжить, Открыть детали, Удалить из истории.
- Пустые/удалённые из каталога эпизоды: graceful fallback, а не переход в никуда.

Тесты `resume`: фильм, сериал S02E03, смена season order, pause/exit, app restart, прогресс 0/duration unknown, конец, реклама/промежуточные куски HLS, offline.

---

## 9. UI-01: переработка Mobile Compose без TV изменений

### 9.1. Главная
- Общий AppBar: краткий бренд, лупа (маршрут Search), настройки; убрать дублирующий overflow поиска и сортировки.
- В `Все`: первым полезным блоком «Продолжить просмотр» при наличии данных, карточки 16:9 с прогрессом; дальше новинки/подборки.
- В `Фильмы`/`Сериалы`: компактная единая строка сортировки + жанры + grid с реальной ленивой пагинацией.
- Верхние режимы достаточно крупные, не дублировать корневую нижнюю навигацию.

### 9.2. Детали
- Сократить hero до разумной высоты, чтобы CTA и важные метаданные были доступны без прокрутки на компактном устройстве.
- Метаданные компактнее; технический «Ссылка 1 HD (o1…/M3U8)» показывать как «Источник 1 · HD» с деталями по отдельному действию.
- CTA «Смотреть»/«Продолжить с ...» с disabled/loading/error; **только после PLAY-01**.
- Для сериалов: сезоны/эпизоды, номер серии, прогресс, следующий эпизод; каждое действие с canonical `playableId`.

### 9.3. Поиск
- Поле на ширину экрана + IME search; результаты компактными строками с thumbnail и 2 строками метаданных.
- Активный источник и число фильтров видимы, ошибки сайта и базы различаются.
- Никаких вторых экранированных поверхностей встроенного поиска главной.

### 9.4. Темы и системные компоненты
- Светлый overflow на `06` заменить `DropdownMenu` в той же Material3 схеме либо временно явно стилизовать старый Popup; не смешивать необоснованно View AlertDialog с Compose темой.
- Токены: `background #0B0C12`, `surface #191B25`, `primary #B6A0FF`, `onSurface #F5F5FA`, `secondary #B8BBC9` (стартовые значения, проверить контраст).
- Кликабельные области ≥48dp, Large Font 1.3/2.0, TalkBack, edge-to-edge, landscape, 320/360/393/412 dp, tablet 600dp+.
- Иконки: одинаковый визуальный вес и расположение, один стиль selected.
- Стабильные `key` и `contentType` в Lazy lists. Не принуждать полный `ContentScale.FillBounds` постеров.

### 9.5. Обслуживание каталога
- Для проверки/импорта обновлений — состояния Idle/Checking/Found/Downloading/Applying/Done/Error/Cancelled.
- Длительные операции выполняются вне main thread (в WorkManager, если должны переживать закрытие процесса; иначе lifecycle-bound и с явной отменой).
- Прогресс, дата синхронизации, отмена и повтор, транзакционная запись в Room, отсутствие дубликатов.
- Опасные настройки вроде усиления звука до «100000%» ограничить/валидировать и объяснять; нельзя включать экстремальный уровень без явного предупреждения.

---

## 10. MAINT-01: очистка legacy без потери данных

Перед удалением: составить список всех маршрутов/экранов/feature flags/старых managers/обработчиков, их callers, сохранённые preferences, экспорт/импорт, источники URL и совместимость deep link. Затем:

1. Доказать новый SearchScreen покрывает старые функции фильтрации и поиска.
2. Заменить старые navigation targets на типизированные redirects.
3. Перевести настройки сортировки/импорта/обновления на единый набор use cases.
4. Измерить, сколько действительно удалено duplicate handlers, requests, observers.
5. Удалять устаревшие экраны/flags только после instrumentation/regression tests.
6. Не удалять данные Room при миграции UI. Room schema change — отдельный PR с migration tests.

---

## 11. План тестирования (конкретные проверки)

### Unit

- [ ] `play_click_routes_to_player_exactly_once`
- [ ] `play_missing_source_reports_error`
- [ ] `playableId_for_unordered_episodes_is_canonical`
- [ ] `resume_reads_shared_progress_store`
- [ ] `search_scope_switch_keeps_query`
- [ ] `search_new_request_cancels_old`
- [ ] `search_filters_apply_only_to_supported_source`
- [ ] `search_results_dedupe_preserves_distinct_remakes`
- [ ] `search_rank_exact_title_above_substring`
- [ ] `sort_does_not_mix_movie_series_modes`
- [ ] `catalog_paging_invalidates_on_filter_change`
- [ ] `back_restores_scroll_and_sort`
- [ ] `history_displays_episode_and_remaining_time`
- [ ] `url_validator_blocks_private_ip_and_rebinding_redirects`
- [ ] `catalog_check_failure_preserves_existing_catalog`

### Instrumentation / Compose UI

- [ ] Нажать «СМОТРЕТЬ» на деталях — UI показывает loading / Player / visible error.
- [ ] Отдельный SearchTab работает с локальным, remote, mixed source, корректной клавиатурой.
- [ ] Лупа главной открывает root SearchTab, не старый home-inline search.
- [ ] Sorting control открывает тот же selector на Films/Series; overflow не дублирует.
- [ ] 4 нижние вкладки работают при многократных переходах, нет duplicate destinations.
- [ ] Фильтры применяются/сбрасываются и корректно маркируют remote неполную поддержку.
- [ ] История открывает правильную серию и кнопку Resume.
- [ ] Favorites CTA открывает поиск и Back корректно возвращает.
- [ ] Показ загрузки и диалогов не тормозит остальные вкладки.
- [ ] Легаси deep links → новый search или понятная ошибка; не показывать спиннер 25 секунд.
- [ ] Тёмное overflow/menu; длинные заголовки/крупный шрифт; keyboard insets.

### Macrobenchmark / profile

- [ ] `coldStart` в profileable non-debuggable варианте.
- [ ] `scrollHome` с изображениями, `scrollCatalog` 1000 карточек, `switchTabs`, `toggleSort`.
- [ ] `searchAndFilter`, `openDetailsBack`, `openPlayer`.
- [ ] P50/P95/P99 frameDuration/frameOverrun до и после.
- [ ] Профили на **физическом** бюджетном и среднем устройстве; debug emulator только для функциональных воспроизведений.

### Матрица устройств и состояний

| Ось | Значения |
|---|---|
| Android | API 26/29/31/33/35/targetSupported (по minSdk проекта) |
| Экран | 320, 360, 393, 412 dp, tablet 600+ dp |
| Шрифт | 100%, 130%, 200% |
| Навигация | Жесты и 3 кнопки |
| Сеть | Wi-Fi, slow, offline, timeout, HTTP 403/404, broken HLS |
| Данные | 0 / 1 / 100 / 1000+ тайтлов, null poster, 2+ серии одинакового имени |
| Lifecycle | Rotation, background/foreground, kill process, restore, PiP только после основного плеера |

### Проверка screenshot evidence

После финальной сборки переснять те же 20 экранов, но снимки должны **валидировать маршрут** — route/title/testTag, запрос/scope/filters должны совпадать с README. `10_search_results_all.png` переснять обязательно. Не выдавать успешный screenshot capture за подтверждение функциональности скрытых действий.

---

## 12. Предлагаемые коммиты и gates

| Коммит | Scope | Gate перед следующим |
|---|---|---|
| `BASE-00` | Тестовые фикстуры, baseline отчёт, screenshot manifest | Текущие 176 tests + assemble, без production изменений |
| `PERF-01` | Instrumentation/benchmark + trace flags | Метрики получены, bottleneck классифицирован |
| `PLAY-01` | Исправить вход из CTA и типизированные ошибки | UI integration + ручной playback, без silent no-op |
| `PLAY-02` | Локальные Media3/lifecycle исправления, если нужны | Progress/resume, playlist, Back, HLS tests |
| `PERF-02A/B` | Только доказанные оптимизации data/UI/image | Benchmark P95/P99 лучше baseline, без regressions |
| `SRCH-01` | Унификация маршрутов поиска и ranking | Scope tests, query cancellation, no duplicates |
| `SORT-01` | Общая сортировка каталога/поиска | Independent mode state + paging + reset tests |
| `NAV-01` | Back stack + legacy redirect | Compose navigation tests |
| `HIST-01` | HistoryRow и общий прогресс | Episode resume + progress integration |
| `UI-01A/B` | UI polish, dark menus, responsive details | Screenshot diffs, accessibility |
| `MAINT-01` | Удаление proven-dead legacy после тестов | No broken deep links/preferences |
| `QA-01` | Full QA, CI, baseline profile | Release-grade sign-off |

Каждый коммит: ограниченный diff → `:app:assembleDebug` → targeted unit → all unit → instrumentation по изменённому сценарию → сравнение screenshot → PERF smoke (если UI/layout/data). Не фиксировать одновременно unrelated Android TV / CI / shared data refactor.

---

## 13. Конкретные команды и артефакты

```bash
# Замер исходного состояния
 git status --short
 git log -3 --oneline
 git show --stat 058e1eb

# Сборка и юниты
 ./gradlew :app:assembleDebug :app:testDebugUnitTest

# Реальные UI-тесты — только если настроен instrumentation runner
 ./gradlew :app:connectedDebugAndroidTest

# Macrobenchmark — пример при условии, что добавлен соответствующий модуль
 ./gradlew :macrobenchmark:connectedCheck

# Main thread / trace для воспроизводимого сценария
 adb shell pidof com.arny.mobilecinema.debug
 adb logcat -c
 adb logcat -v time | tee playback_repro.log
```

Пути Gradle-модулей и package могут отличаться — подтвердить по репозиторию. Для безопасных логов отфильтровать токены, cookies, локальные пути и конфиденциальные URL **до** сохранения/передачи.

**Для отчёта после каждого этапа:**
- что изменено (files, diff, SHA);
- что обнаружено по исходникам/трассе, что остаётся гипотезой;
- какие команды запускались, сколько тестов реально прошло;
- что проверено на эмуляторе и на физическом устройстве;
- до/после performance values и условия измерения;
- одно предложение о следующем минимальном commit.

---

## 14. Stop conditions / Definition of Done

**Нельзя считать мобильный трек готовым, пока:**

- «СМОТРЕТЬ» на реальном фильме не открывает плеер или не сообщает явную ошибку;
- UI продолжает регулярно зависать на типовых сценариях в non-debuggable benchmark/реальном устройстве;
- поиск на главной и поисковая вкладка используют разные неочевидные механизмы;
- сортировка фильмов/сериалов имеет разные независимые обработчики и разные правила;
- контент после Back теряет важные пользовательские параметры (scroll/sort/scope);
- история показывает только IMDb и не умеет resume конкретной серии;
- старый AdvancedSearch может быть открыт и бесконечно висеть;
- screenshot evidence не соответствует его manifest;
- TV/shared бизнес-логика неожиданно изменена без целевого теста.

**Можно считать этап завершённым**, когда playback end-to-end работает, все новые контрактные тесты зелёные, `assemble`/unit/instrumentation и performance regression пройдены, а приложение на устройстве воспринимается отзывчивым без систематических frame overruns.

---

## 15. Актуальные технические источники

- Android Compose performance: https://developer.android.com/develop/ui/compose/performance
- Diagnose Compose stability: https://developer.android.com/develop/ui/compose/performance/stability/diagnose
- Compose lazy performance: https://developer.android.com/develop/ui/compose/performance/bestpractices
- Macrobenchmark setup: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- FrameTimingMetric: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics
- Media3 Compose UI: https://developer.android.com/media/media3/ui/compose
- Media3 UI customization: https://developer.android.com/media/media3/ui/compose-customization
- Compose Paging: https://developer.android.com/topic/libraries/architecture/paging/v3-overview
- Insets: https://developer.android.com/develop/ui/compose/system/insets

**Приоритет для ближайшего цикла:** baseline + трейс lag + «СМОТРЕТЬ». После доказанной стабилизации — поиск/сортировка, история, UI polish. Без переноса внимания на Android TV.
