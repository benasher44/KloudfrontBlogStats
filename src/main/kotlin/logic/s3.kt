package com.benasher44.kloudfrontblogstats.logic

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import java.io.InputStream

internal data class S3Object(val bucket: String, val key: String)

internal class S3Service(region: Region, private val allowDelete: Boolean) {
    private val client = S3Client.builder()
        .region(region)
        .build()

    fun getObject(s3o: S3Object): InputStream {
        val getRequest = GetObjectRequest.builder()
            .bucket(s3o.bucket)
            .key(s3o.key)
            .build()
        return client.getObject(getRequest)
    }

    // noop if allowDelete is false
    fun deleteObject(s3o: S3Object) {
        if (!allowDelete) return
        val deleteRequest = DeleteObjectRequest.builder()
            .bucket(s3o.bucket)
            .key(s3o.key)
            .build()
        client.deleteObject(deleteRequest)
    }

    /** Calls [lambda] for each object in the bucket, paging through all objects in the bucket */
    fun enumerateObjectsInBucket(bucket: String, lambda: (S3Object, Lazy<Int>) -> Unit) {
        var nextContinuationToken: String? = null
        do {
            // build the list request, with a continuation token if we have one
            val listRequest = ListObjectsV2Request.builder()
                .bucket(bucket)
            if (nextContinuationToken != null) {
                listRequest.continuationToken(nextContinuationToken)
            }

            // get the response and store the continuation token for next time
            val response = client.listObjectsV2(listRequest.build())
            if (response.isTruncated) {
                nextContinuationToken = response.nextContinuationToken()
            }

            // call the lambda for each object in the response
            if (response.hasContents()) {
                val count = lazy { response.keyCount() }
                for (s3o in response.contents()) {
                    lambda(S3Object(bucket, s3o.key()), count)
                }
            }
        } while (response.isTruncated)
    }
}
