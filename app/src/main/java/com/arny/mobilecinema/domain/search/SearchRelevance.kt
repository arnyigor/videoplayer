package com.arny.mobilecinema.domain.search

import java.text.Normalizer
import java.util.Locale

/**
 * Релевантность совпадения запроса с названием тайтла (S-014).
 *
 * Приоритет строго по убыванию: точное совпадение **названия** → точное совпадение
 * **оригинального названия** → название начинается с запроса → с запроса начинается слово →
 * запрос встречается внутри → «почти совпадает» (опечатка) → нет совпадения.
 *
 * Нормализация: Unicode NFC, нижний регистр, `ё`→`е`, обрезка и сжатие пробелов. Транслитерация
 * кириллицы в латиницу намеренно **не** угадывается: для этого служит поле оригинального
 * названия, которое приходит с сайта вместе с русским (аудит v2, §4.4).
 *
 * Рейтинг IMDb в оценку не входит: он не отражает совпадение с запросом.
 */
object SearchRelevance {

    const val EXACT: Int = 100
    const val EXACT_ORIGINAL: Int = 90
    const val STARTS_WITH: Int = 80
    const val WORD_STARTS_WITH: Int = 60
    const val CONTAINS: Int = 40
    const val FUZZY: Int = 20
    const val NO_MATCH: Int = 0

    /** Наибольшая оценка по названиям: первое название считается основным (title). */
    fun score(query: String, vararg titles: String): Int {
        if (query.isBlank()) return NO_MATCH
        val q = normalize(query)
        if (q.isEmpty()) return NO_MATCH
        var best = NO_MATCH
        titles.forEachIndexed { index, title ->
            val base = scoreSingle(q, normalize(title))
            // Точное совпадение оригинального названия слабее точного совпадения основного.
            val adjusted = if (base == EXACT && index > 0) EXACT_ORIGINAL else base
            if (adjusted > best) best = adjusted
        }
        return best
    }

    private fun scoreSingle(q: String, t: String): Int {
        if (t.isEmpty()) return NO_MATCH
        if (t == q) return EXACT
        if (t.startsWith(q)) return STARTS_WITH
        val words = t.split(*SEPARATORS).filter { it.isNotBlank() }
        if (words.any { it.startsWith(q) }) return WORD_STARTS_WITH
        if (t.contains(q)) return CONTAINS
        if (isCloseEnough(q, words)) return FUZZY
        return NO_MATCH
    }

    /**
     * «Почти совпадение» только для достаточно длинных запросов: на коротких запросах (1–3 символа)
     * расстояние Левенштейна превращает поиск в случайные подсказки.
     */
    private fun isCloseEnough(q: String, words: List<String>): Boolean {
        if (q.length < 4) return false
        val allowed = if (q.length >= 7) 2 else 1
        return words.any { word ->
            word.length >= q.length - allowed && levenshtein(q, word) <= allowed
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /**
     * Устойчивая сортировка по убыванию релевантности. Порядок элементов с одинаковой
     * оценкой сохраняется, поэтому источник может задать свой приоритет (например,
     * фильмы сайта перед сериалами) до вызова.
     */
    fun <T> rank(query: String, items: List<T>, titles: (T) -> List<String>): List<T> {
        if (query.isBlank() || items.size < 2) return items
        return items
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<T>> { score(query, *titles(it.value).toTypedArray()) }
                    .thenBy { it.index }
            )
            .map { it.value }
    }

    /** Единая нормализация для сравнения: NFC, нижний регистр, ё→е, одиночные пробелы. */
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Ключ поиска по базе: [normalize] плюс замена любых разделителей (дефис, двоеточие,
     * подчёркивание, скобки) на пробел.
     *
     * Нужен потому, что SQLite не умеет ни `lower()` для кириллицы, ни `ё`→`е`: `LIKE`
     * приводит регистр только для ASCII. Поэтому сравнение в SQL возможно только по
     * заранее посчитанному ключу, а не по сырому названию.
     */
    fun searchKey(value: String): String {
        val normalized = normalize(value)
        val sb = StringBuilder(normalized.length)
        var pendingSpace = false
        normalized.forEach { ch ->
            if (ch.isLetterOrDigit()) {
                if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                pendingSpace = false
                sb.append(ch)
            } else {
                pendingSpace = true
            }
        }
        return sb.toString()
    }

    /**
     * Слова запроса для поиска по схеме «все слова должны встретиться».
     *
     * Поиск целой строкой не работает там, где сайт поставил разделитель:
     * «человек паук новый» не является подстрокой «человек паук новый день» в SQL
     * из-за двоеточия в исходном названии.
     */
    fun tokens(value: String): List<String> =
        searchKey(value).split(' ').filter { it.isNotBlank() }

    private val SEPARATORS = charArrayOf(
        ' ', '\t', '\n', ':', ',', '.', '-', '–', '—', '/', '(', ')', '!', '?', '«', '»', '\''
    )
}
