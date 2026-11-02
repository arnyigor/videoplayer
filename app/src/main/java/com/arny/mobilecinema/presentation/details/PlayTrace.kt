package com.arny.mobilecinema.presentation.details

/**
 * Диагностика запуска воспроизведения (план v3 §4.2).
 *
 * Каждый клик получает correlation ID, поэтому цепочка «клик → резолвер → навигация → плеер»
 * склеивается в логах даже когда в неё вмешивается другой запуск. URL в логи попадают только
 * в отредактированном виде: query, fragment и userinfo вырезаются — там бывают токены,
 * cookie-подписи и срок жизни ссылки.
 */
object PlayTrace {

    private const val MAX_ID_ATTEMPTS = 1000

    /** Короткий идентификатор клика. Не криптография: нужен только для склейки логов. */
    fun nextId(): String {
        val random = (1..MAX_ID_ATTEMPTS).random()
        val stamp = System.currentTimeMillis().toString(36).takeLast(4)
        return "p$stamp$random"
    }

    /** Абсолютный http(s)-адрес с хостом — единственное, что плеер умеет открыть. */
    fun isPlayableUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
            return false
        }
        val authority = trimmed.substringAfter("://").substringBefore('/')
        return authority.isNotBlank() && !authority.startsWith(':')
    }

    /**
     * URL без секретов: `https://host/path`, без query, fragment, userinfo и порта-хвостов.
     * Неизвестная схема возвращается как `<scheme>` — содержимое может быть чем угодно.
     */
    fun redactUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return "<empty>"
        val scheme = trimmed.substringBefore("://", "")
        if (scheme.isEmpty() || !trimmed.contains("://")) return "<no-scheme>"
        val rest = trimmed.substringAfter("://")
        val host = rest.substringBefore('/').substringAfterLast('@').substringBefore('?')
        val path = if (rest.contains('/')) {
            "/" + rest.substringAfter('/').substringBefore('?').substringBefore('#')
        } else {
            ""
        }
        val trimmedPath = path.trimEnd('/')
        return "$scheme://$host$trimmedPath"
    }
}
