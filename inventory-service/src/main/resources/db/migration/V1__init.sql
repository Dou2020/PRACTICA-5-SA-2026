CREATE TABLE products (
    sku   VARCHAR(40)  PRIMARY KEY,
    name  VARCHAR(120) NOT NULL,
    -- El CHECK es el último guardián contra sobreventa; el primero es el
    -- UPDATE condicional de ProductRepository.decrementStock.
    stock INTEGER      NOT NULL CHECK (stock >= 0)
);

CREATE TABLE reservations (
    id         BIGSERIAL   PRIMARY KEY,
    -- UNIQUE: una reserva por orden (idempotencia frente a reintentos).
    order_id   BIGINT      NOT NULL UNIQUE,
    sku        VARCHAR(40) NOT NULL REFERENCES products (sku),
    quantity   INTEGER     NOT NULL CHECK (quantity > 0),
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Catálogo de la demo. AGOTADO-01 existe para provocar el fallo de negocio
-- "sin stock" en el segundo paso de la saga (compensación: refund).
INSERT INTO products (sku, name, stock) VALUES
    ('LAPTOP-01',  'Laptop 14"',        50),
    ('MOUSE-01',   'Mouse inalámbrico', 100),
    ('TECLADO-01', 'Teclado mecánico',  30),
    ('AGOTADO-01', 'Monitor 4K',        0);
