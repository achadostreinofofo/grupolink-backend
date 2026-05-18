package com.whatsappgroups.infrastructure.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider

@Configuration
class AwsConfig(
    // Lidos via spring-dotenv (.env) → application.yml → @Value
    @Value("\${app.aws.access-key:}") private val cfgAccessKey: String,
    @Value("\${app.aws.secret-key:}") private val cfgSecretKey: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun awsCredentialsProvider(): AwsCredentialsProvider {
        // Prioridade: @Value (spring-dotenv) → System.getenv() → DefaultCredentialsProvider
        val key = cfgAccessKey.ifBlank { System.getenv("AWS_ACCESS_KEY_ID") ?: "" }
        val sec = cfgSecretKey.ifBlank { System.getenv("AWS_SECRET_ACCESS_KEY") ?: "" }

        return if (key.isNotBlank() && sec.isNotBlank()) {
            log.info("AWS: usando credenciais explícitas (key={}...)", key.take(8))
            StaticCredentialsProvider.create(AwsBasicCredentials.create(key, sec))
        } else {
            log.warn("AWS: nenhuma credencial explícita encontrada — usando DefaultCredentialsProvider. " +
                "Configure AWS_ACCESS_KEY_ID e AWS_SECRET_ACCESS_KEY no .env ou nas variáveis de ambiente.")
            // Exclui ~/.aws/credentials e tokens de sessão — usa só variáveis de ambiente do sistema
            DefaultCredentialsProvider.builder()
                .profileName("default")
                .build()
        }
    }
}
