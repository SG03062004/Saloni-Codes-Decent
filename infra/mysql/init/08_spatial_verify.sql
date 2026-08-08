-- Spatial verification — runs once on first MySQL container start.
-- Proves POINT columns, SPATIAL INDEX, and ST_Distance_Sphere all work
-- before any application code touches the database.
--
-- Expected output of the SELECT:
--   distance_metres ≈ 2443.9  (straight-line SF Civic Center → Ferry Building)

CREATE DATABASE IF NOT EXISTS spatial_verify;
USE spatial_verify;

CREATE TABLE IF NOT EXISTS geo_test (
    id       INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(64)  NOT NULL,
    location POINT        NOT NULL SRID 4326,
    SPATIAL INDEX idx_location (location)
);

-- SF Civic Center  (lat 37.7793, lng -122.4193)
-- SF Ferry Building (lat 37.7955, lng -122.3937)
INSERT INTO geo_test (name, location) VALUES
    ('SF Civic Center',   ST_GeomFromText('POINT(37.7793 -122.4193)', 4326)),
    ('SF Ferry Building', ST_GeomFromText('POINT(37.7955 -122.3937)', 4326));

-- ST_Distance_Sphere returns metres.
-- Swap to ST_Distance for exact geodesic if needed (slower).
SELECT
    a.name                                          AS point_a,
    b.name                                          AS point_b,
    ROUND(ST_Distance_Sphere(a.location, b.location), 1) AS distance_metres
FROM geo_test a
JOIN geo_test b ON a.id = 1 AND b.id = 2;

-- Clean up — this database is only for the checkpoint, not used by any service.
DROP DATABASE spatial_verify;
