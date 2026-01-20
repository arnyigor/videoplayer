package com.arny.mobilecinema.domain.interactors.jsoupupdate

import com.arny.mobilecinema.data.models.DataResultWithProgress
import com.arny.mobilecinema.data.network.jsoup.JsoupService
import com.arny.mobilecinema.data.network.jsoup.JsoupServiceHelper
import com.arny.mobilecinema.data.network.jsoup.LogLevel
import com.arny.mobilecinema.data.utils.cleanAnwapEncryptedData
import com.arny.mobilecinema.data.utils.getDecodedData
import com.arny.mobilecinema.data.utils.getDomainName
import com.arny.mobilecinema.data.utils.getJsoupDuration
import com.arny.mobilecinema.data.utils.getProgress
import com.arny.mobilecinema.data.utils.getProgressPersent
import com.arny.mobilecinema.data.utils.getRemain
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.data.utils.loadingText
import com.arny.mobilecinema.data.utils.noticeText
import com.arny.mobilecinema.data.utils.printTime
import com.arny.mobilecinema.domain.models.AnwapUrl
import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.EpisodeLink
import com.arny.mobilecinema.domain.models.LoadingData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.domain.models.UpdateType
import com.arny.mobilecinema.domain.repository.JsoupUpdateRepository
import com.arny.mobilecinema.domain.repository.UpdateRepository
import data.models.AnwapSeasonPlaylist
import data.utils.isNumeric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

