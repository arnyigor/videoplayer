package com.arny.mobilecinema.domain.models

enum class OrderKey(val pref: String) {
    NORMAL(PrefsConstants.ORDER),
    HISTORY(PrefsConstants.HISTORY_ORDER),
    FAVORITE(PrefsConstants.FAVORITE_ORDER)
}