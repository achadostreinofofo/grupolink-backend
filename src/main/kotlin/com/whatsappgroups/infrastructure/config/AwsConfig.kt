package com.whatsappgroups.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider

@Configuration
class AwsConfig(
    @Value("\${app.aws.access-key:}") private val accessKey: String,
    @Value("\${app.aws.secret-key:}") private val secretKey: String
) {

    @Bean
    fun awsCredentialsProvider(): AwsCredentialsProvider =
        if (accessKey.isNotBlank() && secretKey.isNotBlank())
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        else
            DefaultCredentialsProvider.create()   // lê AWS_ACCESS_KEY_ID + AWS_SECRET_ACCESS_KEY do ambiente
}
