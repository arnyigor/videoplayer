package com.arny.mobilecinema.domain.links

import com.arny.mobilecinema.domain.models.MovieType

/**
 * Разбор ссылки «Добавить по ссылке» (MENU-01, аудит v2 §5.1).
 *
 * UI у «Открыть» и «Сохранить в каталог» общий, но проверки обязательны для обоих:
 * открытие произвольного URL в плеере не должно становиться источником небезопасных
 * сетевых запросов.
 */
data class SiteLink(
    val url: String,
    val movieId: Long,
    val kind: MovieType,
)

sealed interface SiteLinkResult {
    data class Valid(val link: SiteLink) : SiteLinkResult

    data class Invalid(val reason: Reason) : SiteLinkResult

    enum class Reason {
        /** Пустая строка. */
        EMPTY,

        /** Не http/https — `file:`, `javascript:`, `intent:`, `content:` и подобное. */
        NOT_HTTP,

        /** Локальные и внутренние адреса: запрос наружу не должен уходить в сеть устройства. */
        UNSAFE_HOST,

        /** Чужой домен: приложение работает только с доменами сайта. */
        FOREIGN_HOST,

        /** Путь не похож на карточку (`films/<id>` или `serials/<id>`). */
        UNKNOWN_PATH,
    }
}

object SiteLinkParser {

    /**
     * Домены, с которыми работает приложение: настроенная база и её основной CDN-домен.
     * Другие зеркала добавляются здесь осознанно, а не «на всякий случай».
     */
    private val KNOWN_DOMAIN_SUFFIXES = listOf(".anwap.love", ".anwap.media")

    private val CARD_PATH = Regex("""/(films|serials)/(\d+)/?$""")

    fun parse(raw: String, baseUrl: String): SiteLinkResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return SiteLinkResult.Invalid(SiteLinkResult.Reason.EMPTY)

        val url = runCatching { java.net.URI(trimmed) }.getOrNull()
            ?: return SiteLinkResult.Invalid(SiteLinkResult.Reason.EMPTY)
        val scheme = url.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return SiteLinkResult.Invalid(SiteLinkResult.Reason.NOT_HTTP)
        }
        val host = url.host?.lowercase()?.trimEnd('.')
            ?: return SiteLinkResult.Invalid(SiteLinkResult.Reason.FOREIGN_HOST)
        if (isUnsafeHost(host)) {
            return SiteLinkResult.Invalid(SiteLinkResult.Reason.UNSAFE_HOST)
        }
        if (!isSiteHost(host, baseUrl)) {
            return SiteLinkResult.Invalid(SiteLinkResult.Reason.FOREIGN_HOST)
        }
        val match = CARD_PATH.find(url.path.orEmpty().trimEnd('/'))
            ?: return SiteLinkResult.Invalid(SiteLinkResult.Reason.UNKNOWN_PATH)
        val movieId = match.groupValues[2].toLongOrNull() ?: return SiteLinkResult.Invalid(
            SiteLinkResult.Reason.UNKNOWN_PATH
        )
        if (movieId <= 0) return SiteLinkResult.Invalid(SiteLinkResult.Reason.UNKNOWN_PATH)

        return SiteLinkResult.Valid(
            SiteLink(
                // Дальше работаем с нормализованной ссылкой без query/fragment.
                url = "https://$host/${match.groupValues[1]}/$movieId",
                movieId = movieId,
                kind = if (match.groupValues[1] == "serials") MovieType.SERIAL else MovieType.CINEMA,
            )
        )
    }

    private fun isSiteHost(host: String, baseUrl: String): Boolean {
        val baseHost = runCatching { java.net.URI(baseUrl.trim()).host?.lowercase() }.getOrNull()
        if (!baseHost.isNullOrBlank() && host == baseHost.trimEnd('.')) return true
        return KNOWN_DOMAIN_SUFFIXES.any { host.endsWith(it) }
    }

    /** Локальные, private и link-local адреса: их открытие не должно уходить в сеть. */
    private fun isUnsafeHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".local") || host == "[::1]" || host == "::1") {
            return true
        }
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return when {
            parts[0] == 127 -> true // loopback
            parts[0] == 10 -> true // private
            parts[0] == 172 && parts[1] in 16..31 -> true // private
            parts[0] == 192 && parts[1] == 168 -> true // private
            parts[0] == 169 && parts[1] == 254 -> true // link-local
            parts[0] == 0 -> true
            else -> false
        }
    }
}
