-- Contas de demonstração. Só entram com o perfil "demo" (ver application.yml),
-- nunca num ambiente que guarde dados de verdade. Migração repetível e
-- idempotente: roda depois das versionadas e não duplica nem sobrescreve saldos.

INSERT INTO accounts (id, holder_name, document, branch, number, pix_key, balance, status, version, created_at, updated_at) VALUES
    ('0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1001', 'Leonardo Barros',   '11144477700', '0001', '10001-7', 'leonardo@motor.dev',  25000.00, 'ACTIVE',  0, now(), now()),
    ('0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1002', 'Ana Souza',         '52998224700', '0001', '10002-5', 'ana@motor.dev',        3200.50, 'ACTIVE',  0, now(), now()),
    ('0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1003', 'Carlos Lima',       '39053344700', '0001', '10003-3', '+5583999990003',        870.00, 'ACTIVE',  0, now(), now()),
    ('0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1004', 'Padaria Sol Nasce', '12345678000100', '0002', '20004-1', 'pix@padariasol.dev',  15400.00, 'ACTIVE',  0, now(), now()),
    ('0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1005', 'Conta Bloqueada',   '86288366700', '0002', '20005-9', 'bloqueada@motor.dev',   100.00, 'BLOCKED', 0, now(), now())
ON CONFLICT (id) DO NOTHING;

-- Usuários de demonstração. Senha de todos: motor123 (hash BCrypt, custo 10).
INSERT INTO users (id, email, password_hash, account_id, created_at) VALUES
    ('7d1c2a10-0000-4000-8000-000000000001', 'leonardo@motor.dev', '$2a$10$qjgaAvtRye0YALft/Bk7Cupwmeacr.4r6ybHk.u4/eYLKqIxjbUCK', '0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1001', now()),
    ('7d1c2a10-0000-4000-8000-000000000002', 'ana@motor.dev',      '$2a$10$qjgaAvtRye0YALft/Bk7Cupwmeacr.4r6ybHk.u4/eYLKqIxjbUCK', '0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1002', now()),
    ('7d1c2a10-0000-4000-8000-000000000003', 'carlos@motor.dev',   '$2a$10$qjgaAvtRye0YALft/Bk7Cupwmeacr.4r6ybHk.u4/eYLKqIxjbUCK', '0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1003', now()),
    ('7d1c2a10-0000-4000-8000-000000000004', 'padaria@motor.dev',  '$2a$10$qjgaAvtRye0YALft/Bk7Cupwmeacr.4r6ybHk.u4/eYLKqIxjbUCK', '0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1004', now()),
    ('7d1c2a10-0000-4000-8000-000000000005', 'bloqueada@motor.dev','$2a$10$qjgaAvtRye0YALft/Bk7Cupwmeacr.4r6ybHk.u4/eYLKqIxjbUCK', '0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1005', now())
ON CONFLICT (id) DO NOTHING;
