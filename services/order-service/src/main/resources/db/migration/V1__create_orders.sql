-- V1__create_orders.sql
-- MySQL-specific notes:
--   CHAR(36) for UUIDs — no native UUID type in MySQL 8.0 (use BIN(16) only if optimising for space)
--   DATETIME(6) for microsecond precision — TIMESTAMP has a 2038 problem and is timezone-converted
--   INT UNSIGNED for money cents — avoids negative values without a CHECK constraint
--   ON UPDATE CURRENT_TIMESTAMP(6) — MySQL extension, keeps updated_at accurate automatically
--   'order' is a reserved word in MySQL — table is named 'orders'

CREATE TABLE orders (
    id            CHAR(36)      NOT NULL,
    customer_id   CHAR(36)      NOT NULL,
    restaurant_id CHAR(36)      NOT NULL,
    status        VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    total_cents   INT UNSIGNED  NOT NULL,
    eta_minutes   SMALLINT      NULL,
    delivery_lat  DECIMAL(9,6)  NOT NULL,
    delivery_lng  DECIMAL(9,6)  NOT NULL,
    delivery_street VARCHAR(255) NOT NULL,
    delivery_city   VARCHAR(128) NOT NULL,
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_customer_id   (customer_id),
    INDEX idx_restaurant_id (restaurant_id),
    INDEX idx_status        (status)
);

CREATE TABLE order_items (
    id               CHAR(36)     NOT NULL,
    order_id         CHAR(36)     NOT NULL,
    menu_item_id     CHAR(36)     NOT NULL,
    quantity         TINYINT      NOT NULL,
    unit_price_cents INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
