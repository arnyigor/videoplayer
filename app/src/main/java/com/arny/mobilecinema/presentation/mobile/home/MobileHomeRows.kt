package com.arny.mobilecinema.presentation.mobile.home

import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.HomeHighlights
import com.arny.mobilecinema.domain.models.ViewMovie

/** Тип блока и его заголовок: строка берётся из ресурсов, а не из Compose-кода. */
enum class MobileHomeBlockType(val titleRes: Int) {
    CONTINUE(R.string.home_continue),
    MY_LIST(R.string.home_my_list),
    FOR_YOU(R.string.home_for_you),
    NEW(R.string.home_new),
    BEST_NOW(R.string.home_best_now),
}

data class MobileHomeBlock(
    val type: MobileHomeBlockType,
    val id: String,
    val movies: List<ViewMovie> = emptyList(),
    val continueCards: List<ContinueCardModel> = emptyList(),
)

data class MobileHomeContent(
    val featured: ViewMovie?,
    val blocks: List<MobileHomeBlock>,
)

/**
 * Порядок блоков мобильной главной (ТЗ ч. II §20.1), план MOB-09.
 *
 * Featured — один тайтл; «Продолжить» стоит максимально высоко, если история есть;
 * «Мой список» только при наличии данных. Пустые секции не создаются, чтобы новый
 * профиль не видел пустых заголовков.
 */
object MobileHomeRowsFactory {

    fun build(
        highlights: HomeHighlights,
        continueCards: List<ContinueCardModel>,
        myList: List<ViewMovie> = emptyList(),
    ): MobileHomeContent {
        val blocks = buildList {
            if (continueCards.isNotEmpty()) {
                add(
                    MobileHomeBlock(
                        type = MobileHomeBlockType.CONTINUE,
                        id = "continue",
                        continueCards = continueCards,
                    )
                )
            }
            addMovieBlock(MobileHomeBlockType.MY_LIST, "my_list", myList)
            addMovieBlock(MobileHomeBlockType.FOR_YOU, "for_you", highlights.forYou)
            addMovieBlock(MobileHomeBlockType.NEW, "new", highlights.recent)
            addMovieBlock(MobileHomeBlockType.BEST_NOW, "best_now", highlights.bestNow)
        }

        val featured = highlights.recent.firstOrNull()
            ?: highlights.bestNow.firstOrNull()
            ?: highlights.forYou.firstOrNull()

        return MobileHomeContent(featured = featured, blocks = blocks)
    }

    private fun MutableList<MobileHomeBlock>.addMovieBlock(
        type: MobileHomeBlockType,
        id: String,
        movies: List<ViewMovie>,
    ) {
        val distinct = movies.distinctBy { it.dbId }
        if (distinct.isNotEmpty()) add(MobileHomeBlock(type = type, id = id, movies = distinct))
    }
}
