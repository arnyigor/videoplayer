# c-63 — сверка приёмки S-010 (revision27)

План перечитан: S-010 TODO, S-073 ещё не внесён. Оператор прямо распорядился перенести медленный DNS в конец и продолжать. Полное выполнение исходного S-010 не заявляется.

## Подтверждённые проверки (не новые прогоны c-63)

- CTA valid/empty/invalid: build/perf/c59/cta-results.json.
- Двойной тап: PlayClickGateTest; runtime doubletap-logcat.txt имеет один Init, но последующие тапы могли попасть уже в плеер — это ограничение проверки.
- Фильм/серия/сезон: build/perf/c53/result.json. Исправленный resume S02E01/10с: build/perf/c57/result.md, после Back позиция10244мс и тот же playable.
- 403/404/redirect/HLS first-frame: build/perf/c50/instrumentation.log.
- DNS failure (не медленный DNS): build/perf/c60/retry.log.
- Transport offline: build/perf/c61/offline.log, сеть восстановлена.
- Back/details: build/perf/c51/back-result.json; catalog scroll: build/manual/play-next/scroll-result.json.
- Ошибка403→Repeat→ошибка: build/manual/retry-ui/cta-results.json.
- Ошибка403→следующий источник→первый кадр: build/perf/c62/cta-results.json и change-source.log.

## Остаточные ограничения

Медленный DNS не проверен и отложен оператором. Проверки engine выполнены на ExoPlayer2.19.1, не Media3 (миграция относится к S-011). PlayRequested effect отсутствует по текущей архитектуре: проверялся один запуск через Fragment, не выдуманный effect. Не вся матрица ошибок повторена отдельно для фильма и сериала. Не следует утверждать, что каждый исходный подпункт полностью доказан.

## Запрос контроллеру

Вынести медленный DNS в S-073 в конец этого же плана, сохранить честную пометку об ограничениях и назначить независимый S-011 согласно прямому указанию оператора. В c-63 файл плана не изменён. Нового дефекта или нового ограничения среды нет: повторный finding не требуется.
