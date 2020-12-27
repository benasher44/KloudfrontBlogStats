package com.benasher44.kloudfrontblogstats

import com.benasher44.kloudfrontblogstats.logic.S3Object
import com.benasher44.kloudfrontblogstats.logic.S3ObjectParseResult
import com.benasher44.kloudfrontblogstats.logic.s3ResultsFromEventJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EventParsingTest {

    private val sampleJson = """
            {
              "Records": [
                {
                  "eventVersion": "2.1",
                  "eventSource": "aws:s3",
                  "awsRegion": "us-east-2",
                  "eventTime": "2019-09-03T19:37:27.192Z",
                  "eventName": "ObjectCreated:Put",
                  "userIdentity": {
                    "principalId": "AWS:AIDAINPONIXQXHT3IKHL2"
                  },
                  "requestParameters": {
                    "sourceIPAddress": "205.255.255.255"
                  },
                  "responseElements": {
                    "x-amz-request-id": "D82B88E5F771F645",
                    "x-amz-id-2": "vlR7PnpV2Ce81l0PRw6jlUpck7Jo5ZsQjryTjKlc5aLWGVHPZLj5NeC6qMa0emYBDXOo6QBU0Wo="
                  },
                  "s3": {
                    "s3SchemaVersion": "1.0",
                    "configurationId": "828aa6fc-f7b5-4305-8584-487c791949c1",
                    "bucket": {
                      "name": "lambda-artifacts-deafc19498e3f2df",
                      "ownerIdentity": {
                        "principalId": "A3I5XTEXAMAI3E"
                      },
                      "arn": "arn:aws:s3:::lambda-artifacts-deafc19498e3f2df"
                    },
                    "object": {
                      "key": "b21b84d653bb07b05b1e6b33684dc11b",
                      "size": 1305107,
                      "eTag": "b21b84d653bb07b05b1e6b33684dc11b",
                      "sequencer": "0C0F6F405D6ED209E1"
                    }
                  }
                }
              ]
            }
    """.trimIndent()

    @Test
    fun `parses s3 event json into S3 objects`() {
        val results = s3ResultsFromEventJson(sampleJson, "us-east-2").toList()
        assertEquals(1, results.count())
        assertEquals(
            S3Object("lambda-artifacts-deafc19498e3f2df", "b21b84d653bb07b05b1e6b33684dc11b"),
            (results.first() as? S3ObjectParseResult.Success)?.s3Object
        )
    }

    @Test
    fun `logs error when region doesn't match`() {
        val results = s3ResultsFromEventJson(sampleJson, "us-west-2").toList()
        assertEquals(1, results.count())
        assertEquals(
            S3ObjectParseResult.InvalidRegion("us-east-2", "us-west-2"),
            results.first()
        )
    }
}
