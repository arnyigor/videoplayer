# Netflix-подобный видеосервис: план доработок Android Phone + Android TV

**Версия:** 2.1 · **Дата:** 2026-10-21 · **Формат:** пошаговая техническая спецификация для разработчика или coding-agent

> **Область документа:** это практическое продолжение `Android_TV_Android_Phone_Netflix_UX_UI_Spec_v2_0.md`. План основан на предоставленном отчёте, а не на самостоятельном аудите исходников репозитория. Имена новых интерфейсов и пакетов ниже — предлагаемые контракты, их необходимо адаптировать к реально существующим классам. Не делать вид, будто классы или файлы уже существуют, пока они не обнаружены в проекте.

## 0. Зафиксированная отправная точка

| Область | Фактический статус | Решение |
|---|---|---|
| `MOB-13` | MobileTheme, MobileDimens, 5 компонентов с Preview готовы | Сохранить, проверить полноту токенов |
| `MOB-07` | Реализовано, на планшете не проверено | Отдельная визуальная приёмка |
| `MOB-09.1` | ContinueCardMapper: SxxEyy, доля, оставшееся время | Сохранить, дополнить boundary-тесты |
| `MOB-09.2` | MobileHomeRowsFactory: порядок и пустые ряды | Сохранить, добавить fixture/golden тесты |
| `MOB-10` | Mobile player пишет в общий PlaybackProgressStore, читает resume из history | Завершить чтение из общего стора, затем удалить дублирование |
| `MOB-08` | 5 вкладок не начаты | Реализовать с сохранением вкладок и scroll |
| `MOB-09.3` | Compose-главная не начата | Выполнить после единого прогресса и shell |
| `MOB-11/12/14`, `PLY-01/02/09` | Не начаты по отчёту | Сопоставить с реальным task tracker перед изменениями |
| Unit tests | 134/134, `:app:assembleDebug` прошёл | Сохранить как baseline |
| `MoviePlayablesTest` | Legacy coordinates → канонические `playableId` проверены | Обязательный регрессионный тест |

**Стоп-правило:** прежде чем менять playback-flow, создать воспроизводимый regression test для потери/дублирования прогресса. Оставить текущий путь запуска как работающий fallback до подтверждения нового.

## 1. Целевое устройство системы

```text
                         Android app process
                  ┌──────────────────────────────┐
                  │ Mobile App Shell (Material3) │
                  │ 5 tabs / details / search    │
                  │ Mobile Player UI (Compose)   │
                  └──────────────┬───────────────┘
                                 │ StateFlow / UI actions
                  ┌──────────────▼───────────────┐
                  │ ViewModels / Interactors      │
                  │ catalog, resume, next episode │
                  └──────────────┬───────────────┘
                                 │
            ┌────────────────────┼───────────────────┐
            ▼                    ▼                   ▼
   CatalogRepository      PlaybackProgressStore   PlaybackController
   title/season/episode    canonical playableId    Media3 Player API
   stable ordering         account/profile scope   events + engine
            │                    │                   │
            └────────────────────┴───────────────────┘
                                │
                    common domain/data boundary
                                │
                  TV shell (TV Material + D-pad)
```

**Важные архитектурные решения:**

1. UI на телефоне — `androidx.compose.material3`, UI на TV — `androidx.tv:tv-material`. Не смешивать темы и Material-компоненты в одном Compose-дереве.
2. Доменный прогресс, идентификаторы тайтлов, бизнес-логика выбора следующей серии, репозитории и сетевые контракты — общие.
3. Плеер должен управляться через `androidx.media3.common.Player`/контроллер, а не через методы конкретного Composable.
4. Разделение на физические Gradle-модули — отдельная работа после стабилизации; пока существует `:app`, не устраивать рискованный переезд в рамках UI-правок.
5. Netflix-like — описание информационной архитектуры и UX-паттернов, а не копирование фирменных логотипов, графики или закрытых компонентов.

## 2. Порядок выполнения: 15 отдельных изменений

| № | ID для MR/коммита | Приоритет | Содержание | Зависимость |
|---:|---|---|---|---|
| 0 | `AUD-01` | P0 | Снять исходный baseline, обследовать текущий player/history/store | Нет |
| 1 | `PROG-01` | P0 | Интеграционные тесты записи позиции и playableId | AUD-01 |
| 2 | `MOB-10A` | P0 | Читать resume из `PlaybackProgressStore` | PROG-01 |
| 3 | `MOB-10B` | P0 | Удалить двойную запись, обеспечить одну точку сохранения | MOB-10A |
| 4 | `MOB-08A` | P0 | Пять вкладок, back stacks, Back и edge-to-edge | MOB-10A |
| 5 | `MOB-09C` | P0 | Compose Home: hero, continue, rows, state | MOB-08A, MOB-10B |
| 6 | `CAT-01` | P0 | Фильмы/сериалы: сетка, фильтры, сохранение скролла | MOB-08A |
| 7 | `DETAIL-01` | P0 | Детали фильма и сериала, сезоны, эпизоды | CAT-01, MOB-10A |
| 8 | `PLY-AUD` | P0 | Проверить существующий видеодвижок и способы показа UI | AUD-01 |
| 9 | `PLY-01` | P0 | Единый Media3 PlayerAdapter и конфигурация MediaItem | PLY-AUD |
| 10 | `PLY-02` | P0 | Compose-плеер: контролы, seek, tracks, lifecycle | PLY-01 |
| 11 | `PLY-09` | P0 | Серии, end-of-episode, skip intro, error recovery | PLY-02, DETAIL-01 |
| 12 | `MOB-SEARCH` | P1 | Поиск, пустые состояния, отмена запросов | MOB-08A |
| 13 | `MOB-RESP` | P1 | Планшет, foldable, landscape, insets, accessibility | UI-фазы |
| 14 | `QA-REL` | P0 | Матрица E2E, производительность, регрессии TV/phone | Всё P0 |

