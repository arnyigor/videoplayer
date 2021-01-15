package com.arny.mobilecinema.data.utils

import com.arny.mobilecinema.data.network.sources.correctTitle
import com.arny.mobilecinema.data.network.sources.toHlsListMap
import org.junit.jupiter.api.Test

internal class JSONUtilsKtTest {

    private companion object {
        const val hlslist = "{\"480\":\"https://480.m3u8\"," +
                "\"720\": \"https://720.m3u8\"}"
    }

    @Test
    fun `convert to map test`() {
        val qualityMap = hlslist.toHlsListMap()
        assert(qualityMap["480"] == "https://480.m3u8")
    }

    @Test
    fun `get correct title`() {
        val returnTitle =
            correctTitle("Я псих, но это нормально 1 сезон 1-16 серия смотреть онлайн бесплатно в хорошем качестве hd 720")
        assert(returnTitle == "Я псих, но это нормально")
    }

    @Test
    fun `get correct title2`() {
        val returnTitle =
            correctTitle("Скажи что-нибудь хорошее 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 серия (2020) смотреть онлайн бесплатно в хорошем качестве hd")
        assert(returnTitle == "Скажи что-нибудь хорошее")
    }

    @Test
    fun `get correct title2_2`() {
        val returnTitle =
            correctTitle("Скажи что-нибудь хорошее 1 серия (2020) смотреть онлайн бесплатно в хорошем качестве hd")
        assert(returnTitle == "Скажи что-нибудь хорошее")
    }

    @Test
    fun `get correct title3`() {
        assert(correctTitle(
            "Уильям наш, Шекспир / Выскочка Шекспир 4 сезон 1 серия смотреть онлайн бесплатно в хорошем качестве hd 720")
                == "Уильям наш, Шекспир / Выскочка Шекспир")
    }

    @Test
    fun `get correct title4`() {
        val returnTitle =
            correctTitle("Дневники вампира 1-8 сезон 1-16 серия смотреть онлайн бесплатно в хорошем качестве hd 720")
        assert(returnTitle == "Дневники вампира")
    }

    @Test
    fun `get correct title5`() {
        val returnTitle =
            correctTitle("Мандалорец 2 сезон 1-8 серия смотреть онлайн бесплатно в хорошем качестве hd 720")
        assert(returnTitle == "Мандалорец")
    }

    @Test
    fun `get correct title6`() {
        val returnTitle =
            correctTitle("Снежная Королева: Зазеркалье (2019) смотреть онлайн бесплатно в хорошем качестве hd 720")
        assert(returnTitle == "Снежная Королева: Зазеркалье (2019)")
    }

    @Test
    fun `get correct title7`() {
        val returnTitle =
            correctTitle("Реальная белка 2 смотреть онлайн бесплатно в хорошем качестве hd 720")
        assert(returnTitle == "Реальная белка 2")
    }

    @Test
    fun `get correct title8`() {
        val returnTitle =
            correctTitle("Фильм Ударная волна 2 (2020) в hd 720 качестве смотреть онлайн")
        assert(returnTitle == "Ударная волна 2 (2020)")
    }

    @Test
    fun `get correct title9`() {
        val returnTitle =
            correctTitle("Фильм Ударная волна 2 (2020) в hd 720 качестве смотреть онлайн")
        assert(returnTitle == "Ударная волна 2 (2020)")
    }

    @Test
    fun `get correct title10`() {
        val returnTitle =
            correctTitle("Фильм Аэронавты (2019) в HD 1080 качестве смотреть онлайн")
        assert(returnTitle == "Аэронавты (2019)")
    }
}
