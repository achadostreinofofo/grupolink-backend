package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.usecase.auth.CpfAlreadyExistsException
import com.whatsappgroups.application.usecase.auth.EmailAlreadyExistsException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import software.amazon.awssdk.services.s3.model.S3Exception
import java.security.GeneralSecurityException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Inválido") }
        return ResponseEntity.badRequest().body(mapOf("errors" to errors))
    }

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun handleEmailExists(ex: EmailAlreadyExistsException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf(
            "error" to "EMAIL_ALREADY_EXISTS",
            "message" to "Já existe uma conta cadastrada com este e-mail."
        ))

    @ExceptionHandler(CpfAlreadyExistsException::class)
    fun handleCpfExists(ex: CpfAlreadyExistsException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf(
            "error" to "CPF_ALREADY_EXISTS",
            "message" to "Já existe uma conta cadastrada com este CPF."
        ))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "Requisição inválida")))

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (ex.message ?: "Não encontrado")))

    @ExceptionHandler(IllegalAccessException::class)
    fun handleForbidden(ex: IllegalAccessException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "Acesso negado"))

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("error" to (ex.message ?: "Serviço indisponível")))

    @ExceptionHandler(S3Exception::class)
    fun handleS3Error(ex: S3Exception): ResponseEntity<Map<String, String>> {
        val message = when (ex.statusCode()) {
            403  -> "Credenciais AWS inválidas ou sem permissão de acesso ao bucket S3. " +
                    "Verifique as variáveis S3_ACCESS_KEY, S3_SECRET_KEY e S3_BUCKET."
            404  -> "Bucket S3 não encontrado. Verifique a variável S3_BUCKET."
            else -> "Falha ao fazer upload da imagem para o S3 (código ${ex.statusCode()}). " +
                    "Verifique as configurações S3_* no servidor."
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(mapOf("error" to "S3_ERROR", "message" to message))
    }

    // RSA decryption failures — most likely cause: stale public key cached in the browser
    // The frontend must re-fetch /api/security/public-key and retry.
    @ExceptionHandler(GeneralSecurityException::class)
    fun handleCryptoError(ex: GeneralSecurityException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf(
            "error" to "ENCRYPTION_KEY_EXPIRED",
            "message" to "Chave de criptografia expirada. Recarregue a página e tente novamente."
        ))
}
