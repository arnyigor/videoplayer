# Руководство по оптимизации производительности списка видео

## Обзор

Данное руководство содержит конкретные улучшения для повышения производительности списка видео в HomeFragment. Оптимизации затрагивают:
- SQL-запросы и индексы базы данных
- Пагинацию и кэширование
- Загрузку и отображение изображений
- Управление памятью

## 1. Индексы базы данных

### Изменения в MovieEntity.kt

Добавить индексы для часто используемых полей:

```kotlin
@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["title"]),
        Index(value = ["type"]),
        Index(value = ["year"]),
        Index(value = ["ratingImdb"]),
        Index(value = ["ratingKp"]),
        Index(value = ["updated"]),
        Index(value = ["title", "type"]),
        Index(value = ["genre"]),
        Index(value = ["countries"])
    ]
)
```

**Миграция базы данных:**

Создать миграцию в `AppDatabase`:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Добавление индексов
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_movies_title ON movies(title)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_movies_type ON movies(type)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_movies_year ON movies(year)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_movies_rating_imdb ON movies(ratingImdb)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_movies_rating_kp ON movies(ratingKp)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_movies_updated ON movies(updated)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_movies_title_type ON movies(title, type)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_movies_genre ON movies(genre)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_movies_countries ON movies(countries)")
    }
}
```

## 2. Оптимизация SQL-запросов

### Замена PagingSourceHelper.kt

Использовать `PagingSourceHelperOptimized.kt` вместо оригинального файла. Ключевые улучшения:

- **EXISTS вместо LEFT JOIN** для определения isFavorite:
  ```sql
  CASE WHEN EXISTS(SELECT 1 FROM favorites f WHERE f.movie_dbid = m.dbId) THEN 1 ELSE 0 END AS isFavorite
  ```

- **Порядок условий WHERE** - самые селективные условия добавляются первыми

- **Оптимизация COLLATE NOCASE** для сортировки по title

### Интеграция:

Заменить в `MainPagingSource.kt`:

```kotlin
// Было:
val list = dao.getMovies(getMoviesSQL(...))

// Стало:
val list = dao.getMovies(
    PagingSourceHelperOptimized.getMoviesSQL(
        search = search,
        order = order,
        // ... остальные параметры
    )
)
```

## 3. Улучшенная пагинация

### MainPagingSourceOptimized.kt

Основные улучшения:

- **Кэширование страниц** в памяти (до 5 страниц)
- **Повторные попытки** при ошибках (макс 3 попытки)
- **Логирование** для отладки производительности
- **Очистка кэша** при invalidate()

### MoviesRepositoryImplOptimized.kt

Оптимизация конфигурации PagingConfig:

```kotlin
PagingConfig(
    pageSize = when {
        search.isNotBlank() -> 15  // Меньшие страницы для поиска
        else -> 20
    },
    enablePlaceholders = false,
    initialLoadSize = when {
        search.isNotBlank() -> 15
        else -> 20
    },
    prefetchDistance = 5,  // Предзагрузка 5 элементов вперед
    maxSize = 100  // Ограничение кэша в памяти
)
```

### Интеграция:

1. Обновить `MoviesRepositoryImpl`:
   - Заменить `MainPagingSource` на `MainPagingSourceOptimized`
   - Обновить `PagingConfig` параметрами из примера выше

2. Обновить `MoviesInteractorImpl`:
   - Убедиться, что использует обновленный репозиторий

## 4. Оптимизация адаптера

### VideoItemsAdapterOptimized.kt

Улучшения:

- **Кэширование URL изображений** для предотвращения повторной загрузки
- **Оптимальный размер изображений** в зависимости от плотности экрана
- **DiskCacheStrategy.ALL** для кэширования всех версий изображений
- **Метод clear()** для очистки view при переиспользовании
- **Предзагрузка изображений** для следующих элементов

### Интеграция:

1. В `HomeFragment.kt` заменить:
   ```kotlin
   // Было:
   itemsAdapter = VideoItemsAdapter(baseUrl) { item -> ... }
   
   // Стало:
   itemsAdapter = VideoItemsAdapterOptimized(baseUrl) { item -> ... }
   ```

2. Добавить предзагрузку при прокрутке (опционально):
   ```kotlin
   binding.rcVideoList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
       override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
           super.onScrolled(recyclerView, dx, dy)
           val layoutManager = recyclerView.layoutManager as LinearLayoutManager
           val firstVisible = layoutManager.findFirstVisibleItemPosition()
           val lastVisible = layoutManager.findLastVisibleItemPosition()
           
           // Предзагружаем изображения для следующих 10 позиций
           val positionsToPreload = (lastVisible + 1..lastVisible + 10)
           VideoItemsAdapterOptimized.preloadImages(
               adapter,
               recyclerView.context,
               positionsToPreload.toList()
           )
       }
   })
   ```

## 5. Дополнительные оптимизации

### A. Кэширование результатов поиска

Создать кэш поисковых запросов в `MoviesRepositoryImplOptimized`:

```kotlin
private val searchCache = mutableMapOf<String, List<ViewMovie>>()

