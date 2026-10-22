# AUD-01 — карта вызовов меню, поиска и синхронизации

Инвентаризация по заданию `Anwap_Movies_UX_Search_Navigation_Audit_v2.md` (§5 «Обязательная
предварительная задача», §9 AUD-01). Составлено по коду, не по догадкам: каждый пункт —
реальный обработчик, найденный в исходниках. Поведение сайта проверено запросами к
`https://mm.anwap.media` (301 с `my.anwap.love`).

## 1. Восемь действий overflow-меню главной (`res/menu/home_menu.xml`)

Обработчик — `HomeFragment` (`binding.toolbar.setOnMenuItemClickListener`).

| Пункт (string) | id | Функция | Что делает фактически | Сеть | Запись в БД |
|---|---|---|---|---|---|
| Расширенный поиск (`search_extended_title`) | `action_extended_search_settings` | `HomeFragmentDirections.actionNavHomeToNavExtendedSearch()` | Открывает отдельный экран `ExtendedSearchFragment` (тип, год, жанры, страны, IMDb, КП, сортировка) | нет | нет |
| Поиск на сайте (`site_search_menu`) | `action_site_search` | `actionNavHomeToNavSiteSearch().setQuery(searchView.query)` | Открывает `SiteSearchFragment` с текущим текстом строки поиска | да, при вводе | нет (карточка пишется только при открытии результата) |
| Открыть ссылку (`menu_from_path`) | `menu_action_from_path` | `openPath()` → `inputDialog` → `actionNavHomeToNavPlayerView(url, null)` | Отдаёт введённый URL прямо в плеер, без проверки схемы/источника и без записи в каталог | да (плеер) | нет |
| Проверить новые фильмы (`menu_check_update`) | `menu_action_check_update` | `showVideoUpdateDialog()` → `downloadData(force=true)` → `HomeViewModel.downloadData` → `DataUpdateInteractor.getUpdateDate(force)` | Скачивает файл даты обновления (`BuildConfig.UPDATE_LINK`), сравнивает с сохранённой; при наличии — диалог с датой и кнопками «ОК / Отмена / Полное обновление». **Не read-only**: по «ОК» вызывает `requestFile` → `ACTION_DOWNLOAD_DATABASE` | да | нет до подтверждения |
| Загрузить новые фильмы (`menu_download_new`) | `menu_action_update_download_new` | `HomeViewModel.onActionUpdateAll()` → `Alert(AlertType.UpdateAll)` → `DataUpdateInteractor.updateAll()` | Полный обход сайта через `UpdateService` (`ACTION_UPDATE_ALL`) | да, много | да |
| Импорт ссылки (`import_link`) | `menu_action_import_link` | `showImportLinkDialog()` → `importVideoByUrl(url)` → `UpdateService` (`ACTION_UPDATE_BY_URL`) | Требует URL вида `films|serials/<id>`; иначе toast «Неверный формат URL». Разбирает карточку и сохраняет в БД | да | да |
| Обновить список (`update_list`) | `menu_action_update_list` | `HomeViewModel.loadMovies()` → `refreshHome()` | **Только перечитывает локальную Room** (Paging), сети нет. Название не отражает смысл | нет | нет |
| Compose test (`menu_compose_test`) | `menu_action_compose_test` | `startActivity(ComposeTestActivity)` | Уже под `BuildConfig.DEBUG` в обработчике и в `updateMenuVisibility()` | нет | нет |

Выводы для MENU-01/SYNC-01:

- `Проверить` и `Загрузить` — не одна операция: первая читает дату обновления и спрашивает
  подтверждение, вторая запускает полный обход. `Обновить список` — вообще третья семантика
  (перечитать БД), её нельзя объединять ни с чем: её надо переименовать.
- `Открыть ссылку` и `Импорт ссылки` различаются side effects: первая только играет URL,
  вторая пишет каталог. Объединять UI можно, обработчики — нет.
- `Compose test` уже debug-only; осталось убрать его из общего overflow в debug-раздел.

## 2. Два поиска

