-- Runs automatically on first MySQL container start via /docker-entrypoint-initdb.d
-- One logical database per service (database-per-service pattern).

CREATE DATABASE IF NOT EXISTS restaurant_db;
CREATE DATABASE IF NOT EXISTS order_db;
CREATE DATABASE IF NOT EXISTS delivery_db;
CREATE DATABASE IF NOT EXISTS assignment_db;
CREATE DATABASE IF NOT EXISTS ops_db;
