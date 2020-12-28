package com.benasher44.kloudfrontblogstats.utils

import java.util.logging.Level

// OTHER_LOGGER can be set to be used instead of JavaLogger
private var OTHER_LOGGER: Logger? = null
internal fun setLogger(logger: Logger) {
    OTHER_LOGGER = logger
}

internal val LOGGER: Logger by lazy {
    OTHER_LOGGER ?: JavaLogger
}

interface Logger {
    fun log(msg: String)
    fun error(msg: String)
}

private object JavaLogger : Logger {
    private val logger: java.util.logging.Logger = java.util.logging.Logger.getLogger(this::javaClass.name)

    override fun log(msg: String) {
        logger.log(Level.INFO, msg)
    }

    override fun error(msg: String) {
        logger.log(Level.SEVERE, msg)
    }
}

internal fun Throwable.logMessage(): String =
    "$this - $message: ${stackTraceToString()}"
