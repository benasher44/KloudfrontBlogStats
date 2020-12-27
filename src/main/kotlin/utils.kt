package com.benasher44.kloudfrontblogstats

import com.benasher44.AccessLogQueries
import com.benasher44.KBSDatabase
import com.squareup.sqldelight.sqlite.driver.asJdbcDriver
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.postgresql.ds.PGSimpleDataSource

internal fun JsonObject.stringContent(key: String): String? =
    this[key]?.jsonPrimitive?.content

internal fun Throwable.logMessage(): String =
    "$this - $message: ${stackTraceToString()}"

internal fun withNewConnection(dbLambda: (queries: AccessLogQueries) -> Unit) {
    val url = System.getenv("PG_URL")
    val ds = PGSimpleDataSource()
    ds.setUrl(url)
    ds.user = System.getenv("PG_USER")
    ds.password = System.getenv("PG_PASSWORD")
    ds.asJdbcDriver().use { driver ->
        val database = KBSDatabase(driver)
        dbLambda(database.accessLogQueries)
    }
}
