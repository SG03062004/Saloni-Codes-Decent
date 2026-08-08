USE ops_db;

CREATE TABLE IF NOT EXISTS service_metric_snapshots (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    service_name VARCHAR(64)     NOT NULL,
    status       VARCHAR(8)      NOT NULL,  -- UP | DOWN
    uptime_seconds INT UNSIGNED  NOT NULL,
    metrics_json JSON            NOT NULL,  -- full /metrics-lite payload
    collected_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_service_time (service_name, collected_at)
);
