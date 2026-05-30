package com.whatsappgroups.infrastructure.config

object OwnerAccount {
    const val EMAIL = "achadostreinofofo@gmail.com"

    fun isOwner(email: String) = email.equals(EMAIL, ignoreCase = true)
}
