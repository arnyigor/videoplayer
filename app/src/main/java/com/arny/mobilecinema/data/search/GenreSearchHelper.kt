package com.arny.mobilecinema.data.search

import java.util.Locale

object GenreSearchHelper {
    private data class GenreGroup(
        val title: String,
        val aliases: List<String>,
    )

    private val genreGroups = listOf(
        GenreGroup("Мультфильмы", listOf("мульт", "мультик", "мульик", "мультфильм", "мультсериал", "мультипликац", "анимац", "рисован")),
        GenreGroup("Аниме", listOf("аниме", "манга", "меха", "сёнэн", "сенэн")),
        GenreGroup("Комедии", listOf("комеди", "юмор", "юморист", "смешной перевод", "черный юмор", "чёрный юмор", "трагикомеди", "трагифарс", "фантасмагори", "скетч")),
        GenreGroup("Драмы", listOf("драма", "драмат")),
        GenreGroup("Мелодрамы", listOf("мелодрам", "мелодрамм", "любовный роман", "романтика")),
        GenreGroup("Боевики", listOf("боевик", "экшн", "боевые искусства", "единоборств", "карате", "ниндзя", "самурай", "рукопаш")),
        GenreGroup("Триллеры", listOf("триллер", "саспенс")),
        GenreGroup("Ужасы", listOf("ужас", "хоррор")),
        GenreGroup("Фантастика", listOf("фантаст", "научная фантастика", "постапокалипсис")),
        GenreGroup("Фэнтези", listOf("фэнтез", "фентез", "фэнтаз", "мифолог")),
        GenreGroup("Приключения", listOf("приключ")),
        GenreGroup("Детективы", listOf("детектив")),
        GenreGroup("Криминал", listOf("криминал")),
        GenreGroup("Документальные", listOf("документ", "мокументал")),
        GenreGroup("Военные", listOf("воен", "война", "вов")),
        GenreGroup("Исторические", listOf("истор", "былины", "новейшая история")),
        GenreGroup("Биография", listOf("биограф")),
        GenreGroup("Семейные", listOf("семейн")),
        GenreGroup("Детские", listOf("детск")),
        GenreGroup("Спорт", listOf("спорт", "футбол", "автоспорт")),
        GenreGroup("Музыка", listOf("музык", "музыка", "джаз", "рок", "поп", "панк", "punk", "pop", "гранж", "свинг")),
        GenreGroup("Мюзиклы", listOf("мюзикл", "mюзикл")),
        GenreGroup("ТВ-шоу", listOf("тв шоу", "тв-шоу", "шоу", "реальное тв", "реалити", "игра", "церемони", "парад")),
        GenreGroup("Артхаус", listOf("арт хаус", "арт-хаус", "артхаус", "сюрреализм", "философ")),
        GenreGroup("Вестерны", listOf("вестерн", "истерн")),
        GenreGroup("Катастрофы", listOf("катастроф")),
        GenreGroup("Короткометражки", listOf("короткометраж")),
        GenreGroup("Концерты", listOf("концерт")),
        GenreGroup("Обучающие", listOf("обуча")),
        GenreGroup("Эротика", listOf("эрот", "для взрослых", "гей", "лесби")),
        GenreGroup("Индийское кино", listOf("индий")),
        GenreGroup("Комиксы", listOf("комикс")),
        GenreGroup("Новости", listOf("новости")),
        GenreGroup("Сказки", listOf("сказка", "притча")),
        GenreGroup("Пародии", listOf("парод")),
        GenreGroup("Сатира", listOf("сатир")),
        GenreGroup("Фильм-нуар", listOf("нуар")),
        GenreGroup("Классика", listOf("классика", "немое кино")),
        GenreGroup("Экранизации", listOf("экранизац")),
    )

    fun toDisplayGenres(rawGenres: List<String>): List<String> = rawGenres
        .asSequence()
        .flatMap { splitGenres(it).asSequence() }
        .flatMap { toDisplayGenreTitles(it).asSequence() }
        .filter { it.isNotBlank() }
        .distinctBy { normalize(it) }
        .sorted()
        .toList()

    fun searchTermsForGenres(genres: List<String>): List<String> = genres
        .asSequence()
        .flatMap { genre ->
            val groups = findGroups(genre)
            if (groups.isNotEmpty()) {
                groups.flatMap { it.aliases }.asSequence()
            } else {
                splitGenres(genre).ifEmpty { listOf(genre) }.asSequence()
            }
        }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .flatMap { caseVariants(it).asSequence() }
        .distinct()
        .toList()

    fun isGenreMatched(movieGenres: String, selectedGenre: String): Boolean {
        val movieNormalized = normalize(movieGenres)
        return searchTermsForGenres(listOf(selectedGenre))
            .map { normalize(it) }
            .any { term -> movieNormalized.contains(term) }
    }

    private fun toDisplayGenreTitles(genre: String): List<String> {
        val groups = findGroups(genre)
        return groups.map { it.title }.ifEmpty { listOf(genre.trim()) }
    }

    private fun findGroups(genre: String): List<GenreGroup> {
        val normalizedGenre = normalize(genre)
        if (normalizedGenre.isBlank()) return emptyList()
        return genreGroups.filter { group ->
            normalizedGenre == normalize(group.title) || group.aliases.any { alias ->
                normalizedGenre.containsAlias(normalize(alias))
            }
        }
    }

    private fun splitGenres(genres: String): List<String> = genres
        .split(',', ';', '/', '|')
        .map { it.trim() }
        .filter { it.isNotBlank() }

    private fun String.containsAlias(alias: String): Boolean {
        if (alias.isBlank()) return false
        val words = split(' ')
        return if (alias.length <= 3) {
            words.any { it == alias }
        } else {
            contains(alias)
        }
    }

    private fun caseVariants(term: String): List<String> {
        val lower = term.lowercase(Locale.ROOT)
        return listOf(
            term,
            lower,
            lower.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
            lower.uppercase(Locale.ROOT)
        ).distinct()
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("[^a-zа-я0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
