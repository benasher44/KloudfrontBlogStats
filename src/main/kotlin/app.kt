package com.benasher44.kloudfrontblogstats

import com.benasher44.kloudfrontblogstats.logic.S3Object
import com.benasher44.kloudfrontblogstats.logic.S3ObjectParseResult
import com.benasher44.kloudfrontblogstats.logic.S3Service
import com.benasher44.kloudfrontblogstats.logic.enumerateLogs
import com.benasher44.kloudfrontblogstats.logic.s3ResultsFromEventJson
import software.amazon.awssdk.regions.Region
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

private fun handleObject(
    s3o: S3Object,
    s3Service: S3Service = S3Service(Region.of(REGION))
) {
    s3Service.getObject(s3o).use { downloadStream ->
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
    s3Service.deleteObject(s3o)
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
