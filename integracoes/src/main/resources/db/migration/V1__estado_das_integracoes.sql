-- Banco do motor-integracoes. Separado do banco do motor: cada serviço é dono dos próprios dados.

-- Idempotent Consumer do Camel: eventIds já processados. Em memória, um restart
-- esquecia tudo e um evento reentregue pelo Kafka notificava de novo.
CREATE TABLE eventos_processados (
    event_id       VARCHAR(64)  PRIMARY KEY,
    processado_em  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Notificações produzidas. A UNIQUE torna o registro idempotente por si só:
-- mesmo que o evento passe duas vezes, cada parte recebe uma notificação por transferência.
CREATE TABLE notificacoes (
    id             UUID          PRIMARY KEY,
    account_id     UUID          NOT NULL,
    destinatario   VARCHAR(160)  NOT NULL,
    titulo         VARCHAR(80)   NOT NULL,
    mensagem       VARCHAR(500)  NOT NULL,
    transfer_id    UUID          NOT NULL,
    criada_em      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_notificacoes_transfer_conta UNIQUE (transfer_id, account_id)
);
CREATE INDEX ix_notificacoes_conta ON notificacoes (account_id, criada_em DESC);

-- Mensagens ISO 20022 entregues ao SPI simulado, com as contas envolvidas
-- para que cada cliente só enxergue as suas.
CREATE TABLE mensagens_spi (
    end_to_end_id     VARCHAR(32)    PRIMARY KEY,
    tipo              VARCHAR(16)    NOT NULL,
    valor             NUMERIC(19,2)  NOT NULL,
    arquivo           VARCHAR(160)   NOT NULL,
    conta_pagadora    UUID           NOT NULL,
    conta_recebedora  UUID           NOT NULL,
    entregue_em       TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_mensagens_spi_pagadora ON mensagens_spi (conta_pagadora);
CREATE INDEX ix_mensagens_spi_recebedora ON mensagens_spi (conta_recebedora);
