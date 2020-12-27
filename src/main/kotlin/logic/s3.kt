package com.benasher44.kloudfrontblogstats.logic

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.io.InputStream

internal data class S3Object(val bucket: String, val key: String)

internal class S3Service(val region: Region) {
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

    fun deleteObject(s3o: S3Object) {
        val deleteRequest = DeleteObjectRequest.builder()
            .bucket(s3o.bucket)
            .key(s3o.key)
            .build()
        client.deleteObject(deleteRequest)
    }
}
