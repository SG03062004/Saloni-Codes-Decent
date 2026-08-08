USE restaurant_db;

CREATE TABLE IF NOT EXISTS restaurants (
    id         CHAR(36)    NOT NULL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    is_open    TINYINT(1)  NOT NULL DEFAULT 1,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS menu_items (
    id           CHAR(36)     NOT NULL PRIMARY KEY,
    restaurant_id CHAR(36)    NOT NULL,
    name         VARCHAR(255) NOT NULL,
    price_cents  INT UNSIGNED NOT NULL,
    available    TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT fk_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
);
