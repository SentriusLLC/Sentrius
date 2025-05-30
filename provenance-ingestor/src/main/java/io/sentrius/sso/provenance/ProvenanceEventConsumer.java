package io.sentrius.sso.provenance;

import java.util.ArrayList;
import java.util.List;
import io.sentrius.sso.provenance.neo4j.Neo4jProvenanceIngestor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ProvenanceEventConsumer {

    private final Neo4jProvenanceIngestor ingestor;
    private final List<ProvenanceEvent> buffer = new ArrayList<>();
    private final int BATCH_SIZE = 50;

    public ProvenanceEventConsumer(Neo4jProvenanceIngestor ingestor) {
        this.ingestor = ingestor;
    }

    @KafkaListener(topics = "${provenance.kafka.topic}", groupId = "neo4j-ingestor")
    public void handle(ProvenanceEvent event) {
        buffer.add(event);
        if (buffer.size() >= BATCH_SIZE) {
            flush();
        }
    }

    @Scheduled(fixedRate = 10000)
    public void flush() {
        log.info("Flushing {} events to Neo4j", buffer.size());
        if (!buffer.isEmpty()) {
            ingestor.insertEvents(new ArrayList<>(buffer));
            buffer.clear();
        }
    }
}
