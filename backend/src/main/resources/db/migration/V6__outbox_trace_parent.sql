-- O relay publica em outra thread, depois do commit: o contexto de tracing da
-- requisição HTTP já se perdeu. Guardar o traceparent (W3C) junto com o evento
-- permite que o Kafka e o motor-integracoes continuem o mesmo trace.
ALTER TABLE outbox_events ADD COLUMN trace_parent VARCHAR(55);
