package com.arny.mobilecinema.domain.models

/**
 * Домены Collaps, на которые указывают HD-ссылки старых записей (`.mpd` и плейлисты
 * вида `<имя>.mp4/master.m3u8`).
 * Домены ротировались (`takedwn.ws` -> `otsdkstub.ws` -> `deliveryv.ws` -> `matham.ws`)
 * и на 2026-10 мертвы: DNS не резолвится, соединение по 443 не устанавливается.
 *
 * Такие ссылки нельзя отдавать плееру: воспроизведение обрывается на середине, после чего
 * PlayerViewFragment.onPlayerError -> retryOpenCinema начинает перебирать те же мертвые
 * источники по кругу. Фильтр применяется и при чтении из БД, и при разборе страницы,
 * чтобы записи, пришедшие из старого дампа, не показывались как «HD».
 */
private val DEAD_SOURCE_HOSTS = listOf(
    "takedwn.ws",
    "otsdkstub.ws",
    "deliveryv.ws",
    "matham.ws",
)

private fun String.urlHost(): String =
    if (!contains("://")) {
        ""
    } else {
        substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore('@')
            .substringBefore(':')
            .lowercase()
    }

fun String.isDeadSourceUrl(): Boolean {
    val host = urlHost()
    if (host.isEmpty()) return false
    return DEAD_SOURCE_HOSTS.any { host == it || host.endsWith(".$it") }
}

/** Пустая строка для ссылки-заглушки/мертвого хоста: источник считается отсутствующим. */
fun String.playableSourceUrl(): String =
    if (isBlank() || isDeadSourceUrl()) "" else this

/**
 * Живые источники. Пустой список после фильтра означает «источников нет» —
 * на этом основано решение перепарсить карточку (см. needsHdBackfill).
 */
fun List<String>?.playableSourceUrls(): List<String> =
    this?.filter { it.playableSourceUrl().isNotEmpty() }.orEmpty()

fun CinemaUrlData.withPlayableSourceUrls(): CinemaUrlData =
    CinemaUrlData(
        hdUrl = hdUrl?.let { url -> url.copy(urls = url.urls.playableSourceUrls()) },
        cinemaUrl = cinemaUrl?.let { url -> url.copy(urls = url.urls.playableSourceUrls()) },
    )

fun List<SerialSeason>.withPlayableSourceUrls(): List<SerialSeason> =
    map { season ->
        season.copy(
            episodes = season.episodes.map { episode ->
                episode.copy(
                    hls = episode.hls.playableSourceUrl(),
                    dash = episode.dash.playableSourceUrl()
                )
            }
        )
    }
