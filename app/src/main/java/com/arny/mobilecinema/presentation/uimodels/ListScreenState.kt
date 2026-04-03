package com.arny.mobilecinema.presentation.uimodels

/**
 * Единое состояние экрана со списком элементов.
 * Используется вместо разрозненных флагов loading/empty для предотвращения
 * противоречивых UI-состояний (например, одновременно показанный прогресс и пустой экран).
 */
sealed class ListScreenState {
    /** Идёт первоначальная загрузка данных */
    object Loading : ListScreenState()

    /** Данные загружены, но список пуст */
    object Empty : ListScreenState()

    /** Ошибка загрузки данных */
    data class Error(val message: String) : ListScreenState()

    /** Контент успешно загружен и отображается */
    object Content : ListScreenState()
}
