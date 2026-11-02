# S-004 — эмуляторные UI-регрессии на реальном каталоге

c-23, revision 12. План перечитан; прошлые результаты c-21 (seek +5с на паузе,
Checking -> «Обновлений нет») не подменяют оставшиеся проверки.

Добавлены два instrumentation-теста, проверенные на emulator-5554 / Pixel_5 API31,
обычный com.arny.mobilecinema.debug с существующим реальным каталогом:

- `presentation.catalog.CatalogModeStateIntegrationTest`: Films Rating + Anime,
  переход в Series -> New + All, изменение Series на Alphabetical,
  возврат в Films -> Rating + Anime сохранены. Это runtime-регрессия независимых
  ViewModel, сортировки **и выбранного жанра**, не только состава карточек.
- `presentation.sitesearch.SearchQueryIntegrationTest`: одна main-thread серия изменений
  EditText `m -> matrix -> матрица`, локальный поиск с непустым результатом,
  All -> Remote -> Local сохраняют кириллический запрос. Реальные TextWatcher вызываются,
  Unicode не подменяется латинским запросом. Это не тест физической клавиатуры/IME.

Сборка test APK сначала не прошла offline из-за отсутствующих артефактов уже объявленных
зависимостей (Espresso/Compose test/runner). Обычная Gradle-сборка скачала их и прошла;
повторная offline-сборка также успешна. Новые библиотеки/версии не добавлялись.

## Воспроизведение

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -PmobileUiTest=true --offline
# Установить target APK из app/build/outputs/apk/debug, не очищая данные.
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/*androidTest.apk
adb -s emulator-5554 shell am instrument -w \
  -e class com.arny.mobilecinema.presentation.catalog.CatalogModeStateIntegrationTest,com.arny.mobilecinema.presentation.sitesearch.SearchQueryIntegrationTest \
  com.arny.mobilecinema.debug.test/androidx.test.runner.AndroidJUnitRunner
```

`mobileUiTest=true` выбирает debug вместо исторического release testBuildType только
для этого запуска Gradle. Без флага сохранён release. Не запускать все существующие
integration-тесты автоматически: среди них есть изменяющие каталог live-update тесты.
Новые тесты работают с существующими данными, не делают seed/clear и не меняют настройки
каталога на диске. В каждом теле теста — emulator hardware assumption. Запуск адресован
только emulator-5554, без connectedCheck по всем подключённым устройствам.

## Фактический результат

Окончательный повтор после очистки импортов/расположения классов:
`OK (2 tests)`, 13.765с. Артефакты этого хода:
`build/manual/c23-ui-build.log`, `build/manual/c23-ui-tests.log`.

Новых production-правок, контактов с телефоном и правок контроллерного плана в этом ходе нет.

## Дополнение c-24

Добавлен `presentation.settings.CatalogCheckResponsivenessTest`: test-only Koin override
задерживает ответ getUpdateDate через CompletableDeferred. Проверяется видимое
«Проверяем обновления…» и успешный переход в Search **до** release ответа. Исходный
interactor восстанавливается в finally. Каталог не изменяется, live-download не запускается.
Это проверка UI при незавершённом ответе/возможной отмене при навигации, не замер тяжёлого
production I/O. Реальный ответ «Обновлений нет» проверялся отдельно в c-21.

Совместный повтор всех трёх классов: `OK (3 tests)`, 16.147с;
`build/manual/c24-ui-tests.log`, `build/manual/c24-ui-build.log`.

Отдельно выполнен process-cold запуск штатного StartActivity на реальном каталоге:
`am start -W`: MainActivity, COLD, TotalTime 3899мс. Сэмпл в 3–3.313с ещё показывает splash;
в 6–6.703с видны Home, текст и настоящие постеры. Это верхние границы по сэмплам, а не точный
first-draw timestamp. Артефакты: `build/manual/c24-cold-launch/results.json`, `am-start.txt`,
`frame-3.png`, `frame-6.png`, `main.xml`. Нагрузка/сеть/кэши не контролируются как в benchmark.
Телефон не использовался, production-код и контроллерный план не менялись.

## Что ещё не доказано

- Нет доказательства отмены каждого старого remote-response: проверяется текст запроса
  и локальная выдача, а не request-generation на каждом callback.
- Точное время первого текстового кадра и progressive poster fill при холодном image-cache
  не измерены; наблюдён реальный launcher -> main + текст/постеры с существующим кэшем.
- Повтор всей матрицы на 100/1000/10000 карточек остаётся незавершённым.
- Эти 3 теста не доказывают FPS, отсутствие ANR во всех сценариях или release/TV-регрессию.

S-004 остаётся незавершённым; тесты дают воспроизводимое покрытие ранее ручных/неподтверждённых
пунктов (независимый genre state и быстрая кириллица со сменой source).
