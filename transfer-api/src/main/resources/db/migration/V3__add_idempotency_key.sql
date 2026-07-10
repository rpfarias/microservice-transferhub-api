-- Migração segura: adiciona a coluna nullable, preenche linhas existentes,
-- depois torna NOT NULL e cria a restrição UNIQUE. Assim não quebra se a
-- tabela já tiver dados (em produção real haveria linhas).

ALTER TABLE transfers ADD COLUMN idempotency_key VARCHAR(64);

-- Backfill: transferências antigas (pré-idempotência) recebem uma chave derivada do id.
UPDATE transfers SET idempotency_key = id::text WHERE idempotency_key IS NULL;

ALTER TABLE transfers ALTER COLUMN idempotency_key SET NOT NULL;

ALTER TABLE transfers ADD CONSTRAINT uk_transfers_idempotency_key UNIQUE (idempotency_key);
