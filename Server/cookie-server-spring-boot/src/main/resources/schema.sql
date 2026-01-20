-- Cookie Database Schema
-- PostgreSQL Script

-- Datenbank erstellen (falls noch nicht vorhanden)
-- Hinweis: Diesen Befehl separat als Superuser ausführen
-- CREATE DATABASE cookie;

-- Verbindung zur Datenbank herstellen
-- \c cookie

-- Benutzer erstellen (falls noch nicht vorhanden)
-- CREATE USER belos WITH PASSWORD 'belos';
-- GRANT ALL PRIVILEGES ON DATABASE cookie TO belos;

-- ============================================
-- Tabellen löschen (falls vorhanden)
-- ============================================
DROP TABLE IF EXISTS market CASCADE;
DROP TABLE IF EXISTS market_stock CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- ============================================
-- Users Tabelle
-- ============================================
CREATE TABLE users (
    steam_id VARCHAR(255) PRIMARY KEY,
    token VARCHAR(255),
    cookies DOUBLE PRECISION DEFAULT 0,
    sugar DOUBLE PRECISION DEFAULT 0,
    flour DOUBLE PRECISION DEFAULT 0,
    eggs DOUBLE PRECISION DEFAULT 0,
    butter DOUBLE PRECISION DEFAULT 0,
    chocolate DOUBLE PRECISION DEFAULT 0,
    milk DOUBLE PRECISION DEFAULT 0
);

-- Index für schnellere Token-Suche
CREATE INDEX idx_users_token ON users(token);

-- ============================================
-- Market Tabelle (nur Preis-Historie)
-- ============================================
CREATE TABLE market (
    id VARCHAR(255) PRIMARY KEY,
    date TIMESTAMP NOT NULL,
    sugar_price DOUBLE PRECISION DEFAULT 0,
    flour_price DOUBLE PRECISION DEFAULT 0,
    eggs_price DOUBLE PRECISION DEFAULT 0,
    butter_price DOUBLE PRECISION DEFAULT 0,
    chocolate_price DOUBLE PRECISION DEFAULT 0,
    milk_price DOUBLE PRECISION DEFAULT 0
);

-- Index für schnellere Datumsabfragen
CREATE INDEX idx_market_date ON market(date DESC);

-- ============================================
-- Market Stock Tabelle (Singleton - aktueller Lagerbestand)
-- ============================================
CREATE TABLE market_stock (
    id VARCHAR(255) PRIMARY KEY DEFAULT 'SINGLETON',
    sugar_stock DOUBLE PRECISION DEFAULT 1000,
    flour_stock DOUBLE PRECISION DEFAULT 1000,
    eggs_stock DOUBLE PRECISION DEFAULT 1000,
    butter_stock DOUBLE PRECISION DEFAULT 1000,
    chocolate_stock DOUBLE PRECISION DEFAULT 1000,
    milk_stock DOUBLE PRECISION DEFAULT 1000
);

-- ============================================
-- Berechtigungen für Benutzer belos
-- ============================================
GRANT ALL PRIVILEGES ON TABLE users TO belos;
GRANT ALL PRIVILEGES ON TABLE market TO belos;
GRANT ALL PRIVILEGES ON TABLE market_stock TO belos;

-- ============================================
-- Optionale Beispieldaten
-- ============================================

-- Beispiel Marktdaten einfügen
INSERT INTO market (id, date, sugar_price, flour_price, eggs_price, butter_price, chocolate_price, milk_price)
VALUES
    (gen_random_uuid()::text, NOW(), 1.50, 2.00, 3.50, 4.00, 5.50, 1.75);

-- Initialer Market Stock (Singleton)
INSERT INTO market_stock (id, sugar_stock, flour_stock, eggs_stock, butter_stock, chocolate_stock, milk_stock)
VALUES
    ('SINGLETON', 1000, 1000, 1000, 1000, 1000, 1000);

-- Beispiel Benutzer (auskommentiert)
-- INSERT INTO users (steam_id, token, cookies, sugar, flour, eggs, butter, chocolate, milk)
-- VALUES ('76561198012345678', 'sample-token-123', 100.0, 50.0, 50.0, 30.0, 25.0, 20.0, 40.0);

COMMENT ON TABLE users IS 'Speichert Benutzerinformationen und Ressourcen-Inventar';
COMMENT ON TABLE market IS 'Speichert historische Marktpreise für Ressourcen';
COMMENT ON TABLE market_stock IS 'Speichert den aktuellen Markt-Lagerbestand (Singleton)';

COMMENT ON COLUMN users.steam_id IS 'Steam-Benutzer-ID (Primärschlüssel)';
COMMENT ON COLUMN users.cookies IS 'Anzahl der Cookies des Benutzers';
COMMENT ON COLUMN market.date IS 'Zeitstempel der Marktdaten';
COMMENT ON COLUMN market_stock.id IS 'Singleton-ID (immer SINGLETON)';
