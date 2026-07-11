CREATE TABLE settlement_records (
    id                UUID          PRIMARY KEY,
    transfer_id       UUID          NOT NULL UNIQUE,          -- idempotência do consumer
    target_account_id UUID          NOT NULL,
    amount            NUMERIC(19, 4) NOT NULL,
    outcome           VARCHAR(20)   NOT NULL,                 -- SETTLED | REJECTED
    rejection_reason  VARCHAR(255),
    processed_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Índice para a consulta do limite diário: soma por conta destino numa janela de tempo.
CREATE INDEX idx_settlement_target_processed ON settlement_records (target_account_id, processed_at);