> ID `CAT-01`, `DETAIL-01` и прочие — предложения для этого плана. Существующие `MOB-11`, `MOB-12`, `MOB-14` нужно связать с ними по реальным определениям задач, не перенумеровывать существующие статусы вслепую.

---

## 3. AUD-01 — инвентаризация без изменений поведения

**Осмотреть и записать:**

- Где создаётся текущий player, кто его освобождает, есть ли `ExoPlayer`, старый `com.google.android.exoplayer2`, Media3, WebView, VLC или другой механизм.
- Кто формирует URL потока, MIME, заголовки/авторизацию, DRM, субтитры, варианты качества, сроки жизни ссылки.
- Точки записи и чтения `PlaybackProgressStore`, history, resume в деталях и в проигрывателе.
- Есть ли два одновременных таймера сохранения, запись из UI и из player callback одновременно.
- Как обрабатываются сериал, фильм, сезон, порядок эпизодов и `legacy coordinates`.
- Каковы фактические навигационная библиотека, Koin/Hilt, Room migration и lifecycle policy.
- Есть ли физический TV UI в том же `:app` или отдельное приложение; не предполагать заранее.

**Результат:** схема текущего data flow, список точек вызова, контракт прогресса, список устаревших API, известные несовместимости; без рефакторинга «заодно».

**Проверки:** `./gradlew :app:assembleDebug` и `./gradlew :app:testDebugUnitTest`; зафиксировать 134 baseline-теста как историческое значение (после новых тестов их должно стать больше).

## 4. PROG-01 — зафиксировать поведение прогресса тестами

### 4.1. Источник истины

**Ключ записи:** `(profileId, playableId)`; если архитектура поддерживает несколько аккаунтов в одном хранилище, добавить `accountId` в ключ/изоляцию. `movieId`, `(seasonIndex, episodeIndex)` и видимые подписи не должны быть первичными ключами прогресса.

Предлагаемый логический контракт:

```kotlin
// Пример интерфейса; адаптировать к существующей модели и DAO.
data class PlaybackProgress(
    val profileId: String,
    val playableId: String,
    val positionMs: Long,
    val contentDurationMs: Long?,
    val completed: Boolean,
    val updatedAtMs: Long
)

interface PlaybackProgressRepository {
    fun observe(profileId: String, playableId: String): Flow<PlaybackProgress?>
    fun observeContinueWatching(profileId: String): Flow<List<PlaybackProgress>>
    suspend fun save(progress: PlaybackProgress)
}
```

`contentDurationMs == null` допустим, если длина неизвестна. Нельзя записывать `C.TIME_UNSET`, отрицательные числа или `0` как реальную длительность.

### 4.2. Unit/integration проверки

| ID | Given → When → Then |
|---|---|
| `PROG-UT-01` | Movie → 2 мин просмотра → запись фильма с правильным playableId |
| `PROG-UT-02` | S02E03 → запись canonical `ep:...`, без координат в ключе |
| `PROG-UT-03` | Сезоны и серии приходят из API не по порядку → сохраняется верный playableId |
| `PROG-UT-04` | Координата отсутствует → null/ошибка, никакой записи другого эпизода |
| `PROG-UT-05` | Два одинаковых save подряд → один логический результат (upsert) |
| `PROG-UT-06` | Pause, затем Activity stop → нет регрессии positionMs к более старой |
| `PROG-UT-07` | Seek backward → меньшая позиция может быть корректной, policy не делает `max(position)` |
| `PROG-UT-08` | Switch profile → новая история не содержит старый профиль |
| `PROG-UT-09` | Потеря сети / ошибка storage → событие не рушит UI, ошибка обрабатывается |
| `PROG-UT-10` | Progress complete → корректный следующий эпизод в «Продолжить» |

Использовать **контролируемые fakes** для стора, часов, resolver/каталога и player events. При возможности добавить `androidTest` против реальной Room test DB, чтобы проверять сериализацию и upsert.

### 4.3. Обязательный ручной прогон на устройстве

1. Очистить данные тестового профиля или создать изолированный профиль.
2. Запустить фильм, посмотреть 60–120 секунд.
3. Нажать паузу → Back. Проверить фактическую строку `playback_progress` через Database Inspector/тестовый debug-экран, **не выдавать содержимое продакшен-профиля в логи**.
4. Перезапустить приложение, открыть «Продолжить», сравнить playableId, позицию, долю, подпись.
5. Повторить для эпизода из сезона 2 с нетривиальным порядком каталога.
6. Выйти по Home, свернуть, повернуть устройство, убить процесс и вновь проверить resume.

**Done:** нет неверных playableId; один просмотр приводит к одной актуальной логической записи; UI отображает актуальное состояние.

## 5. MOB-10A — перевести resume с history на общий store

**Обязательный порядок действий:**

