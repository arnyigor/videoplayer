package com.arny.mobilecinema.domain.interactors.jsoupupdate

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.DataResultWithProgress
import com.arny.mobilecinema.domain.models.LoadingData
import com.arny.mobilecinema.domain.models.SiteSearchItem
import kotlinx.coroutines.flow.FlowCollector

interface JsoupUpdateInteractor {
    fun getDecodePlayerData(value: String): String
    suspend fun getPageData(
        url: String,
        updateToNow: Boolean,
        flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>
    )

    suspend fun parsing(
        flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>
    )

    fun stopParsing(): Boolean

    /**
     * Поиск по каталогу самого сайта (/films/search/ и /serials/search/).
     * Нужен для карточек, до которых обход листингов ещё не дошёл.
     */
    suspend fun searchOnSite(query: String, page: Int = 1): DataResult<List<SiteSearchItem>>

    /**
     * Быстрое сохранение карточки по её адресу: разбирается только шапка страницы
     * (название, постер, год/жанры/описание и прочая информация), без серий и ссылок на видео.
     * Серии и ссылки добирает UpdateService из экрана деталей
     * (`DetailsFragment.requestAutoUpdateIfNeeded` → ACTION_UPDATE_BY_URL),
     * когда видит, что карточка ещё не разобрана полностью.
     *
     * Возвращает dbId строки в БД — по нему открывается экран деталей.
     */
    suspend fun fetchAndSaveMovieCard(url: String): DataResult<Long>
}
