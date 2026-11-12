package com.haolo.ticketing.messaging;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import com.haolo.ticketing.payments.RefundRecoveryService;
import org.slf4j.LoggerFactory;

@Component @ConditionalOnProperty(name={"messaging.enabled","messaging.scheduling-enabled"},havingValue="true")
public class MessagingScheduler {
    private final OutboxPublisher publisher;private final RefundRecoveryService refunds;
    public MessagingScheduler(OutboxPublisher publisher,RefundRecoveryService refunds){this.publisher=publisher;this.refunds=refunds;}
    @Scheduled(fixedDelay=500) public void publish(){try{publisher.publishOnce();}catch(RuntimeException ex){LoggerFactory.getLogger(getClass()).warn("Outbox pass failed type={}",ex.getClass().getSimpleName());}}
    @Scheduled(fixedDelay=1000) public void compensate(){try{refunds.runOnce();}catch(RuntimeException ex){LoggerFactory.getLogger(getClass()).warn("Refund recovery pass failed type={}",ex.getClass().getSimpleName());}}
}