1. Найти *все* вызовы `getResume`/`getHistory`/`continueWatching` мобильного плеера, карточки деталей, стартовых CTA.
2. Добавить `GetResumeTarget(profileId, titleId)` или расширить существующий use case.
3. Получать прогресс только по каноническому `playableId`; сериалы сопоставлять через проверенный `MoviePlayables` mapper.
4. Рассчитать `ResumeDecision`: ResumeCurrent, PlayNextEpisode, StartFromBeginning, Unavailable. Не класть решение в Composable.
5. Пустой store → осознанный fallback на history **только в период миграции**, с отдельным логом/счётчиком без личных данных. Это не должна быть бессрочная двойная бизнес-логика.
6. Сохранение состояния и подписка через `StateFlow`, с отменой старых запросов при смене фильма/профиля.
7. Не сдвигать позицию молча на новую серию при просмотре незавершённой.

**Policy для resume:**

- `positionMs < 0` → invalid; `C.TIME_UNSET` → invalid.
- Не выполнять seek за пределы известной и проверенной длительности.
- Если контент завершён → переход к следующему доступному эпизоду или предложение пересмотра (для фильма).
- Если серия удалена из каталога → выбрать предсказуемый fallback и показать статус доступности.
- Порог завершения по доле/секундам согласовать с продуктовой логикой; не считать `progress >= 90%` универсальным фактом завершения.
- На точности HLS и рекламы см. раздел 14.

**Тесты:** отсутствие записи, частичный фильм, частичная серия, завершённая серия, законченный сезон, неизвестный эпизод, смешанный порядок, обновление профиля, конкурентное обновление через Flow.

**Done:** основное чтение мобильного resume идёт из `PlaybackProgressStore`, E2E на устройстве зелёный; TV-путь не меняется без тестов.

## 6. MOB-10B — удалить двойную запись

**Цель:** единственный владелец persistence политики, не обязательно единственное входящее событие.

```text
Player.Listener + player position sampling
                 ↓
       ProgressRecorder/Coordinator
                 ↓
     PlaybackProgressRepository
                 ↓
        Room/DAO + Flow UI
```

**Действия:**

1. Собрать все write call sites; выбрать один `ProgressRecorder`.
2. Каждое событие помечать `playableId`, `profileId`, `playbackSessionId`, типом (tick/pause/stop/end/seek).
3. `tick` записывать экономно (например, каждые 10–20 секунд *воспроизведения*, значение настроечное); финальные stop/pause/end — сразу.
4. Не создавать новый таймер при каждой рекомпозиции или смене ориентации.
5. Устранить гонку `late tick` после `pause/end`: сериализация событий в coordinator, учитывая session generation и event order.
6. Для разных playbackSessionId не использовать слепой `max(position)`, поскольку перемотка назад является допустимым намерением.
7. Удалить legacy history writer только когда history не является необходимым источником другой функции. Если history — пользовательская история просмотра, оставить **проекцию из канонического progress**, но не два независимых источника позиции.
8. Добавить диагностику `progress_write_count`, `resume_source`, `save_reason` без URL потоков и токенов.

**Тесты:** 1 pause → не 2 записи; вращение экрана → нет дополнительного writer; окончание эпизода → completed не перезатирается старым tick; переключение серии → старая запись не пишет в новую; конфликт обновлений → порядок детерминирован.

**Done:** один owner записи/прочтения; двойная запись ликвидирована; сквозной просмотр выдержан.

---

## 7. MOB-08A — пять вкладок и надёжная мобильная навигация

**Вкладки:** Главная, Фильмы, Сериалы, Моё, Поиск. Профиль и настройки — из аватара или отдельного действия в верхней панели; не делать шестую вкладку.

**Compose-компоненты:** `Scaffold`, `NavigationBar`, `NavigationBarItem`, Navigation Compose **или** Navigation 3 — выбрать *одну* библиотеку, исходя из текущего проекта. Переход на Navigation 3 сам по себе не нужен ради красоты. Для больших окон — adaptive navigation suite/rail.

**Поведение:**

- Each top-level tab: собственный back stack, собственный `LazyListState`/`LazyGridState`, выбранные фильтры и поисковый запрос.
- Детали тайтла и полный player скрывают нижнюю навигацию; плеер не наследует случайно Insets `Scaffold`.
- Back: Sheet → child details → tab root → системный выход согласно выбранной политике; не делать Back с tab root закрытием приложения из случайного состояния.
- Повторное нажатие текущей вкладки: выбрать политику «прокрутить вверх» и тестировать; не очищать все состояния.
- Не создавать ViewModel заново при каждой смене вкладки; сохранять state согласно lifecycle/nav entry.
- Передавать в route IDs (`titleId`, `playableId`), а не сериализованные гигантские Movie объекты или stream URL.
- Диплинки и внешние открытия — после проверки авторизации и существования тайтла.

**Тесты:** `NAV-01` переход между всеми вкладками, `NAV-02` Home → 20-й постер → Details → Back, `NAV-03` фильтры и прокрутка фильмов переживают вкладку, `NAV-04` process death через restore, `NAV-05` keyboard Back с Search, `NAV-06` detail CTA открывает верный playableId, `NAV-07` sheet при Back закрывается первым.

**Done:** вкладки работают на физическом телефоне, не теряют scroll/filter, Back одинаков при gesture и hardware/software button.

## 8. MOB-09C — главная Netflix-like на Compose

