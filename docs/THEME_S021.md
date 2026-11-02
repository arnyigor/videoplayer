# S-021: тема и системные компоненты — что исправлено и сверено

## Исправлено: светлый overflow на тёмном экране

Было: меню «Ещё» открывалось **белой панелью** на тёмном фоне (скриншот
`build/perf/c101/overflow-before.png`), текст в ней тёмный.

Причина: `popupMenuStyle` и `ItemMenuTextAppearance` в `values/` и `values-night/` жёстко
задавали `@android:color/white`; но фон overflow тулбара берётся не оттуда, а из
`actionOverflowMenuStyle`, которого в теме не было — поэтому первая правка дала
светлый текст на белом фоне (`overflow-after.png` — нечитаемо).

Исправление:
- добавлен стиль `AppOverflowMenu` (`Widget.MaterialComponents.PopupMenu.Overflow`) с
  `android:popupBackground = @color/colorPrimary` и светлым текстом, подключён как
  `actionOverflowMenuStyle` в `AppTheme` **и** в `values-night`;
- `ItemMenuTextAppearance` / `popupMenuStyle` переведены с белого на тот же тёмный фон и
  светлый текст, чтобы попапы не расходились с экраном.

Проверено: меню «Ещё» теперь тёмное, пункты читаемы (`build/perf/c101/overflow-after2.png`).

## Токены и контраст (сверено, менять не потребовалось)

| Пара | Контраст | Вывод |
|---|---|---|
| surface `#1C2028` / onBackground `#F4F6FA` | 15.09 : 1 | AAA |
| background `#0D0F13` / textSecondary `#ABA9B9` | 8.32 : 1 | AAA |
| surface `#1C2028` / accent `#B6A0FF` | 7.35 : 1 | AAA |

Значения плана (`#0B0C12`, `#191B25`, `#B6A0FF`, `#F5F5FA`, `#B8BBC9`) дают близкие цифры
(15.78 / 10.22 / 7.72). Текущая палитра требования по контрасту выполняет, поэтому цвета не
перекрашивал: это изменение внешнего вида без функциональной пользы.

## Сверено по коду

- **Минимальная зона нажатия**: `MobileDimens.MinTouchTarget = 48.dp`, используется в кнопках,
  чипах и селекторе сортировки.
- **`key` и `contentType` в ленивых списках**: каталог — `items(state.items, key = { it.dbId },
  contentType = { "poster" })`, жанры — `key/contentType`, секции поиска — `ListAdapter`
  с `DiffUtil` по `stableId`/`siteRef`; в ленте главной ряды строятся по `key = { it.dbId }`.
- **Постеры не растягиваются**: `ContentScale.Crop` с `aspectRatio(2f/3f)`, без
  `FillBounds`.

## Не проверено (остаётся в S-021)

- Large Font 1.3/2.0, TalkBack-озвучка, landscape, ширины 320/360/393/412 dp, планшет 600dp+ —
  ни один из этих режимов в этом цикле не прогонялся.
- Единый визуальный вес иконок и одинаковый стиль «selected» — глазами не сверял.
- Смешение View-диалогов (`AlertDialog`) с Compose-темой: диалог ошибок плеера остаётся
  View-`AlertDialog`; он стилизован `MyDialogStyle`, но к Compose-токенам не приведён.
