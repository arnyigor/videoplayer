# План доработки по ТЗ «Android TV + Phone / Tablet UX/UI v2.0»

Дата: 2026-10-20. База: `8461646` (v1.6.5).
ТЗ: [Android_TV_Android_Phone_Netflix_UX_UI_Spec_v2_0.md](Android_TV_Android_Phone_Netflix_UX_UI_Spec_v2_0.md) (v2.0, 1386 строк, сохранён в `docs/`)
— рассматривается как обязательное ТЗ, а не как набор рекомендаций. Анализ разрывов: [TV_SPEC_V2_ANALYSIS.md](TV_SPEC_V2_ANALYSIS.md).

Документ описывает: целевую архитектуру, механику перехода на Compose for TV, модель данных и прогресса,
плеер, экранные планы, фазы, бэклог задач, тест-план, риски и порядок релизов.

---

## Содержание

0. [Принципы и рамки](#0-принципы-и-рамки)
1. [Целевая архитектура](#1-целевая-архитектура)
2. [Переход на Compose for TV](#2-переход-на-compose-for-tv)
3. [Данные, прогресс, следующая серия](#3-данные-прогресс-следующая-серия)
4. [Состояние экранов и восстановление фокуса](#4-состояние-экранов-и-восстановление-фокуса)
5. [Плеер](#5-плеер)
6. [Экранные планы](#6-экранные-планы)
7. [Мобильная часть](#7-мобильная-часть-часть-ii-тз)
8. [Фазы](#8-фазы)
9. [Бэклог задач](#9-бэклог-задач)
10. [Тест-план](#10-тест-план)
11. [Риски и меры](#11-риски-и-меры)
12. [Релизная стратегия](#12-релизная-стратегия)
13. [Что не входит в план](#13-что-не-входит-в-план)
- [Приложение A. Проверенные версии](#приложение-a-проверенные-версии-и-источники)
- [Приложение B. Трассировка ТЗ → задачи](#приложение-b-трассировка-тз--задачи)

---

## 0. Принципы и рамки

1. **Каждый этап заканчивается рабочим приложением.** Никаких «полгода рефакторинга, потом релиз».
2. **Сначала данные и корректность, потом пиксели.** Пока нет `playableId/duration/completed`, любые
   красивые карточки «Продолжить S02E05 · 19:26» будут врать.
3. **Leanback не дорабатывается сверх необходимого.** Всё, что можно сделать один раз в Compose, делается в Compose.
   В Leanback остаются только дешёвые исправления дефектов (пиксели, строки, back, баннер).
4. **Один экран — один вертикальный срез.** Миграция экрана = смена базового класса фрагмента + Compose-контент,
   навигационный контракт (`tv_nav_graph.xml`, SafeArgs) не меняется.
5. **TV не регрессирует из-за мобильной части.** Мобильный UI переписывается отдельным этапом (D4).
6. **Проверка — сборка + unit-тесты + ручной прогон по матрице.** Эмулятор в рамках подготовки плана не запускается;
   все runtime-проверки вынесены в этапы реализации.

Ограничения среды: один разработчик, локальная сборка, публикация через GitHub Releases + AAB.
Оценки — в человеко-днях (1 день = 6 продуктивных часов), без буфера; в конце даётся буфер +25%.

### 0.1. Первые шаги (можно начать без эмулятора)

1. `FND-06` + `CI-01`: тест-харнесс и сборка/тесты на каждый push — чтобы дальнейшие изменения были проверяемы.
2. `DOM-01`…`DOM-03`: `PlayableId`, доменный прогресс, Room v8 (это разблокирует все экраны).
3. `DOM-05`/`DOM-06`: resolver и policy с тестами — до любого UI.
4. `TST-02`: зафиксировать сценарии следующей серии и завершения.
5. `CMP-01`/`CMP-02`: включить Track A и проверить focus-API на минимальном экране.
6. Только после этого — `CMP-03`/`CMP-04` (тема и компоненты) и `CMP-05` (Home).

---

## 1. Целевая архитектура

### 1.1. Модули: решение D1

Сейчас: один `:app` (199 Kotlin-файлов), внутри — data/domain/presentation и два независимых плеера.
ТЗ §26.1 предлагает `app-tv` + `app-mobile` + core-модули. Для текущего продукта это преждевременно:

- один `applicationId` (`com.arny.mobilecinema`), одна подпись, один CI и одна БД (`Movies`);
- `UpdateService`/`MovieDownloadService`, настройки и кэш общие;
- пользователи уже получают единый APK с двумя launcher-категориями;
- разделение приложений потребует двух релизных потоков и синхронизации схемы БД.

**Решение:** оставить один `:app`; новые «чистые» правила (playableId, resolver, policy) держать
в пакетах `domain/playback/**` без Android-зависимостей и покрыть unit-тестами. Выделить `:core-domain`
в Phase 3 — вместе с общим playback-ядром, когда появится второй потребитель (TV и mobile плееры);
это входит в задачу PLY-01.

Критерий пересмотра (когда сплит оправдан): TV-UI полностью на Compose, мобильный UI тоже, и
размер/зависимости TV-сборки начинают мешать мобильной (например, tv-material и Leanback в одном APK
дают заметный вклад или конфликт версий Compose).

### 1.2. Пакеты

```
app/src/main/java/com/arny/mobilecinema/
├── domain/
│   ├── models/                 # существующие + PlayableRef, PlaybackProgress
│   └── playback/               # НОВОЕ: чистые правила, без Android
│       ├── PlayableId.kt
│       ├── NextEpisodeResolver.kt
│       ├── ProgressCompletionPolicy.kt
│       └── ContinueWatchingMapper.kt
├── data/
│   ├── db/                     # Room v8: playback_progress, history
│   └── repository/playback/    # НОВОЕ: PlaybackProgressStore
├── presentation/
│   ├── tv/
│   │   ├── ui/                 # НОВОЕ: Compose-дизайн-система TV
│   │   │   ├── theme/          # TvTheme, TvTypography, TvColors, TvDimens
│   │   │   ├── components/     # PosterCard, LandscapeCard, ContinueCard,
│   │   │   │                   # Hero, SectionHeader, TvButton, StateViews
│   │   │   └── focus/          # FocusRestore, ListStateStore
│   │   ├── home/               # TvHomeFragment → Compose
│   │   ├── catalog/            # НОВОЕ: Movies/Series catalogs
│   │   ├── details/
│   │   ├── search/
│   │   ├── library/            # НОВОЕ: «Моё»
│   │   └── player/
│   └── playerview/             # mobile player (позже — на общем ядре)
└── playback/                   # НОВОЕ (Phase 3): общий контроллер Media3
```

Правило: `presentation/tv/ui/**` не импортирует `androidx.leanback.*`; `domain/playback/**` не импортирует
ни Android, ни Compose, ни Room.

### 1.3. Зависимости

Добавляются в `gradle/libs.versions.toml` (версии проверены по метаданным, см. Приложение A):

| Библиотека | Версия | Когда | Зачем |
|---|---|---|---|
| `androidx.compose:compose-bom` | **2024.06.00** (UI 1.6.8) | Phase 1 | совместимость с tv-material 1.0.1 |
| `androidx.tv:tv-material` | **1.0.1** | Phase 1 | TV-компоненты: Card, Button, Chip, TabRow, NavigationDrawer, Carousel, ListItem |
| `io.coil-kt:coil-compose` | **2.7.0** | Phase 1 | постеры в Compose, запрос размера под контейнер |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.7.0 | Phase 1 | `collectAsStateWithLifecycle` |
| `androidx.navigation:navigation-compose` | — | не добавляем (D2) | навигация остаётся Fragment/Nav 2.5.3 |
| `androidx.media3:media3-exoplayer` (+hls, dash, ui) | **1.4.1** | Phase 3 | `minCompileSdk=34`, совместим с текущим compileSdk |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.6.4 | Phase 0 | тесты корутин без подъёма coroutines |
| `app.cash.turbine:turbine` | 1.0.0 | Phase 0 | тесты Flow (совместим с coroutines 1.6.x) |

Не добавляем: Compose Navigation, `glide-compose`, `material3-adaptive` (мобильная часть отложена),
`media3-ui-compose` (TV-контролы делаем Compose-оверлеем над `PlayerView` в `AndroidView`).

### 1.4. DI

Новые синглтоны в `di/KoinModules.kt`:

```kotlin
single { PlaybackProgressStore(androidContext(), get(), get()) }
single { NextEpisodeResolver() }
single { ProgressCompletionPolicy() }
single { ContinueWatchingMapper() }
```

ViewModel'и экранов получают store/resolver через конструктор, как сейчас (`TvHomeViewModel` и др.).

---

## 2. Переход на Compose for TV

### 2.1. Варианты и решение

| Вариант | Плюсы | Минусы | Вердикт |
|---|---|---|---|
| A. `ComposeView` внутри существующих фрагментов | Навигация, SafeArgs, back stack и deep links не меняются; экран откатывается флагом; релиз после каждого экрана | Остаётся Fragment-хост и Leanback в непереведённых экранах | **Выбран** |
| B. Новая Compose-only Activity + Compose Navigation | Чистая архитектура сразу | Нужно переписать все экраны до первого релиза; двойная поддержка переходного периода | Отклонён |
| C. Новый модуль `app-tv` | Соответствует §26.1 | Дублирование инфраструктуры, два релиза, общая БД | Отложен до Phase 6 (gate) |

### 2.2. Правила интеропа (обязательны для всех экранов)

1. `ComposeView` создаётся в `onCreateView` фрагмента; composition strategy —
   `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`.
2. Один экран — либо Leanback, либо Compose. Внутри одного экрана их не смешивать.
3. Навигационный контракт не меняется: ID destination и аргументы SafeArgs остаются прежними.
4. Back: `BackHandler` только для внутренних слоёв (drawer, диалог, панель). Системный Back по-прежнему
   обрабатывает `NavController`; `TvMainActivity` переводится на `OnBackPressedDispatcher` в Phase 0 (FND-05).
5. Фокус: `Modifier.focusGroup()` на каждый ряд; `FocusRequester` — только для намеренного восстановления;
   первый элемент получает фокус ровно один раз на входе.
6. Состояние скролла/фокуса хранится в ViewModel экрана (`BrowseUiState`), а не в `remember` внутри Compose:
   view фрагмента уничтожается при переходе в детали.
7. Тема: `TvMaterialTheme` оборачивает только Compose-дерево; `Theme.Leanback` остаётся темой Activity,
   пока есть Leanback-экраны. Две темы не вкладываются друг в друга.
8. Изображения: Coil `AsyncImage` с явным размером (`Size(width, height)`), без декодирования 4K в мини-карточку.
9. Никаких `androidx.leanback.*` импортов в `presentation/tv/ui/**`.
10. Каждый экран получает `@Preview` с фейковыми данными — проверка вёрстки без эмулятора.

Скелет экрана:

```kotlin
class TvHomeFragment : Fragment() {
    private val viewModel: TvHomeViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            TvTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    actions = viewModel,
                    onOpenDetails = { id -> findNavController().navigate(R.id.actionToDetails, bundleOf("movieId" to id)) },
                )
            }
        }
    }
}
```

### 2.3. Дизайн-система

Токены (по §10 ТЗ) задаются один раз в Kotlin и в `res/values` (для ещё не переведённых Leanback-экранов):

```kotlin
// presentation/tv/ui/theme/TvTokens.kt
object TvDimens {
    val ScreenH = 48.dp; val ScreenV = 27.dp
    val PosterW = 124.dp; val PosterH = 186.dp
    val EpisodeW = 196.dp; val EpisodeH = 110.dp
    val CardGap = 16.dp; val SectionGap = 28.dp
    val FocusStroke = 3.dp
}
```

Палитра: фон `#0D0F13`, поверхность `#1C2028`, текст `#F4F6FA`, акцент — текущий брендовый
`colorAccent #B6A0FF` (ТЗ §10 допускает один конфигурируемый акцент; менять бренд без решения владельца продукта нельзя).
Контраст `textLabel` (4.14:1) и `text_hint` (2.58:1) исправляется в SYS-08.

Компоненты (все — на `tv-material:1.0.1`, классы проверены в AAR):

| Компонент | На чём | Назначение |
|---|---|---|
| `PosterCard` | `Card` + `AsyncImage` | тайтл 2:3, фокус 1.05×, название 1 строка |
| `LandscapeCard` | `Card` | эпизод / история 16:9 |
| `ContinueCard` | `Card` + прогресс-бар | «Продолжить»: 16:9, `SxxEyy`, процент |
| `Hero` | `Surface`/`Card` + `WideButton` | backdrop, метаданные, 2 CTA |
| `SectionHeader` | `Text` (TitleLarge) | заголовок ряда, 22/28 sp |
| `TvButton` | `Button`/`WideButton` | главное и вторичное действие |
| `TvFilterChip` | `FilterChip` | `selected` и `focused` — разные состояния |
| `SeasonTabs` | `TabRow` | сезоны, сохранение фокуса |
| `TvNavigationDrawer` | `NavigationDrawer` | rail: Главная/Фильмы/Сериалы/Моё/Поиск/Настройки |
| `StateViews` | `Text`/`Button`/`CircularProgressIndicator` | loading / empty / error + retry |

Типографика — из `androidx.tv.material3.Typography` (BodySmall 12/16 … TitleLarge 22/28): карточки переводятся
с 14/12 sp на `BodyMedium`/`BodyLarge` (14–16 sp), заголовки секций — `TitleLarge` (22 sp).

### 2.4. Порядок миграции экранов

| Шаг | Экран | Что проверяется | Откат |
|---|---|---|---|
| 1 | Home | ряды, фокус в LazyRow/LazyColumn, восстановление после Details, hero | флаг `tv_compose_home` |
| 2 | Catalogs (Movies/Series) | сетка 2:3, чипы, фокус в сетке, сохранение фильтра | флаг `tv_compose_catalog` |
| 3 | Movie Details | CTA, действия, метаданные, скролл | флаг `tv_compose_details` |
| 4 | Series Details | сезоны/эпизоды, CTA `SxxEyy`, статусы, фокус после смены сезона | тот же флаг |
| 5 | My | вкладки «Продолжить/Список/История» | тот же флаг |
| 6 | Search | debounce, пустые состояния, IME | флаг `tv_compose_search` |
| 7 | Player overlay | контролы, seek, next episode | флаг `tv_compose_player` |
| 8 | Удаление Leanback | компиляция без `androidx.leanback`, размер APK, все P0-сценарии | тег релиза |

Флаги — обычные `Prefs` boolean + `BuildConfig` default, чтобы откат не требовал пересборки у пользователя
(для внутренних проверок достаточно BuildConfig).

### 2.5. Фокус и скролл в Compose

Правила, без которых миграция провалит §9.2:

- каждая строка — `LazyRow` с `key = { it.id }`, `contentPadding = PaddingValues(horizontal = 48.dp)`
  (место под focus scale, чтобы карточка не обрезалась);
- `LazyListState` каждой строки хранится в `BrowseUiState.rows[rowId].index/offset` (ViewModel), а не в composition;
- при возврате: `LaunchedEffect(restoredFocusKey) { listState.scrollToItem(index); focusRequester.requestFocus() }`;
- `Modifier.focusGroup()` на ряд, `focusProperties { canFocus = ... }` для конфликтных направлений;
- `focusRestorer()` использовать только если API доступен в Compose 1.6.8 (проверка — CMP-02, 0.5 дня);
  иначе fallback: `FocusRequester` + сохранённый индекс;
- Down с карточки переводит фокус на тот же горизонтальный индекс соседнего ряда (`focusedTitleId`
  в `BrowseUiState`), а не на первый элемент;
- при удалении ряда из ответа API выбирается ближайший допустимый `rowId`, экран не падает;
- скелетоны и заглушки **не** `focusable`.

### 2.6. Версии: два трека

| | Track A (сейчас) | Track B (позже) |
|---|---|---|
| tv-material | 1.0.1 | 1.1.0 |
| Compose BOM | 2024.06.00 (UI 1.6.8) | 2026.03.00 (UI 1.10.5, Material3 1.4.0) |
| Kotlin | 2.0.21 (без изменений) | ≥ 2.1.20 (требование stdlib 1.1.0) |
| compileSdk | 34 | 35 (`minCompileSdk` 1.1.0) |
| AGP | 8.7.3 | ≥ 8.6.0 (уже выполнено) |

Правило: **не совмещать** подъём тулчейна и миграцию экрана. Track B — отдельная задача с собственной
проверкой сборки/релиза, выполняется после Phase 3, если нужны новые API 1.1.0 (например, изменения focus API).

---

## 3. Данные, прогресс, следующая серия

### 3.1. Целевые модели (контракт)

```kotlin
// domain/playback/PlayableId.kt
sealed interface PlayableId {
    val value: String
    data class Film(val movieDbId: Long) : PlayableId {
        override val value get() = "film:$movieDbId"
    }
    data class Episode(val movieDbId: Long, val seasonId: Int, val episodeId: Int) : PlayableId {
        override val value get() = "ep:$movieDbId:$seasonId:$episodeId"
    }
    companion object { fun parse(value: String): PlayableId? = /* ... */ }
}

data class PlayableRef(
    val id: PlayableId,
    val movieDbId: Long,
    val titleName: String,
    val seasonNumber: Int?,      // отображаемый номер (не индекс массива)
    val episodeNumber: Int?,
    val episodeTitle: String?,
    val durationMs: Long?,       // из метаданных источника, если есть
    val hlsUrl: String?,
    val dashUrl: String?,
    val posterUrl: String?,
    val isAvailable: Boolean,    // есть хотя бы один URL
)
```

Ограничение: `SerialSeason.id` и `SerialEpisode.id` приходят с сайта (`Int?`/`Int`). Если id отсутствует (0),
используется индекс с префиксом `i` (`ep:12:i1:i5`) — такой id **нестабилен** и помечается в логах;
это осознанная деградация для неполных метаданных, а не скрытая эвристика.

### 3.2. Соответствие текущим моделям

| Сейчас | Станет | Примечание |
|---|---|---|
| `Movie.seasons: List<SerialSeason>` (JSON) | `Series`/`Season`/`Episode` (маппинг на чтении) | DTO не тащим в UI, но и не переписываем парсер в Phase 0 |
| `SerialEpisode.episode: String` | `episodeNumber: Int?` + `rawLabel` | `"1"`, `"1-2"` → номер + исходная строка |
| `SerialEpisode.hls/dash` | `PlayableRef.hlsUrl/dashUrl` | приоритет HLS на TV сохраняется |
| `ViewMovie` | `TitleSummary` + `PlaybackProgress?` | карточка получает прогресс |
| `HistoryEntity.season/episode: Int` | `last_playable_id: String` | индексы остаются для обратной совместимости |
| `PlaybackProgressEntity.played_ms` | остаётся (персонализация) + `position_ms` | `played_ms` — суммарно просмотрено, `position_ms` — текущая позиция |

### 3.3. Room: миграция 7 → 8

```sql
-- playback_progress
ALTER TABLE playback_progress ADD COLUMN playable_id  TEXT    NOT NULL DEFAULT '';
ALTER TABLE playback_progress ADD COLUMN profile_id   TEXT    NOT NULL DEFAULT 'default';
ALTER TABLE playback_progress ADD COLUMN position_ms  INTEGER NOT NULL DEFAULT 0;
ALTER TABLE playback_progress ADD COLUMN duration_ms  INTEGER NOT NULL DEFAULT 0;
ALTER TABLE playback_progress ADD COLUMN completed    INTEGER NOT NULL DEFAULT 0;
ALTER TABLE playback_progress ADD COLUMN updated_at   INTEGER NOT NULL DEFAULT 0;

DROP INDEX IF EXISTS index_playback_progress_movie_dbid_season_episode;
CREATE UNIQUE INDEX IF NOT EXISTS index_playback_progress_profile_playable
    ON playback_progress (profile_id, playable_id);

UPDATE playback_progress
   SET playable_id = 'legacy:' || movie_dbid || ':' || season || ':' || episode,
       updated_at  = latest_time
 WHERE playable_id = '';

-- history
ALTER TABLE history ADD COLUMN last_playable_id TEXT    NOT NULL DEFAULT '';
ALTER TABLE history ADD COLUMN duration_ms      INTEGER NOT NULL DEFAULT 0;
ALTER TABLE history ADD COLUMN completed        INTEGER NOT NULL DEFAULT 0;
```

Правила миграции:

- старые строки получают `playable_id = 'legacy:...'` и **лениво** переносятся при первом открытии тайтла
  (`LegacyProgressMigration`): парсим `Movie.seasons`, находим эпизод по `(seasonIndex, episodeIndex)`,
  создаём строку с реальным `playableId`, старую помечаем `completed = 0` и исключаем из выдачи;
- `position_ms` у legacy-строк остаётся 0; при ленивой миграции берётся `history.position`
  (абсолютная позиция уже хранилась там), а `duration_ms` = 0, пока длительность неизвестна;
- `updated_at` = `latest_time`;
- `duration_ms = 0` означает «неизвестна» — процент не показывается, завершение не вычисляется;
- откат миграции не предусмотрен (Room destructive fallback запрещён), поэтому миграция покрывается
  instrumentation-тестом на копии схемы.

### 3.4. `NextEpisodeResolver`

```kotlin
class NextEpisodeResolver {
    /** Порядок каталога: сезоны по id, серии по номеру; спецсезон 0 — по правилу каталога. */
    fun ordered(movie: Movie): List<PlayableRef>

    fun resumeTarget(
        movie: Movie,
        progressByPlayable: Map<String, PlaybackProgress>,
    ): ResumeTarget
}

sealed interface ResumeTarget {
    data class Resume(val ref: PlayableRef, val positionMs: Long) : ResumeTarget
    data class Start(val ref: PlayableRef) : ResumeTarget       // первая доступная
    data class Restart(val ref: PlayableRef) : ResumeTarget     // всё просмотрено
    data class Unavailable(val reason: String) : ResumeTarget   // нет доступных серий
}
```

Алгоритм (ровно по §8.2, без `season + 1`):

1. Построить `ordered` из каталога; `isAvailable` = есть HLS или DASH.
2. Если для `playableId` есть незавершённый прогресс (`completed = false`, `position > 0`) — `Resume`.
3. Иначе найти последний завершённый в `ordered` и взять **следующий доступный**.
4. Если завершён последний — `Restart(ordered.first { isAvailable })`.
5. Если истории нет — `Start(ordered.first { isAvailable })`.
6. Если следующая серия недоступна — вернуть `Unavailable`, **не подменять** первой серией другого сезона.

### 3.5. `ProgressCompletionPolicy`

```kotlin
class ProgressCompletionPolicy(
    private val minRemainingMs: Long = 30_000L,
    private val fraction: Double = 0.95,
    private val creditsStartMs: Long? = null, // когда источник отдаст метаданные
) {
    fun isCompleted(positionMs: Long, durationMs: Long?): Boolean
    fun shouldPersist(positionMs: Long, durationMs: Long?): Boolean // > 10 c и не completed
    fun resumePosition(positionMs: Long, durationMs: Long?): Long  // 0, если completed
    fun fraction(positionMs: Long, durationMs: Long?): Float?
}
```

Порог §7.3: `position >= duration - max(30 c, 5% duration)`; при наличии `creditsStartMs` — `position >= creditsStartMs`.
Только `>= 90%` не используется (короткие эпизоды завершались бы до титров).

### 3.6. «Продолжить просмотр»

Запрос: последняя незавершённая позиция на тайтл.

```sql
SELECT p.*, m.title, m.img, m.type
FROM playback_progress p
JOIN movies m ON m.dbId = p.movie_dbid
WHERE p.profile_id = :profileId
  AND p.completed = 0
  AND p.position_ms > 15000
  AND p.updated_at = (
      SELECT MAX(p2.updated_at) FROM playback_progress p2
      WHERE p2.movie_dbid = p.movie_dbid AND p2.completed = 0
  )
ORDER BY p.updated_at DESC
LIMIT :limit
```

`ContinueWatchingMapper` превращает строку в карточку: тайтл, `SxxEyy`, название серии, процент.
Одна карточка на сериал — гарантируется `MAX(updated_at)` и `movie_dbid`.

### 3.7. Запись прогресса

| Событие | Действие |
|---|---|
| старт воспроизведения | `Resume/Start` из resolver; позиция применяется один раз |
| каждые 15 с при `isPlaying` | upsert `position_ms`, `duration_ms`, `updated_at` |
| pause / stop / onStop / конец серии | немедленный upsert + `completed` по policy |
| seek | немедленный upsert (не ждать тикера) |
| смена серии | upsert текущей, затем загрузка следующей |
| ошибка потока | прогресс не обнуляется |

Запись идемпотентна: `@Upsert` по `(profile_id, playable_id)` + `updated_at`; в Phase 0 без сервера,
в Phase 4 — очередь синхронизации не добавляем (нет аккаунтов).

### 3.8. Профиль

`profileId = "default"` во всех запросах и записях. UI выбора профиля не появляется (D7), но схема и API
готовы к §8.3/§28. Тесты проверяют, что смена `profileId` не возвращает чужие данные.

---

## 4. Состояние экранов и восстановление фокуса

### 4.1. Контейнеры

```kotlin
data class BrowseUiState(
    val routeId: String,
    val filters: CatalogFilters = CatalogFilters(),
    val sort: SortOrder = SortOrder.NEW,
    val focusedRowId: String? = null,
    val focusedTitleId: Long? = null,
    val rows: Map<String, RowScroll> = emptyMap(), // rowId -> index + offset
    val state: ScreenState<List<RowUi>> = ScreenState.Loading,
)

data class DetailsUiState(
    val titleId: Long,
    val selectedSeasonId: Int? = null,
    val recommendedPlayableId: String? = null,
    val focusedElementKey: String? = null,
    val episodesScrollIndex: Int = 0,
    val state: ScreenState<DetailsUi>,
)

data class PlayerUiState(
    val playableId: String,
    val positionMs: Long,
    val durationMs: Long?,
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val isControlsVisible: Boolean = true,
)
```

### 4.2. Где хранится

| Данные | Место | Почему |
|---|---|---|
| фильтр/сортировка/фокус/скролл | ViewModel экрана (`SavedStateHandle` для процесса) | переживает уничтожение view фрагмента |
| `selectedSeasonId`, `episodesScrollIndex` | ViewModel деталей | возврат из плеера |
| позиция воспроизведения | Room (`position_ms`) | переживает процесс и устройство |
| выбранные дорожки | DataStore/Prefs по профилю | §7.6 |
| тысячи постеров | **не** в SavedState | §9.1 |

### 4.3. Сценарии-инварианты (§9.2)

| ID | Сценарий | Ожидание |
|---|---|---|
| `HOME-FOCUS-01` | Главная → ряд 8 → карточка 12 → Детали → Back | та же карточка и скролл |
| `CAT-FILTER-01` | Фильмы → «Триллер» → Детали → Back | фильтр и сетка сохранены |
| `SERIES-FOCUS-02` | Сериал → S03 → E06 → Плеер → Back | сезон и серия восстановлены |
| `SERIES-NEXT-03` | S01E10 → S02E01 | без повторного S01E10 |
| `SERIES-NEXT-04` | S01E10 без следующей | `Unavailable`, без подмены |
| `PLAYER-INPUT-01` | seek при скрытой панели/открытом меню | без конфликтов |
| `POSTERS-01` | нет poster/backdrop/thumbnail | placeholder, макет не ломается |
| `PROFILE-01` | два `profileId` | история не пересекается |

Каждый сценарий получает автоматический тест там, где это возможно без устройства (resolver, policy, mapper,
state-restorer), и ручной чек-лист — где нужен фокус.

### 4.4. Чек-лист интерактивных элементов (обязателен для каждого Compose-экрана)

Ни один экран не считается сданным, пока на устройстве не нажата **каждая** кнопка/чип/карточка и результат
не записан в `TV_SPEC_V2_PROGRESS.md`:

- CTA и вторичные действия — открывают ровно то, что обещает подпись;
- чипы фильтра/сортировки — применяются по OK, фокус их не применяет;
- карточки тайтлов/серий/источников — открывают плеер/детали с той серией и тем URL, что выбраны;
- диалоги (обновление, подтверждения) — обе кнопки работают, Back закрывает без побочных эффектов;
- Back на каждом экране возвращает на предыдущий и не теряет состояние списка/фокуса;
- кнопки, у которых нет данных (нет источников, нет обработчиков внешнего плеера), скрыты, а не «мертвы».

---

## 5. Плеер

### 5.1. Media3: миграция

Цель §7.1/§24.1 — один движок и один контроллер, разные оболочки. Сейчас два независимых плеера:
`TvPlayerFragment` (1280 строк) и `PlayerViewFragment` (1574 строки) на ExoPlayer2 2.19.1.

Шаги:

1. Заменить зависимости `com.google.android.exoplayer:exoplayer*` на `androidx.media3:media3-exoplayer`,
   `media3-exoplayer-hls`, `media3-exoplayer-dash`, `media3-ui` версии **1.4.1** (совместима с compileSdk 34).
2. Механические переименования пакетов: `com.google.android.exoplayer2.*` → `androidx.media3.*`
   (включая `Player`, `MediaItem`, `PlaybackException`, `DefaultTrackSelector`, `MediaSource`, `PlayerView`).
3. Вынести общее ядро `playback/PlaybackController.kt`:
   - владеет `ExoPlayer`, `MediaItem`/`MediaSource`, очередью серий, `TrackSelector`;
   - `prepare(playable, positionMs)`, `next()`, `previous()`, `seekBy(ms)`, `selectQuality()`,
     `selectAudio(lang)`, `selectSubtitle(lang)`;
   - не знает про View/Compose и про навигацию;
   - освобождается в `onDestroyView`/`onDestroy` владельца, а не в `@Composable`.
4. TV-плеер переводится первым (меньше поверхностей), мобильный — вторым, с сохранением PiP и жестов.
5. `MediaSessionService` **не добавляем**: фоновое воспроизведение по ТЗ выключено (§7.7).

Проверка: `assembleDebug` + ручной прогон HLS/DASH, seek, смена качества, выход на Home.
Риск: `PlayerView` из media3-ui требует тему/атрибуты — проверить на TV-макете (без эмулятора — компиляцией
и layout-инфляцией в unit-тесте невозможно, поэтому первый прогон — на устройстве в Phase 3).

### 5.2. Клавиши (§7.2)

| Кнопка | Сейчас | Станет |
|---|---|---|
| OK / Enter | pause/play | pause/play; в открытом меню — подтверждение |
| Left/Right | ±5 с | ±10 с; long press → ускорение 10→30→60 с с индикатором |
| Up/Down | показать контролы | показать контролы; при открытой панели — переход по элементам |
| Back | сохранить и выйти | сначала закрыть меню/панель, затем сохранить и выйти |
| MediaNext/ChannelUp | следующая серия (вручную) | следующая серия с подтверждением позиции |
| Menu | аспект-рацио | настройки плеера (аспект, качество, дорожки) |
| Play/Pause | play/pause | без изменений |

Ключи обрабатываются в одном месте: `onKeyEvent`/`onKeyDown` Compose-оверлея; при открытом диалоге
события не должны одновременно перематывать видео (`PLAYER-INPUT-01`).

### 5.3. Прогресс

Реализуется в Phase 0 на текущем ExoPlayer2, затем переносится на Media3 без изменения контракта.
Точки записи — таблица §3.7. Обязательные свойства:

- `positionMs` ограничивается `[0, durationMs]` при чтении (после смены версии потока);
- если `completed`, resume начинается с 0 (или предлагается «Смотреть снова»);
- «просмотрено» не выставляется по одному `>= 90%` (см. policy);
- прогресс сериала на карточке — это прогресс **конкретного** `playableId`, не сумма.

### 5.4. Внешний плеер (доработка по запросу владельца продукта)

Сейчас воспроизведение возможно только во встроенном ExoPlayer. Внешний плеер нужен для потоков,
которые встроенный движок не тянет (редкие кодеки, HEVC на старых устройствах), и как привычный
сценарий для пользователей VLC/MX Player.

Контракт:

1. Источник — уже вычисленный `PlayableRef` (`hlsUrl` приоритетом, иначе `dashUrl`, иначе первый URL
   из `CinemaUrlData`): отдельной логики выбора ссылки не появляется.
2. Запуск — `Intent(ACTION_VIEW)` c `data = Uri.parse(url)` и `type = "video/*"`, флаг
   `FLAG_ACTIVITY_NEW_TASK`; для выбора приложения — `Intent.createChooser` только если обработчиков > 1.
3. Стартовая позиция передаётся best-effort: `EXTRA_START_POSITION` поддерживают не все плееры,
   поэтому отсутствие поддержки не считается ошибкой и не блокирует запуск.
4. Если обработчиков нет (типично для TV-приставки без сторонних плееров) — пункт скрывается,
   а не показывает пустой chooser/тост.
5. Возврат прогресса из внешнего плеера не ожидается: позиция не пишется, пока пользователь не
   вернётся в приложение; `completed` внешним плеером не выставляется.
6. Точки входа: кнопка «Открыть во внешнем плеере» на экране деталей (рядом с CTA) и пункт в панели
   настроек плеера (mobile/TV), чтобы можно было переключиться во время неудачного воспроизведения.

### 5.5. Следующая серия (§7.4)

```text
конец эпизода
   ├── resolver.next(playable) == null → экран завершения, ничего не запускать
   └── есть следующая → панель «Следующая серия · S02E01 · Название · 10 с»
          ├── OK → play(next)
          ├── Back/Cancel → панель закрыть, остаться на конце эпизода
          └── таймер истёк → автозапуск, если prefs.autoplayNext == true
```

Отмена таймера: открыт системный диалог, ошибка DRM, выбор дорожки, смена профиля, приложение в фоне.
Автопереход по умолчанию **включён** для сериалов и **выключен** для фильмов (решение продукта, вынести в prefs).
Отдельно: ручной `nextEpisode()` не должен «прыгать» через недоступные серии — берёт resolver.

### 5.5. Дорожки, субтитры, качество (§7.6)

- «Auto» по умолчанию; ручное качество — только при наличии вариантов.
- Аудио: язык + описание дорожки; субтитры: выкл / языки / «как в системе».
- Предпочтения хранятся по `profileId` в `Prefs`; при отсутствии дорожки — тихо остаёмся на Auto,
  без диалога-ошибки.
- Панель настроек — единый Compose-диалог (качество, дорожки, аспект), а не три разных места.

### 5.6. Ошибки, буферизация, жизненный цикл

| Состояние | UI | Действие |
|---|---|---|
| `STATE_BUFFERING` | индикатор по центру, контролы не скрываются | — |
| ошибка источника | панель: «Не удалось воспроизвести», код ошибки, «Повторить», «Назад» | повтор текущего `playable` |
| нет источников | экран «Нет доступных источников» + «Обновить данные» | без бесконечного спиннера |
| уход в фон | пауза + сохранение позиции | без foreground service |
| активное видео | `KEEP_SCREEN_ON` на окне/`PlayerView` | защита от Ambient Mode |

### 5.7. Приёмка плеера

- [ ] HLS и DASH играют, seek и смена качества работают.
- [ ] Back сохраняет позицию; повторный вход продолжает с неё.
- [ ] Конец серии предлагает следующую; отмена работает; автозапуск управляется настройкой.
- [ ] Ошибка потока даёт «Повторить», а не сырой текст.
- [ ] Буферизация видна; экран не гаснет во время просмотра.
- [ ] Смена аудио/субтитров не сбрасывает позицию.
- [ ] При уходе на Home видео не продолжает играть.

---

## 6. Экранные планы

### 6.1. Главная (§3)

| Блок | Состав | Данные | Состояния |
|---|---|---|---|
| Hero | backdrop (fallback: постер/цвет), название, год/рейтинг/жанр, 2–3 строки, CTA «Смотреть/Продолжить» + «Подробнее» | первый тайтл `HomeHighlights` | нет backdrop → подложка |
| Продолжить | 16:9 карточки с `SxxEyy`, названием серии, процентом | `ContinueWatchingMapper` | нет истории → блок скрыт |
| Для вас | постеры 2:3 | `HomeHighlights.forYou` | пусто → скрыт |
| Новинки/Популярное | постеры 2:3 | `recent`/`bestNow` | пусто → скрыт |
| Сериалы | постеры 2:3 | фильтр по `MovieType.SERIAL` | пусто → скрыт |

Порядок фиксируется в `HomeRowsFactory` (unit-тестируемо), а не в порядке `adapter.add`.
Системные действия («Обновить данные», «Очистить историю», «Поиск по сайту») уезжают в rail/настройки,
а не в первый экран (F-06).

Новый профиль: нет истории → нет блока «Продолжить» и нет заголовка «Для вас», если рекомендаций нет.
Пустые секции не рисуются (а не рисуются пустыми).

### 6.2. Каталоги (§4)

- Экран «Фильмы»/«Сериалы»: заголовок, чипы жанров (`FilterChip`), сортировка (кнопка → меню),
  сетка `LazyVerticalGrid(GridCells.Adaptive(124.dp))`.
- Чип: `focused` (обводка/scale) и `selected` (заливка + галочка) — разные состояния; фокус не меняет выдачу.
- Фильтр применяется по OK; запрос отменяется при следующем выборе (`flatMapLatest` уже даёт отмену).
- Карточка 2:3, название 1 строка, без описания; бейдж «Новинка» — опционально.
- Возврат из деталей сохраняет фильтр, скролл и карточку.

### 6.3. Детали фильма (§5)

Сверху вниз: backdrop с градиентом → название → метаданные (год, возраст, длительность, жанр) →
описание 2–4 строки → CTA (`Продолжить с 42:15` / `Смотреть`) → «Мой список» / «Трейлер» →
похожие (Phase 4, при наличии данных).

CTA — первая фокусируемая кнопка. «Смотреть сначала» — вторичное действие, если фильм начат.
Ошибки: поток недоступен → страница остаётся, панель с «Повторить»; тайтл удалён → экран с «В каталог».

### 6.4. Детали сериала (§6)

- CTA: `Продолжить S02E05` / `Смотреть S01E01` / `Пересмотреть`.
- Сезоны: `TabRow` (1–4) или выпадающий список (>4), выбор по OK, фокус остаётся на выбранном сезоне.
- Эпизоды: 16:9 196×110 dp, `№ + название`, длительность, статус (просмотрено/на 19:26/не начато).
- Смена сезона не переходит на другой экран; список серий скроллится к сохранённой позиции.
- OK на эпизоде сразу запускает его (без подтверждения).
- Будущие серии не спойлерить: если нет thumbnail — placeholder, без описания.

### 6.5. «Моё» (§19.2/§22)

Вкладки: «Продолжить» (одна запись на сериал), «Мой список», «История».
Удаление из «Продолжить» и очистка истории — разные действия; подтверждение только для необратимого.

### 6.6. Поиск (§22)

Один вход (rail/кнопка), один экран с полем ввода; «поиск по каталогу» и «поиск по сайту» — переключатель
режима внутри экрана, а не два разных пункта меню (F-09/C-10).
Debounce 300 мс, отмена устаревшего запроса, состояния: пусто → подсказки/популярное, ввод → loading,
нет результатов → «Ничего не найдено» + «Искать на сайте», ошибка → «Повторить».


Голосовой ввод — через системную клавиатуру/`RecognizerIntent`, если доступен.

### 6.7. Настройки

Минимум для ТЗ: автопереход к следующей серии, качество по умолчанию, язык аудио/субтитров,
очистка истории, обновление данных. Размещение — в rail, не на главной.

---

## 7. Мобильная часть (часть II ТЗ)

### 7.1. Что делаем в этом плане

| Задача | Почему |
|---|---|
| 5-я вкладка/экран поиска и каталоги «Фильмы/Сериалы» | §19.1 |
| Карточки с прогрессом и «Продолжить» | §20.1–20.2, зависит от DOM-* |
| Детали сериала: BottomSheet сезона, строки серий со статусом | §21.2 |
| Поиск с debounce и пустыми состояниями | §22 |
| Политика PiP/фона и следующей серии на общем ядре | §24.4–24.5 |
| `WindowSizeClass` для планшет/складных (без двухпанельного layout в MVP) | §23 |
| Планшетные постеры: сетка по ширине экрана вместо фиксированных 2 колонок | §23, жалоба владельца: «на планшете постеры огромные» |
| Внешний плеер (`ACTION_VIEW`) для mobile и TV | §7.1, запрос владельца продукта |

### 7.2. Что откладываем

- Compose-переписывание мобильного UI (D4) — отдельный gate после TV.
- Cast и офлайн-загрузки как новые фичи: загрузки уже есть, Cast нет; ТЗ относит их к отдельным проектам.
- Аккаунты/синхронизация TV ↔ phone (§28) — нет сервера и авторизации; `profileId` пока локальный.

### 7.2.1. Мобильный Compose-трек (решение владельца продукта, срез 11)

ТЗ ч. II (§18–§25) читается как основное для телефона. Решение: **мобильный UI переводим на Compose,
без сплита модулей** — `ComposeView` внутри существующих фрагментов, `nav_graph_main.xml` и аргументы
не меняются, каждый экран откатывается флагом `Prefs` (тот же приём, что на TV).

Задачи (после MOB-01…07, либо параллельно, если экран не меняет данные):

| ID | Задача | Точки | Дни | Проверка |
|---|---|---|---:|---|
| MOB-08 | `MobileAppShell`: 5 вкладок по §19.1 (Главная / Фильмы / Сериалы / Моё / Поиск), `NavigationBar` только на корнях, свой back stack и скролл на вкладку | `bottom_nav_menu.xml`, `nav_graph_main.xml`, новый `MobileAppShellFragment` | 2.0 | ручной Back/смена вкладок, состояние не сбрасывается |
| MOB-09 | Главная на Compose по §20: шапка, `FeaturedHero` (динамическая высота), «Продолжить» (`ContinueCard` 16:9 + полоса + серия), «Мой список», новинки, подборки; скелетоны фиксированной геометрии, ошибка ряда не убивает остальные | `presentation/mobile/home/`, `ui/components/` | 3.0 | Preview + Pixel 5: скролл/возврат на том же тайтле |
| MOB-10 | Общий прогресс для телефона: `PlaybackProgressStore` вместо `addPlaybackProgress`, «осталось N мин» на карточке | `PlayerViewFragment`, `PlayerViewModel` | 1.5 | unit: запись/чтение по `playableId`; ручной resume |
| MOB-11 | Детали на Compose по §21: фильм (backdrop, CTA «Смотреть/Продолжить», «Развернуть» описания) и сериал (CTA `SxxEyy`, `ModalBottomSheet` сезонов, `EpisodeRow` 16:9 с прогрессом) | `presentation/mobile/details/` | 3.5 | Preview + ручной: сезон сохраняется при Back/повороте |
| MOB-12 | Поиск на Compose по §22: автофокус по переходу, debounce 250–400 мс, недавние запросы, loading без мерцания, «каталог/сайт» переключателем | `presentation/mobile/search/` | 2.0 | unit: отмена устаревшего запроса; ручной ввод |
| MOB-13 | Токены и компоненты мобильного Compose: единая палитра с TV (`#0D0F13`, акцент `#B6A0FF`), `PosterCard` 2:3, `LandscapeCard`, `ContinueCard`, `EpisodeRow`, `FeaturedHero`, `StateViews`; паддинг 16 dp, gap 8–12 dp, сенсорная зона ≥48 dp | `presentation/mobile/ui/theme/`, `.../components/` | 2.5 | Preview всех компонентов; a11y: 48 dp |
| MOB-14 | Адаптив по §23: `WindowSizeClass` вместо `isTablet`, rail 600–839 dp, 2–4 постера, max content width на 1200 dp+, плеер перестраивается при повороте/split-screen без потери позиции | layouts + Compose | 2.0 | Preview конфигураций; эмулятор планшета |

Итого мобильный Compose-трек: **16.5 дня** (без учёта `PLY-01/02` Media3 и `PLY-09`).

Правила (из ТЗ, обязательны в каждом срезе): Back закрывает sheet → дочерний экран → корень и не ломает
восстановление состояния; повторное скачивание каталога при смене вкладки запрещено; воспроизведение
начинается только по явному действию; изображения запрашиваются под фактический размер контейнера.

### 7.3. Gate для мобильного Compose

Переходить, если выполняются все условия: TV-UI полностью на Compose, `:core-domain` выделен,
на мобильном есть 2–3 экрана, которые чаще всего меняются и дают максимальную выгоду (Home/Details/Player).
Иначе — оставить View-стек: он не блокирует приёмку ТЗ по TV.

---

## 8. Фазы

Порядок учитывает, что переписывать Leanback-экраны дважды нельзя: UX-работы по §3–§6 выполняются
**сразу в Compose**, а в Leanback остаются только исправления дефектов из Phase 0.

| Фаза | Содержание | Длительность | Выход (DoD) |
|---|---|---:|---|
| **0. Корректность и фундамент** | токены, px→dp, строки, баннер, back; `PlayableId`, прогресс, Room v8, resolver, policy; быстрые фиксы плеера (KEEP_SCREEN_ON, буферизация, seek, ошибка); тест-харнесс | **18–22 дня** | сборка зелёная; unit-тесты resolver/policy/migration; прогресс пишется по `playableId`; resume не уезжает при пропущенных сериях |
| **1. Compose: каркас + Главная** | deps Track A, тема/токены, компоненты, rail, Home (hero, continue, ряды), восстановление фокуса, флаги | **15–18 дней** | Home на Compose за флагом; P0-сценарии фокуса на Home; Leanback-главная отключаема |
| **2. Compose: каталоги и детали** | Movies/Series, Details movie/series, My, Search, удаление Leanback-главной | **28–34 дня** | все TV-экраны, кроме плеера, на Compose; §13 P0 (кроме плеера) зелёный |
| **3. Media3 и плеер** | media3 1.4.1, общий контроллер, Compose-оверлей, next episode, дорожки, ошибки | **15–19 дней** | один движок на TV+mobile; приёмка §5.7; Leanback-зависимость удалена |
| **4. Системная интеграция** | Watch Next, глобальный поиск, deep links/shared URL, настройки автоперехода, baseline profiles, a11y, 4K-ассеты | **10–13 дней** | §15 закрыт; deep link из лаунчера ведёт на тайтл; P1-матрица |
| **5. Мобильный паритет** | вкладки, каталоги, детали серии, поиск, политика PiP/next на общем ядре | **11–14 дней** | §30.2 P0 для mobile (кроме Cast/Downloads) |
| **6. Gate: мобильный Compose / сплит модулей** | решение по D1/D4, при необходимости — `app-mobile` | 0 или 15–20 дней | решение зафиксировано, TV не регрессирует |

Итого TV-ядро (0–3): **76–93 дня** (≈15–19 недель). Полный объём без мобильного Compose: **97–120 дней**
(≈20–24 недели). С буфером +25%: **до 30 недель** для одного разработчика.

Если нужен более ранний TV-MVP, допустима остановка после Phase 2 (TV-UI целиком, старый плеер) или
после Phase 3 (TV-UI + Media3 и новый плеер) — это и есть минимальный продукт по части I ТЗ.

Релизы внутри фаз: минорный bump после каждой фазы (1.7.0, 1.8.0, …), фича-флаги для Compose-экранов,
тег и GitHub Release по текущему процессу (CI уже собирает APK/AAB по тегу).

---

## 9. Бэклог задач

Оценки — идеальные дни одного разработчика. «Проверка» — минимальная достаточная проверка на момент сдачи задачи;
для UI-задач без эмулятора обязательна сборка + `@Preview`, runtime-проверка — в общем прогоне фазы.

### 9.1. Phase 0 — корректность и фундамент (18–22 дня)

| ID | Задача | Точки в коде | Зависит | Дни | Проверка |
|---|---|---|---:|---:|---|
| FND-01 | Токены: `dimens.xml`, `colors.xml`, `textAppearance` для TV; заменить магические числа в презентерах | `res/values/*`, `presentation/tv/presenters/**`, `TvSourceCardPresenter` | — | 1.5 | сборка; grep не находит чисел в `setPadding` |
| FND-02 | Пиксели → dp: `313×176` в `MovieCardPresenter`, `SiteSearchCardPresenter`, `TvEpisodeCardPresenter`; Glide `override` по dp | те же файлы | FND-01 | 1.0 | unit-тест конвертации; визуально в Preview/на устройстве позже |
| FND-03 | TV-строки в `strings.xml` (18 литералов), убрать жаргон ZOOM/FILL/FIT из тостов | `presentation/tv/**` | — | 0.5 | `grep -P "[А-Яа-я]"` по `presentation/tv` пуст (кроме комментариев) |
| FND-04 | Баннер 320×180 с названием приложения (TV-BN) | `res/drawable/tv_banner.xml` | — | 0.25 | сборка; визуальная проверка ресурса |
| FND-05 | Back: `OnBackPressedDispatcher` в `TvMainActivity`, убрать deprecated override; политика выхода | `TvMainActivity.kt` | — | 0.5 | сборка; ручной прогон Back на устройстве |
| FND-06 | Тест-харнесс: coroutines-test + Turbine + фейковые Movie/SerialSeason | `app/build.gradle`, `src/test/**` | — | 0.5 | `./gradlew :app:test` |
| DOM-01 | `PlayableId`, `PlayableRef`, маппинг из `Movie/SerialSeason/SerialEpisode` | `domain/playback/` | — | 1.0 | unit: film/episode id, fallback `i` |
| DOM-02 | Доменная `PlaybackProgress` (`profileId`, `playableId`, `positionMs`, `durationMs`, `completed`, `updatedAt`) | `domain/models/` | DOM-01 | 0.5 | unit: сериализация/ключи |
| DOM-03 | Room v8: колонки, индексы, `MIGRATION_7_8`, DAO upsert/запросы | `data/db/**`, `AppDatabase` | DOM-02 | 1.5 | `Migration7To8Test` (androidTest, позже); схема экспортируется |
| DOM-04 | `PlaybackProgressStore`: upsert по policy, `observeContinueWatching()`, `progressFor(playableId)` | `data/repository/playback/` | DOM-02/03 | 1.0 | unit на фейковом DAO |
| DOM-05 | `NextEpisodeResolver` + `ResumeTarget` | `domain/playback/` | DOM-01 | 1.0 | unit: 6 сценариев §3.4 |
| DOM-06 | `ProgressCompletionPolicy` | `domain/playback/` | — | 0.5 | unit: 95 %/30 с/credits/короткий эпизод |
| DOM-07 | Плеер: сохранять `playableId + position + duration`, убрать маппинг «индекс → серия»; resume из resolver | `TvPlayerFragment`, `PlayerViewModel` | DOM-03/05 | 1.5 | unit на маппере; ручной resume-сценарий позже |
| DOM-08 | Ленивая миграция legacy-прогресса при открытии тайтла | `PlaybackProgressStore`, `TvDetailsViewModel` | DOM-03 | 1.0 | unit: legacy-строка → реальный id |
| FOC-01 | `BrowseUiState` + `SavedStateHandle`: фильтр/сортировка/фокус/скролл для текущей главной | `TvHomeViewModel`, `TvHomeFragment` | — | 1.0 | unit: восстановление по rowId/titleId |
| FOC-02 | `DetailsUiState`: `selectedSeasonId`, `episodesScrollIndex`, фокус | `TvDetailsViewModel/Fragment` | — | 1.0 | unit: смена сезона сохраняет выбор |
| PLR-01 | `KEEP_SCREEN_ON` + индикатор буферизации + правила скрытия контролов | `TvPlayerFragment`, `f_tv_player.xml` | — | 0.5 | сборка; ручной прогон |
| PLR-02 | Seek: шаг 10 с, long-press ускорение, превью времени; `SeekBar` получает фокус | `TvPlayerFragment`, layout | — | 1.0 | ручной прогон; unit на расчёте шага |
| PLR-03 | Панель ошибки: причина, код, «Повторить», «Назад» | `TvPlayerFragment` | — | 1.0 | ручной прогон (404/таймаут) |
| PLR-04 | Жёсткая остановка авто-перехода: не запускать следующую серию без панели | `TvPlayerFragment` | DOM-05 | 1.0 | unit/ручной: конец серии → панель |
| TST-01 | Базовые фикстуры каталога/истории для тестов | `src/test/fixtures` | FND-06 | 1.0 | тесты используют одни данные |
| TST-02 | Тесты resolver/policy/mapper | `src/test/**` | DOM-05/06, TST-01 | 1.5 | `./gradlew :app:test` |
| CI-01 | CI на push/PR: сборка debug + unit-тесты (сейчас workflow только по тегам) | `.github/workflows/build.yml` | — | 0.5 | зелёный workflow на тестовой ветке |

### 9.2. Phase 1 — Compose-каркас и Главная (15–18 дней)

| ID | Задача | Точки | Зависит | Дни | Проверка |
|---|---|---|---:|---:|---|
| CMP-01 | Track A: BOM 2024.06.00, `tv-material:1.0.1`, `coil-compose:2.7.0`, `lifecycle-runtime-compose`; сборка | `libs.versions.toml`, `app/build.gradle` | — | 0.5 | `assembleDebug` зелёный |
| CMP-02 | Spike: `focusRestorer`/`focusGroup` в Compose 1.6.8; решение по API | тестовый экран | CMP-01 | 0.5 | вывод зафиксирован в коде/комментарии |
| CMP-03 | `TvTheme`, `TvColors`, `TvTypography`, `TvDimens` | `presentation/tv/ui/theme/` | CMP-01 | 1.5 | `@Preview` всех стилей |
| CMP-04 | Компоненты: `PosterCard`, `LandscapeCard`, `ContinueCard`, `Hero`, `SectionHeader`, `TvButton`, `TvFilterChip`, `StateViews` | `presentation/tv/ui/components/` | CMP-03 | 3.0 | Preview + Compose UI-тест на семантику |
| CMP-05 | Home на Compose: hero, «Продолжить», ряды; `ComposeView` во фрагменте за флагом | `ThemedHomeFragment`, `HomeScreen` | CMP-04, HOME-01..04 | 3.0 | флаг переключает экран без пересборки |
| CMP-06 | Восстановление фокуса/скролла Home (`BrowseUiState` ↔ `LazyListState`) | `ui/focus/`, `TvHomeViewModel` | CMP-05, FOC-01 | 1.5 | `HOME-FOCUS-01` вручную |
| HOME-01 | `HomeRowsFactory`: порядок §3.1, без системных действий | `presentation/tv/home/` | DOM-04 | 1.0 | unit: порядок рядов и пустые секции |
| HOME-02 | Hero-блок с метаданными и 2 CTA, без autoplay | `ui/components/Hero.kt` | CMP-04 | 1.5 | Preview + ручной фокус |
| HOME-03 | Ряд «Продолжить просмотр» (одна карточка на тайтл) | `ContinueWatchingMapper`, Home | DOM-04 | 1.0 | unit mapper + ручной вид |
| HOME-04 | Новый профиль: скрытие пустых блоков, «Популярное/Новинки» вместо «Для вас» | Home | HOME-01 | 0.5 | unit: пустая история |
| SYS-01 | Единые `Loading/Empty/Error` + retry для TV | `ui/components/StateViews.kt` | CMP-03 | 1.0 | Preview трёх состояний |
| TST-03 | Compose UI-тесты: первый фокус, переход по ряду, клик по карточке | `src/androidTest/**` | CMP-05 | 1.0 | `connectedDebugAndroidTest` (позже) |

### 9.3. Phase 2 — каталоги, детали, «Моё», поиск (26–32 дня)

| ID | Задача | Точки | Зависит | Дни | Проверка |
|---|---|---|---:|---:|---|
| CMP-07 | Rail (`NavigationDrawer`): Главная/Фильмы/Сериалы/Моё/Поиск/Настройки; переход по OK; drawer закрывается Back | `ui/components/TvNavigationDrawer.kt` | CMP-03 | 2.0 | ручной фокус; Back |
| CMP-08 | Экран каталога: сетка `GridCells.Adaptive(124.dp)`, чипы, сортировка | `presentation/tv/catalog/` | CMP-07, CAT-01..03 | 2.0 | Preview + фокус |
| CMP-09 | Детали фильма: backdrop, метаданные, CTA, действия | `presentation/tv/details/` | CMP-04, DET-01/02 | 2.0 | Preview + P0 |
| CMP-10 | Детали сериала: сезоны, эпизоды, CTA `SxxEyy`, статусы | `presentation/tv/details/` | CMP-09, DET-03/04 | 3.0 | Preview + P0 |
| CMP-11 | «Моё»: вкладки «Продолжить/Список/История» | `presentation/tv/library/` | DOM-04 | 2.0 | Preview + пустые состояния |
| CMP-12 | Поиск на Compose: поле, debounce, режимы, пустые состояния | `presentation/tv/search/` | CMP-04, SRCH-01/02 | 2.0 | ручной ввод/фокус |
| CMP-13 | Удаление Leanback: зависимости, неиспользуемые презентеры, тема | `app/build.gradle`, `presentation/tv/**` | CMP-07..12 | 1.0 | сборка без `androidx.leanback` |
| CAT-01 | Раздел «Фильмы»/«Сериалы»: источник данных, пагинация, пустые состояния | `MoviesInteractor` + новый экран | DOM-04 | 1.5 | unit: фильтр по типу |
| CAT-02 | Чипы жанров: `focused` ≠ `selected`, применение по OK, сохранение фильтра | catalog UI + state | CMP-08 | 2.0 | unit state; ручной фокус |
| CAT-03 | Постеры 2:3 (124×186 dp), название 1 строка, placeholder | `PosterCard` | CMP-04 | 1.0 | Preview с длинным названием |
| DET-01 | Backdrop + градиент + метаданные (год/возраст/длительность/жанр) | details UI | CMP-04 | 1.5 | Preview |
| DET-02 | CTA фильма: «Продолжить с MM:SS» / «Смотреть»; «Смотреть сначала» вторично | details + resolver | DOM-05, DOM-06 | 1.0 | unit: подпись CTA по прогрессу |
| DET-03 | CTA сериала + выбор сезона с сохранением фокуса | details + `DetailsUiState` | DOM-05, FOC-02 | 2.0 | unit: CTA = `nextPlayableEpisode` |
| DET-04 | Карточки эпизодов 196×110 dp: номер, название, длительность, статус | EpisodeCard | DOM-04 | 2.0 | Preview трёх статусов |
| DET-05 | Похожие/жанры/актёры (если данные есть) | details | DET-01 | 1.0 | скрывается при отсутствии данных |
| DET-06 | Ошибки/пустота деталей: поток недоступен, тайтл удалён, retry | details + StateViews | SYS-01 | 1.0 | Preview состояний |
| SRCH-01 | Единый вход в поиск; каталог/сайт — режим внутри экрана | rail + search | CMP-07 | 1.5 | ручной |
| SRCH-02 | Debounce 300 мс, отмена, loading/empty/error | search VM | SRCH-01 | 1.0 | unit: out-of-order ответы |
| SRCH-03 | Подтверждение записи карточки из поиска по сайту | site search | SRCH-01 | 1.0 | ручной диалог |
| FOC-03 | Хелперы восстановления фокуса/скролла для Compose-списков | `ui/focus/` | CMP-02 | 1.0 | unit: index/offset |
| FOC-04 | Прогон сценариев §9.2 на всех TV-экранах | — | CMP-09..12 | 0.5 | чек-лист |

### 9.4. Phase 3 — Media3 и плеер (15–19 дней)

| ID | Задача | Точки | Зависит | Дни | Проверка |
|---|---|---|---:|---:|---|
| PLY-01 | media3 1.4.1, общий `PlaybackController`, TV-плеер на нём | `playback/`, `TvPlayerFragment` | — | 3.0 | сборка; ручной HLS/DASH |
| PLY-02 | Мобильный плеер на общем ядре, сохранить PiP/жесты | `PlayerViewFragment` | PLY-01 | 2.0 | ручной прогон mobile |
| PLY-03 | Compose-оверлей TV: контролы, seek, панель настроек | `presentation/tv/player/` | PLY-01, CMP-04 | 3.0 | Preview + ручной |
| PLY-04 | Панель «Следующая серия» + таймер + prefs автоперехода | player + `Prefs` | DOM-05, PLY-03 | 1.5 | unit: отмена/истечение; ручной |
| PLY-05 | Дорожки/субтитры/качество, предпочтения по профилю | `PlaybackController`, `Prefs` | PLY-01 | 2.0 | ручной (потоки с 2+ дорожками) |
| PLY-06 | Resume-предложение: «Продолжить с MM:SS» / «Сначала» | player + policy | DOM-06 | 1.0 | unit + ручной |
| PLY-07 | Skip intro — только при наличии `introStart/End`; иначе задача не берётся | resolver/player | PLY-03 | 1.5 | unit: отсутствие метаданных → кнопки нет |
| PLY-08 | Финальные состояния: буферизация, ошибка, нет источников, lifecycle | player | PLY-01..07 | 1.0 | чек-лист §5.7 |
| PLY-09 | «Открыть во внешнем плеере»: `ACTION_VIEW` на выбранном `PlayableRef`, скрытие пункта при отсутствии обработчиков, кнопка на деталях и в панели плеера | `playback/ExternalPlayerLauncher.kt` (новый), `DetailsScreen`, `TvPlayerFragment`, `PlayerViewFragment` | PLY-01, DOM-01 | 1.5 | unit: выбор URL и содержимое Intent, нет обработчиков → пункт скрыт; ручной прогон VLC/MX на телефоне и проверка отсутствия плееров на TV |
| TST-04 | Ручной UX-прогон плеера по матрице §13 | — | PLY-08 | 1.0 | чек-лист |

### 9.5. Phase 4 — системная интеграция (10–13 дней)

| ID | Задача | Точки | Зависит | Дни | Проверка |
|---|---|---|---:|---:|---|
| SYS-02 | Настройки плеера/автоперехода/дорожек, очистка истории | `presentation/settings/` | PLY-04/05 | 1.0 | ручной |
| SYS-03 | Watch Next: публикация незавершённых и следующей серии, обновление только затронутых | `presentation/services/` + TvProvider | DOM-03/05 | 3.0 | проверка на TV-лаунчере (позже) |
| SYS-04 | Глобальный поиск: `searchable.xml`, `ACTION_SEARCH`, вход в экран поиска | manifest/res/xml | CMP-12 | 1.5 | `adb shell am start -a android.intent.action.SEARCH` (позже) |
| SYS-05 | Deep links `title/{id}`, `watch/{playableId}`; shared URL на TV | manifest, nav graph | DOM-01 | 2.0 | unit parse + ручной |
| SYS-06 | `profileId` в prefs/истории, один профиль | prefs/data | DOM-02 | 1.0 | unit: смена профиля |
| SYS-07 | Baseline profiles + macrobenchmark запуска | `baseline-prof.txt`, benchmark module | CMP-13 | 1.0 | сборка; замер позже |
| SYS-08 | A11y и контраст: `contentDescription`, `textLabel`/`text_hint`, 4.5:1 | res + components | CMP-04 | 1.5 | ручной аудит контраста |
| SYS-09 | Изображения под размер и плотность (постер/backdrop/still), 4K | Coil/Glide | CMP-05 | 0.5 | проверка запросов в логах |

### 9.6. Phase 5 — мобильный паритет (11–14 дней)

| ID | Задача | Точки | Зависит | Дни | Проверка |
|---|---|---|---:|---:|---|
| MOB-01 | Вкладки: добавить «Фильмы/Сериалы/Поиск» (5) или объединить в «Каталог» (4) | `bottom_nav_menu.xml`, nav graph | — | 2.0 | ручной Back/состояние вкладок |
| MOB-02 | Каталог: 2 колонки, чипы, сортировка, прогресс в карточках | mobile UI | DOM-04 | 2.0 | ручной |
| MOB-03 | Детали сериала: BottomSheet сезона, строки серий со статусом | `DetailsFragment` | DOM-05 | 2.5 | ручной |
| MOB-04 | Поиск-экран с debounce и пустыми состояниями | mobile UI | DOM-04 | 1.0 | ручной |
| MOB-05 | Политика PiP/фона и следующей серии на общем ядре | `MainActivity`, player | PLY-01/04 | 2.0 | ручной |
| MOB-06 | `WindowSizeClass`: 2/3–6 колонок, rail на >= 600 dp, без двухпанельного MVP | layouts | MOB-02 | 2.0 | Preview/эмулятор позже |
| MOB-07 (сделано, срез 10) | Планшетные постеры: `spanCount` из ширины (180 dp на карточку, 2…6 колонок) вместо `GridLayoutManager(context, 2)`, Glide `override` по размеру ячейки, одинаковый 2:3 на Home/Избранное/История | `HomeFragment` (`GridLayoutManager(context, 2)`), `i_home_video.xml`, `AndroidExtentions` (`override(200, 200)`), `FavoritesFragment`, `HistoryFragment` | MOB-02 | 1.5 | unit: `spanCount` для 360/600/840 dp; эмулятор планшета — карточки не крупнее 220 dp по ширине |

### 9.7. Phase 6 — gate

| ID | Задача | Условие | Дни |
|---|---|---|---:|
| GATE-01 | Решение по мобильному Compose и сплиту `app-tv`/`app-mobile` | TV-UI на Compose, `:core-domain` выделен, мобильные экраны стабильны | 0.5 |
| GATE-02 | При положительном решении — `app-mobile` (Material3) и перенос экранов | GATE-01 | 15–20 |

---

## 10. Тест-план

### 10.1. Unit-тесты (без устройства)

| Тест | Что проверяет |
|---|---|
| `PlayableIdTest` | film/episode id, fallback на индексы, стабильность, парсинг legacy |
| `NextEpisodeResolverTest` | S01E10 → S02E01; последняя → `Restart`; нет следующей → `Unavailable`; сезон 0/specials; пропуск недоступной серии; нет истории → первая доступная |
| `ProgressCompletionPolicyTest` | 95 %/30 с; не завершено при 90 % и 10 мин остатка; короткий эпизод; `creditsStartMs`; clamp `position > duration` |
| `ContinueWatchingMapperTest` | одна карточка на сериал; берётся последняя незавершённая; завершённые исключены; сортировка по `updatedAt` |
| `LegacyProgressMigrationTest` | legacy-строка → реальный `playableId`; позиция сохраняется; duration = 0 |
| `BrowseStateRestorerTest` | rowId/titleId → индекс; удалённый ряд → ближайший; пустой state |
| `CatalogFiltersTest` | out-of-order ответы не перезаписывают актуальный (flatMapLatest) |
| `HomeRowsFactoryTest` | порядок §3.1; пустые блоки скрыты; профиль без истории |
| `Migration7To8Test` (androidTest) | схема, индексы, backfill legacy-строк |

### 10.2. Compose UI-тесты (позже, на устройстве/эмуляторе)

| Тест | Сценарий |
|---|---|
| `HomeFocusTest` | первый фокус виден; Right внутри ряда; Down на тот же индекс |
| `FocusRestoreTest` | Home → Details → Back возвращает карточку |
| `FilterChipTest` | фокус не меняет выдачу, OK — меняет; `selected` отображается |
| `SeriesCtaTest` | CTA соответствует resolver при разных прогрессах |
| `PlayerPanelTest` | next-episode панель: OK/Back/таймер, нет автозапуска при меню |

### 10.3. Ручная матрица

- P0 из §13 ТЗ — до релиза каждой фазы; для Phase 0–2 дополнительно проверяются `HOME-FOCUS-01`,
  `CAT-FILTER-01`, `SERIES-FOCUS-02`, `SERIES-NEXT-03/04`, `POSTERS-01`.
- P1 из §13 — перед первым публичным релизом Compose-версии.
- Мобильная матрица §30.1 — при Phase 5.
- Устройства: TV-эмулятор 1080p (позже), недорогая приставка, второе TV-устройство/телефон;
  сейчас — только сборка и unit-тесты.

### 10.4. Команды проверки

```bash
./gradlew :app:assembleDebug          # сборка
./gradlew :app:test                   # unit-тесты (JUnit5)
./gradlew :app:lintDebug              # статический анализ
./gradlew :app:assembleRelease        # R8/подпись, перед релизом
```

Замечание: в `app/build.gradle` задан `testBuildType = "release"` — это влияет на instrumentation-тесты,
не на unit. Перед добавлением Compose UI-тестов проверить, что `androidTest` собирается на release-варианте.

---

## 11. Риски и меры

| Риск | Вероятность | Влияние | Мера |
|---|---|---|---|
| Миграция Room повредит историю пользователей | средняя | высокое | миграция + backfill + `Migration7To8Test`; релиз с бэкапом БД (экспорт перед обновлением) |
| Compose-фокус ведёт себя иначе, чем Leanback | высокая | высокое | спайк CMP-02; вертикальный срез Home; ручной прогон P0 до перевода остальных экранов |
| Двойная работа из-за Leanback | средняя | среднее | Leanback только дефекты; UX — сразу в Compose |
| Media3 ломает HLS/DASH-источники | средняя | высокое | миграция TV первой; тест на реальных потоках; возможность откатить зависимость до релиза |
| Прогресс «уезжает» на сериях без URL | уже есть | высокое | DOM-07 + тест на пропуск; resolver по `playableId` |
| Автопереход запускается не вовремя | средняя | среднее | PLY-04: таймер отменяется при меню/ошибке/фоне; тест |
| Размер APK растёт (tv-material + Compose + Leanback) | высокая | низкое | CMP-13 удаляет Leanback; R8; замер на каждом релизе |
| Track B потребует подъёма Kotlin/compileSdk | средняя | среднее | не совмещать с миграцией; отдельная задача после Phase 3 |
| Мобильная часть отстаёт и ломает общие модели | средняя | среднее | сначала DOM-*, потом MOB-*; общий playback-контроллер |
| Нет времени на ручные TV-прогоны | высокая | высокое | P0-чек-лист в каждой фазе; эмулятор/приставка в конце фазы, не «потом» |
| Долгая фаза 2 (26–32 дня) | средняя | среднее | разбить на релизы: каталоги (2.1), детали (2.2), поиск/«Моё» (2.3) |
| Источник не отдаёт длительность/таймкоды | высокая | среднее | duration = 0 → процент скрыт; skip intro не включаем (D8) |

---

## 12. Релизная стратегия

1. Ветка на фазу, минорный bump (`1.7.0` … `1.12.0`), тег и GitHub Release — процесс уже работает.
2. Compose-экраны включаются флагом; при регрессии флаг выключается без отката релиза.
3. Каждый релиз содержит: `assembleRelease`, `apksigner verify`, unit-тесты, ручной P0-чек-лист.
4. Перед Phase 2 — бэкап/экспорт БД в инструкции релиза (миграция v8 необратима).
5. AAB публикуется вручную; CI собирает артефакты по тегу (как в v1.6.5).
6. Метрики после релиза: crash-free sessions, время до первого кадра, доля успешных resume,
   число ручных «next episode» отмен (косвенно — качество таймера).

---

## 13. Что не входит в план

- Трейлеры с автоплагином/autoplay на главной, motion-background.
- Персональные рекомендации сложнее текущих эвристик.
- Детские профили и родительский контроль.
- Аккаунты, серверная синхронизация TV ↔ phone, конфликт-резолюция (§28) — нет сервера.
- Cast и новые офлайн-функции (загрузки уже есть; Cast — отдельный проект).
- Полный редизайн мобильного UI на Compose (gate Phase 6).
- 3D/poster effects, тяжёлые анимации переходов.

---

## Приложение A. Проверенные версии и источники

| Факт | Значение | Источник |
|---|---|---|
| Проект | Kotlin 2.0.21, AGP 8.7.3, compileSdk/targetSdk 34, minSdk 24 | `gradle/libs.versions.toml` |
| Compose BOM 2024.02.02 | UI/Foundation 1.6.1, Material3 1.2.1 | pom BOM (Gradle-кэш) |
| Compose BOM 2024.06.00 | UI/Foundation 1.6.8, Material3 1.2.1 | pom BOM (Google Maven) |
| tv-material 1.0.1 | Compose 1.6.8, `minCompileSdk=34`, stdlib 1.8.22 | `tv-material-1.0.1.module` + `aar-metadata.properties` (кэш) |
| tv-material 1.1.0 | Compose 1.10.3, `minCompileSdk=35`, AGP ≥ 8.6.0, stdlib 2.1.20 | `tv-material-1.1.0.module` + AAR (Google Maven) |
| Compose BOM 2026.03.00 | UI/Foundation 1.10.5, Material3 1.4.0 | pom BOM (кэш) |
| media3 1.4.1 | `minCompileSdk=34` | AAR metadata |
| media3 1.5.1 | `minCompileSdk=35` | AAR metadata |
| Состав tv-material 1.0.1 | Card, Button, WideButton, Chip, TabRow, ListItem, Carousel, NavigationDrawer, Switch, Surface | `classes.jar` AAR |
| Текущий плеер | ExoPlayer2 2.19.1, Leanback 1.0.0 | `libs.versions.toml` |
| БД | Room 2.6.1, schema v7 | `AppDatabase.kt` |

## Приложение B. Трассировка ТЗ → задачи

| Раздел ТЗ | Задачи |
|---|---|
| §2 IA, rail, Back | FND-05, CMP-07, CMP-13 |
| §3 Главная | HOME-01..04, CMP-05/06 |
| §4 Каталоги | CAT-01..03, CMP-08 |
| §5 Детали фильма | DET-01/02/05/06, CMP-09 |
| §6 Детали сериала | DET-03/04, DOM-05, CMP-10 |
| §7 Плеер | PLR-01..04, PLY-01..08 |
| §8 Модель/прогресс | DOM-01..08 |
| §9 Состояние/фокус | FOC-01..04, CMP-06 |
| §10 Токены | FND-01/02, CMP-03 |
| §11 Сеть/загрузка | SYS-01/09, PLY-08 |
| §12 Организация кода | CMP-03/04, PLY-01 |
| §13 UX-тесты | TST-01..04 |
| §15 Системная интеграция | SYS-03/04/05 |
| §19–§25 Mobile | MOB-01..06, PLY-02 |
| §26 Архитектура | D1, PLY-01, GATE-01/02 |
| §27 Compose | CMP-01..13 |
| §28 Профили/синхронизация | SYS-06 (локально) |
| §29 Сборка/манифест | FND-04, SYS-04/05 |
| §30–§33 Приоритеты | §8 фаз, §10 тест-план |
