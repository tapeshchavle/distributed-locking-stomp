package com.bookmyshow.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "notifications_exchange";
    public static final String SEAT_WAIT_EXCHANGE = "seat_wait_exchange";
    public static final String DEAD_LETTER_EXCHANGE = "seat_dead_letter_exchange";
    
    public static final String QUEUE_BOOKING_SUCCESS = "booking_success_queue";
    public static final String QUEUE_SEAT_WAIT = "seat_wait_queue";
    public static final String QUEUE_SEAT_EXPIRATION_DLQ = "seat_expiration_dlq";
    
    public static final String ROUTING_KEY_SUCCESS = "booking.success";
    public static final String ROUTING_KEY_SEAT_EXPIRATION = "seat.expiration";

    @Bean
    public Queue bookingSuccessQueue() {
        return new Queue(QUEUE_BOOKING_SUCCESS, true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Binding bindingBuilder(Queue bookingSuccessQueue, DirectExchange exchange) {
        return BindingBuilder.bind(bookingSuccessQueue).to(exchange).with(ROUTING_KEY_SUCCESS);
    }

    @Bean
    public Queue seatWaitQueue() {
        return org.springframework.amqp.core.QueueBuilder.durable(QUEUE_SEAT_WAIT)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_SEAT_EXPIRATION)
                .build();
    }

    @Bean
    public DirectExchange seatWaitExchange() {
        return new DirectExchange(SEAT_WAIT_EXCHANGE);
    }

    @Bean
    public Binding seatWaitBinding(Queue seatWaitQueue, DirectExchange seatWaitExchange) {
        return BindingBuilder.bind(seatWaitQueue).to(seatWaitExchange).with(ROUTING_KEY_SEAT_EXPIRATION);
    }

    @Bean
    public Queue seatExpirationDlq() {
        return new Queue(QUEUE_SEAT_EXPIRATION_DLQ, true);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Binding expirationBinding(Queue seatExpirationDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(seatExpirationDlq).to(deadLetterExchange).with(ROUTING_KEY_SEAT_EXPIRATION);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}