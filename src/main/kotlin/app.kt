package com.benasher44.kloudfrontblogstats

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream

private val REGION = requireNotNull(System.getenv("AWS_DEFAULT_REGION")) {
    "Specify AWS region by setting the AWS_DEFAULT_REGION env var"
}

private data class S3Object(val bucket: String, val key: String)

public fun s3Handler(input: InputStream, output: OutputStream) {
    try {
        val stringInput = input.readBytes().decodeToString()
        val json = Json { }.parseToJsonElement(stringInput)
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
            withNewConnection { queries ->
                queries.transaction {
                    input.enumerateLogs { date, time, referer, userAgent, path ->
                        queries.insertLog(
                            // date and time in UTC
                            "$date $time z",
                            referer,
                            userAgent,
                            path
                        )
                    }
                }
            }
        }
    }

    val deleteRequest = DeleteObjectRequest.builder()
        .bucket(s3o.bucket)
        .key(s3o.key)
        .build()
    S3.deleteObject(deleteRequest)
}
