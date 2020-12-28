package com.benasher44.kloudfrontblogstats

import com.benasher44.kloudfrontblogstats.logic.CLI
import com.benasher44.kloudfrontblogstats.logic.S3Object
import com.benasher44.kloudfrontblogstats.logic.S3Service
import com.benasher44.kloudfrontblogstats.logic.enumerateLogs
import com.benasher44.kloudfrontblogstats.utils.LOGGER
import com.benasher44.kloudfrontblogstats.utils.logMessage
import com.benasher44.kloudfrontblogstats.utils.setLogger
import com.benasher44.kloudfrontblogstats.utils.withLazyConnection
import software.amazon.awssdk.regions.Region
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import kotlin.system.exitProcess

private val REGION by lazy {
    requireNotNull(System.getenv("LOG_BUCKET_REGION")) {
        "Specify log bucket region by setting the LOG_BUCKET_REGION env var"
    }
}

private val BUCKET by lazy {
    requireNotNull(System.getenv("LOG_BUCKET")) {
        "Specify log bucket by setting the LOG_BUCKET env var"
    }
}

@Suppress("unused", "RedundantVisibilityModifier")
public fun s3Handler(input: InputStream, output: OutputStream) {
    try {
        handleObjects(
            BUCKET,
            S3Service(Region.of(REGION), true)
        )
    } finally {
        input.close()
        output.close()
    }
}

private fun handleObjects(bucket: String, s3Service: S3Service) {
    try {
        var count = 0
        withLazyConnection { lazyQueries ->
            s3Service.enumerateObjectsInBucket(bucket) { s3o, listCount ->
                if (!listCount.isInitialized()) {
                    LOGGER.log("Listed ${listCount.value} keys.")
                }
                try {
                    handleObject(s3o, s3Service, lazyQueries.value)
                    count += 1
                } catch (e: Throwable) {
                    LOGGER.error("Processing error (${s3o.key}):  ${e.logMessage()}")
                }
                LOGGER.log("Processed ${s3o.key}")
            }
        }
        LOGGER.log("Processed $count objects successfully.")
    } catch (e: Throwable) {
        LOGGER.error(e.logMessage())
        throw e
    }
}

private fun handleObject(
    s3o: S3Object,
    s3Service: S3Service,
    queries: AccessLogQueries
) {
    LOGGER.log("Getting ${s3o.key}")
    s3Service.getObject(s3o).use { downloadStream ->
        GZIPInputStream(downloadStream).use { input ->
            queries.transaction {
                input.enumerateLogs { date, time, referer, userAgent, path ->
                    queries.insertLog(
                        // date and time in UTC
                        "$date $time",
                        referer,
                        userAgent,
                        path
                    )
                }
            }
        }
    }
    LOGGER.log("Deleting ${s3o.key}")
    s3Service.deleteObject(s3o)
}

fun main(args: Array<String>) = CLI {
    setLogger(this)
    try {
        handleObjects(
            bucket,
            S3Service(region, allowDelete)
        )
    } catch (e: Throwable) {
        LOGGER.error(e.logMessage())
        exitProcess(1)
    }
    exitProcess(0)
}.main(args)
