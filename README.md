# Anwap Movies

Универсальное Android-приложение для просмотра фильмов и сериалов с поддержкой мобильных устройств и Android TV.

## Возможности

- **Просмотр кино и сериалов** — потоковое воспроизведение через ExoPlayer
- **Избранное и история** — сохранение понравившихся фильмов и просмотренного
- **Поиск** — расширенный поиск по каталогу
- **Скачивание** — загрузка видео для офлайн-просмотра с использованием FFmpeg
- **Android TV** — полноценная поддержка Leanback-интерфейса с D-pad управлением
- **Парсинг контента** — автоматическое обновление данных с источника через Jsoup

## Архитектура

Проект построен на принципах **Clean Architecture** с чётким разделением на три слоя:

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│  (Activities, Fragments, ViewModels, Presenters)          │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                           │
│  (Interactors, Repository Interfaces, Domain Models)       │
├─────────────────────────────────────────────────────────────┤
│                       Data Layer                            │
│  (Repository Impl, Room DB, Network, API)                 │
└─────────────────────────────────────────────────────────────┘
```

### Структура пакетов

| Пакет | Назначение |
|-------|------------|
| `presentation/` | UI слой (Activities, Fragments, ViewModels) |
| `domain/` | Бизнес-логика (Interactors, Repository interfaces) |
| `data/` | Работа с данными (DB, Network, Repositories) |
| `di/` | Зависимости Dagger (Components, Modules, Scopes) |

### Зависимости между слоями

```
Activity/Fragment
      ↓ (inject)
ViewModel
      ↓ (использует)
Interactor
      ↓ (работает с)
Repository (interface)
      ↓ (реализация в Data)
RepositoryImpl → Room DB / Network
```

## Зависимости

### Ключевые библиотеки

- **Dagger 2.44.2** — внедрение зависимостей
- **Room 2.6.1** — локальная база данных
- **ExoPlayer 2.19.1** — видеоплеер
- **Ktor 2.2.2** — HTTP-клиент
- **Jsoup 1.15.3** — HTML-парсинг
- **Navigation Component 2.5.3** — навигация
- **Glide 4.11.0** — загрузка изображений
- **Leanback 1.2.0-alpha02** — Android TV UI
- **FFmpeg Kit** — обработка видео
- **Coroutines 1.6.4** — асинхронное программирование
- **Lifecycle 2.3.1** — жизненный цикл компонентов
- **Paging 3.1.1** — постраничная загрузка

## Сборка и запуск

### Требования

- **JDK** — 17+
- **Gradle** — 8.2+
- **Android SDK** — 34 (compileSdk)
- **minSdk** — 24

### Типы сборки

| Тип | Особенности |
|-----|-------------|
| `debug` | Отладочная сборка с включённым debuggable |
| `release` | Релизная сборка с minification и shrinkResources |

### Сборка APK

```bash
./gradlew assembleDebug
# или
./gradlew assembleRelease
```

APK появится в `app/build/outputs/apk/`

### Особенности сборки

#### Имена файлов APK

| Тип | Имя файла |
|-----|-----------|
| Debug | `mobilecinema-1.4.5-debug.apk` |
| Release | `mobilecinema-1.4.5-release.apk` |

#### Universal APK

Приложение собирается в **единый универсальный APK** для мобильных устройств и Android TV. Маршрутизация интерфейса (phone vs TV) происходит автоматически при запуске `StartActivity` на основе `UiModeManager`.

#### Ключи подписи

Для сборки release-версии требуется файл `signing.properties` в корне проекта:

```properties
STORE_FILE=path/to/keystore.jks
STORE_PASSWORD=keystore_password
KEY_ALIAS=key_alias
KEY_PASSWORD=key_password
```

При отсутствии файла release-сборка будет создана без подписи.

#### Minification

Release-сборка использует:
- `minifyEnabled = true` — обфускация кода
- `shrinkResources = true` — удаление неиспользуемых ресурсов

#### BuildConfig

При сборке автоматически генерируется `BuildConfig`:

```kotlin
const val VERSION_NAME = "1.4.5"
const val VERSION_CODE = 145
```

### Версия

- **versionCode**: 145
- **versionName**: 1.4.5

## Архитектура DI (Dagger)

### Компоненты

```
AppComponent (Singleton)
    │
    ├── AppModule
    ├── UiModule  
    ├── DomainModule
    ├── DataModule
    ├── ServiceBuilderModule
    └── AndroidSupportInjectionModule
