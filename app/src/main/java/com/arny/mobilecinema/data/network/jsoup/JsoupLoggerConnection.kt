package com.arny.mobilecinema.data.network.jsoup

import org.jsoup.Connection
import org.jsoup.helper.HttpConnection

enum class LogLevel {
    NONE,
    BASE,
    ALL
}

class JsoupLoggerConnection private constructor(
    private val logLevel: LogLevel,
    private val resetCookie: Boolean,
) : HttpConnection() {

    companion object {
        fun connect(url: String, logLevel: LogLevel = LogLevel.NONE, resetCookie: Boolean): Connection {
            val connection: Connection = JsoupLoggerConnection(logLevel, resetCookie)
            connection.url(url)
            return connection
        }
    }

    override fun execute(): Connection.Response {
        if (logLevel != LogLevel.NONE) {
            log(this.request())
        }
        val response = super.execute()
        if (logLevel != LogLevel.NONE) {
            log(response)
        }
        return response
    }

    private fun log(request: Connection.Request): String {
        println("========================================")
        var line = "[url] ${request.url()}"
        var log = "$line\n\n== REQUEST ==\n"
        println(line)
        println("== REQUEST ==")
        log += logBase(request)

        line = "[method] ${request.method()}"
        log += "$line\n"
        println(line)

        line = "[proxy] ${request.proxy()}"
        log += "$line\n"
        println(line)

        for (data in request.data()) {
            line = "[data] ${data.key()}=${data.value()}"
            log += "$line\n"
            println(line)
        }

        if (logLevel == LogLevel.ALL) {
            line = "[request body] ${request.requestBody()}"
            log += "$line\n"
            println(line)
        }
        return log
    }

    private fun log(response: Connection.Response): String {
        var line = ""
        var log = "\n== RESPONSE ==\n"

        println("== RESPONSE ==")
        log += logBase(response)

        if (resetCookie) {
            for (cookie in response.cookies()) {
                JsoupServiceHelper.cookie[cookie.key] = cookie.value
            }
        }
        line = "[code] ${response.statusCode()}"
        log += "$line\n"
        println(line)

        line = "[status msg] ${response.statusMessage()}"
        log += "$line\n"
        println(line)
        if (logLevel == LogLevel.ALL) {
            line = "[body] ${response.body()}"
            log += "$line\n"
            println(line)
        }
        println("========================================")
        return log
    }

    private fun logBase(base: Connection.Base<*>): String {
        var line = ""
        var log = ""
        for (header in base.headers()) {
            line = "[header] ${header.key}=${header.value}"
            log += "$line\n"
            println(line)
        }
        for (cookie in base.cookies()) {
            line = "[cookie] ${cookie.key}: ${cookie.value}"
            log += "$line\n"
            println(line)
        }
        return log
    }

}