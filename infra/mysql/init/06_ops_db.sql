USE ops_db;

CREATE TABLE IF NOT EXISTS service_metric_snapshots (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    service_name    VARCHAR(64)     NOT NULL,
    status          VARCHAR(8)      NOT NULL,   -- UP | DOWN
    request_count   INT UNSIGNED    NOT NULL DEFAULT 0,
    error_count     INT UNSIGNED    NOT NULL DEFAULT 0,
    avg_latency_ms  DECIMAL(10,2)   NOT NULL DEFAULT 0,
    captured_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_service_time (service_name, captured_at)
);
