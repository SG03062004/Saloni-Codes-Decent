USE notification_db;

CREATE TABLE IF NOT EXISTS notifications (
    id           CHAR(36)    NOT NULL PRIMARY KEY,
    order_id     CHAR(36)    NOT NULL,
    event_type   VARCHAR(32) NOT NULL,   -- order-created | order-delivered
    channel      VARCHAR(16) NOT NULL,   -- mock | email | sms
    recipient    VARCHAR(255) NOT NULL,
    payload_json JSON        NOT NULL,
    sent_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order (order_id)
);