**Рекомендуемый порядок (из v2.0 §20.1):**

1. Компактный TopAppBar: аватар/название, Cast только когда реализован.
2. Featured Hero одного рекомендованного тайтла, с контрастным фоном и CTA; без autoplay-трейлера в MVP.
3. `Продолжить просмотр` **если есть валидный progress**; высоко на экране.
4. `Мой список` если не пуст.
5. `Рекомендации` если доступны.
6. `Новинки`, `Популярное`, жанровые ряды из фабрики.

**Переиспользовать:** `MobileHomeRowsFactory` и `ContinueCardMapper`, не повторять их условия в UI.

**Composable-план:** `MobileHomeScreen(state, actions)`, `FeaturedHero`, `ContinueWatchingRow`, `PosterRow`, `LandscapeRow`, `HomeLoadingSkeleton`, `HomeEmptyState`, `HomeErrorSection`. Компоненты должны быть stateless (или хранить только собственный мелкий UI state), доступными для Preview и screenshot-тестов.

**Расположение / tokens, проектные стартовые значения:**

| Параметр | Compact phone | Tablet / expanded |
|---|---|---|
| Общий горизонтальный отступ | 16dp | 24–32dp |
| Hero | ограниченный по высоте, CTA в зоне видимости | широкий hero, не заслонять контент |
| Gap карточек | 8–12dp | 12–16dp |
| Gap рядов | 20–28dp | 24–36dp |
| Постер в каталоге | 2 колонки, 2:3 | adaptive 3–6 колонок |
| Серия/продолжение | 16:9, информация вне превью | 16:9, больше метаданных |
| Touch target | ≥48dp | ≥48dp |

**Continue card:** показать постер/кадр, название сериала, `SxxEyy`, полосу прогресса, «Осталось N мин» при достоверной длительности. Tap запускает **продолжение конкретного playableId**, при этом отдельный доступный путь открывает детали. Не скрывать delete/remove за единственным long press.

**Состояния:** offline cached, skeleton фиксированной геометрии, ошибка отдельного ряда, пустая лента, fallback artwork, long Russian title. Запрет на каскадную перезагрузку всех рядов после `progress` tick.

**Тесты:** порядок рядов, пустые секции скрыты, запись прогресса проявляется в первой карточке, Back восстанавливает позицию, обновление rows не телепортирует scroll, screenshot-тесты compact/expanded, TalkBack семантика карточек.

## 9. CAT-01 — каталоги «Фильмы» и «Сериалы»

**UI:** заголовок, фильтр-чипы, сортировка, адаптивная `LazyVerticalGrid`, постеры 2:3, служебная зона для ошибок/пустого результата, пагинация при достижении порога.

**Конкретные действия:**

1. Единая `TitleCard` со стабильным key `titleId`, а не index.
2. `LazyVerticalGrid` с рассчитанным минимумом ширины карточки; не фиксировать `GridCells.Fixed(2)` для всех окон.
3. `FilterState` в ViewModel/SavedStateHandle; жанр/год/порядок/тип контента.
4. Отмена устаревших запросов через `flatMapLatest`; идентификатор query/filters при обработке ответа.
5. Пагинация с dedup по title ID; не терять видимые карточки при догрузке.
6. Любая карточка открывает детали, не запускает видео неожиданно.

**Тесты:** длинные названия, 0/1/1000 элементов, 403/404/500, быстрое изменение фильтров, дубли страниц, process death и scroll restoration, RTL/TalkBack если поддерживается.

## 10. DETAIL-01 — экран фильма и сериала

**Movie Details:** backdrop 16:9 + контрастный градиент, название, год/длительность/жанры/возраст, CTA «Смотреть/Продолжить», «Мой список», короткое описание, похожее.

**Series Details:** CTA «Продолжить S02E06»; селектор сезона (на телефоне — `ModalBottomSheet`); вертикальные карточки серий с номерами/названиями/длительностью/прогрессом; рекомендациями внизу.

**Логика:**

1. Тайтл, сезон, эпизод имеют **стабильные IDs**. Видимые номера эпизодов не использовать как array index.
2. Следующий эпизод определяется явным порядком каталога (включая specials, пропуски номеров и неполную доступность).
3. CTA принимает `playableId` и `ResumeDecision`, не индекс текущей видимой строки.
4. После переключения сезона сохранять selection при Back, ротации и повторном входе.
5. Недоступные эпизоды показывать как недоступные, а не автоматически запускать соседний.
6. История текущего профиля и доступность от каталога могут обновляться независимо, исключить flicker и замену реального selected season.

**Тесты:** сортировка `2,1` сезонов; эпизоды `2,1`; specials; пустой сезон; 2 серии с одинаковым номером в метаданных; старый `playableId`, удалённая серия; частично просмотренное; серия завершена; права доступа; Back после выбора сезона.

---

## 11. PLY-AUD — стратегия перехода на Media3

**Не заменять работающий player целиком одним коммитом.** Сначала определить, что уже используется:

- Если уже `androidx.media3` — это рефакторинг UI/Player owner, а не повторная миграция движка.
- Если `com.google.android.exoplayer2` — мигрировать на `androidx.media3` по official migration guide и убрать дубли legacy artifacts.
- Если иной playback engine — подготовить совместимый `PlaybackController`, затем заменить реализацию за feature flag.
- Если Media3 есть, но UI View-based — сначала сохранить работоспособный `PlayerView`, отдельно внедрить Compose UI.

