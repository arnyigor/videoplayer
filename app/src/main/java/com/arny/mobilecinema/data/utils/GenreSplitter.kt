package com.arny.mobilecinema.data.utils

import android.content.Context
import com.arny.mobilecinema.R
import java.util.Locale

/**
 * Разделение жанров, приходящих с сайта в разных форматах.
 *
 * Нормальный формат — список или строка с запятыми. Но часть карточек приходит
 * одной строкой без разделителей («боевик триллер мелодрама комедия»), и на
 * экране деталей это выглядело одним блоком текста вместо отдельных чипов.
 *
 * Строка без запятых разбирается жадным поиском самых длинных известных жанров
 * (словарь R.array.genres). Многословные жанры («боевые искусства»,
 * «научная фантастика») при этом остаются целыми. Неизвестный текст не ломается.
 */
object GenreSplitter {

    fun loadDictionary(context: Context): List<String> =
        context.resources.getStringArray(R.array.genres).toList()

    fun split(raw: List<String>, dictionary: List<String>): List<String> {
        val tokens = raw.flatMap { it.split(',', ';', '/') }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val dictByLength = dictionary
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedByDescending { it.length }
            .map { it.trim() }

        val result = mutableListOf<String>()
        for (token in tokens) {
            if (dictByLength.any { it.equals(token, ignoreCase = true) }) {
                result.add(token)
                continue
            }
            if (token.contains(' ')) {
                val segmented = segment(token, dictByLength)
                if (segmented != null) {
                    result.addAll(segmented)
                } else {
                    result.add(token)
                }
            } else {
                result.add(token)
            }
        }
        return result.distinctBy { it.lowercase(Locale.ROOT) }
    }

    /**
     * Жадное разбиение строки на известные жанры словаря.
     * Возвращает null, если строка целиком не покрывается словарём:
     * тогда показываем её как есть, а не выдумываем разбиение.
     */
    private fun segment(token: String, dictionary: List<String>): List<String>? {
        val segments = mutableListOf<String>()
        var index = 0
        val text = token.lowercase(Locale.ROOT)
        while (index < text.length) {
            if (text[index] == ' ') {
                index++
                continue
            }
            val match = dictionary.firstOrNull { entry ->
                val lower = entry.lowercase(Locale.ROOT)
                text.startsWith(lower, index)
            } ?: return null
            segments.add(match)
            index += match.length
        }
        return segments.ifEmpty { null }
    }
}
