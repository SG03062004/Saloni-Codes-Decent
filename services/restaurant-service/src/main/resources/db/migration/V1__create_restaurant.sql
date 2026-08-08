-- V1__create_restaurant.sql
-- avg_prep_time_minutes: used when publishing order-accepted.
-- DEFAULT 15 is a safe fallback for restaurants that haven't set their own value.

CREATE TABLE restaurants (
    id                    CHAR(36)     NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    is_open               TINYINT(1)   NOT NULL DEFAULT 1,
    avg_prep_time_minutes TINYINT      NOT NULL DEFAULT 15,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
);

CREATE TABLE menu_items (
    id            CHAR(36)     NOT NULL,
    restaurant_id CHAR(36)     NOT NULL,
    name          VARCHAR(255) NOT NULL,
    price_cents   INT UNSIGNED NOT NULL,
    available     TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    INDEX idx_restaurant_id (restaurant_id),
    CONSTRAINT fk_menu_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id)
);
