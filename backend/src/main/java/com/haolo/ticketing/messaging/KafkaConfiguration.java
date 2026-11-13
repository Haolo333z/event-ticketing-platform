package com.haolo.ticketing.messaging;

import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.*;
import org.springframework.kafka.config.*;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.clients.admin.NewTopic;
import com.haolo.ticketing.operations.MessagingMetrics;

@Configuration @EnableKafka @ConditionalOnProperty(name="messaging.enabled",havingValue="true")
public class KafkaConfiguration {
    @Bean ProducerFactory<String,String> eventProducerFactory(@Value("${messaging.bootstrap-servers}")String bootstrap){
        return new DefaultKafkaProducerFactory<>(Map.ofEntries(
            Map.entry("bootstrap.servers",bootstrap),Map.entry("key.serializer","org.apache.kafka.common.serialization.StringSerializer"),Map.entry("value.serializer","org.apache.kafka.common.serialization.StringSerializer"),
            Map.entry("acks","all"),Map.entry("enable.idempotence",true),Map.entry("max.in.flight.requests.per.connection",1),
            Map.entry("delivery.timeout.ms",3000),Map.entry("request.timeout.ms",1000),Map.entry("max.block.ms",2000),Map.entry("linger.ms",0)));
    }
    @Bean KafkaTemplate<String,String> eventKafka(ProducerFactory<String,String> factory){
        var template=new KafkaTemplate<>(factory);
        template.setProducerListener(new org.springframework.kafka.support.ProducerListener<>(){
            public void onError(org.apache.kafka.clients.producer.ProducerRecord<String,String> record,org.apache.kafka.clients.producer.RecordMetadata metadata,Exception error){org.slf4j.LoggerFactory.getLogger(KafkaConfiguration.class).warn("Kafka send failed topic={} type={}",record.topic(),error.getClass().getSimpleName());}
        });return template;
    }
    @Bean KafkaAdmin eventAdmin(@Value("${messaging.bootstrap-servers}")String bootstrap){var admin=new KafkaAdmin(Map.of("bootstrap.servers",bootstrap,"request.timeout.ms",3000,"default.api.timeout.ms",5000));admin.setFatalIfBrokerNotAvailable(false);admin.setOperationTimeout(5);return admin;}
    @Bean NewTopic bookingTopic(@Value("${messaging.topic}")String topic){return TopicBuilder.name(topic).partitions(3).replicas(1).config("retention.ms","604800000").config("min.insync.replicas","1").build();}
    @Bean NewTopic dlqTopic(@Value("${messaging.dlq-topic}")String topic){return TopicBuilder.name(topic).partitions(3).replicas(1).config("retention.ms","604800000").build();}
    @Bean ConsumerFactory<String,String> eventConsumerFactory(@Value("${messaging.bootstrap-servers}")String bootstrap){return new DefaultKafkaConsumerFactory<>(Map.of(
        "bootstrap.servers",bootstrap,"key.deserializer","org.apache.kafka.common.serialization.StringDeserializer","value.deserializer","org.apache.kafka.common.serialization.StringDeserializer",
        "enable.auto.commit",false,"auto.offset.reset","earliest","max.poll.records",10,"max.poll.interval.ms",120000,"default.api.timeout.ms",5000));}
    @Bean ConcurrentKafkaListenerContainerFactory<String,String> paymentFactory(ConsumerFactory<String,String> factory,FailureStore failures){return listenerFactory(factory,failures,EventConsumer.PAYMENT);}
    @Bean ConcurrentKafkaListenerContainerFactory<String,String> auditFactory(ConsumerFactory<String,String> factory,FailureStore failures){return listenerFactory(factory,failures,EventConsumer.AUDIT);}
    private ConcurrentKafkaListenerContainerFactory<String,String> listenerFactory(ConsumerFactory<String,String> factory,FailureStore failures,String group){
        var result=new ConcurrentKafkaListenerContainerFactory<String,String>();result.setConsumerFactory(factory);
        result.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        result.getContainerProperties().setDeliveryAttemptHeader(true);
        var errors=new DefaultErrorHandler((record,error)->failures.park(group,record,error),new FixedBackOff(250,2));
        // Application logs carry validated IDs; do not dump listener exceptions/records on retries.
        errors.setLogLevel(org.springframework.kafka.KafkaException.Level.DEBUG);
        errors.setClassifications(Map.of(Exception.class,true),true);result.setCommonErrorHandler(errors);return result;
    }
    @Bean KafkaListeners eventListeners(EventConsumer consumer,MessagingMetrics metrics){return new KafkaListeners(consumer,metrics);}
    public static class KafkaListeners {
        private final EventConsumer consumer;private final MessagingMetrics metrics;
        KafkaListeners(EventConsumer consumer,MessagingMetrics metrics){this.consumer=consumer;this.metrics=metrics;}
        private void attempt(String group,org.apache.kafka.clients.consumer.ConsumerRecord<String,String> record){
            var header=record.headers().lastHeader(org.springframework.kafka.support.KafkaHeaders.DELIVERY_ATTEMPT);
            if(header!=null&&header.value()!=null&&header.value().length==4)metrics.consumerAttempt(group,java.nio.ByteBuffer.wrap(header.value()).getInt());
        }
        @KafkaListener(id="payment-listener",groupId="${messaging.group-prefix}.payment-v1",topics="${messaging.topic}",containerFactory="paymentFactory")
        public void payment(org.apache.kafka.clients.consumer.ConsumerRecord<String,String> record){attempt(EventConsumer.PAYMENT,record);consumer.accept(EventConsumer.PAYMENT,record.key(),record.value());}
        @KafkaListener(id="audit-listener",groupId="${messaging.group-prefix}.booking-audit-v1",topics="${messaging.topic}",containerFactory="auditFactory")
        public void audit(org.apache.kafka.clients.consumer.ConsumerRecord<String,String> record){attempt(EventConsumer.AUDIT,record);consumer.accept(EventConsumer.AUDIT,record.key(),record.value());}
    }
}
