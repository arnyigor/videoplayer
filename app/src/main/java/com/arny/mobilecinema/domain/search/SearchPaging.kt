package com.arny.mobilecinema.domain.search

import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SiteSearchItem

/**
 * Признак «у сайта есть следующая страница выдачи».
 *
 * Размеры страницы у разделов разные (проверено на живом сайте: фильмы — 10 карточек,
 * сериалы — 20), поэтому счётчик одной секции не годится как стоп-условие для обеих.
 *
 * Отдельно учитывается повтор последней страницы: за пределами выдачи сайт отдаёт ту же
 * страницу ещё раз, и «страница полная» там остаётся истиной. Поэтому вызывающий код обязан
 * дополнительно прекращать обход, когда страница не принесла ни одной новой карточки.
 */
object SearchPaging {
    const val FILMS_PAGE_SIZE: Int = 10
    const val SERIALS_PAGE_SIZE: Int = 20

    fun hasMore(items: List<SiteSearchItem>): Boolean =
        items.count { it.type == MovieType.CINEMA } >= FILMS_PAGE_SIZE ||
                items.count { it.type == MovieType.SERIAL } >= SERIALS_PAGE_SIZE
}
