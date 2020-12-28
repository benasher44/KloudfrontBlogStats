package com.benasher44.kloudfrontblogstats.utils

import com.benasher44.kloudfrontblogstats.AccessLogQueries
import com.benasher44.kloudfrontblogstats.KBSDatabase
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.asJdbcDriver
import org.postgresql.ds.PGSimpleDataSource

internal fun withNewConnection(dbLambda: (queries: AccessLogQueries) -> Unit) {
    val url = System.getenv("PG_URL")
    val ds = PGSimpleDataSource()
    ds.setUrl("jdbc:$url")
    ds.user = System.getenv("PG_USER")
    ds.password = System.getenv("PG_PASSWORD")
    ds.asJdbcDriver().use { driver ->
        val database = KBSDatabase(driver)

        // TODO: fix setVersion query and support upgrades
        if (!driver.schemaVersionTableExists()) {
            database.schemaVersionQueries.transaction {
                KBSDatabase.Schema.create(driver)
            }
            database.schemaVersionQueries.setVersion()
        }

        dbLambda(database.accessLogQueries)
    }
}

private fun SqlDriver.schemaVersionTableExists(): Boolean {
    executeQuery(
        null,
        """|SELECT 1 FROM information_schema.tables
            |WHERE table_schema = 'schemaname' AND
            |      table_name = 'SchemaVersion'
            |FETCH FIRST ROW ONLY
            |""".trimMargin(),
        0
    ).use {
        if (!it.next()) return false
        return it.getLong(0) == 1L
    }
}
