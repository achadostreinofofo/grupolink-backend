package com.whatsappgroups

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableAsync
class WhatsappGroupsSaasApplication

fun main(args: Array<String>) {
    runApplication<WhatsappGroupsSaasApplication>(*args)
}
