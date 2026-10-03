CREATE TABLE payments (
    id         BIGSERIAL PRIMARY KEY,
    -- UNIQUE: un cobro por orden. Es lo que vuelve idempotente POST /payments
    -- frente a los reintentos del orquestador (ver PaymentService.charge).
    order_id   BIGINT         NOT NULL UNIQUE,
    amount     NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    status     VARCHAR(20)    NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL
);
