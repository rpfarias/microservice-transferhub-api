CREATE TABLE transfers (
    id                UUID          PRIMARY KEY,
    source_account_id UUID          NOT NULL REFERENCES accounts (id),
    target_account_id UUID          NOT NULL REFERENCES accounts (id),
    amount            NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    status            VARCHAR(20)   NOT NULL,
    failure_reason    VARCHAR(255),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Consultas de histórico por conta (usadas no GET e, mais adiante, em relatórios).
CREATE INDEX idx_transfers_source_account ON transfers (source_account_id);
CREATE INDEX idx_transfers_target_account ON transfers (target_account_id);
