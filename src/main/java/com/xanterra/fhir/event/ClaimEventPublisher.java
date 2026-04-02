package com.xanterra.fhir.event;

import com.xanterra.fhir.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ClaimEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventPublisher.class);

    private final KafkaTemplate<String, ClaimEvent> kafkaTemplate;

    public ClaimEventPublisher(KafkaTemplate<String, ClaimEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishClaimEvent(ClaimEvent event) {
        String topic = switch (event.getEventType()) {
            case EOB_GENERATED -> KafkaConfig.EOB_EVENTS_TOPIC;
            default -> KafkaConfig.CLAIM_EVENTS_TOPIC;
        };
        log.info("Publishing {} to {} for claim {}", event.getEventType(), topic, event.getClaimId());
        kafkaTemplate.send(topic, event.getClaimId(), event);
    }
}
