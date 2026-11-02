# S-063: воспроизводимые данные для эмулятора

Только изолированные `com.arny.mobilecinema.perfdebug` и `.perfbenchmark`.
Обычные debug/release, их каталог и история не используются и не очищаются.
Production/TV исходники, схема Room и URL-validator не изменены.

## Подготовка

Требуются Python (stdlib), adb, ffmpeg/ffprobe, Pixel_5 с root adbd.

```sh
./gradlew :app:assembleDebug :app:assembleBenchmark -PperfIsolated=true --offline
python scripts/perf_fixtures.py generate
# Отдельный локальный процесс, привязан только к 127.0.0.1:8765:
python scripts/perf_fixtures.py serve
adb -s emulator-5554 root
adb -s emulator-5554 wait-for-device
# Установить оба APK из app/build/outputs/apk/debug и benchmark.
python scripts/perf_fixtures.py load --serial emulator-5554
python scripts/perf_fixtures.py verify --serial emulator-5554
```

`load` принимает только `emulator-N`, проверяет ro.kernel.qemu=1 и root.
Пакеты жёстко ограничены двумя perf ID. Заменяет только их синтетические БД и preferences;
не запускайте на этих пакетах пользовательские сессии. Установленный общий каталог обычного
приложения `com.arny.mobilecinema.debug` не затрагивается.

Последовательно загружаются 100, 1000, 10000 строк в оба APK; в конце остаётся 10000.
База создаётся по экспортированной Room schema v8, включая identity hash и индексы,
без изменения схемы/миграций. Перед копированием процесс остановлен, старые WAL/SHM удалены,
восстановлены UID/SELinux-контексты. Root используется только в тестовом эмуляторе —
никакого exported seed-компонента в APK нет.

## Содержимое

Фиксированный seed `s063-v1-no-random`. Фильмы/сериалы, годы и жанры, Matrix/Матрица/ремейк,
два сезона с перемешанными сериями, 2 history/progress и 1 favorite. Продолжение сериала —
канонический `ep:2:2:201`, S02E01, позиция 10 с из 30 с.

Сгенерированные оригинальные материалы: синий PNG 160×90 и H.264 MP4 160×90, 30 с,
без звука/чужих ассетов. Сервер отдаёт успешный постер, 404 для битого постера,
фильмовую поисковую карточку для matrix/матрица и пустую выдачу сериалов, а также фиксированную
дату обновления. MP4 поддерживает одиночный byte-range для seek. Обновление `/data.zip`
намеренно возвращает 404: проверка даты/ошибки доступна, применение каталога не симулируется.
Сервер не публикует БД или directory listing и не сохраняет запросы.

Prefs обоих perf-пакетов содержат локальный base URL. `adb reverse tcp:8765 tcp:8765`
позволяет обращаться к 127.0.0.1 из эмулятора. Cleartext разрешён только для 127.0.0.1
в opt-in ресурсах `src/perfFixtures`, не в production. Secrets-plugin не перезаписывает
локальные update URLs при `perfIsolated=true`; с этим флагом release-task запрещён.
Без флага debug/benchmark используют обычные endpoints и network policy.
Для фикстур открывать MainActivity напрямую: splash может разрешить адрес живого сайта.

## Проверено (c-17)

- Изолированные assembleDebug/assembleBenchmark — BUILD SUCCESSFUL (сборка завершилась после
  прерывания c-16). Исправлена lint-ошибка includeSubdomains; отключение lint не применялось.
- В c-17 оба свежих APK переустановлены, все 6 загрузок повторены. SQLite на самом эмуляторе:
  100/1000/10000 movies, 2 history, 1 favorite и ep:2:2:201; SHA-256 БД одинаков между APK
  для каждого размера. Room/MainActivity открывается без немедленного завершения процесса.
- Повторная генерация побайтово воспроизводит manifest/checksums всех БД и обоих медиа.
- Через эмулятор получены PNG/MP4 с совпадением SHA, missing.png=404, поисковые HTML,
  update.txt с ожидаемой датой и data.zip=404. ffprobe подтвердил H.264 и 30 с.
- Генератор компилируется; default BuildConfig без perfIsolated не содержит fixture endpoints.
- Реальный каталог обычного debug-приложения не изменялся этим инструментом.

Артефакты (ignored build/):
`build/perf/c17-fixture-verification.json`, `build/perf/fixtures/manifest.json`,
`build/perf/fixtures/load-report.json`, `build/perf/fixtures/network-report.json`,
`build/perf/c16-build.log`, `build/perf/c17-load.log`.

Это проверка подготовки данных/получения ответов, не полный прогон пользовательских
сценариев S-004. Парсинг/ранжирование в UI, реальное воспроизведение/seek, навигация,
задержки и ошибка применения обновления ещё требуют сценарных проверок.
