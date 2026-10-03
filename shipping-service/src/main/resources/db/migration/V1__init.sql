CREATE TABLE shipments (
    id            BIGSERIAL    PRIMARY KEY,
    -- UNIQUE: un envío por orden (idempotencia frente a reintentos).
    order_id      BIGINT       NOT NULL UNIQUE,
    address       VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    tracking_code VARCHAR(40),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);
