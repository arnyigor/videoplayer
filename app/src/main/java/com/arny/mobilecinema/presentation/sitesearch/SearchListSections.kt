package com.arny.mobilecinema.presentation.sitesearch

import com.arny.mobilecinema.domain.search.SearchFilters
import com.arny.mobilecinema.domain.search.SearchHit
import com.arny.mobilecinema.domain.search.SearchSource

/** Секция списка результатов: заголовок или карточка. */
sealed interface SearchListItem {
    data class Header(val type: Type) : SearchListItem {
        enum class Type {
            /** Каталог: фильтры к нему применимы и применены. */
            CATALOG_FILTERED,

            /** Сайт: фильтры каталога к внешней выдаче не применялись. */
            SITE_UNFILTERED,
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
    ): List<SearchListItem> {
        if (hits.isEmpty()) return emptyList()
        val catalog = hits.filter { it.inCatalog }
        val site = hits.filterNot { it.inCatalog }
        val needsSections = source == SearchSource.ALL && (catalog.isNotEmpty() && site.isNotEmpty())
        if (!needsSections) {
            return hits.map { SearchListItem.Hit(it) }
        }
        val items = mutableListOf<SearchListItem>()
        if (catalog.isNotEmpty()) {
            items += SearchListItem.Header(SearchListItem.Header.Type.CATALOG_FILTERED)
            items += catalog.map { SearchListItem.Hit(it) }
        }
        if (site.isNotEmpty()) {
            items += SearchListItem.Header(SearchListItem.Header.Type.SITE_UNFILTERED)
            items += site.map { SearchListItem.Hit(it) }
        }
        return items
    }

    /** Фильтры недоступны для сайта: в режиме «только сайт» их нужно не применять, а выключить. */
    fun areFiltersAvailable(source: SearchSource): Boolean = source != SearchSource.REMOTE
}
