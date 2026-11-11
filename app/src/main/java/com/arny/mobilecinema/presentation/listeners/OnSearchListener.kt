package com.arny.mobilecinema.presentation.listeners

interface OnSearchListener {
    /** Поиск «завершён»: поле пустое и показан обычный список (не результаты). */
    fun isSearchComplete(): Boolean

    /** Поле поиска сейчас развёрнуто (видно на экране). */
    fun isSearchOpen(): Boolean

    /** Первое «Назад» при активном поиске: очистить текст и результаты, поиск остаётся открытым. */
    fun resetSearchContent()

    /** Второе «Назад»: закрыть поиск и вернуть экран в обычное состояние. */
    fun collapseSearch()
}
