package com.whatsappgroups.infrastructure.messaging

import org.springframework.amqp.core.*
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitMQConfig {

    companion object {
        const val GROUP_CREATION_QUEUE    = "group.creation"
        const val GROUP_CREATION_DLQ      = "group.creation.dlq"
        const val GROUP_CREATION_EXCHANGE = "group.creation.exchange"
        const val GROUP_CREATION_KEY      = "group.creation"
    }

    @Bean
    fun groupCreationQueue(): Queue =
        QueueBuilder.durable(GROUP_CREATION_QUEUE)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", GROUP_CREATION_DLQ)
            .withArgument("x-message-ttl", 300_000)  // 5 min TTL
            .build()

    @Bean
    fun deadLetterQueue(): Queue = QueueBuilder.durable(GROUP_CREATION_DLQ).build()

    @Bean
    fun groupCreationExchange(): TopicExchange = TopicExchange(GROUP_CREATION_EXCHANGE)

    @Bean
    fun groupCreationBinding(groupCreationQueue: Queue, groupCreationExchange: TopicExchange): Binding =
        BindingBuilder.bind(groupCreationQueue).to(groupCreationExchange).with(GROUP_CREATION_KEY)

    @Bean
    fun jsonMessageConverter(): Jackson2JsonMessageConverter = Jackson2JsonMessageConverter()

    @Bean
    fun rabbitTemplate(connectionFactory: ConnectionFactory, converter: Jackson2JsonMessageConverter): RabbitTemplate =
        RabbitTemplate(connectionFactory).apply { messageConverter = converter }
}
