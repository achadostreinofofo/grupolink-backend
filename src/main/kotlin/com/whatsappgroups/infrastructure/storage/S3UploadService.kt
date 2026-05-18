package com.whatsappgroups.infrastructure.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

@Service
class S3UploadService(
    @Value("\${app.s3.bucket}")          private val bucket: String,
    @Value("\${app.s3.region}")          private val region: String,
    @Value("\${app.s3.access-key:}")     private val accessKey: String,
    @Value("\${app.s3.secret-key:}")     private val secretKey: String,
    @Value("\${app.s3.base-url:}")       private val baseUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val s3: S3Client by lazy {
        val builder = S3Client.builder().region(Region.of(region))
        if (accessKey.isNotBlank() && secretKey.isNotBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
            )
        }
        builder.build()
    }

    fun uploadImage(file: MultipartFile, userId: String): String {
        val ext  = file.originalFilename?.substringAfterLast('.', "jpg") ?: "jpg"
        val key  = "messages/$userId/${UUID.randomUUID()}.$ext"
        val type = file.contentType ?: "image/jpeg"

        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(type)
                .contentLength(file.size)
                .build(),
            RequestBody.fromInputStream(file.inputStream, file.size)
        )

        val url = if (baseUrl.isNotBlank()) "$baseUrl/$key"
                  else "https://$bucket.s3.$region.amazonaws.com/$key"

        log.info("Uploaded image: $url")
        return url
    }
}
