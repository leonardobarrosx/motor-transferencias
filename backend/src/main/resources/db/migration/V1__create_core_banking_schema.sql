-- Núcleo do motor: contas, transferências e o livro-razão de partidas dobradas.
-- As constraints aqui são a última linha de defesa: mesmo que um bug passe pela
-- camada de domínio, o banco recusa saldo negativo, valor zero e chave repetida.

CREATE TABLE accounts (
    id           UUID          PRIMARY KEY,
    holder_name  VARCHAR(120)  NOT NULL,
    document     VARCHAR(14)   NOT NULL UNIQUE,
    branch       VARCHAR(4)    NOT NULL,
    number       VARCHAR(12)   NOT NULL UNIQUE,
    pix_key      VARCHAR(77)   NOT NULL UNIQUE,
    balance      NUMERIC(19,2) NOT NULL,
    status       VARCHAR(16)   NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_accounts_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
);

CREATE TABLE transfers (
    id                UUID          PRIMARY KEY,
    end_to_end_id     VARCHAR(32)   NOT NULL UNIQUE,
    idempotency_key   UUID          NOT NULL,
    request_hash      VARCHAR(64)   NOT NULL,
    source_account_id UUID          NOT NULL REFERENCES accounts (id),
    target_account_id UUID          NOT NULL REFERENCES accounts (id),
    amount            NUMERIC(19,2) NOT NULL,
    description       VARCHAR(140),
    status            VARCHAR(16)   NOT NULL,
    rejection_reason  VARCHAR(32),
    created_at        TIMESTAMPTZ   NOT NULL,
    -- a chave de idempotência é o que impede a mesma ordem de virar dois débitos
    CONSTRAINT uk_transfers_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_transfers_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transfers_distinct_accounts CHECK (source_account_id <> target_account_id),
    CONSTRAINT ck_transfers_status CHECK (status IN ('COMPLETED', 'REJECTED')),
    CONSTRAINT ck_transfers_rejection CHECK (
        (status = 'REJECTED' AND rejection_reason IS NOT NULL)
        OR (status = 'COMPLETED' AND rejection_reason IS NULL)
    )
);

CREATE INDEX ix_transfers_source_created ON transfers (source_account_id, created_at DESC);
CREATE INDEX ix_transfers_target_created ON transfers (target_account_id, created_at DESC);

CREATE TABLE ledger_entries (
    id                      UUID          PRIMARY KEY,
    account_id              UUID          NOT NULL REFERENCES accounts (id),
    counterparty_account_id UUID          NOT NULL REFERENCES accounts (id),
    transfer_id             UUID          NOT NULL REFERENCES transfers (id),
    type                    VARCHAR(8)    NOT NULL,
    amount                  NUMERIC(19,2) NOT NULL,
    balance_after           NUMERIC(19,2) NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_ledger_type CHECK (type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_amount_positive CHECK (amount > 0),
    -- cada transferência gera exatamente um débito e um crédito
    CONSTRAINT uk_ledger_transfer_type UNIQUE (transfer_id, type)
);

CREATE INDEX ix_ledger_account_created ON ledger_entries (account_id, created_at DESC, id);
