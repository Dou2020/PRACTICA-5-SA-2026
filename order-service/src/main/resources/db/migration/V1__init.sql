CREATE TABLE orders (
    id             BIGSERIAL      PRIMARY KEY,
    customer       VARCHAR(80)    NOT NULL,
    sku            VARCHAR(40)    NOT NULL,
    quantity       INTEGER        NOT NULL CHECK (quantity > 0),
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    address        VARCHAR(255)   NOT NULL,
    status         VARCHAR(30)    NOT NULL,
    -- Por qué terminó cancelada: el código estable (OUT_OF_STOCK,
    -- PAYMENT_UNAVAILABLE, ...) y el mensaje ya redactado para el timeline.
    failure_code   VARCHAR(40),
    failure_reason VARCHAR(500),
    -- Ids remotos que devolvió cada paso. Las compensaciones que van por id
    -- (refund, cancelar envío) los necesitan; null = ese paso no llegó a
    -- confirmar. Sin FK: viven en otra base, de otro servicio.
    payment_id     BIGINT,
    reservation_id BIGINT,
    shipment_id    BIGINT,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL
);

-- El log de la saga: una fila por cada ejecución y cada compensación. Es el
-- estado persistido del patrón (el del Circuit Breaker vive en memoria) y el
-- timeline que muestra el frontend.
CREATE TABLE saga_steps (
    id         BIGSERIAL    PRIMARY KEY,
    order_id   BIGINT       NOT NULL REFERENCES orders (id),
    step       VARCHAR(20)  NOT NULL,
    action     VARCHAR(20)  NOT NULL,
    outcome    VARCHAR(20)  NOT NULL,
    detail     VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX saga_steps_order_id_idx ON saga_steps (order_id);