| | Локальный (тулбар главной) | Сайт (`SiteSearchFragment`, вкладка «Поиск») |
|---|---|---|
| Вход | `SearchView` в тулбаре главной, `home_menu.action_search` | нижняя вкладка `nav_site_search`, пункт «Поиск на сайте» |
| VM | `HomeViewModel.loadMovies(query)` → `UiAction.Search` | `SiteSearchViewModel.search()` |
| Слой | `MoviesInteractor.getMovies` → `MoviesRepositoryImpl.getMovies` → Paging3 `MainPagingSource` → `getMoviesSQL` (Room, `LIKE`) | `JsoupUpdateInteractorImpl.searchOnSite` → `loadPage` → `getSearchPageItems` |
| Поля поиска | было `m.title LIKE ...`; **теперь** `title` **и** `origTitle` | сайт ищет сам (`/films/search/?slv=…&vid=1`, `/serials/search/?word=…&vid=1`) |
| Ранжирование | было: только популярность (`SMART`); **теперь** `CASE` по совпадению названия первым ключом | было: порядок сайта, сериалы перед фильмами; **теперь** `SearchRelevance.rank` (фильмы первыми при равном счёте) |
| Пагинация | Paging3, 20 на страницу | страницы фильмов по 10; у сериалов 1 страница |
| Запись в БД | нет | при открытии результата — `fetchAndSaveMovieCard` |

Расширенный поиск (`ExtendedSearchFragment` → `ExtendSearchResult`) — третья точка входа:
возвращает фильтры в главную через `setFragmentResultListener` и переиспользует тот же
локальный запрос. То есть «расширенные фильтры» уже технически живут поверх локального
поиска, а не отдельной выдачи.

## 3. Поведение сайта поиска (проверено запросами 22.10.2026)

- `GET /films/search/?slv=matrix&vid=1` → 10 карточек: «Секс-файлы: Секс-матрица»,
  «Матрица: Воскрешение», «Матрица: Перезагрузка», «Матрица: Революция», «Матрица» (1999),
  далее нерелевантный хвост сайта («Родина», «Брачное приложение»).
- `GET /serials/search/?word=matrix&vid=1` → в разметке есть плашка
  `<div class="err">К сожалению по вашему запросу в названии "matrix" ничего не найдено</div>`,
  а ниже — **fallback-подборка** из 20 карточек («Список смертников», «Менталист»,
  «Сверхъестественное»…). Для мусорного запроса `zzzqqq1235` подборка **та же самая**.
- Причина дефекта из скриншота §4.4: парсер брал `div.my_razdel.film` независимо от плашки
  «ничего не найдено», поэтому fallback-подборка сериалов выдавалась как результаты поиска
  и стояла **выше** точного совпадения (сериалы запрашивались первыми).

Исправлено в этом коммите: `isSearchPageEmpty` / `getSearchPageItems` (маркер
`div.err` + текст «ничего не найдено»), фильмы запрашиваются и показываются первыми,
`SearchRelevance` переставляет точные совпадения вверх, `SiteSearchViewModel` отменяет
предыдущий запрос и отбрасывает устаревший ответ.

## 4. Три sync-действия и сервис

| Команда | Interactor/Repository | Сервис | Побочные эффекты |
|---|---|---|---|
| Проверка даты | `DataUpdateInteractor.getUpdateDate(force)` — скачивает `UPDATE_LINK`, сверяет с `repository.lastUpdate`, учитывает `hasPartUpdate` | — | перезаписывает `newUpdate`/`checkUpdate` в prefs |
| Скачивание пакета | `requestFile(force, hasPartUpdate)` → `repository.downloadUpdates(dataLink, force)` | `ACTION_DOWNLOAD_DATABASE` (ZIP+JSON) | запись каталога |
| Полный обход | `updateAll()` | `ACTION_UPDATE_ALL` | массовая запись каталога, отмена — `ACTION_UPDATE_ALL_CANCEL` |
| Карточка по URL | `UpdateService` | `ACTION_UPDATE_BY_URL` (Jsoup) | запись одной карточки |

Конкурентность: отмена предусмотрена только для полного обхода (`ACTION_UPDATE_ALL_CANCEL`);
`ACTION_DOWNLOAD_DATABASE` и `ACTION_UPDATE_BY_URL` отдельного «занято» не проверяют.

## 5. Что из этого следует для следующих шагов

- SYNC-01: модель `check → preview → apply → report` ложится на существующие вызовы
  (`getUpdateDate` = check+preview, `requestFile`/`updateAll` = apply), но требует единого
  координатора: сейчас три входа живут в разных местах без общего статуса.
- SRCH-02/03: локальный поиск и расширенные фильтры уже поверх одного запроса, поэтому
  единый экран поиска = перенос `SearchView`-логики и `ExtendedSearchFragment` в
  `SiteSearchFragment` как источник «В каталоге» + `ModalBottomSheet` фильтров.
- DBG-01: `menu_action_compose_test` уже скрыт в release; в настройках остались
  пользовательские переключатели `pref_home_highlights_enabled` и `pref_compose_home`.
