-- ============================================================
-- Cookie Game – Datenbank Setup
-- Einmalig in pgAdmin ausführen (als postgres Superuser)
-- ============================================================

-- 1. Datenbank anlegen (falls noch nicht vorhanden)
-- In pgAdmin: Rechtsklick auf "Databases" -> Create -> Database
-- Name: cookie
-- Alternativ diesen Block als postgres-User ausführen:

-- CREATE DATABASE cookie;

-- ============================================================
-- 2. Dedizierten App-User anlegen
-- ============================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'cookie') THEN
        CREATE USER cookie WITH PASSWORD 'cookie';
    END IF;
END
$$;

GRANT ALL PRIVILEGES ON DATABASE cookie TO cookie;

-- ============================================================
-- 3. Tabellen anlegen (in der cookie Datenbank ausführen!)
-- ============================================================

-- Verbindung zur cookie-Datenbank wechseln:
-- \c cookie   (in psql)
-- In pgAdmin: Verbindung auf "cookie" DB wechseln, dann weiter

CREATE TABLE IF NOT EXISTS players (
    steamid   TEXT PRIMARY KEY,
    token     TEXT,
    cookies   FLOAT NOT NULL DEFAULT 0.0,
    sugar     FLOAT NOT NULL DEFAULT 0.0,
    flour     FLOAT NOT NULL DEFAULT 0.0,
    eggs      FLOAT NOT NULL DEFAULT 0.0,
    butter    FLOAT NOT NULL DEFAULT 0.0,
    chocolate FLOAT NOT NULL DEFAULT 0.0,
    milk      FLOAT NOT NULL DEFAULT 0.0
);

CREATE TABLE IF NOT EXISTS market (
    id              TEXT PRIMARY KEY,
    date            TIMESTAMP NOT NULL,
    sugar_price     FLOAT NOT NULL DEFAULT 0.0,
    flour_price     FLOAT NOT NULL DEFAULT 0.0,
    eggs_price      FLOAT NOT NULL DEFAULT 0.0,
    butter_price    FLOAT NOT NULL DEFAULT 0.0,
    chocolate_price FLOAT NOT NULL DEFAULT 0.0,
    milk_price      FLOAT NOT NULL DEFAULT 0.0
);

CREATE TABLE IF NOT EXISTS market_stock (
    id              TEXT PRIMARY KEY DEFAULT 'SINGLETON',
    sugar_stock     FLOAT NOT NULL DEFAULT 1000.0,
    flour_stock     FLOAT NOT NULL DEFAULT 1000.0,
    eggs_stock      FLOAT NOT NULL DEFAULT 1000.0,
    butter_stock    FLOAT NOT NULL DEFAULT 1000.0,
    chocolate_stock FLOAT NOT NULL DEFAULT 1000.0,
    milk_stock      FLOAT NOT NULL DEFAULT 1000.0
);

-- Berechtigungen für den cookie-User
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cookie;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO cookie;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO cookie;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO cookie;

-- ============================================================
-- Fertig. Backend kann jetzt gestartet werden.
-- ============================================================
