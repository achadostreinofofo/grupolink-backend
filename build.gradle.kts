import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.spring") version "1.9.23"
    kotlin("plugin.jpa") version "1.9.23"
}

group = "com.whatsappgroups"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Flyway
    implementation("org.flywaydb:flyway-core")

    // Carrega automaticamente o arquivo .env em desenvolvimento local
    implementation("me.paulschwarz:spring-dotenv:4.0.0")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Database
    runtimeOnly("org.postgresql:postgresql")

    // Mercado Pago SDK
    implementation("com.mercadopago:sdk-java:2.1.22")

    // HTTP Client (para WhatsApp Cloud API)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Google OAuth2
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // OpenAPI / Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")

    // RabbitMQ
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // AWS S3 + SES
    implementation("software.amazon.awssdk:s3:2.25.40")
    implementation("software.amazon.awssdk:ses:2.25.40")
    implementation("software.amazon.awssdk:sts:2.25.40")

    // Testes
    testImplementation("org.testcontainers:junit-jupiter:1.19.8")
    testImplementation("org.testcontainers:rabbitmq:1.19.8")
    testImplementation("com.ninja-squad:springmockk:4.0.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    testRuntimeOnly("com.h2database:h2")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Lê o .env e injeta cada variável como environment variable no processo do bootRun.
// Garante funcionamento no Windows independente do spring-dotenv.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    val envFile = project.file(".env")
    if (envFile.exists()) {
        envFile.readLines()
            .filter { line -> line.isNotBlank() && !line.trimStart().startsWith("#") && "=" in line }
            .forEach { line ->
                val idx   = line.indexOf("=")
                val key   = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                environment(key, value)
            }
    }
}
