package com.arny.mobilecinema.data.network.jsoup

import com.arny.mobilecinema.BuildConfig
import com.arny.mobilecinema.data.utils.getDomainName
import kotlinx.coroutines.delay
import org.jsoup.Connection
import org.jsoup.helper.Validate
import org.jsoup.nodes.Document
import javax.inject.Inject
import kotlin.random.Random

class JsoupService @Inject constructor() {

    companion object {
        @Volatile
        private var instance: JsoupService? = null

        fun getInstance(): JsoupService {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = JsoupService()
                    }
                }
            }
            return instance!!
        }
    }


    private val helper = JsoupServiceHelper.getInstance()

    fun loadPage(
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
        logLevel: LogLevel = LogLevel.NONE,
        timeout: Int = 10000,
        resetCookie: Boolean = false
    ): Document {
        return JsoupLoggerConnection.connect(url, logLevel, resetCookie, helper).apply {
            userAgent(helper.userAgent)
            headers(requestHeaders)
            val cookie = helper.cookie
            if (resetCookie && cookie.isNotEmpty()) {
                cookies(cookie)
            }
            timeout(timeout)
            followRedirects(true)
            ignoreContentType(true)
            ignoreHttpErrors(true)
            maxBodySize(0)
            getDomainName(url).takeIf { it.isNotBlank() }?.let {
                referrer(it)
            }
        }.run {
            execute()
        }.run {
            Validate.notNull(this)
            parse()
        }
    }

    suspend fun loadPage(
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
        currentProxy: Pair<String, Int>? = null,
        needDelay: Boolean = false,
        delayMin: Long = 500,
        delayMax: Long = 3000,
        logLevel: LogLevel = LogLevel.NONE,
        timeout: Int = 10000,
        resetCookie: Boolean = false,
        domain: String = "",
        path: String = "",
    ): Document {
        val headers = requestHeaders.map { it.key to it.value }.shuffled().toMap().toMutableMap()
        val randomTimeout = Random.nextLong(delayMin, delayMax)
        return JsoupLoggerConnection.connect(url, logLevel, resetCookie, helper).apply {
            if (needDelay) {
                delay(randomTimeout)
            }
            userAgent(helper.userAgent)
            currentProxy?.let { (address, port) -> proxy(address, port) }
            val cookie = helper.cookie
            if (cookie.isNotEmpty()) {
                val s = cookie["PHPSESSID"]
                if (!s.isNullOrBlank()) {
                    headers["PHPSESSID"] = s
                }
            }
            if (domain.isNotBlank() && headers["Authority"] != domain) {
                headers["Authority"] = BuildConfig.BASE_LINK
            }
            if (path.isNotBlank() && headers["path"] != path) {
                headers["path"] = path
            }
            headers(headers)
            if (resetCookie && helper.cookie.isNotEmpty()) {
                cookies(helper.cookie)
            }

            timeout(timeout)
            followRedirects(true)
            ignoreContentType(true)
            ignoreHttpErrors(true)
            maxBodySize(0)
            getDomainName(url).takeIf { it.isNotBlank() }?.let {
                referrer(it)
            }
        }.run {
            execute()
        }.run {
            Validate.notNull(this)
            parse()
        }
    }

    suspend fun loadUrl(
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
        currentProxy: Pair<String, Int>? = null,
        needDelay: Boolean = false,
        delayMin: Long = 500,
        delayMax: Long = 3000,
        changeUA: Boolean = false,
        logLevel: LogLevel = LogLevel.NONE,
        timeout: Int = 10000,
        resetCookie: Boolean = false
    ): String {
        val headers = requestHeaders.map { it.key to it.value }.shuffled().toMap()
        val randomTimeout = Random.nextLong(delayMin, delayMax)
        val connection = JsoupLoggerConnection.connect(url, logLevel, resetCookie, helper).apply {
            if (needDelay) {
                delay(randomTimeout)
            }
            userAgent(helper.userAgent)
            currentProxy?.let { (address, port) -> proxy(address, port) }
            headers(headers)
            if (resetCookie && helper.cookie.isNotEmpty()) {
                cookies(helper.cookie)
            }
            timeout(timeout)
            followRedirects(true)
            ignoreContentType(true)
            ignoreHttpErrors(true)
            maxBodySize(0)
            getDomainName(url).takeIf { it.isNotBlank() }?.let {
                referrer(it)
            }
        }
        var response: Connection.Response? = null
        try {
            response = connection.execute()
        } catch (e: Exception) {
            response = connection.response()
            e.printStackTrace()
        }
        return response?.url().toString()
    }
}