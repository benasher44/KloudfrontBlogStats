package com.benasher44.kloudfrontblogstats

import java.io.InputStream

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
        lines.filter { line ->
            if (line.startsWith("#Fields:")) {
                fields = line.substringAfter("#Feilds:")
                    .trim()
                    .split(" ")
                    .withIndex()
                    .associate { it.value to it.index }
                false
            } else !line.startsWith("#")
        }
        for (line in lines) {
            val values = CSVLine(line, fields)

            // HTTP status
            if (values["sc-status"] != "200") continue

            // HTTP method
            if (values["cs-method"] != "GET") continue

            lambda(
                // date and time in UTC
                values["date"],
                values["time"],

                // Referer header
                values["cs(Referer)"].nullIfEmptyLogValue(),

                // User-Agent
                values["cs(User-Agent)"].nullIfEmptyLogValue(),

                // Path
                values["cs-uri-stem"]
            )
        }
    }
}

// null values are represented by "-" in the log
private fun String.nullIfEmptyLogValue(): String? =
    this.takeUnless { it == "-" }
