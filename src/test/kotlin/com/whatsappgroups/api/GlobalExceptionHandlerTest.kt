package com.whatsappgroups.api

import com.mercadopago.exceptions.MPApiException
import com.mercadopago.exceptions.MPException
import com.whatsappgroups.application.usecase.auth.CpfAlreadyExistsException
import com.whatsappgroups.application.usecase.auth.EmailAlreadyExistsException
import com.whatsappgroups.interfaces.api.GlobalExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.ses.model.SesException
import java.security.GeneralSecurityException

class GlobalExceptionHandlerTest {

    private lateinit var handler: GlobalExceptionHandler

    @BeforeEach
    fun setUp() {
        handler = GlobalExceptionHandler()
    }

    @Test
    fun `handleEmailExists returns 409 CONFLICT`() {
        val response = handler.handleEmailExists(EmailAlreadyExistsException())
        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body!!["error"]).isEqualTo("EMAIL_ALREADY_EXISTS")
    }

    @Test
    fun `handleCpfExists returns 409 CONFLICT`() {
        val response = handler.handleCpfExists(CpfAlreadyExistsException())
        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body!!["error"]).isEqualTo("CPF_ALREADY_EXISTS")
    }

    @Test
    fun `handleIllegalArgument returns 400 with message`() {
        val response = handler.handleIllegalArgument(IllegalArgumentException("bad input"))
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!["error"]).isEqualTo("bad input")
    }

    @Test
    fun `handleNotFound returns 404`() {
        val response = handler.handleNotFound(NoSuchElementException("not found"))
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body!!["error"]).isEqualTo("not found")
    }

    @Test
    fun `handleForbidden returns 403`() {
        val response = handler.handleForbidden(IllegalAccessException())
        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(response.body!!["error"]).isEqualTo("Acesso negado")
    }

    @Test
    fun `handleIllegalState returns 503`() {
        val response = handler.handleIllegalState(IllegalStateException("service down"))
        assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        assertThat(response.body!!["error"]).isEqualTo("service down")
    }

    @Test
    fun `handleMercadoPagoApiError 400 returns descriptive message`() {
        val ex = mock<MPApiException>()
        whenever(ex.statusCode).thenReturn(400)
        whenever(ex.message).thenReturn("bad request")

        val response = handler.handleMercadoPagoApiError(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        assertThat(response.body!!["error"]).isEqualTo("MERCADOPAGO_ERROR")
        assertThat(response.body!!["message"]).contains("inválidos")
    }

    @Test
    fun `handleMercadoPagoApiError 401 returns token error message`() {
        val ex = mock<MPApiException>()
        whenever(ex.statusCode).thenReturn(401)
        whenever(ex.message).thenReturn("unauthorized")

        val response = handler.handleMercadoPagoApiError(ex)
        assertThat(response.body!!["message"]).contains("Token")
    }

    @Test
    fun `handleMercadoPagoApiError 403 returns permissions message`() {
        val ex = mock<MPApiException>()
        whenever(ex.statusCode).thenReturn(403)
        whenever(ex.message).thenReturn("forbidden")

        val response = handler.handleMercadoPagoApiError(ex)
        assertThat(response.body!!["message"]).contains("autorizado")
    }

    @Test
    fun `handleMercadoPagoApiError 422 returns validation message`() {
        val ex = mock<MPApiException>()
        whenever(ex.statusCode).thenReturn(422)
        whenever(ex.message).thenReturn("unprocessable")

        val response = handler.handleMercadoPagoApiError(ex)
        assertThat(response.body!!["message"]).contains("rejeitados")
    }

    @Test
    fun `handleMercadoPagoApiError unknown code returns generic message`() {
        val ex = mock<MPApiException>()
        whenever(ex.statusCode).thenReturn(500)
        whenever(ex.message).thenReturn("server error")

        val response = handler.handleMercadoPagoApiError(ex)
        assertThat(response.body!!["message"]).contains("500")
    }

    @Test
    fun `handleMercadoPagoError returns 503`() {
        val ex = mock<MPException>()
        whenever(ex.message).thenReturn("sdk error")

        val response = handler.handleMercadoPagoError(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        assertThat(response.body!!["error"]).isEqualTo("MERCADOPAGO_ERROR")
    }

    @Test
    fun `handleS3Error 403 returns credentials message`() {
        val ex = mock<S3Exception>()
        whenever(ex.statusCode()).thenReturn(403)

        val response = handler.handleS3Error(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        assertThat(response.body!!["error"]).isEqualTo("S3_ERROR")
        assertThat(response.body!!["message"]).contains("AWS_ACCESS_KEY_ID")
    }

    @Test
    fun `handleS3Error 404 returns bucket not found message`() {
        val ex = mock<S3Exception>()
        whenever(ex.statusCode()).thenReturn(404)

        val response = handler.handleS3Error(ex)
        assertThat(response.body!!["message"]).contains("S3_BUCKET")
    }

    @Test
    fun `handleS3Error generic code returns fallback message`() {
        val ex = mock<S3Exception>()
        whenever(ex.statusCode()).thenReturn(500)

        val response = handler.handleS3Error(ex)
        assertThat(response.body!!["message"]).contains("500")
    }

    @Test
    fun `handleSesError 403 returns permissions message`() {
        val ex = mock<SesException>()
        whenever(ex.statusCode()).thenReturn(403)

        val response = handler.handleSesError(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        assertThat(response.body!!["error"]).isEqualTo("SES_ERROR")
        assertThat(response.body!!["message"]).contains("AWS_ACCESS_KEY_ID")
    }

    @Test
    fun `handleSesError 400 returns unverified email message`() {
        val ex = mock<SesException>()
        whenever(ex.statusCode()).thenReturn(400)

        val response = handler.handleSesError(ex)
        assertThat(response.body!!["message"]).contains("SES_FROM_EMAIL")
    }

    @Test
    fun `handleSesError generic code returns fallback message`() {
        val ex = mock<SesException>()
        whenever(ex.statusCode()).thenReturn(500)

        val response = handler.handleSesError(ex)
        assertThat(response.body!!["message"]).contains("500")
    }

    @Test
    fun `handleCryptoError returns 400 with ENCRYPTION_KEY_EXPIRED`() {
        val response = handler.handleCryptoError(GeneralSecurityException("crypto error"))
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!["error"]).isEqualTo("ENCRYPTION_KEY_EXPIRED")
    }
}
