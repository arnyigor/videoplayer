package com.arny.mobilecinema.presentation.utils

import com.arny.mobilecinema.data.utils.getWithDomain as resolveUrl

/**
 * Абсолютный URL постера. Relative path без настроенного baseUrl раньше давал пустую
 * строку и карточки оставались без изображений на устройстве.
 */
fun String.getWithDomain(baseUrl: String): String = resolveUrl(baseUrl)
