package com.haolo.ticketing.operations;

import java.util.Map;
import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

/** Fixed label sets only. Counters count physical delivery attempts in this JVM, not unique events. */
@Component
public class MessagingMetrics {
    private final Counter outboxRetry,dlqRetry,dlqAcknowledged,dlqFailed;
    private final Map<String,Counter> consumerRetries;
    public MessagingMetrics(MeterRegistry registry){
        outboxRetry=retry(registry,"outbox","none");dlqRetry=retry(registry,"dlq","none");
        consumerRetries=Map.of("payment-v1",retry(registry,"consumer","payment-v1"),"booking-audit-v1",retry(registry,"consumer","booking-audit-v1"));
        dlqAcknowledged=delivery(registry,"ACKNOWLEDGED");dlqFailed=delivery(registry,"FAILED");
    }
    private Counter retry(MeterRegistry registry,String stage,String consumer){return Counter.builder("ticketing.messaging.retries").tags("stage",stage,"consumer",consumer).baseUnit("retries").description("Delivery attempts after the initial attempt; JVM-local").register(registry);}
    private Counter delivery(MeterRegistry registry,String outcome){return Counter.builder("ticketing.messaging.dlq.deliveries").tag("outcome",outcome).baseUnit("deliveries").description("DLQ send attempts by acknowledgement outcome; JVM-local").register(registry);}
    public void outboxAttempt(int previousAttempts){if(previousAttempts>0)outboxRetry.increment();}
    public void dlqAttempt(int previousAttempts){if(previousAttempts>0)dlqRetry.increment();}
    public void consumerAttempt(String consumer,int attempt){var counter=consumerRetries.get(consumer);if(counter!=null&&attempt>1)counter.increment();}
    public void dlqResult(boolean acknowledged){(acknowledged?dlqAcknowledged:dlqFailed).increment();}
}
