package com.arny.mobilecinema.domain.search

import com.arny.mobilecinema.domain.models.MovieType

/** Источник поиска на едином экране (аудит v2, §4.1). */
enum class SearchSource {
    ALL,
    LOCAL,
    REMOTE,
}

/**
 * Откуда пришёл результат. `CATALOG` — тайтл уже в локальной базе (есть `stableId`),
 * `SITE` — карточка сайта, которая попадёт в базу только при открытии.
 */
enum class SearchOrigin {
    CATALOG,
    SITE,
}

/**
 * Одна модель результата для двух источников (аудит v2, §4.3).
 *
 * `stableId` — существующий canonical id локального тайтла; внешний идентификатор
 * (`siteRef`, pageUrl) никогда его не подменяет.
 */
data class SearchHit(
    val origin: SearchOrigin,
    val title: String = "",
    val originalTitle: String = "",
    val year: Int = 0,
    val kind: MovieType = MovieType.NO_TYPE,
    val posterUrl: String = "",
    val description: String = "",
    val rating: Float = 0f,
    val matchScore: Int = 0,
    /** dbId локального тайтла; null для результата, которого нет в каталоге. */
    val stableId: Long? = null,
    /** pageUrl карточки сайта; пусто для локального результата. */
    val siteRef: String = "",
) {
    val inCatalog: Boolean get() = origin == SearchOrigin.CATALOG
}

/**
 * Слияние локальной и внешней выдачи в один список.
 *
 * Правила (аудит v2, §4.3):
 * - совпадение по `pageUrl` — это один и тот же тайтл, остаётся локальная карточка;
 * - совпадение по нормализованному названию и году (если год известен у обоих) — тоже
 *   один тайтл; при расхождении года записи НЕ склеиваются (римейки и одноимённые фильмы);
 * - порядок: релевантность, затем локальные карточки, затем порядок источника.
 *
 * Транслитерация намеренно не угадывается: «Матрица» и «The Matrix» склеятся только при
 * совпадении оригинального названия, иначе это разные строки выдачи.
 */
object SearchMerge {

    fun merge(local: List<SearchHit>, remote: List<SearchHit>): List<SearchHit> {
        if (remote.isEmpty()) return local.sortedByRelevance()
        if (local.isEmpty()) return remote.sortedByRelevance()

        val catalogKeys = local.mapNotNull { it.matchKey() }.toMutableSet()
        val catalogRefs = local.map { it.siteRef }.filter { it.isNotBlank() }.toMutableSet()

        val uniqueRemote = remote.filterNot { hit ->
            (hit.siteRef.isNotBlank() && hit.siteRef in catalogRefs) ||
                    hit.matchKey()?.let { it in catalogKeys } == true
        }

        return (local + uniqueRemote)
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<SearchHit>> { it.value.matchScore }
                    .thenBy { if (it.value.inCatalog) 0 else 1 }
                    .thenBy { it.index }
            )
            .map { it.value }
    }

    private fun List<SearchHit>.sortedByRelevance(): List<SearchHit> =
        withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<SearchHit>> { it.value.matchScore }
                    .thenBy { if (it.value.inCatalog) 0 else 1 }
                    .thenBy { it.index }
            )
            .map { it.value }

    /**
     * Ключ тождества результата. Год входит в ключ только когда он известен: без года
     * сравнение по названию склеило бы «Матрицу» 1999 и «Матрицу» 2021.
     */
    private fun SearchHit.matchKey(): String? {
        val normalized = normalizeTitle(title)
        if (normalized.isEmpty()) return null
        return if (year > 0) "$normalized|$year|${kind.value}" else null
    }

    fun normalizeTitle(title: String): String =
        title.lowercase()
            .replace('ё', 'е')
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ")
}
