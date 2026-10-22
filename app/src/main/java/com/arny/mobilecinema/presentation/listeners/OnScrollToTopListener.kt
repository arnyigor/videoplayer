package com.arny.mobilecinema.presentation.listeners

/**
 * Вкладка умеет вернуть свой список в начало по повторному тапу по её пункту в нижней навигации
 * (MOB-08A). Состояние вкладки при этом не сбрасывается: данные, фильтры и стек остаются.
 */
interface OnScrollToTopListener {
    fun scrollToTop()
}