```

### Scopes

| Scope | Область видимости |
|-------|-------------------|
| `@Singleton` | Приложение целиком |
| `@ActivityScope` | Одна Activity |
| `@FragmentScope` | Один Fragment |

### Модули Activity

В `ActivitiesModule.kt` определены все Activity и их Fragment-модули:

```kotlin
@ContributesAndroidInjector(modules = [...])
abstract fun bindMainActivity(): MainActivity

@ContributesAndroidInjector(modules = [...])
abstract fun bindTvMainActivity(): TvMainActivity
```

## Android TV

### Маршрутизация UI

Приложение автоматически определяет тип устройства при запуске в `StartActivity`:

```kotlin
val targetActivity = if (DeviceUtils.isTV(this)) {
    TvMainActivity::class.java  // Leanback UI
} else {
    MainActivity::class.java    // Стандартный Material UI
}
```

### Создание нового TV-экрана

1. **Создайте ViewModel** в `presentation/tv/viewmodel/`

2. **Создайте модуль фрагмента** в `presentation/di/`:
   ```kotlin
   @Module
   abstract class TvNewFragmentModule {
       @FragmentScope
       @ContributesAndroidInjector
       abstract fun contributeTvNewFragment(): TvNewFragment
   }
   ```

3. **Добавьте модуль в ActivitiesModule**:
   ```kotlin
   @ContributesAndroidInjector(modules = [TvNewFragmentModule::class, ...])
   abstract fun bindTvMainActivity(): TvMainActivity
   ```

4. **Добавьте навигацию** в `tv_nav_graph.xml`

### Тестирование TV-версии

Используйте эмулятор с профилем **Android TV (1080p или 720p)**.

Управление осуществляется **только** с помощью D-Pad (стрелки и кнопка OK). Сенсорный ввод для TV-экранов отключён.

### Особенности реализации TV UI

- Используйте `DetailsSupportFragment` для экранов деталей
- Реализуйте `OnItemViewClickedListener` для обработки клиентов
- Используйте `ArrayObjectAdapter` для списков
- Применяйте `PresenterSelector` для разных типов контента

## Навигация

### Мобильная версия

Используется `nav_graph_main.xml` с Bottom Navigation:

- **Home** — главный экран с фильмами
- **Favorites** — избранное
- **History** — история просмотров

### TV-версия

Используется `tv_nav_graph.xml`:

- **TvHomeFragment** — главная
- **TvDetailsFragment** — детали фильма
- **TvPlayerFragment** — плеер
- **TvSearchFragment** — поиск

## Модели данных

### Domain Models

- `Movie` — основная модель фильма/сериала
- `MovieType` — тип (CINEMA/SERIAL)
- `SerialSeason` / `SerialEpisode` — сезоны и эпизоды
- `HistoryItem` — элемент истории

### Data Entities

- `MovieEntity` — сущность для Room DB
- `FavoriteEntity` — избранное
- `HistoryEntity` — история

## Работа с данными

### Источники данных

1. **Локальная БД (Room)** — кэш и пользовательские данные
2. **Сеть (Jsoup)** — парсинг контента с внешнего сайта
3. **Preferences** — настройки приложения

### Обновление контента

`UpdateService` запускается периодически для синхронизации данных. Использует `JsoupUpdateInteractor` для парсинга HTML.

## Безопасность

- Приватные ключи и secrets хранятся в `signing.properties` (не в репозитории)
- ProGuard правила настроены в `proguard-rules.pro`

## Логирование

Используется **Timber** для логирования. Конфигурация в `VideoApp.kt`.

## Дополнительные инструменты

- **DebugDB** — инспектор БД (debug build)
- **LeakCanary** — поиск утечек памяти (debug build)
- **Chuck** — перехват HTTP-запросов (debug build)
- **Tracer** — сбор и анализ крешей (release build)

## Требования к коду

- Kotlin 1.9.x
- Java 17 (source/target compatibility)
- ViewBinding включён
- SafeArgs для типизированной навигации
- Kotlinx Serialization для JSON

## Лицензия

Приватный проект. Все права защищены.