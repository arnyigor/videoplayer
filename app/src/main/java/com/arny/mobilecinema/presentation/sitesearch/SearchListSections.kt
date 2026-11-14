package com.arny.mobilecinema.presentation.sitesearch

import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.search.SearchFilters
import com.arny.mobilecinema.domain.search.SearchHit
import com.arny.mobilecinema.domain.search.SearchRelevance
import com.arny.mobilecinema.domain.search.SearchSource

/** Секция списка результатов: заголовок или карточка. */
sealed interface SearchListItem {
    data class Header(val type: Type) : SearchListItem {
        enum class Type {
            /** Каталог: фильтры к нему применимы и применены. */
            CATALOG_FILTERED,

            /** Сайт: фильтры каталога к внешней выдаче не применялись. */
            SITE_UNFILTERED,

            /**
             * Сайт: карточки, ни одно название которых не совпало с запросом. Поиск сайта
             * сортирует по рейтингу, а не по совпадению, поэтому такие строки — шум первого
             * экрана; держим их отдельно и ниже, но не выбрасываем (среди них бывает нужный
             * тайтл под неточным названием).
             */
            SITE_SIMILAR,
        }
    }

    data class Hit(val hit: SearchHit) : SearchListItem
}

/**
 * S-013: в режиме «Везде» выдача не должна выглядеть одинаково отфильтрованной.
 *
 * Фильтры умеет только локальный каталог, поэтому при активных фильтрах (или когда в списке
 * есть оба источника) список делится на секции с честными подписями. Для одного источника
 * заголовки не нужны — это шум.
 */
object SearchListSections {

    fun build(
        hits: List<SearchHit>,
        source: SearchSource,
        filters: SearchFilters,
        searchType: String = AppConstants.SearchType.TITLE,
    ): List<SearchListItem> {
        if (hits.isEmpty()) return emptyList()
        val catalog = hits.filter { it.inCatalog }
        val site = hits.filterNot { it.inCatalog }
        // A film title need not contain its actor/director/genre search query.
        val siteSimilar = if (searchType == AppConstants.SearchType.TITLE) {
            site.filter { it.matchScore <= SearchRelevance.NO_MATCH }
        } else emptyList()
        val siteMatched = site - siteSimilar.toSet()
        val needsSections = source == SearchSource.ALL && (catalog.isNotEmpty() && site.isNotEmpty())
        // Секция «Похожие» нужна всегда, когда такие карточки есть: без неё мусор выдачи
        // сайта стоит в одном списке с совпадениями.
        val needsSimilarSection = siteSimilar.isNotEmpty()
        if (!needsSections && !needsSimilarSection) {
            return hits.map { SearchListItem.Hit(it) }
        }
        val items = mutableListOf<SearchListItem>()
        if (catalog.isNotEmpty()) {
            items += SearchListItem.Header(SearchListItem.Header.Type.CATALOG_FILTERED)
            items += catalog.map { SearchListItem.Hit(it) }
        }
        if (siteMatched.isNotEmpty()) {
            if (needsSections) {
                items += SearchListItem.Header(SearchListItem.Header.Type.SITE_UNFILTERED)
            }
            items += siteMatched.map { SearchListItem.Hit(it) }
        }
        if (needsSimilarSection) {
            items += SearchListItem.Header(SearchListItem.Header.Type.SITE_SIMILAR)
            items += siteSimilar.map { SearchListItem.Hit(it) }
        }
        return items
    }

    /** Фильтры недоступны для сайта: в режиме «только сайт» их нужно не применять, а выключить. */
    fun areFiltersAvailable(source: SearchSource): Boolean = source != SearchSource.REMOTE
}
