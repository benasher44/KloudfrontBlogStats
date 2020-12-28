package com.benasher44.kloudfrontblogstats

import com.benasher44.kloudfrontblogstats.logic.CLI
import com.benasher44.kloudfrontblogstats.logic.S3Object
import com.benasher44.kloudfrontblogstats.logic.S3ObjectParseResult
import com.benasher44.kloudfrontblogstats.logic.S3Service
import com.benasher44.kloudfrontblogstats.logic.enumerateLogs
import com.benasher44.kloudfrontblogstats.logic.s3ResultsFromEventJson
import com.benasher44.kloudfrontblogstats.utils.LOGGER
import com.benasher44.kloudfrontblogstats.utils.logMessage
import com.benasher44.kloudfrontblogstats.utils.setLogger
import com.benasher44.kloudfrontblogstats.utils.withNewConnection
import software.amazon.awssdk.regions.Region
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import kotlin.system.exitProcess

private val REGION by lazy {
    requireNotNull(System.getenv("AWS_DEFAULT_REGION")) {
        "Specify AWS region by setting the AWS_DEFAULT_REGION env var"
    }
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
                    LOGGER.error(result.message)
                    soFar
                }
                is S3ObjectParseResult.Success -> {
                    try {
                        handleObject(result.s3Object)
                        soFar + 1
                    } catch (e: Throwable) {
                        LOGGER.error("Processing error: $result -  ${e.logMessage()}")
                        soFar
                    }
                }
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
    s3Service: S3Service? = null
) {
    val service = s3Service ?: S3Service(Region.of(REGION), true)
    service.getObject(s3o).use { downloadStream ->
        GZIPInputStream(downloadStream).use { input ->
            withNewConnection { queries ->
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
    }
    service.deleteObject(s3o)
}

fun main(args: Array<String>) = CLI {
    setLogger(this)
    val service = S3Service(region, allowDelete)
    try {
        service.enumerateObjectsInBucket(bucket) { s3o, count ->
            if (!count.isInitialized()) {
                LOGGER.log("Listed ${count.value} keys.")
            }
            handleObject(s3o, service)
            LOGGER.log("Processed ${s3o.key}")
        }
    } catch (e: Throwable) {
        LOGGER.error(e.logMessage())
        exitProcess(1)
    }
    exitProcess(0)
}.main(args)
