-- Só Pix para outras instituições vai ao SPI, e quem recebe não tem conta aqui.
ALTER TABLE mensagens_spi ALTER COLUMN conta_recebedora DROP NOT NULL;
