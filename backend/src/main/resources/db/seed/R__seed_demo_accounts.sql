-- Contas de demonstração. Só entram com o perfil "demo" (ver application.yml),
-- nunca num ambiente que guarde dados de verdade. Migração repetível e
-- idempotente: roda depois das versionadas e não duplica nem sobrescreve saldos.

INSERT INTO accounts (id, holder_name, document, branch, number, pix_key, balance, status, version, created_at, updated_at) VALUES
    ('0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1001', 'Leonardo Barros',   '11144477735', '0001', '10001-7', 'leonardo@motor.dev',  25000.00, 'ACTIVE',  0, now(), now()),
    ('0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1002', 'Ana Souza',         '52998224725', '0001', '10002-5', 'ana@motor.dev',        3200.50, 'ACTIVE',  0, now(), now()),
    ('0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1003', 'Carlos Lima',       '39053344705', '0001', '10003-3', '+5583999990003',        870.00, 'ACTIVE',  0, now(), now()),
    ('0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1004', 'Padaria Sol Nasce', '12345678000195', '0002', '20004-1', 'pix@padariasol.dev',  15400.00, 'ACTIVE',  0, now(), now()),
    ('0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1005', 'Conta Bloqueada',   '86288366757', '0002', '20005-9', 'bloqueada@motor.dev',   100.00, 'BLOCKED', 0, now(), now())
ON CONFLICT (id) DO NOTHING;
