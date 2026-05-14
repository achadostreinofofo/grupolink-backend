package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.CheckoutResponse
import com.whatsappgroups.application.dto.CreateCheckoutRequest
import com.whatsappgroups.application.dto.SubscriptionStatusResponse
import com.whatsappgroups.application.usecase.payment.CheckoutUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/subscriptions")
class SubscriptionController(private val checkoutUseCase: CheckoutUseCase) {

    @PostMapping("/checkout")
    fun createCheckout(
        @AuthenticationPrincipal user: UserDetails,
        @Valid @RequestBody request: CreateCheckoutRequest
    ): ResponseEntity<CheckoutResponse> =
        ResponseEntity.ok(checkoutUseCase.createCheckout(UUID.fromString(user.username), request.plan))

    @GetMapping("/current")
    fun currentSubscription(
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<SubscriptionStatusResponse> =
        ResponseEntity.ok(checkoutUseCase.getActiveSubscription(UUID.fromString(user.username)))

    @DeleteMapping("/cancel")
    fun cancel(@AuthenticationPrincipal user: UserDetails): ResponseEntity<Void> {
        checkoutUseCase.cancelSubscription(UUID.fromString(user.username))
        return ResponseEntity.noContent().build()
    }
}
