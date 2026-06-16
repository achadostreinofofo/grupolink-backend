package com.whatsappgroups.infrastructure.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

@Service
class S3UploadService(
    private val awsCredentialsProvider: AwsCredentialsProvider,
    @Value("\${app.s3.bucket}")    private val bucket: String,
    @Value("\${app.s3.region}")    private val region: String,
    @Value("\${app.s3.base-url:}") private val baseUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val s3: S3Client by lazy {
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(awsCredentialsProvider)
            .build()
    }

    fun uploadImage(file: MultipartFile, userId: String): String {
        val ext  = file.originalFilename?.substringAfterLast('.', "jpg") ?: "jpg"
        val key  = "messages/$userId/${UUID.randomUUID()}.$ext"
        val type = file.contentType ?: "image/jpeg"

        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket).key(key).contentType(type).contentLength(file.size).build(),
            RequestBody.fromInputStream(file.inputStream, file.size)
        )

        val url = if (baseUrl.isNotBlank()) "$baseUrl/$key"
                  else "https://$bucket.s3.$region.amazonaws.com/$key"

        log.info("Uploaded image: $url")
        return url
    }

    // Faz upload de uma imagem já carregada em memória (ex.: baixada de uma URL externa).
    // Usado para persistir no S3 a imagem principal de produtos do ML, de modo que a mídia
    // salva na mensagem seja idêntica a um upload manual (não dependa de URL externa no envio).
    fun uploadImageBytes(bytes: ByteArray, contentType: String, userId: String): String {
        val ext = when {
            contentType.contains("png")  -> "png"
            contentType.contains("webp") -> "webp"
            contentType.contains("gif")  -> "gif"
            else                         -> "jpg"
        }
        val key  = "messages/$userId/${UUID.randomUUID()}.$ext"
        val type = contentType.ifBlank { "image/jpeg" }

        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket).key(key).contentType(type).contentLength(bytes.size.toLong()).build(),
            RequestBody.fromBytes(bytes)
        )

        val url = if (baseUrl.isNotBlank()) "$baseUrl/$key"
                  else "https://$bucket.s3.$region.amazonaws.com/$key"

        log.info("Uploaded image from bytes: $url")
        return url
    }
}
