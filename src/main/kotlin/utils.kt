package com.benasher44.kloudfrontblogstats

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.logging.Level
import java.util.logging.Logger

internal fun JsonObject.stringContent(key: String): String? =
    this[key]?.jsonPrimitive?.content

internal fun Throwable.logMessage(): String =
    "$this - $message: ${stackTraceToString()}"

internal object Logging {
    private val logger: Logger = Logger.getLogger(this::javaClass.name)

    fun log(msg: String) {
        logger.log(Level.INFO, msg)
    }

    fun error(msg: String) {
        logger.log(Level.SEVERE, msg)
    }
}

internal class CSVLine(line: String, private val fields: Map<String, Int>) {
    private val values = line.trim().split("\t")

    operator fun get(key: String): String = values[fields[key]!!]
}