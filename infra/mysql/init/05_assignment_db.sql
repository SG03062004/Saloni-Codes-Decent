USE assignment_db;

-- driver_locations replaces Redis GEOSEARCH.
-- MySQL ST_Distance_Sphere on a SPATIAL INDEX is the nearest-driver query mechanism (see ADR-001).
CREATE TABLE IF NOT EXISTS driver_locations (
    driver_id   CHAR(36)   NOT NULL PRIMARY KEY,
    location    POINT      NOT NULL SRID 4326,
    is_available TINYINT(1) NOT NULL DEFAULT 1,
    updated_at  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    SPATIAL INDEX idx_location (location)
);
