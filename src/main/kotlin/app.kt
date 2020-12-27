package com.benasher44.kloudfrontblogstats

import com.benasher44.kloudfrontblogstats.logic.S3Object
import com.benasher44.kloudfrontblogstats.logic.S3ObjectParseResult
import com.benasher44.kloudfrontblogstats.logic.enumerateLogs
import com.benasher44.kloudfrontblogstats.logic.s3ResultsFromEventJson
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.io.InputStream
import java.io.OutputStream
import java.util.logging.Level
import java.util.logging.Logger
import java.util.zip.GZIPInputStream

private val REGION = requireNotNull(System.getenv("AWS_DEFAULT_REGION")) {
    "Specify AWS region by setting the AWS_DEFAULT_REGION env var"
}

@Suppress("unused", "RedundantVisibilityModifier")
public fun s3Handler(input: InputStream, output: OutputStream) {
    try {
        handleObjects(
            s3ResultsFromEventJson(input.readBytes().decodeToString(), REGION),
        )
    } finally {
        input.close()
        output.close()
    }
}

private fun handleObjects(objects: Iterable<S3ObjectParseResult>) {
    try {
        val count = objects.fold(0) { soFar, result ->
            when (result) {
                is S3ObjectParseResult.InvalidRegion -> {
                    Logging.error(result.message)
                    soFar
                }
                is S3ObjectParseResult.Success -> {
                    try {
                        handleObject(result.s3Object)
                        soFar + 1
                    } catch (e: Throwable) {
                        Logging.error("Processing error: $result -  ${e.logMessage()}")
                        soFar
                    }
                }
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

private object Logging {
    private val logger: Logger = Logger.getLogger(this::javaClass.name)

    fun log(msg: String) {
        logger.log(Level.INFO, msg)
    }

    fun error(msg: String) {
        logger.log(Level.SEVERE, msg)
    }
}
