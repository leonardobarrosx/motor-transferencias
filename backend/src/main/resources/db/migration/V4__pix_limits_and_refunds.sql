-- Limites Pix por período (Resolução BCB nº 1/2020 e regras de segurança de 2021):
-- um teto para o período diurno (06h–20h) e outro, menor, para o noturno (20h–06h).
ALTER TABLE accounts
    ADD COLUMN daytime_pix_limit   NUMERIC(19,2) NOT NULL DEFAULT 5000.00,
    ADD COLUMN nighttime_pix_limit NUMERIC(19,2) NOT NULL DEFAULT 1000.00,
    ADD CONSTRAINT ck_accounts_limits_positive CHECK (daytime_pix_limit > 0 AND nighttime_pix_limit > 0);

-- Devolução: uma transferência nova, no sentido contrário, que aponta para a original.
-- O livro-razão nunca é editado: estornar é lançar de novo.
ALTER TABLE transfers
    ADD COLUMN kind                 VARCHAR(16) NOT NULL DEFAULT 'PIX',
    ADD COLUMN original_transfer_id UUID REFERENCES transfers (id),
    ADD COLUMN refund_reason        VARCHAR(24),
    ADD CONSTRAINT ck_transfers_kind CHECK (kind IN ('PIX', 'REFUND')),
    ADD CONSTRAINT ck_transfers_refund_points_to_original
        CHECK ((kind = 'REFUND') = (original_transfer_id IS NOT NULL));

CREATE INDEX ix_transfers_original ON transfers (original_transfer_id) WHERE original_transfer_id IS NOT NULL;
