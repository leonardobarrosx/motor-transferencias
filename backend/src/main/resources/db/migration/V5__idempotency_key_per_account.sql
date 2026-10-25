-- A chave de idempotência passa a ser única por conta de origem.
-- Global, a chave de um cliente colidia com a de outro (IDEMPOTENCY_KEY_REUSED)
-- e revelava que aquele UUID já tinha sido usado por alguém.
ALTER TABLE transfers DROP CONSTRAINT uk_transfers_idempotency_key;
ALTER TABLE transfers ADD CONSTRAINT uk_transfers_source_idempotency_key UNIQUE (source_account_id, idempotency_key);
