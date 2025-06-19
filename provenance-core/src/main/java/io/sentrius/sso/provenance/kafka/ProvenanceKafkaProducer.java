package io.sentrius.sso.provenance.kafka;


import io.sentrius.sso.provenance.ProvenanceEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProvenanceKafkaProducer {

    private final KafkaTemplate<String, ProvenanceEvent> kafkaTemplate;

    @Value("${provenance.kafka.topic}")
    private String topic;

    public ProvenanceKafkaProducer(KafkaTemplate<String, ProvenanceEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(ProvenanceEvent event) {
        try {
            var future =
                kafkaTemplate.send(topic, event.getEventId(), event);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Kafka send failed", ex);
                }
            });
        }catch( Exception e){
            AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties());
            client.listTopics().names().whenComplete((topics, ex) -> {
                if (ex != null) {
                    log.error("Failed to retrieve Kafka topics", ex);
                } else {
                    log.info("Kafka topics: {}", topics);
                }
            });
        }
    }

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @PostConstruct
    public void logTopics() {
        AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties());
        client.listTopics().names().whenComplete((topics, ex) -> {
            if (ex != null) {
                log.error("Failed to retrieve Kafka topics", ex);
            } else {
                log.info("Kafka topics: {}", topics);
            }
        });
    }
}