**Зависимости Media3** — единой версии, выбранной под текущую Android Gradle Plugin/Compose/SDK-матрицу; не смешивать разные Media3 minor версии:

```kotlin
implementation("androidx.media3:media3-exoplayer:<media3Version>")
implementation("androidx.media3:media3-exoplayer-hls:<media3Version>")  // если нужен HLS
implementation("androidx.media3:media3-exoplayer-dash:<media3Version>") // если нужен DASH
implementation("androidx.media3:media3-session:<media3Version>")        // при необходимости MediaSession
implementation("androidx.media3:media3-ui-compose:<media3Version>")    // custom UI
// ЛИБО вместо предыдущего для готового Material3-плеера:
// implementation("androidx.media3:media3-ui-compose-material3:<media3Version>")
```

`media3-ui-compose-material3` уже зависит от core Compose модуля; оба вручную подключать не обязательно. Для TV не использовать mobile M3-контролы без оценки стилей/фокуса; общий engine допустим, TV UI раздельный.

**Исследовать экспериментально два мобильных прототипа:**

| Вариант | Преимущество | Риск | Выбор |
|---|---|---|---|
| Media3 `Player` (Compose Material3) с slots | Быстрая реализация и стандартные кнопки | Дополнительная настройка под Netflix-like | Предпочтительный старт |
| `ContentFrame/PlayerSurface` + custom controls | Полная свобода дизайна | Больше ответственности за seek, visibility, a11y | Если слотный UI ограничивает UX |

Для видеоповерхности **по умолчанию `SurfaceView`**, а не `TextureView`: это обычно эффективнее по мощности и поддержке DRM/HDR. Выбирать TextureView только после доказанного требования к трансформациям/анимациям/совместимости.

**Spike acceptance:** 3 реальных видео (MP4, HLS VOD, HLS c рекламой или имитированной discontinuity) × поворот × Back × seek; видео не чёрное, нет утечки Surface, один player session.

## 12. PLY-01 — единый playback-controller + MediaItem

**Входной контракт:** `StartPlayback(playableId, profileId, resumePolicy)` → resolver → PlaybackDescriptor → MediaItem → Player. Не передавать `streamUrl` через navigation route или UI state, доступный логированию.

**PlaybackDescriptor** (логический контракт):

```kotlin
// Пример. Подстроить поля под API и типы авторизации.
data class PlaybackDescriptor(
    val playableId: String,
    val uri: String,
    val mimeType: String?,
    val title: String,
    val drm: DrmDescriptor?,
    val subtitleTracks: List<SubtitleDescriptor>,
    val catalogDurationMs: Long?,
    val timelineKind: TimelineKind // VOD, LIVE, VOD_WITH_ADS
)
```

**Изолировать:** создание Player, MediaSourceFactory, DataSource.Factory, headers, DRM, audio focus, wake mode, callbacks, error mapping, cleanup и track preferences. `Player` один на playback session; не создавать его в `@Composable` без контролируемого владельца.

**Lifecycle policy для MVP:** при уходе из foreground — пауза и сохранение позиции (если PiP/фон не разрешены). При повороте/конфигурации — не пересоздавать playback session из-за одного UI изменения. При реальном завершении — release. При media session/background-политике определять owner отдельно.

**Player events:** player state, isPlaying, ready/buffering/ended, errors, timeline changes, media item transitions, track changes. Секвенс событий должен обрабатываться один раз. Не переписывать `progress` при `STATE_BUFFERING` как завершение.

**Tests:** один Player на сеанс, ошибка prepare, повторный click Play, заменённая URI, DRM не доступен, таймаут, audio focus interruption, stopped session, HLS unknown duration, live seek not allowed.

## 13. PLY-02 — мобильный Compose-плеер

**Слои:**

```text
FullscreenContainer (edge-to-edge)
  Video ContentFrame/PlayerSurface
  Scrim/TopControls: back + film/SxxEyy + Cast (если реализован)
  CenterControls: rewind / play-pause / forward
  BottomControls: seekbar, played / duration, tracks, subtitles, settings
  Overlay: buffering / error / next episode / skip intro
  BottomSheet: audio/subtitles/quality
```

**Управление:**

- Tap по видео показывает или скрывает контролы; время автоисчезновения проектно 3–5 сек при playing, не скрывать во время drag/selection.
- ±10 секунд для быстрых кнопок; long drag по слайдеру с предварительным position preview; seek применять только по завершении drag (или throttled) без лавины вызовов.
- Отдельные состояния: paused, playing, buffering, seeking, ended, error. Не смешивать paused и buffering.
- Выбор дорожек и субтитров по реальному `Tracks` Media3; сохранять предпочтение по профилю/языку, валидировать доступность в каждом MediaItem.
- По умолчанию ABR/«Авто», вручную выставлять качество только при наличии вариантов и корректно сбрасывать override.
- Поддержать captions поверх видео с правильными insets и доступностью.
- Полноэкранный landscape: предсказуемое возвращение в portrait; orientation lock не навязывать всему приложению.
- Portrait embedded-player (если выбран): не запускать второй ExoPlayer при переключении в fullscreen.
- System Back: сначала закрыть overlays/sheet, потом fullscreen, потом player — без неожиданного выхода из приложения.

**Порядок UI-миграции:** создать Compose shell → подключить к уже работающему controller → внедрить базовые контролы → заменить View UI, только когда паритет подтверждён → удалить временный путь.

