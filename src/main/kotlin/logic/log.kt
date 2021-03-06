package com.benasher44.kloudfrontblogstats.logic

import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8

private class LogLine(line: String, private val fields: Map<String, Int>) {
    private val values = line.trim().split("\t")

    // log values are url-encoded
    operator fun get(key: String): String = URLDecoder.decode(
        // avoid URLDecoder decoding "+" as " "
        values[fields[key]!!].replace("+", "%2B"),
        UTF_8.name()
    )
}

internal typealias LogLambda = (
    date: String,
    time: String,
    referer: String?,
    userAgent: String?,
    path: String
) -> Unit

internal fun InputStream.enumerateLogs(lambda: LogLambda) {
    bufferedReader().useLines { lines ->
        lateinit var fields: Map<String, Int>
        val filteredLines = lines.filter { line ->
            if (line.startsWith("#Fields:")) {
                fields = line.substringAfter("#Fields:")
                    .trim()
                    .split(" ")
                    .withIndex()
                    .associate { it.value to it.index }
                false
            } else !line.startsWith("#")
        }
        for (line in filteredLines) {
            val values = LogLine(line, fields)

            // HTTP status
            if (values["sc-status"] != "200" && values["sc-status"] != "304") continue

            // HTTP method
            if (values["cs-method"] != "GET") continue

            lambda(
                // date and time in UTC
                values["date"],
                values["time"],

                // Referer header
                values["cs(Referer)"].nullIfEmptyLogValue()?.normalizeUrl(),

                // User-Agent
                values["cs(User-Agent)"].nullIfEmptyLogValue(),

                // Path
                values["cs-uri-stem"].normalizeUrl()
            )
        }
    }
}

// null values are represented by "-" in the log
private fun String.nullIfEmptyLogValue(): String? =
    this.takeUnless { it == "-" }

private fun String.normalizeUrl(): String = this

    // trim trailing slash
    .trimEnd('/')

    // remove leading http://
    .substringAfter("http://")

    // remove leading https://
    .substringAfter("https://")
