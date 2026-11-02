# S-011: PiP после миграции на Media3 — найден и исправлен дефект

## Что обнаружено

Первый прогон `PlayerPipTest` на CPU-эмуляторе показал: вход в PiP успешен
(`isInPictureInPictureMode == true`), но **воспроизведение стоит**:
`before=4198 after=4198` за 2.5 с. То есть PiP показывает застывший кадр — режим бесполезен.

Причина в коде (не связана с миграцией, поведение было и раньше):
`PlayerViewFragment.onPause()` вызывает `forcePausedOnRestore()`, который делает
`player.pause()`. В PiP активность уходит в `onPause`, поэтому видео встаёт.

## Исправление

В `PlayerViewFragment` добавлено:

```kotlin
private fun isInPictureInPictureMode(): Boolean = activity?.isInPictureInPictureMode == true

private fun forcePausedOnRestore() {
    if (isInPictureInPictureMode()) return   // PiP = «свёрнуто, но смотрю»
    shouldStartPaused = true
    player?.pause()
}
```

Остальные ветки (обычный уход в фон, сохранение позиции, `releasePlayer` на старых API)
не тронуты.

## Проверка

| Прогон | Результат |
|---|---|
| `PlayerPipTest` до исправления | FAIL: `playback stalled in PiP` |
| `PlayerPipTest` после | **PASS**: вход в PiP + позиция растёт (`build/perf/c71-pip2.log`) |
| Пакет `presentation.playerview` целиком | **16/16 PASS**, 0 failed (`build/perf/c71-all.log`) |
| Юнит-тесты | BUILD SUCCESSFUL (`build/perf/c71-unit.log`) |

## Остаток по S-011

Не проверены: визуальная анимация оверлея «+10 с», смена ориентации/resize, субтитры,
аудиодорожки, автопереход следующей серии, ads, TV-экран после механической миграции.