**Тесты на реальном устройстве:** MP4, HLS, плохая сеть, seek, rotation 10×, phone call/audio focus, гашение экрана, home/back, дорожки, субтитры, поток недоступен. Наблюдать рендер первого кадра, память, число active players и вызовов разрешения URI.

## 14. CRITICAL — HLS, рекламные вставки и достоверная длительность

**Проблема:** `catalogDurationMs`, `player.duration`, `player.currentPosition`, `contentPosition` могут описывать разные временные шкалы.

Разделить:

1. **Catalog duration** — нормативная длительность фильма/эпизода из каталога, нужна для информации «1 ч 25 мин».
2. **Playback timeline duration** — длительность текущего MediaItem, может включать вставки/окна/сдвиги HLS.
3. **Content position** — позиция в основном контенте, определяемая player при интегрированной рекламе.
4. **Persisted content position** — стабильная координата resume; её контракт должен соответствовать seek API и способу рекламной интеграции.

Для **client-side ads, интегрированной с Media3**, оценить `player.contentPosition` / `contentDuration`; во время рекламы `currentPosition` может относиться к самой рекламе. Для **SSAI/просто склеенного HLS** Media3 не всегда может автоматически вычесть длительность рекламных сегментов. Может потребоваться metadata mapping от backend, markers или хранение координат stream timeline.

**Запрещено:** безусловно подменять все `durationMs` данными из `player.duration` или, наоборот, каталогом; безусловно приводить 100% raw stream time к `completed`.

**Решение до проверки SSAI:**

- Отдельно хранить `positionKind`: content/stream (как контракт, не обязательно поле в Room без миграции), `durationSource`: catalog/player/unknown.
- Для вставок с неизвестным mapping сохранять внутренние координаты текущего playback engine и использовать тот же seek semantics для resume; не выводить ложные «осталось 4 мин».
- Completion делать от `STATE_ENDED` на VOD + явного события перехода серии; процентный эвристический fallback только после тестов.
- Провести тестовые кейсы 0/1/2 ad breaks, skip intro, seek до и после рекламы, reconnect с изменением HLS manifest, замена variant playlist.

**Пример матрицы:**

| Сценарий | Проверка |
|---|---|
| MP4 VOD без рекламы | catalog ≈ timeline, корректный seek |
| HLS VOD без рекламы | продолжительность известна/неизвестна, resume валиден |
| HLS CSAI | во время ad не сохраняем ad position как позицию фильма |
| HLS SSAI | seek по восстановленной координате попадает в смысловой момент |
| HLS discontinuity | нет отрицательных значений и ложного завершения |
| Live stream | не создаём проценты завершения VOD |

**Gate:** пока timeline mapping не валиден на реальном HLS, нельзя обещать точный cross-device resume при рекламных вставках.

## 15. PLY-09 — сериал: next, intro, recap, credits

**Функциональность:**

- CTA после просмотра «Следующая серия» с названием и SxxEyy, кнопкой отмены.
- Автопереход только по продуктовой policy, учитывающей настройки пользователя, доступность следующего эпизода, ошибку сети и просроченный source URL.
- Переход по `playableId` следующего реально доступного эпизода, не по `episodeIndex + 1`.
- Skip intro, recap, credits — только если backend выдаёт валидные интервалы; не гадать по длине или названию.
- При skip intro пользовательская позиция должна перескочить к содержательной точке и быть сохранена с той же timeline семантикой.
- На финальном эпизоде сезона — следующий сезон, если доступен; на финале сериала — экран завершения.
- Никакой рекурсивной автоматической цепочки при `STATE_ENDED` на пустой/ошибочной серии.

**Тесты:** 1-я серия; последняя серия; конец сезона; specials; серия недоступна; stream уже сменился; повторный `onEnded`; запрет autoplay; skip-intro interval отсутствует/ошибочен; Back во время countdown.

## 16. MOB-SEARCH — поиск и фильтры

- Search screen с системным IME и запросом с debounce 250–400мс (конфигурационный параметр).
- `flatMapLatest`/cancellable API; нулевые/устаревшие результаты не должны подменять актуальные.
- Без запроса: поисковая история и тренды (если есть). С запросом: фильмы/сериалы в результатах, категории при наличии метаданных.
- Search query и scroll сохраняются при Details → Back; активная клавиатура не закрывает CTA непреднамеренно.
- Empty/Error/Offline/Loading — отдельные состояния; без бесконечного спиннера.

**Тесты:** быстрое печатание, очистка, разные языки, пустой результат, offline, открыть 15-й результат и Back, поворот при открытой IME.

## 17. MOB-RESP — адаптивность, Compose дизайн и визуальная приёмка

**Design tokens сохранить из `MOB-13`, дополнить если отсутствуют:** цвета surface/overlay/scrim/focus; шкала spacing; формы; типографика; dimensions compact/medium/expanded; motion durations; elevation; состояния disabled/loading/error.

**Рекомендованная визуальная концепция:** тёмный каталог, изображения приоритетнее рамок, компактная верхняя панель, узнаваемая чёткая CTA, минимум плашек/метаданных, без autoplay на Home, предсказуемые переходы. Не копировать интерфейс Netflix пиксель-в-пиксель.

