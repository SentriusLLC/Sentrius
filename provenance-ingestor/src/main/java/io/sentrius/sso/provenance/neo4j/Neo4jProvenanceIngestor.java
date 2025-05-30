package io.sentrius.sso.provenance.neo4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.neo4j.driver.Values.parameters;
import io.sentrius.sso.provenance.ProvenanceEvent;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class Neo4jProvenanceIngestor {

    private final Driver driver;

    public Neo4jProvenanceIngestor(@Value("${neo4j.uri}") String uri,
                                   @Value("${neo4j.username}") String user,
                                   @Value("${neo4j.password}") String password) {
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    public void insertEvents(List<ProvenanceEvent> events) {
        try (Session session = driver.session()) {
            log.info("Inserting {} events", events.size());
            session.writeTransaction(tx -> {
                tx.run("""
                UNWIND $events AS e
                MERGE (a:Actor {id: e.actor})
                MERGE (u:User {id: e.triggeringUser})
                MERGE (ev:Event {id: e.eventId})
                SET ev += {
                    type: e.eventType,
                    output: e.outputSummary,
                    timestamp: datetime(e.timestamp)
                }
                WITH a, u, ev, e
                CALL apoc.merge.relationship(a, e.eventType, {}, {}, ev) YIELD rel
                FOREACH (docId IN e.sourceDocs |
                    MERGE (d:Document {id: docId})
                    MERGE (ev)-[:USED]->(d)
                )
        
            """, parameters("events", events.stream().map(this::mapToParams).toList()));
                return null;
            });
        }
    }


    private Map<String, Object> mapToParams(ProvenanceEvent e) {
        Map<String, Object> map = new HashMap<>();
        map.put("eventId", e.getEventId().toString());
        map.put("actor", e.getActor());
        map.put("triggeringUser", e.getTriggeringUser());
        map.put("eventType", e.getEventType().name()); // must be string
        map.put("outputSummary", e.getOutputSummary());
        map.put("timestamp", e.getTimestamp().toString());
        map.put("sourceDocs", e.getSourceDocs() != null ? e.getSourceDocs() : List.of());
        return map;
    }


    public void close() {
        driver.close();
    }
}