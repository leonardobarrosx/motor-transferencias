package dev.leonardobarros.motor.outbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import dev.leonardobarros.motor.shared.persistence.AssignedIdEntity;

/** Um evento esperando para ser publicado no Kafka. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 40, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    private String eventType;

    /** Vira a chave da mensagem no Kafka: mesma chave, mesma partição, ordem garantida. */
    @Column(name = "partition_key", nullable = false, length = 80, updatable = false)
    private String partitionKey;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
        // JPA
    }

    public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType,
                       String partitionKey, String payload, Instant now) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.createdAt = now;
    }

    public void markPublished(Instant now) {
        this.publishedAt = now;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
