CREATE TABLE IF NOT EXISTS drivers (
    id         CHAR(36)     NOT NULL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    is_active  TINYINT(1)   NOT NULL DEFAULT 1,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS deliveries (
    id           CHAR(36)    NOT NULL PRIMARY KEY,
    order_id     CHAR(36)    NOT NULL UNIQUE,
    driver_id    CHAR(36)    NOT NULL,
    status       VARCHAR(32) NOT NULL DEFAULT 'ASSIGNED',
    eta_minutes  SMALLINT    NULL,
    delivered_at DATETIME    NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_driver (driver_id)
);