override fun getMovies(...): Pager<Int, ViewMovie> {
    val cacheKey = generateCacheKey(search, order, searchType, genres, countries, years, imdbs, kps, likesPriority)
    
    // Проверяем кэш
    searchCache[cacheKey]?.let { cached ->
        // Возвращаем Pager с кэшированными данными
        return Pager(...) { CachedPagingSource(cached) }
    }
    
    // Обычная загрузка с сохранением в кэш
    return Pager(...) { 
        object : PagingSource<Int, ViewMovie>() {
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ViewMovie> {
                val result = // ... загрузка
                if (params.key == 0) {
                    searchCache[cacheKey] = result.data
                }
                return result
            }
        }
    }
}

private fun generateCacheKey(...): String {
    return listOf(search, order, searchType, genres, countries, years, imdbs, kps, likesPriority)
        .joinToString("|") { it.toString() }
}
```

### B. Оптимизация debounce в ViewModel

Уменьшить debounce время для более быстрого отклика:

```kotlin
// В HomeViewModel.kt
.actionStateFlow.filterIsInstance<UiAction.Search>()
    .distinctUntilChanged()
    .debounce(150)  // Уменьшено с 350 до 150 мс
```

### C. Использование DiffUtil для обновлений

Убедиться, что `diffItemCallback` оптимизирован:

```kotlin
fun diffItemCallback(
    itemsTheSame: (old: ViewMovie, new: ViewMovie) -> Boolean,
    contentsTheSame: (old: ViewMovie, new: ViewMovie) -> Boolean
) = object : DiffUtil.ItemCallback<ViewMovie>() {
    override fun areItemsTheSame(oldItem: ViewMovie, newItem: ViewMovie): Boolean =
        itemsTheSame(oldItem, newItem)

    override fun areContentsTheSame(oldItem: ViewMovie, newItem: ViewMovie): Boolean =
        contentsTheSame(oldItem, newItem)
}
```

## 6. Профилирование и мониторинг

### Добавление Timber для логирования

Включить логирование производительности:

```kotlin
// В MainPagingSourceOptimized.kt
Timber.d("Page $page loaded in ${System.currentTimeMillis() - startTime}ms")
```

### Создание профилировщика

Добавить в `HomeFragment`:

```kotlin
private var lastLoadTime: Long = 0

private fun observeData() {
    launchWhenCreated {
        viewModel.moviesDataFlow.collectLatest { pagingData ->
            val loadTime = System.currentTimeMillis() - lastLoadTime
            Timber.d("PagingData received, load time: ${loadTime}ms")
            lastLoadTime = System.currentTimeMillis()
            
            itemsAdapter?.submitData(pagingData)
        }
    }
}
```

## 7. Тестирование производительности

### A. Бенчмарки

Создать тесты для измерения:
- Время загрузки первой страницы
- Время прокрутки между страницами
- Использование памяти
- Частота кадров (FPS) при прокрутке

### B. Инструменты

- **Android Studio Profiler** для анализа CPU, памяти, сети
- **StrictMode** для обнаружения медленных операций на главном потоке
- **LeakCanary** для обнаружения утечек памяти

## 8. Миграция с текущей реализации

### Пошаговый план:

1. **Неделя 1**: Внедрить индексы и миграцию базы данных
   - Протестировать на устройстве с большим количеством записей (>1000)
   - Проверить время выполнения запросов

2. **Неделя 2**: Внедрить оптимизированный PagingSource и репозиторий
   - Протестировать пагинацию и кэширование
   - Проверить логи на наличие ошибок

3. **Неделя 3**: Внедрить оптимизированный адаптер
   - Протестировать плавность прокрутки
   - Проверить загрузку изображений

4. **Неделя 4**: Мониторинг и отладка
   - Собрать метрики производительности
   - Внести финальные корректировки

## 9. Ожидаемые результаты

После внедрения всех оптимизаций ожидается:

- **Ускорение загрузки списка** на 30-50%
- **Плавность прокрутки** - стабильные 60 FPS
- **Снижение использования памяти** на 20-30%
- **Уменьшение времени отклика** при поиске и фильтрации
- **Снижение нагрузки на базу данных** за счет эффективных индексов

## 10. Обратная совместимость

Все изменения обратно совместимы:
- Миграция базы данных сохраняет существующие данные
- API интерфейсов не меняется
- Можно откатить изменения при необходимости

## Контакты для поддержки

При возникновении вопросов по внедрению оптимизаций обращайтесь к архитектору проекта.