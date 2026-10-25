-- Pix para outro banco: o SPI decide depois, então a transferência nasce PENDING.
--
-- Enquanto o SPI não responde, o valor fica RESERVADO (saldo disponível = saldo
-- menos os Pix pendentes), sem lançamento no livro-razão. Confirmado (pacs.002 ACSC),
-- vira um débito na conta do cliente e um crédito na conta de liquidação (a conta PI
-- do banco no Banco Central). Recusado (RJCT), a reserva é liberada: a compensação
-- da saga é não mexer em dinheiro que nunca saiu.

ALTER TABLE transfers DROP CONSTRAINT ck_transfers_status;
ALTER TABLE transfers ADD CONSTRAINT ck_transfers_status CHECK (status IN ('PENDING', 'COMPLETED', 'REJECTED'));
ALTER TABLE transfers DROP CONSTRAINT ck_transfers_rejection;
ALTER TABLE transfers ADD CONSTRAINT ck_transfers_rejection CHECK (
    (status = 'REJECTED' AND rejection_reason IS NOT NULL)
    OR (status IN ('PENDING', 'COMPLETED') AND rejection_reason IS NULL)
);

-- quem recebe num banco de fora: não tem conta aqui, então os dados vêm do DICT
ALTER TABLE transfers
    ADD COLUMN payee_ispb            VARCHAR(8),
    ADD COLUMN payee_institution     VARCHAR(80),
    ADD COLUMN payee_name            VARCHAR(120),
    ADD COLUMN payee_document        VARCHAR(14),
    ADD COLUMN payee_pix_key         VARCHAR(77),
    ADD COLUMN payee_branch          VARCHAR(4),
    ADD COLUMN payee_account_number  VARCHAR(12),
    ADD COLUMN spi_reason_code       VARCHAR(8),
    ADD COLUMN settled_at            TIMESTAMPTZ;

-- reservas: a soma dos pendentes de uma conta é lida a cada Pix, com a conta travada
CREATE INDEX ix_transfers_pending_source ON transfers (source_account_id) WHERE status = 'PENDING';

-- contas internas do banco (a de liquidação) não são de clientes nem têm chave Pix utilizável
ALTER TABLE accounts ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_type CHECK (type IN ('CUSTOMER', 'SETTLEMENT'));

INSERT INTO accounts (id, holder_name, document, branch, number, pix_key, balance, status, type, version, created_at, updated_at)
VALUES ('00000000-0000-4000-8000-00000000c0de', 'Liquidação Pix (conta PI no BCB)', '99999999000000', '0000', 'PI-99999999',
        'interno:liquidacao-spi', 0, 'ACTIVE', 'SETTLEMENT', 0, now(), now())
ON CONFLICT (id) DO NOTHING;

-- DICT simulado: chaves Pix de clientes de OUTRAS instituições (dados fictícios).
-- No mundo real, a consulta vai ao DICT do Banco Central.
CREATE TABLE dict_chaves_externas (
    pix_key        VARCHAR(77)   PRIMARY KEY,
    holder_name    VARCHAR(120)  NOT NULL,
    document       VARCHAR(14)   NOT NULL,
    ispb           VARCHAR(8)    NOT NULL,
    institution    VARCHAR(80)   NOT NULL,
    branch         VARCHAR(4)    NOT NULL,
    account_number VARCHAR(12)   NOT NULL
);

INSERT INTO dict_chaves_externas VALUES
    ('maria@bancoexemplo.dev',  'Maria Oliveira',  '74185296300', '88888888', 'Banco Exemplo',  '0101', '55501-2'),
    ('loja@cooperativa.dev',    'Loja da Esquina', '98765432000155', '77777777', 'Cooperativa Fictícia', '0007', '7001-0'),
    -- o banco recebedor recusa esta (conta encerrada): exercita a compensação
    ('encerrada@bancoexemplo.dev', 'Conta Encerrada', '15935745600', '88888888', 'Banco Exemplo', '0101', '55599-9');
