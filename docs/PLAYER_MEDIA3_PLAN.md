# S-011: переход на Media3 с сохранением YouTube-жестов (состояние)

## Сделано и проверено

1. Найден Media3-форк той же библиотеки жестов: `flocasts/DoubleTapPlayerView` (форк `vkay94/DoubleTapPlayerView`), поддержка Media3, лицензия MIT.
2. Исходники форка (коммит `507a72b5e6c2be06dba1e8e04a2f3e0eb04b7877`) выложены локально в `third_party/doubletapplayerview-media3` + `UPSTREAM.md` с лицензией.
3. Модуль подключён в `settings.gradle.kts` и **собирается отдельно**: `:doubletapplayerview-media3:assembleDebug` — BUILD SUCCESSFUL (Media3 1.4.1, compileSdk 34, minSdk 21). Так жесты и оверлей остаются ровно те же, что нравятся в текущем приложении.
4. JitPack для форка недоступен (read timeout) — поэтому локальная копия, а не зависимость.

## Что мешает включить в приложение (проверено компиляцией)

- Media3 1.5.1 требует compileSdk 35 (проект на 34), Media3 1.4.1 проходит по compileSdk.
- После подключения модуля к приложению класс `com.github.vkay94.dtpv.DoubleTapPlayerView` приходит уже из Media3-форка, а код экрана и `PlayerSource` работают с ExoPlayer2 → 10 файлов и 2 layout используют `com.google.android.exoplayer2.*`, компиляция падает (`isControllerVisible`, несовпадение типов Player/MediaSource, двусмысленные overload).
- Значит нужен отдельный шаг миграции: `PlayerSource` → Media3 (`androidx.media3.datasource/exoplayer/offline`), `PlayerViewFragment` (20 импортов), `VideoCache`, `MovieDownloadService`, `PlayerExtentions`, а TV (`TvPlayerFragment`, `f_tv_player.xml`) переводить отдельно или оставить на ExoPlayer2 до проверки.
- MediaSessionConnector из `exoplayer2.ext.mediasession` в Media3 заменяется на `androidx.media3.session.MediaSession` — это не механическая замена импортов.

## Текущее состояние дерева

Приложение снова собирается и проходит юнит-тесты (`build/media3-probe/reverted.log`): подключение Media3 на этом цикле откатано, чтобы не оставлять красную сборку. Рабочие жесты в приложении не тронуты — приложение по-прежнему на ExoPlayer2.19.1.

## Следующий шаг (S-011, не выполнен)

Адаптер `PlayerSource` под Media3 + миграция мобильного экрана, затем на CPU-эмуляторе проверить: двойной тап и перемотка, оверлей, fullscreen/ориентация, PiP, resume, HLS/MP4. Полная миграция не заявляется выполненной.
