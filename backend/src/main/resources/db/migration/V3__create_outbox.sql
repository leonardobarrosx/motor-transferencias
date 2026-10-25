-- Outbox transacional: o evento da transferência é gravado na MESMA transação que
-- move o dinheiro. Um relay lê daqui e publica no Kafka. Assim nunca existe
-- transferência sem evento, nem evento de transferência que deu rollback.

CREATE TABLE outbox_events (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(40)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(60)  NOT NULL,
    partition_key  VARCHAR(80)  NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ
);

-- índice parcial: o relay só procura o que ainda não foi publicado
CREATE INDEX ix_outbox_pending ON outbox_events (created_at) WHERE published_at IS NULL;
