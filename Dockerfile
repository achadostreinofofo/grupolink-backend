FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
# tzdata + TZ: o app trabalha com horários locais (LocalDateTime). Sem isto a JVM roda em UTC
# e os agendamentos disparam 3h adiantados no Brasil. Fixado em horário de Brasília.
RUN apk add --no-cache curl tzdata
ENV TZ=America/Sao_Paulo
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=America/Sao_Paulo", "-jar", "app.jar"]
