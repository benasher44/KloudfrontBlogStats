package com.benasher44.kloudfrontblogstats

import com.benasher44.AccessLog
import com.benasher44.KBSDatabase
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.asJdbcDriver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.postgresql.ds.PGSimpleDataSource
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.io.InputStream
import java.io.OutputStream
import java.sql.DriverManager
import java.util.zip.GZIPInputStream
import javax.sql.DataSource

private val REGION = requireNotNull(System.getenv("AWS_DEFAULT_REGION")) {
    "Specify AWS region by setting the AWS_DEFAULT_REGION env var"
}

private data class S3Object(val bucket: String, val key: String)

public fun s3Handler(input: InputStream, output: OutputStream) {
    try {
        val stringInput = input.readBytes().decodeToString()
        val json = Json {  }.parseToJsonElement(stringInput)
        val records = json.jsonObject["Records"]!!.jsonArray

        val objects = records.asSequence()
            .mapNotNull { it as? JsonObject }

            // only S3 create events
            .filter { it.stringContent("eventSource") == "aws:s3" }
            .filter { it.stringContent("eventName") == "ObjectCreated:Put" }
            .filter {
                val region = it["awsRegion"]!!.jsonPrimitive.content
                (region == REGION).also { matches ->
                    if (!matches) Logging.error("$region does not match $REGION")
                }
            }

            // capture region and s3 info
            .map { it["s3"]!!.jsonObject }

            // parse into object
            .map { s3 ->
                val bucket = s3["bucket"]!!.jsonObject["name"]!!.jsonPrimitive.content
                val key = s3["object"]!!.jsonObject["key"]!!.jsonPrimitive.content
                S3Object(bucket, key)
            }
        handleObjects(objects.asIterable())
    } finally {
        input.close()
        output.close()
    }
}

private fun handleObjects(objects: Iterable<S3Object>) {
    try {
        val count = objects.fold(0) { soFar, s3o ->
            try {
                handleObject(s3o)
                soFar + 1
            } catch (e: Throwable) {
                Logging.error("Processing error: $s3o -  ${e.logMessage()}")
                soFar
            }
        }
        Logging.log("Processed $count objects successfully.")
    } catch (e: Throwable) {
        Logging.error(e.logMessage())
        throw e
    }
}

private val S3 = S3Client.builder()
    .region(Region.of(REGION))
    .build()

private fun handleObject(s3o: S3Object) {
    val getRequest = GetObjectRequest.builder()
        .bucket(s3o.bucket)
        .key(s3o.key)
        .build()
    S3.getObject(getRequest).use { downloadStream ->
        GZIPInputStream(downloadStream).use { input ->
            input.bufferedReader().useLines { lines ->
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

                val url = System.getenv("PG_URL")
                val ds = PGSimpleDataSource()
                ds.setUrl(url)
                ds.user = System.getenv("PG_USER")
                ds.password = System.getenv("PG_PASSWORD")

                ds.asJdbcDriver().use { driver ->
                    val database = KBSDatabase(driver)
                    val queries = database.accessLogQueries
                    queries.transaction {
                        for (line in lines) {
                            val values = CSVLine(line, fields)

                            // HTTP status
                            if (values["sc-status"] != "200") continue

                            // HTTP method
                            if (values["cs-method"] != "GET") continue

                            queries.insertLog(
                                // date and time in UTC
                                "${values["date"]} ${values["time"]} z",

                                // Referer header
                                values["cs(Referer)"].takeUnless { it == "-" },

                                // User-Agent
                                values["cs(User-Agent)"].takeUnless { it == "-" },

                                // Path
                                values["cs-uri-stem"]
                            )
                        }
                    }
                }
            }
        }
    }
}