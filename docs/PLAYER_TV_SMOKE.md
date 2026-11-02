# S-011: TV после миграции на Media3 — минимальный smoke

## Что сделано

Поднят отдельный TV-эмулятор `Television_1080p` (порт 5558, `-gpu swiftshader` — программная
графика на CPU, как договорились). Boot: 25.5 с.

Установлен `perfdebug` и запущен `MainActivity`:

- `am start -W` → `Status: ok`, `TotalTime: 2217 ms`;
- экран отрисован: `Anwap Movies`, вкладки ВСЕ/ФИЛЬМЫ/СЕРИАЛЫ, навигация
  Главная/Поиск/История/Избранное (`build/perf/c73-tv/screen.xml`);
- в logcat нет `FATAL EXCEPTION` / `ActivityNotFound` — приложение после механической
  миграции на Media3 стартует на TV-конфигурации.

## Что это НЕ доказывает

- `TvPlayerFragment` и `f_tv_player.xml` на TV **не запускались**: нужен TV-путь навигации
  (реальный каталог/серии), поэтому нельзя утверждать, что TV-плеер после замены
  `com.google.android.exoplayer2.ui.StyledPlayerView` → `androidx.media3.ui.PlayerView`
  работает корректно.
- Не проверены пульт/D-pad, фокус, медиа-кнопки и PiP на TV.
- Использовался мобильный UI (`MainActivity`) — сам TV-экран приложения на этом запуске
  не открывался.
