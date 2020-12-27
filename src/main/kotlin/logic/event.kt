package com.benasher44.kloudfrontblogstats.logic

import com.benasher44.kloudfrontblogstats.stringContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal sealed class S3ObjectParseResult {
    data class InvalidRegion(val foundRegion: String, val expectedRegion: String) : S3ObjectParseResult() {
        val message = "$foundRegion does not match $expectedRegion"
    }
    class Success(val s3Object: S3Object) : S3ObjectParseResult()
}

internal fun s3ResultsFromEventJson(jsonStr: String, expectedRegion: String): Iterable<S3ObjectParseResult> {
    val json = Json.Default.parseToJsonElement(jsonStr)
    val records = json.jsonObject["Records"]!!.jsonArray

    return records.asSequence()
        .mapNotNull { it as? JsonObject }

        // only S3 create events
        .filter { it.stringContent("eventSource") == "aws:s3" }
        .filter { it.stringContent("eventName") == "ObjectCreated:Put" }

        // capture region and s3 info
        .map { it["awsRegion"]!!.jsonPrimitive.content to it["s3"]!!.jsonObject }

        // parse into object
        .map { (region, s3) ->
            if (region == expectedRegion) {
                S3ObjectParseResult.Success(
                    S3Object(
                        bucket = s3["bucket"]!!.jsonObject["name"]!!.jsonPrimitive.content,
                        key = s3["object"]!!.jsonObject["key"]!!.jsonPrimitive.content
                    )
                )
            } else {
                S3ObjectParseResult.InvalidRegion(region, expectedRegion)
            }
        }
        .asIterable()
}