**Material3:** использовать дизайн-токены как `ColorScheme/Typography/Shapes`; доступность / 48dp; `Scaffold` + корректные `WindowInsets`, не складывать system bar padding дважды. Поддержать edge-to-edge на целевых SDK.

**Adaptive:** `WindowSizeClass` (фактическое окно, не только тип устройства), `NavigationBar` compact, `NavigationRail` medium/expanded, сетки через adaptive min size, master-detail на планшете при наличии места. Landscape не тождественен tablet.

**Проверить планшет для MOB-07:** portrait/landscape 600/840dp классы, двухпанельный экран деталей при наличии места, UI не растянут из compact без изменений; fold/unfold и multi-window.

**Визуальная матрица:**

| Размер/ситуация | Критерий |
|---|---|
| 360×800dp portrait | нижнее меню и CTA не обрезаны |
| 393×852dp portrait | адекватная ширина карточек и первого ряда |
| Landscape low height | плеер видим, нет конфликтов с системными барами |
| 600dp wide split-screen | отступы/колонки корректны |
| 840dp+ tablet | rail и увеличенные grid, UI не пустует |
| Font scale 1.3 и 2.0 | текст/контролы доступны и не налезают |
| TalkBack | контентные описания карточек и кнопок |
| Reduce motion | необязательные анимации сокращены |

**UI snapshot/golden тесты:** Home empty/loading/content, Details movie/series, season sheet, Search, Player paused/buffering/error, progress cards 0/50/95%, very long title, unavailable artwork. Зафиксировать одни и те же данные и устройство при сравнении скриншотов.

## 18. Безопасная архитектура исполнения и тестирования

### 18.1. Unit tests (`src/test`)

- `MoviePlayablesTest`: сохранить существующий пример и добавить property-based случайную перестановку порядка.
- `ContinueCardMapperTest`: различить неизвестную duration, 0, NaN/некорректный ratio, точно завершённый.
- `HomeRowsFactoryTest`: порядок, скрытие пустых, отсутствие дублей.
- `ResumeDecisionTest`: completed/incomplete/removed/next episode.
- `PlaybackProgressRecorderTest`: tick/pause/stop/end/seek/race.
- `EpisodeOrderTest`: непоследовательные сезоны, specials, отсутствие следующей.
- `PlayerErrorMapperTest`: сеть, 403, decoder, DRM, Media3-specific errors.

### 18.2. Instrumentation (`src/androidTest`)

- Compose `testTag`/semantic labels для Home, NavBar, Hero CTA, продолжения, PosterCard, EpisodeRow, PlayerControls.
- `ComposeUiTest`: навигация, нажатия, восстановление позиции, BottomSheet, клавиатура.
- Room test DB: записи прогресса, upsert, миграции.
- Реальный player test: prepared HLS на тестовом потоке; не заменять его юнит-фейком.
- Activity recreation / process death: контролируемый тест с системными средствами, не считать проверенным только от `rememberSaveable`.

### 18.3. End-to-end ручные сценарии (обязательны)

| ID | Действие | Ожидаемый результат |
|---|---|---|
| `E2E-01` | Movie 2 мин → Pause → Back → Home | одна ContinueCard, правильный ID |
| `E2E-02` | Полностью закрыть приложение → открыть и Play | валидный resume на той же позиции |
| `E2E-03` | S02E03 → Back → Details | сезон, серия и progress верны |
| `E2E-04` | End эпизода | next доступный canonical playableId |
| `E2E-05` | Быстрый переход фильм→сериал→фильм | чужой progress не подмешивается |
| `E2E-06` | Rotate 10 раз во время воспроизведения | не два player, позиция не сброшена |
| `E2E-07` | Home/lock/unlock/return | lifecycle policy, позиция, audio focus правильны |
| `E2E-08` | HLS со вставкой → resume | resume попадает в корректную точку либо функция явно ограничена |
| `E2E-09` | Навигация по 5 вкладкам и Back | фильтры/scroll/back stacks сохранены |
| `E2E-10` | Portrait→tablet→split-screen | элементы доступны и не обрезаны |
| `E2E-11` | Профиль A → B | отсутствие истории A |
| `E2E-12` | TV→phone и phone→TV | проверить только при наличии общего backend/sync; локальный Room сам по себе не sync |

### 18.4. Производительность

- Release-like Macrobenchmark на физическом устройстве: cold/warm startup, Home scroll, вкладки, детали.
- Отдельно измерять `time-to-first-frame` видео и rebuffer ratio; не подменять их startup UI.
- Baseline Profiles для critical user journeys (Home→Details→Player; скролл каталога).
- Следить за активными Player instances, памятью Coil/битмапов, сетевыми вызовами, превышением recomposition бюджета.
- Устанавливать бюджет после замеров baseline на целевом слабом устройстве; не объявлять неподтверждённые миллисекунды SLA.