class JsoupUpdateInteractorImpl @Inject constructor(
    private val jsoupService: JsoupService,
    private val repository: JsoupUpdateRepository,
    private val updateRepository: UpdateRepository,
    private val helper: JsoupServiceHelper,
) : JsoupUpdateInteractor {
    private companion object {
        const val MAX_TRYING = 3
        const val IGNORE_COUNT_MAX = 30
    }

    private var loadPageTime = 0L

    @Volatile
    private var isParsing = true

    @Volatile
    private var curTry = 1

    @Volatile
    private var cinemaPage = 0

    @Volatile
    private var serialPage = 0
    private val linkAv = mutableListOf<Long>()
    private val pageAv = mutableListOf<Long>()
    private val noticeAv = mutableListOf<Long>()

    @Volatile
    private var ignoreCount = 0

    @Volatile
    private var currentParsingType = ""

    @Volatile
    private var isFullParsing = false

    override suspend fun parsing(flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>) {
        withContext(Dispatchers.IO) {
            parsingAll(
                pageStart = 0,
                parseType = Selectors.TYPE_ALL,
                updateType = true,
                flowCollector = flowCollector
            )
        }
    }

    private suspend fun parsingAll(
        pageStart: Int,
        parseType: String,
        updateType: Boolean,
        flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>
    ) {
        resetParsing()
        if (updateType) {
            updateParsingType(parseType)
        }
        val urlAdd = initParseUrl(currentParsingType)
        try {
            mainParsingByType(urlAdd, pageStart, currentParsingType, flowCollector)
        } catch (e: Exception) {
            when (e) {
                is ParsingStopException -> {
                    if (isFullParsing) {
                        updateParsingType(currentParsingType)
                        isFullParsing = false
                        parsingAll(
                            pageStart = 0,
                            parseType = currentParsingType,
                            updateType = false,
                            flowCollector = flowCollector
                        )
                    } else {
                        throw e
                    }
                }

                else -> throw e
            }
        }
    }

    private fun resetParsing() {
        isParsing = true
        loadPageTime = 0
    }

    private fun initParseUrl(parseType: String): String = when (parseType) {
        Selectors.TYPE_FILMS -> Selectors.FILMS_URL
        Selectors.TYPE_SERIALS -> Selectors.SERIALS_URL
        else -> Selectors.FILMS_URL
    }

    private fun updateParsingType(parseType: String) {
        currentParsingType = when (parseType) {
            Selectors.TYPE_ALL -> {
                isFullParsing = true
                Selectors.TYPE_FILMS
            }

            Selectors.TYPE_FILMS -> Selectors.TYPE_SERIALS
            Selectors.TYPE_SERIALS -> Selectors.TYPE_FILMS
            else -> Selectors.TYPE_FILMS
        }
    }

    private suspend fun mainParsingByType(
        urlAdd: String,
        startPage: Int,
        parsingType: String,
        flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>
    ) {
        val baseUrl = updateRepository.baseUrl
        val url = baseUrl + urlAdd
        val pages = getPagesCount(loadPage(url))
        val globalStart = System.currentTimeMillis()
        for (page in startPage..pages) {
            savePage(parsingType, page)
            flowCollector.emit(loading(UpdateType.PAGE_NEW to "$page"))
            val pageTimeStart = System.currentTimeMillis()
            val filmLinks = getParsingLinks(
                baseUrl,
                page,
                parsingType
            )
            val size = filmLinks.size
            parseLinks(
                fCollector = flowCollector,
                filmLinks = filmLinks,
                page = page,
                pages = pages,
                size = size,
                globalStart = globalStart,
                pageTimeStart = pageTimeStart,
            )
        }
    }

    private suspend fun getParsingLinks(
        url: String,
        page: Int,
        parseType: String
    ): List<String> {
        val pageUrl = getPageUrl(url, page, parseType)
        val document = loadPage(pageUrl)
        return getFilmLinks(document, document.location())
    }

    private suspend fun parseLinks(
        fCollector: FlowCollector<DataResultWithProgress<LoadingData>>,
        filmLinks: List<String>,
        page: Int,
        pages: Int,
        size: Int,
        globalStart: Long,
        pageTimeStart: Long,
    ) {
        for ((linkId, filmLink) in filmLinks.withIndex()) {
            yield()
            if (!isParsing) {
                throw ParsingStopException("Парсинг завершен")
            }
            val linkTimeStart = System.currentTimeMillis()
            parseLink(fCollector, filmLink)
            fCollector.emit(loading(UpdateType.PAGE_CURRENT_LINK to "Всего записей ${repository.getMoviesSize()} текущая страница $page из $pages"))
            fCollector.emit(loading(UpdateType.PROGRESS1 to "${getProgress(linkId + 1, size)}"))
            fCollector.emit(
                getUpdateInfo(
                    linkId,
                    size,
                    linkTimeStart,
                    globalStart,
                    UpdateType.TIME,
                    linkAv,
                    10
                )
            )
        }
        fCollector.emit(getNoticeInfo(page, pages, pageTimeStart, UpdateType.NOTICE, noticeAv, 10))
        fCollector.emit(loading(UpdateType.PROGRESS2 to "${getProgress(page, pages)}"))
        fCollector.emit(
            getUpdateInfo(
                page,
                pages,
                pageTimeStart,
                globalStart,
                UpdateType.TIME2,
                pageAv,
                10
            )
        )
    }

    private fun savePage(parseType: String, cPage: Int) {
        when (parseType) {
            Selectors.TYPE_FILMS -> {
                if (cinemaPage != cPage) {
                    cinemaPage = cPage
                }
            }

            else -> {
                if (serialPage != cPage) {
                    serialPage = cPage
                }
            }
        }
    }

    override fun stopParsing(): Boolean {
        isParsing = false
        return true
    }

    private suspend fun parseLink(
        flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>,
        filmLink: String,
        updateToNow: Boolean = false,
    ) {
        flowCollector.emit(loading(UpdateType.URL to filmLink))
        val url = filmLink.removeDomain()
        val dbMovie = repository.selectMovieByUrl(url)
        try {
            val doc = loadPage(filmLink)
            val code = doc.connection().response().statusCode()
            val anwapMovie = getAnwapMovie(
                doc = doc,
                full = false,
                filmLink = filmLink,
                updateToNow = updateToNow,
                flowCollector = flowCollector
            )
            flowCollector.emit(loading(UpdateType.TITLE to "\"${anwapMovie.title}\""))
            when {
                code == 404 && dbMovie != null -> {
                    flowCollector.emit(loading(UpdateType.LINK to "Не найден результат - 404"))
                    flowCollector.emit(loading(UpdateType.MOVIE to dbMovie.toString()))
                    curTry = 1
                    if (ignoreCount >= IGNORE_COUNT_MAX) {
                        isParsing = false
                    }
                    ignoreCount++
                }

                code == 404 -> {
                    flowCollector.emit(loading(UpdateType.LINK to "Не найден результат - 404"))
                    curTry = 1
                    ignoreCount++
                }

                dbMovie == null -> {
                    val message = "Новая загрузка \"${anwapMovie.title}\""
                    flowCollector.emit(loading(UpdateType.LINK to message))
                    val movie = getAnwapMovie(
                        doc = doc,
                        full = true,
                        filmLink = filmLink,
                        updateToNow = updateToNow,
                        flowCollector = flowCollector
                    )
                    val checkDuplicates = checkDuplicates(
                        filmLink = filmLink,
                        newMovie = movie,
                        strongCheck = true
                    )
                    if (checkDuplicates) {
                        repository.insertMovie(movie)
                    }
                    curTry = 1
                    ignoreCount = 0
                    flowCollector.emit(loading(UpdateType.MOVIE to movie.toString()))
                }

                !dbMovie.hasAllVideoData() -> {
                    val message =
                        "Обновление c \"${dbMovie.title}\" ${dbMovie.pageUrl} на \"${anwapMovie.title}\" ${anwapMovie.pageUrl}"
                    flowCollector.emit(loading(UpdateType.LINK to message))
                    val movie = getAnwapMovie(
                        doc = doc,
                        full = true,
                        filmLink = filmLink,
                        updateToNow = updateToNow,
                        flowCollector = flowCollector
                    )
                    flowCollector.emit(loading(UpdateType.MOVIE to movie.toString()))
                    checkDuplicates(
                        filmLink = filmLink,
                        newMovie = movie,
                        strongCheck = false
                    )
                    updateComplete(movie, dbMovie, flowCollector)
                }

                anwapMovie.title != dbMovie.title -> {
                    val time =
                        "Обновление c \"${dbMovie.title}\" ${dbMovie.pageUrl} на \"${anwapMovie.title}\" ${anwapMovie.pageUrl}"
                    flowCollector.emit(loading(UpdateType.LINK to time))
                    val movie = getAnwapMovie(
                        doc = doc,
                        full = true,
                        filmLink = filmLink,
                        updateToNow = updateToNow,
                        flowCollector = flowCollector
                    )
                    flowCollector.emit(loading(UpdateType.MOVIE to movie.toString()))
                    checkDuplicates(
                        filmLink = filmLink,
                        newMovie = movie,
                        strongCheck = false
                    )
                    updateComplete(movie, dbMovie, flowCollector)
                }

                isUpdateByUpdateTime(anwapMovie, dbMovie, updateToNow) -> {
                    val oldTime = dbMovie.info.updated.printTime()
                    val newTime = anwapMovie.info.updated.printTime()
                    val time = "Обновление \"${anwapMovie.title}\" c $oldTime на $newTime"
                    Timber.d(time)
                    flowCollector.emit(loading(UpdateType.LINK to time))
                    val movie = getAnwapMovie(
                        doc = doc,
                        full = true,
                        filmLink = filmLink,
                        updateToNow = updateToNow,
                        flowCollector = flowCollector
                    )
                    checkDuplicates(
                        filmLink = filmLink,
                        newMovie = movie,
                        strongCheck = false
                    )
                    updateComplete(movie, dbMovie, flowCollector)
                }

                else -> {
//                    val oldTime = dbMovie.info.updated.printTime()
//                    flowCollector.emit(isInitValid(UpdateType.LINK to "Уже имеется ${dbMovie.title} с датой $oldTime"))
//                    flowCollector.emit(isInitValid(UpdateType.MOVIE to dbMovie.toString()))
                    curTry = 1
                    if (ignoreCount >= IGNORE_COUNT_MAX) {
                        isParsing = false
                    }
                    ignoreCount++
                }
            }
        } catch (e: Exception) {
            when (e) {
                is ParsingRetryException -> {
                    if (curTry <= MAX_TRYING) {
                        curTry++
                        Timber.d("Ошибка: не найдены ссылки ${filmLink}, пробуем еще раз, попыток $curTry из $MAX_TRYING")
                        flowCollector.emit(loading(UpdateType.MOVIE to "Ошибка ${e.message}, пробуем еще раз, попыток $curTry из $MAX_TRYING"))
                        parseLink(flowCollector, filmLink, updateToNow)
                    } else {
                        e.printStackTrace()
                        Timber.d("Ошибка: попыток $curTry из $MAX_TRYING\n ${e.message}")
                        flowCollector.emit(DataResultWithProgress.Error(e))
                    }
                }

                is ParsingIgnoreException -> {
                    e.printStackTrace()
                    Timber.d("Ошибка: не найдены ссылки для загрузки ${filmLink}, пропускаем")
                    flowCollector.emit(DataResultWithProgress.Error(e))
                }

                else -> {
                    e.printStackTrace()
                    flowCollector.emit(DataResultWithProgress.Error(e))
                }
            }
        }
    }

    private fun isUpdateByUpdateTime(
        anwapMovie: Movie,
        dbMovie: Movie?,
        updateToNow: Boolean
    ): Boolean {
        val newTime = anwapMovie.info.updated
        val dbTime = dbMovie?.info?.updated ?: 0L
        return updateToNow ||
                TimeUnit.MILLISECONDS.toHours(newTime - dbTime) > 1
    }

    private suspend fun updateComplete(
        movie: Movie,
        dbMovie: Movie,
        flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>
    ) {
        val updateMovie = repository.updateMovie(
            movie = movie,
            dbId = dbMovie.dbId,
        )
        val s = if (updateMovie) {
            "Успешно"
        } else {
            "Не успешно"
        }
        curTry = 1
        ignoreCount = 0
        flowCollector.emit(
            loading(
                params = UpdateType.TITLE to "\"${movie.title}\" $s",
                complete = true,
                success = true
            )
        )
        flowCollector.emit(
            loading(
                UpdateType.MOVIE to movie.toString(),
                success = updateMovie,
                complete = true
            )
        )
    }

    private fun checkDuplicates(filmLink: String, newMovie: Movie, strongCheck: Boolean): Boolean {
        val dbMovie = repository.selectMovieByImg(newMovie.img)
        return when {
            strongCheck && dbMovie != null -> {
                Timber.d("Уже имеется $filmLink c названием ${dbMovie.title}")
                false
            }

            filmLink.contains("films") && newMovie.type != MovieType.CINEMA -> {
                error("Несовпадение типов")
            }

            filmLink.contains("serials") && newMovie.type != MovieType.SERIAL -> {
                error("Несовпадение типов")
            }

            else -> true
        }
    }

    /*private fun fixDuplicates() {
        val group = listOf(
            Tables.Movies.TITLE_COL,
            Tables.Movies.IMG_COL,
        ).joinToString(",")
        val duplicates = repository.getDuplicatesBy(group)
        val size = duplicates.size
        Timber.d("duplicates:$size")
        val fullList = mutableListOf<Movie>()
        for (duplicate in duplicates) {
            val allDuplicates = repository.getMoviesByQuery(
                search = "${duplicate.movieId}",
                column = Tables.Movies.MOVIE_ID_COL,
                equals = true
            )
            if (allDuplicates.isNotEmpty()) {
                fullList.addAll(allDuplicates)
            }
        }
        for (movie in fullList) {
            val errors = repository.getErrors(
                args = listOf(Tables.Errors.PAGE_URL_COLUMN to SqlParam.StringParam(movie.pageUrl))
            )
            if (errors.isEmpty()) {
                dbRepository.insertError(movie.pageUrl, "Дублирование")
            }
            repository.removeMovie(movie.dbId)
            Timber.d("${movie.title} -> ${movie.pageUrl}\n")
        }
        isParsing = false
    }*/

    private suspend fun loadPage(url: String): Document =
        loadPageWithProxy(url)

    private suspend fun loadPageWithProxy(
        url: String
    ): Document {
        val start = System.currentTimeMillis()
        val domainName = getDomainName(url)
        val domain = domainName.substringAfter("//")
        val path = url.substringAfter(domainName)
        return try {
            val delayMin = 1500L
            val delayMax = 2500L
            val needDelay = loadPageTime in 1L..delayMax
            val document = jsoupService.loadPage(
                url = url,
                requestHeaders = JsoupServiceHelper.getInstance().headers,
                currentProxy = null,
                needDelay = needDelay,
                delayMin = delayMin,
                delayMax = delayMax,
                timeout = 120000,
                logLevel = LogLevel.NONE,
                resetCookie = false,
                domain = domain,
                path = path
            )
            loadPageTime = System.currentTimeMillis() - start
            document
        } catch (ex: Exception) {
            loadPageTime = System.currentTimeMillis() - start
            throw ex
        }
    }

    private suspend fun loadUrl(
        url: String,
    ): String {
        val start = System.currentTimeMillis()
        return try {
            val delayMin = 1500L
            val delayMax = 2500L
            val needDelay = loadPageTime in 1L..delayMax
            val document = jsoupService.loadUrl(
                url = url,
                requestHeaders = helper.headers,
                needDelay = needDelay,
                delayMin = delayMin,
                delayMax = delayMax,
                timeout = 5000,
                changeUA = false,
                logLevel = LogLevel.NONE,
                resetCookie = true
            )
            loadPageTime = System.currentTimeMillis() - start
            document
        } catch (ex: Exception) {
            ex.printStackTrace()
            ""
        }
    }

    private fun getUpdateInfo(
        i: Int,
        pages: Int,
        timeStart: Long,
        timeStartGlobal: Long,
        key: String,
        list: MutableList<Long>,
        maxAverage: Int,
    ): DataResultWithProgress<LoadingData> {
        val progress = getProgressPersent(i, pages)
        val remainStr = getRemain(i, pages, timeStart, list, maxAverage)
        val duration = getJsoupDuration(timeStart)
        val durationGlobal = getJsoupDuration(timeStartGlobal)
        val text = loadingText(
            index = i,
            size = pages,
            progress = progress,
            duration = duration,
            durationGlobal = durationGlobal,
            remainStr = remainStr,
        )
        return loading(key to text)
    }

    private fun getNoticeInfo(
        i: Int,
        pages: Int,
        timeStart: Long,
        key: String,
        list: MutableList<Long>,
        maxAverage: Int,
    ): DataResultWithProgress<LoadingData> {
        val progress = getProgressPersent(i, pages)
        val remainStr = getRemain(i, pages, timeStart, list, maxAverage)
        val text = noticeText(
            index = i,
            size = pages,
            progress = progress,
            remainStr = remainStr,
        )
        return loading(key to text)
    }

    override suspend fun getPageData(
        url: String,
        updateToNow: Boolean,
        flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>
    ) {
        withContext(Dispatchers.IO) {
            parseLink(
                flowCollector = flowCollector,
                filmLink = url,
                updateToNow = updateToNow,
            )
        }
    }

    private suspend fun getAnwapMovie(
        doc: Document,
        full: Boolean,
        filmLink: String,
        updateToNow: Boolean = false,
        flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>,
    ): Movie {
        val page = doc.select(Selectors.PAGE).firstOrNull()
        requireNotNull(page)
        val location = doc.location()
        val id = getMovieId(location)
        val type = getMovieType(location)
        val title = getTitle(page)
        val img = getImg(page, location)
        val info = getInfo(page, updateToNow)
        var cinemaUrlData = CinemaUrlData()
        var seasons = emptyList<SerialSeason>()
        if (full) {
            if (type == MovieType.CINEMA) {
                flowCollector.emit(loading(UpdateType.PAGE_CURRENT_LINK to "Получаем данные фильма $title"))
                cinemaUrlData = getCinemaUrlData(page)
                cinemaUrlData = getMp4UrlData(page, location, cinemaUrlData)
                if (cinemaUrlData.isNoCinemaUrls()) {
                    throw ParsingRetryException("Не найдены ссылки на $filmLink")
                }
            } else {
                flowCollector.emit(loading(UpdateType.PAGE_CURRENT_LINK to "Получаем данные сериала $title"))
                seasons = getSeasons(
                    page = page,
                    location = location,
                    filmLink = filmLink,
                    flowCollector = flowCollector
                )
            }
        }
        return Movie(
            movieId = id,
            title = title,
            type = type,
            origTitle = info.origTitle,
            pageUrl = filmLink.removeDomain(),
            img = img,
            info = info,
            seasons = seasons,
            cinemaUrlData = cinemaUrlData
        )
    }

    private fun getCinemaUrlData(
        page: Element
    ): CinemaUrlData {
        val data = page.selectFirst(Selectors.PAGE_SCRIPT)?.data()
        val scriptData = data.orEmpty().trimIndent().trim()
        val cinemaUrl = getUrlsData(
            scriptData = scriptData,
            regex = Selectors.KINO_REGEXP.toRegex(),
            simpleRegex = Selectors.SIMPLE_REGEXP.toRegex(),
            require = true,
        )
        return CinemaUrlData(
            cinemaUrl = cinemaUrl,
        )
    }

    private suspend fun getMp4UrlData(
        page: Element,
        location: String,
        cinemaUrlData: CinemaUrlData
    ): CinemaUrlData {
        var data = cinemaUrlData
        val mp4Link: String? =
            getMp4Link(page.getAllCinemaLinks().lastOrNull()?.getWithDomain(location))
        if (!mp4Link.isNullOrBlank()) {
            val urls = data.cinemaUrl?.urls.orEmpty().toMutableList()
            urls.add(mp4Link)
            data = data.copy(cinemaUrl = AnwapUrl(urls = urls))
        }
        return data
    }

    private suspend fun getMp4Link(link: String?): String? {
        var result = link
        if (!result.isNullOrBlank() && !result.endsWith("mp4") && result.startsWith("http")) {
            val url = loadUrl(result)
            if (url.isNotBlank()) {
                result = url
            }
        }
        return result
    }

    private suspend fun getSeasons(
        page: Element,
        location: String,
        filmLink: String,
        flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>,
    ): List<SerialSeason> {
        // Результат: список сезонов
        val resultSeasons = mutableListOf<SerialSeason>()

        // Шаг 1: Получаем ссылки на сезоны
        val seasonsLinks = getSeasonsLinks(page)
        flowCollector.emit(loading(UpdateType.PAGE_CURRENT_LINK to "Получаем данные сериала seasonsLinks->${seasonsLinks.isNotEmpty()}"))

        if (seasonsLinks.isEmpty()) {
            // Если ссылок на сезоны нет, завершаем выполнение
            return emptyList()
        }

        // Шаг 2: Получаем общее количество эпизодов
        val episodesCount = getAllEpisodes(page)

        // Шаг 3: Обрабатываем ссылки на сезоны через плейлист
        getByPlayList(flowCollector, seasonsLinks, resultSeasons, location)

        // Сортируем сезоны по ID
        val seasons = resultSeasons.sortedBy { it.id }

        // Шаг 4: Проверяем, все ли эпизоды найдены
        var hasAllEpisodes = hasAllEpisodes(resultSeasons, episodesCount)
        flowCollector.emit(loading(UpdateType.PAGE_CURRENT_LINK to "Проверяем наличие всех эпизодов -> $hasAllEpisodes"))

        if (!hasAllEpisodes) {
            // Шаг 5: Если не все эпизоды найдены, обрабатываем отсутствующие сезоны
            val absentSeasonsLinks = getAbsentSeasonsLinks(seasons, seasonsLinks)

            for ((seasonId, link) in absentSeasonsLinks) {
                // Пробуем получить сезон из первого эпизода
                val season = getSeasonFromFirstEpisode(
                    loadPage(link.getWithDomain(location)),
                    location,
                    seasonId
                )
                if (season != null) {
                    resultSeasons.add(season)
                } else {
                    // Если не удалось, получаем все эпизоды
                    getFullEpisodes(
                        i = seasonId - 1,
                        link = link,
                        location = location,
                        resultSeasons = resultSeasons,
                        flowCollector = flowCollector
                    )
                }
            }

            flowCollector.emit(loading(UpdateType.PAGE_CURRENT_LINK to "Обрабатываем отсутствующие сезоны -> $hasAllEpisodes"))
        }

        // Шаг 6: Проверяем снова, все ли эпизоды найдены
        hasAllEpisodes = hasAllEpisodes(resultSeasons, episodesCount)

        if (!hasAllEpisodes) {
            // Шаг 7: Если всё ещё не все эпизоды найдены, обрабатываем отсутствующие эпизоды
            for ((i, link) in seasonsLinks.withIndex()) {
                if (resultSeasons.isNotEmpty()) {
                    getAbsentEpisodes(
                        i = i,
                        resultSeasons = resultSeasons,
                        link = link,
                        location = location,
                        flowCollector = flowCollector
                    )
                } else {
                    getFullEpisodes(
                        i = i,
                        link = link,
                        location = location,
                        resultSeasons = resultSeasons,
                        flowCollector = flowCollector
                    )
                }
            }

            flowCollector.emit(loading(UpdateType.PAGE_CURRENT_LINK to "Обрабатываем отсутствующие эпизоды -> $hasAllEpisodes"))
        }

        // Шаг 8: Итоговая проверка
        hasAllEpisodes = hasAllEpisodes(resultSeasons, episodesCount)

        if (!hasAllEpisodes) {
            // Если всё ещё не все эпизоды найдены, выбрасываем ошибку
            val sum = resultSeasons.sumOf { it.episodes.size }
            val abs = abs(episodesCount - sum)
            val diffOneOrZero = sum > episodesCount || abs <= 1

            if (!diffOneOrZero) {
                error("Не найдены все эпизоды $filmLink, $sum!=$episodesCount")
            }
        }

        // Возвращаем отсортированный список сезонов
        return resultSeasons.sortedBy { it.id }
    }

    private suspend fun getByPlayList(
        flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>,
        seasonsLinks: List<String>,
        resultSeasons: MutableList<SerialSeason>,
        location: String
    ) {
        flowCollector.emit(loading(UpdateType.PAGE_CURRENT_LINK to "Получаем данные сериала playlist"))
        for ((ind, link) in seasonsLinks.withIndex()) {
            getEpisodesBySeasonPlaylist(ind, resultSeasons, link, location)
        }
    }

    private suspend fun getEpisodesBySeasonPlaylist(
        i: Int,
        resultSeasons: MutableList<SerialSeason>,
        link: String,
        location: String,
    ) {
        val seasonId = i + 1
        val page = loadPage(link.getWithDomain(location))
        val links = getEpisodesLinks(page)
        val episodeId = links.firstOrNull()?.substringAfterLast("/")
        if (episodeId != null) {
//            https://ma.anwap.today/serials/playlist_57202_h.txt
            val url = "${getDomainName(location)}/serials/playlist_${episodeId}_h.txt"
            val season = getSeasonFromPlaylist(url, seasonId)
            resultSeasons.add(season)
        }
    }

    private suspend fun getSeasonFromPlaylist(
        url: String,
        seasonId: Int
    ): SerialSeason {
        val data = getSeasonData(listOf(url))
        return SerialSeason(
            id = seasonId,
            episodes = data.playlist.mapIndexed { index, urlData ->
                val urlsFromFile = getUrlsFromFile(urlData.file.orEmpty())
                SerialEpisode(
                    id = (urlData.id?.toIntOrNull() ?: 0) + 1,
                    episode = "${index + 1}",
                    title = urlData.title.orEmpty(),
                    hls = urlsFromFile.firstOrNull().orEmpty()
                )
            }
        )
    }

    private suspend fun getFullEpisodes(
        i: Int,
        link: String,
        location: String,
        resultSeasons: MutableList<SerialSeason>,
        flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>,
    ) {
        val seasonId = i + 1
        val allEpisodes = mutableListOf<SerialEpisode>()
        val episodesLinks = getSeasonEpisodesLinks(link, location)
        val size2 = episodesLinks.size
        for ((index, episodeLink) in episodesLinks.withIndex()) {
            val iter = index + 1
            flowCollector.emit(loading(UpdateType.PAGE_CURRENT_LINK to "Получаем данные сериала : $iter из $size2"))
            val episode = loadPage(episodeLink.link.getWithDomain(location))
            val serialEpisode = getEpisode(episode, location, index)
            if (serialEpisode != null) {
                allEpisodes.add(serialEpisode)
            }
        }
        resultSeasons.add(SerialSeason(seasonId, allEpisodes.sortedBy { it.episode.toInt() }))
    }

    private suspend fun getAbsentEpisodes(
        i: Int,
        resultSeasons: MutableList<SerialSeason>,
        link: String,
        location: String,
        flowCollector: FlowCollector<DataResultWithProgress<LoadingData>>,
    ) {
        val seasonId = i + 1
        val seasonIndex = resultSeasons.indexOfFirst { it.id == seasonId }
        val serialSeason = resultSeasons[seasonIndex]
        val episodesLinks = getSeasonEpisodesLinks(link, location)
        val linkList = episodesLinks.map { it.link }
        val episodes = serialSeason.episodes.toMutableList()
        val absentEpisodes = getAbsentEpisodesLinks(episodes, linkList)
        val size = episodes.size + absentEpisodes.size
        if (absentEpisodes.isNotEmpty()) {
            for ((episodeId, absentLink) in absentEpisodes) {
                val episodeHtml = loadPage(absentLink.getWithDomain(location))
                val index = episodeId - 1
                val serialEpisode = getEpisode(episodeHtml, location, index)
                if (serialEpisode != null) {
                    if (episodes.getOrNull(index) != null) {
                        episodes[index] = serialEpisode
                    } else {
                        episodes.add(serialEpisode)
                    }
                }
                flowCollector.emit(loading(UpdateType.PAGE_CURRENT_LINK to "Получаем остальные эпизоды -> ${serialEpisode?.episode}, ${episodes.size} из $size"))
            }
            serialSeason.episodes = episodes.sortedBy { it.episode.toInt() }
            resultSeasons[seasonIndex] = serialSeason
        }
    }

    private suspend fun getEpisode(
        episode: Document,
        location: String,
        index: Int
    ): SerialEpisode? {
        val downloadLinks = getAllDownloadLinks(episode)
        return if (downloadLinks.isNotEmpty()) {
            val episodeUrl = downloadLinks.last().replace("^/".toRegex(), "")
            val link = getMp4Link(downloadLinks.lastOrNull()?.getWithDomain(location)).orEmpty()
            val img = getImg(episode, location)
            val episodeId = index + 1
            val resId = episodeUrl.substringAfterLast("/").toIntOrNull() ?: episodeId
            SerialEpisode(
                id = resId,
                episode = "$episodeId",
                title = getTitle(episode),
                dash = link,
                poster = img
            )
        } else {
            null
        }
    }

    private fun hasAllEpisodes(seasonList: List<SerialSeason>, episodes: Int): Boolean {
        val allLinks = seasonList.isNotEmpty() && seasonList.all { season ->
            season.episodes.isNotEmpty() && season.episodes.all { episode ->
                episode.dash.isNotBlank() || episode.hls.isNotBlank()
            }
        }
        var allEpisodes = 0
        for (season in seasonList) {
            var lastEpisode = 0 //season.episodes.size
            for (episode in season.episodes) {
                val episodeId = "(\\d+)-?(\\d+)?"
                val last = findByGroup(episode.episode, episodeId.toRegex(), 2)
                if (!last.isNullOrBlank() && last.isNumeric()) {
                    lastEpisode += last.toInt()
                } else {
                    lastEpisode++
                }
            }
            if (lastEpisode != 0) {
                allEpisodes += lastEpisode
            }
        }
        val notRepeated = getRepeatedSeasonCount(seasonList) == 0
        val abs = abs(allEpisodes - episodes)
        return notRepeated && episodes != 0 && allLinks && (abs <= 2 || allEpisodes > episodes)
    }

    private suspend fun getSeasonFromFirstEpisode(
        page: Document,
        location: String,
        seasonId: Int
    ): SerialSeason? {
        val links = getEpisodesLinks(page)
        val absentEpisodeLink = links.firstOrNull()
        if (!absentEpisodeLink.isNullOrBlank()) {
            val absentEpisodePage = loadPage(absentEpisodeLink.getWithDomain(location))
            val urls = getSeriyaUrlData(absentEpisodePage)
            if (urls.isNotEmpty()) {
                val data = getSeasonData(urls)
                return SerialSeason(
                    id = seasonId,
                    episodes = data.playlist.mapIndexed { index, urlData ->
                        SerialEpisode(
                            id = (urlData.id?.toIntOrNull() ?: 0) + 1,
                            episode = "${index + 1}",
                            title = urlData.title.orEmpty(),
                            hls = getUrlsFromFile(urlData.file.orEmpty(), "(.m3u8)").firstOrNull()
                                .orEmpty()
                        )
                    }
                )
            }
        }
        return null
    }

    private suspend fun getSeasonData(urls: List<String>): AnwapSeasonPlaylist {
        val document = urls.firstOrNull()?.takeIf { it.endsWith(".txt") }?.let { loadPage(it) }
        val data = document?.select(Selectors.PAGE)?.text().orEmpty()
        return Json.decodeFromString(data)
    }

    private fun getAbsentEpisodesLinks(
        list: List<SerialEpisode>,
        links: List<String>
    ): Map<Int, String> {
        val needEpisodesIds = mutableListOf<Int>()
        val episodes = list.sortedBy { it.episode.toInt() }
        for (index in links.indices) {
            val id = index + 1
            val serialEpisode = episodes.find { it.episode.toInt() == id }
            if (serialEpisode == null) {
                needEpisodesIds.add(id)
            } else {
                if (serialEpisode.dash.isBlank() && serialEpisode.hls.isBlank()) {
                    needEpisodesIds.add(id)
                }
            }
        }
        val needEpisodes = mutableMapOf<Int, String>()
        for (needEpisode in needEpisodesIds) {
            needEpisodes[needEpisode] = links[needEpisode - 1]
        }
        return needEpisodes
    }


    private fun getAbsentSeasonsLinks(
        seasons: List<SerialSeason>,
        links: List<String>
    ): Map<Int, String> {
        val needSeasonsIds = mutableListOf<Int>()
        for (index in links.indices) {
            val id = index + 1
            val serialEpisode = seasons.find { it.id == id }
            if (serialEpisode == null) {
                needSeasonsIds.add(id)
            }
        }
        val needSeasons = mutableMapOf<Int, String>()
        for (needSeason in needSeasonsIds) {
            needSeasons[needSeason] = links[needSeason - 1]
        }
        return needSeasons
    }

    private suspend fun getSeasonEpisodesLinks(
        url: String,
        location: String
    ): List<EpisodeLink> {
        val urlSeason = url.getWithDomain(location)
        val basePage = loadPage(urlSeason)
        val pages = getPagesCount(basePage)
        val linkAll = mutableListOf<EpisodeLink>()
        if (pages == 1) {
            linkAll.addAll(episodeLinks(basePage))
        } else {
            for (i in 1..pages) {
                val page = loadPage("$urlSeason-$i")
                linkAll.addAll(episodeLinks(page))
            }
        }
        return linkAll
    }

    fun getRepeatedEpisodeCount(episodesLinks: List<EpisodeLink>) = episodesLinks
        .groupingBy { it.name }
        .eachCount()
        .filter { it.value > 1 }
        .map { it.value }.sumOf { it - 1 }

    private fun getRepeatedSeasonCount(seasons: List<SerialSeason>) = seasons
        .groupingBy { it.id!! }
        .eachCount()
        .filter { it.value > 1 }
        .map { it.value }.sumOf { it - 1 }

    private fun episodeLinks(basePage: Document): List<EpisodeLink> {
        val episodesLinks = getEpisodesLinks(basePage)
        val episodesNames = getEpisodesNames(basePage)
        if (episodesLinks.size != episodesNames.size) {
            error("Ошибка в количестве ссылок и имен")
        }
        return episodesLinks.mapIndexed { index, link -> EpisodeLink(link, episodesNames[index]) }
    }

    override fun getDecodePlayerData(value: String): String {
        return value.cleanAnwapEncryptedData().getDecodedData()
    }
}