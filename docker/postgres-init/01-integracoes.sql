-- Banco e usuário do motor-integracoes, no mesmo servidor Postgres do compose.
-- Bancos separados: um serviço não lê nem escreve as tabelas do outro.
-- (Só roda na criação do volume; para recriar: docker compose down -v)
CREATE USER integracoes WITH PASSWORD 'integracoes';
CREATE DATABASE integracoes OWNER integracoes;
