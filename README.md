# Anwap Movies

Android-приложение для просмотра фильмов и сериалов с поддержкой мобильных устройств и Android TV.

## Технологии

| Область | Технология |
|---------|-----------|
| Язык | Kotlin 2.0.21, Java 17 |
| DI | Koin 3.5.6 |
| База данных | Room 2.6.1 + Paging 3 |
| Сеть | Ktor 2.2.2, Jsoup 1.15.3 |
| Плеер | ExoPlayer 2.19.1 |
| Навигация | Navigation Component 2.5.3 + SafeArgs |
| Изображения | Glide 4.16.0 |
| TV UI | Leanback 1.0.0 |
| Обработка видео | FFmpeg Kit |
| Сборка | Gradle 8.7.3, KSP |

## Сборка

### Требования

- JDK 17+
- Android SDK 34
- minSdk 24

### Команды

```bash
# Debug
./gradlew assembleDebug

# Release
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/`

### Подпись release

Создайте `signing.properties` в корне проекта:

```properties
STORE_FILE=path/to/keystore.jks
STORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

### Версия

Текущая версия задаётся в `version.properties`:

```
1.5.3 (versionCode 153)
```

## Структура

Единый модуль `app`, universal APK для Mobile + TV.

| Пакет | Назначение |
|-------|-----------|
| `presentation/` | UI: Activities, Fragments, ViewModels, Services |
| `domain/` | Бизнес-логика: Interactors, Repository interfaces, Models |
| `data/` | Реализации: Room DB, Jsoup, Ktor, Prefs |
| `di/` | Koin-модули |

Подробнее: [Arch.md](Arch.md)

## Лицензия

Приватный проект. Все права защищены.
