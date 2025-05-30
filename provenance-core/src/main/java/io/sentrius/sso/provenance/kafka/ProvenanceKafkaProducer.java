package io.sentrius.sso.provenance.kafka;


import io.sentrius.sso.provenance.ProvenanceEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProvenanceKafkaProducer {

    private final KafkaTemplate<String, ProvenanceEvent> kafkaTemplate;

    @Value("${provenance.kafka.topic}")
    private String topic;

    public ProvenanceKafkaProducer(KafkaTemplate<String, ProvenanceEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(ProvenanceEvent event) {
        kafkaTemplate.send(topic, event.getEventId(), event);
    }
}