### 18.5. Команды и отчёт каждого MR

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
# Если androidTest есть и устройство/эмулятор подключены:
./gradlew :app:connectedDebugAndroidTest
# Дополнительные команды - в зависимости от текущих модулей,
# Gradle variants и существующего benchmark module.
```

**Формат отчёта coding-agent:**

```text
ID / Commit:
Точная цель:
Изменённые файлы:
Переиспользованные классы:
Какие тесты добавлены:
Команда теста / количество / результат:
Ручная проверка: устройство, поток, длительность, результат:
Сравнение с baseline:
Известные риски/непроверенное:
Что НЕ затрагивалось:
Следующая задача:
```

**Обязательно:** статусы «юнит-тесты зелёные», «UI подтверждён на устройстве» и «HLS с SSAI подтверждён» — три разные статуса, не подменять друг другом.

## 19. Hard gates / критерии готовности к релизу

### P0 — выпуск блокируется

- [ ] Канонический `playableId` без ошибок для фильма/серии.
- [ ] Чтение и запись resume через единый `PlaybackProgressStore`.
- [ ] Нет дублей writer, нет устаревших tick поверх completed.
- [ ] 5 вкладок, Back и scroll restoration реально работают.
- [ ] Главная отображает Continue и корректно обновляет её.
- [ ] Детали сериала выбирают нужный сезон/эпизод.
- [ ] Media3 playback: Play/Pause/Seek, HLS, audio/subtitles, error states.
- [ ] Нет двойных игроков, утечек Surface, сбросов при rotation.
- [ ] HLS timestamp semantics для имеющихся типов потока проверена.
- [ ] Unit/instrumentation/ручной E2E по возможности в полном объёме, старые 134 теста не регрессировали.
- [ ] Для Android TV отдельные smoke-tests или подтверждение отсутствия изменений TV-кода.

### P1 — высокое качество

- [ ] Adaptive tablets/foldables и большие шрифты.
- [ ] Поиск/фильтры с отменой stale requests.
- [ ] Темы и скриншоты экранов соответствуют design tokens.
- [ ] Baseline Profiles/бенчмарки, отсутствие значимых регрессий.
- [ ] Описательные ошибки потоков, минимальная диагностическая телеметрия без секретов.

### P2 — только после стабильного ядра

- [ ] PiP с сохранением playback session.
- [ ] Cast session handoff, remote player и синхронизация позиции.
- [ ] Offline downloads, лицензии DRM, квоты и восстановление.
- [ ] Autoplay trailers/background в каталоге — только после UX/perf оценки.
- [ ] Cross-device sync через backend; локальный `PlaybackProgressStore` сам по себе недостаточен.

## 20. Первая серия коммитов: конкретное задание агенту

### Коммит 1 — PROG-01

**Изменить:** только тестовую инфраструктуру для PlaybackProgress и реализации fake player/store; не трогать UI. Добавить минимум 6 новых тестов на прогресс, mapping и гонки.

**Приёмка:** `assembleDebug` + `testDebugUnitTest`, 134 существующих теста остаются зелёными; новые воспроизводят нужный контракт. Представить отрицательный тест на старое поведение, если оно ошибочно.

### Коммит 2 — MOB-10A

**Изменить:** interactor для ResumeDecision и чтение мобильных CTA/player из общего хранилища; оставить запись без изменения.

**Приёмка:** resume читается из `PlaybackProgressStore`, возвращает канонические IDs, результат Home/Details совпадает, UI и TV без регрессий.

### Коммит 3 — MOB-10B

**Изменить:** унифицировать writer (но не делать DB migration без необходимости), удалить лишний путь сохранения, отфильтровать stale tick.

**Приёмка:** `1 запись → 1 логическая актуальная строка`, сохранились pause/stop/end и scroll. Ручная проверка E2E-01/02/03 обязательна.

### Коммит 4 — MOB-08A

**Изменить:** `MobileAppShell`, 5 вкладок, back stack, системные Insets; не переделывать плеер.

**Приёмка:** Compose UI tests NAV-01..07 + реальная проверка системного Back.

### Коммит 5 — MOB-09C

**Изменить:** только Compose Home на базе `HomeRowsFactory`, существующей темы и компонентов.

**Приёмка:** screenshot-тесты Home и тесты порядка секций; карточка «Продолжить» запускает правильный playableId.

**После коммита 5:** выполнить первый полный ручной UX-прогон Android Phone и закрыть визуальные замечания до работ с Media3. Затем работать по таблице этапов из раздела 2.

## 21. Официальные справочные материалы (проверено 2026-10-21)

- [Media3 Compose: выбор `media3-ui-compose` и `media3-ui-compose-material3`](https://developer.android.com/media/media3/ui/compose)
- [Media3 Compose customization / player control slots](https://developer.android.com/media/media3/ui/compose-customization)
- [Media3 surface types / SurfaceView vs TextureView](https://developer.android.com/media/media3/ui/surface)
- [Media3 HLS](https://developer.android.com/media/media3/exoplayer/hls)
- [Media3 Player API (`getContentPosition`, `getContentDuration`)](https://developer.android.com/reference/androidx/media3/common/Player)
- [Media3 migration guide](https://developer.android.com/media/media3/exoplayer/migration-guide)
- [Media3 background playback](https://developer.android.com/media/media3/session/background-playback)
- [Navigation 3 multiple back stacks](https://developer.android.com/guide/navigation/navigation-3/recipes/multiple-backstacks)
- [Adaptive Window Size Classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)
- [Compose UI testing](https://developer.android.com/develop/ui/compose/testing)
- [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
- [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile)
- [Compose window insets](https://developer.android.com/develop/ui/compose/system/insets)

---

**Итог:** сначала устранить расхождение и гонки в прогрессе; затем показать работающую Compose-навигацию и главную; далее стабильно заменить/адаптировать плеер под Media3; завершать E2E на реальном телефоне, планшете и TV. Все операции должны оставлять приложение собираемым и существующие 134 теста зелёными.
