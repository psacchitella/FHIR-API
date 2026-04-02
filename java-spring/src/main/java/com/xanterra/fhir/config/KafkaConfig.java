package com.xanterra.fhir.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String CLAIM_EVENTS_TOPIC = "fhir.claim.events";
    public static final String EOB_EVENTS_TOPIC = "fhir.eob.events";

    @Bean
    public NewTopic claimEventsTopic() {
        return TopicBuilder.name(CLAIM_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic eobEventsTopic() {
        return TopicBuilder.name(EOB_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
