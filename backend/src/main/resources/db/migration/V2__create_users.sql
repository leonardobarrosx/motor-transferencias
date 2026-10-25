-- Quem acessa a conta. Um usuário por conta neste simulador; a senha só existe
-- como hash BCrypt, e o e-mail de login é independente da chave Pix.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    account_id    UUID         NOT NULL UNIQUE REFERENCES accounts (id),
    created_at    TIMESTAMPTZ  NOT NULL
);

-- e-mail único sem diferenciar maiúsculas: Ana@Motor.dev e ana@motor.dev são a mesma pessoa
CREATE UNIQUE INDEX uk_users_email_lower ON users (lower(email));
