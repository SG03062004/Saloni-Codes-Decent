USE order_db;

CREATE TABLE IF NOT EXISTS orders (
    id            CHAR(36)     NOT NULL PRIMARY KEY,
    customer_id   CHAR(36)     NOT NULL,
    restaurant_id CHAR(36)     NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
                                -- PENDING | ACCEPTED | REJECTED | DRIVER_ASSIGNED | DELIVERED
    total_cents   INT UNSIGNED NOT NULL,
    eta_minutes   SMALLINT     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_customer (customer_id),
    INDEX idx_restaurant (restaurant_id)
);

CREATE TABLE IF NOT EXISTS order_items (
    id              CHAR(36)     NOT NULL PRIMARY KEY,
    order_id        CHAR(36)     NOT NULL,
    menu_item_id    CHAR(36)     NOT NULL,
    quantity        TINYINT      NOT NULL,
    unit_price_cents INT UNSIGNED NOT NULL,
    CONSTRAINT fk_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
