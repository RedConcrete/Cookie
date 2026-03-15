# Cookie Game

Ein Idle-Economy-Spiel. Spieler produzieren Cookies aus Zutaten und handeln Ressourcen auf einem globalen Spieler-getriebenen Markt.

---

## Projektstruktur

```
Cookie/
├── Server/
│   └── cookie-server-spring-boot/   ← Java Spring Boot Backend (Port 9876)
├── frontend/                         ← Vue 3 + Electron Frontend (Port 5173)
├── database/
│   └── setup.sql                     ← Datenbank-Schema (einmalig ausführen)
├── start.bat                         ← Startet Backend + Frontend
└── README.md
```

---

## Voraussetzungen

| Software | Version | Download |
|---|---|---|
| Java (JDK) | 21+ | https://adoptium.net |
| Node.js | 20+ | https://nodejs.org |
| PostgreSQL | 16+ | https://www.postgresql.org |

---

## Datenbank einrichten

### 1. PostgreSQL läuft auf Port 5432

Standard-Zugangsdaten (lokal):
```
Host:     localhost
Port:     5432
User:     postgres
Password: (dein postgres-Passwort)
DB:       cookie
```

### 2. Datenbank anlegen

In pgAdmin: Rechtsklick auf **Databases** → **Create** → **Database** → Name: `cookie`

### 3. Schema einrichten

Option A — Automatisch (beim ersten Backend-Start via Hibernate `ddl-auto=update`)

Option B — Manuell in pgAdmin Query Tool:
```sql
-- Inhalt von database/setup.sql einfügen und ausführen
```

Oder via psql:
```bash
psql -U postgres -d cookie -f database/setup.sql
```

### Datenbank-Schema

**`players`** — Spielerdaten
```sql
steamid TEXT PRIMARY KEY
token TEXT
cookies FLOAT   -- Hauptwährung
sugar FLOAT
flour FLOAT
eggs FLOAT
butter FLOAT
chocolate FLOAT
milk FLOAT
```

**`market`** — Preis-Historie (jeder Eintrag = ein Snapshot)
```sql
id TEXT PRIMARY KEY
date TIMESTAMP
sugar_price FLOAT
flour_price FLOAT
eggs_price FLOAT
butter_price FLOAT
chocolate_price FLOAT
milk_price FLOAT
```

**`market_stock`** — Markt-Lagerbestand (Singleton, immer 1 Zeile)
```sql
id TEXT PRIMARY KEY DEFAULT 'SINGLETON'
sugar_stock FLOAT
flour_stock FLOAT
eggs_stock FLOAT
butter_stock FLOAT
chocolate_stock FLOAT
milk_stock FLOAT
```

---

## Backend konfigurieren

Datei: `backend/cookie-server-spring-boot/src/main/resources/application.properties`

```properties
server.port=9876

spring.datasource.url=jdbc:postgresql://localhost:5432/cookie
spring.datasource.username=postgres
spring.datasource.password=DEIN_PASSWORT

# Marktpreise alle 2 Sekunden aktualisieren
market.update-interval-ms=2000

# Startpreis pro Ressource (Cookies)
market.initial-sugar-price=1.0
market.initial-flour-price=1.5
market.initial-eggs-price=2.0
market.initial-butter-price=3.0
market.initial-chocolate-price=5.0
market.initial-milk-price=1.2

# Neuer Spieler bekommt
player.initial-cookies=100.0
player.initial-sugar=1000
player.initial-flour=1000
player.initial-eggs=1000
player.initial-butter=1000
player.initial-chocolate=1000
player.initial-milk=1000
```

---

## Build & Start

### build.bat / build.sh — Interaktives Menü

```
==========================================
 Cookie Game — Build & Start
==========================================

  1  Dev starten    (Backend + Frontend)
  2  Windows bauen  (.exe Installer)
  3  Linux bauen    (AppImage)
  4  Beides bauen   (Windows + Linux)
  5  Docker starten
  6  Beenden
```

**Windows:** Doppelklick auf `build.bat`

**Linux / WSL:**
```bash
./build.sh
```

#### Build-Ausgabe

Nach Auswahl 2/3/4 liegt das fertige Paket in `frontend/release/`:

| Plattform | Datei |
|---|---|
| Windows | `Cookie Setup x.x.x.exe` (NSIS Installer) |
| Linux | `Cookie-x.x.x.AppImage` |

Der Build-Prozess:
1. Baut das Backend zu einer JAR-Datei (`mvnw package`)
2. Baut das Frontend mit Vite (`vite build`)
3. Packt alles mit electron-builder zusammen
4. Die Backend-JAR wird automatisch in die App eingebettet und beim Start geladen

---

### Docker (alle Services in Containern)

Startet PostgreSQL, Backend und Frontend automatisch in Containern.

**Voraussetzung:** Docker Desktop läuft

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:9876 |
| PostgreSQL | localhost:5432 |

Beim ersten Start wird die Datenbank automatisch eingerichtet (`database/setup.sql`).

Neu bauen nach Code-Änderungen:
```bash
docker compose up --build
```

Stoppen:
```bash
docker compose down
```

Datenbank komplett zurücksetzen:
```bash
docker compose down -v
```

---

### Schnellstart ohne Docker (Windows)

Doppelklick auf `start.bat` im Repo-Root.

Öffnet zwei Terminals:
- **Cookie Backend** — Spring Boot startet auf Port 9876
- **Cookie Frontend** — `npm install` (einmalig) + Vite auf Port 5173

Dann im Browser: **http://localhost:5173**

### Manuell

**Backend:**
```bash
cd backend/cookie-server-spring-boot
./mvnw spring-boot:run          # Linux/Mac
mvnw.cmd spring-boot:run        # Windows
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev                     # Browser (http://localhost:5173)
npm run electron:dev            # Electron Desktop-Fenster
```

---

## API

Basis-URL: `http://localhost:9876`

### Spieler

| Method | Endpoint | Beschreibung |
|---|---|---|
| `GET` | `/api/v1/users/{steamId}` | Spieler laden (auto-erstellt wenn neu) |
| `POST` | `/api/v1/users/{steamId}` | Spieler explizit anlegen |
| `DELETE` | `/api/v1/users/{steamId}` | Spieler löschen |

### Spiel

| Method | Endpoint | Beschreibung |
|---|---|---|
| `GET` | `/api/v1/game/init/{steamId}?marketHistoryAmount=20` | Spieler + Marktdaten in einem Call |
| `POST` | `/api/v1/game/produce/{steamId}` | Cookies backen |

### Markt

| Method | Endpoint | Beschreibung |
|---|---|---|
| `GET` | `/api/v1/market/all` | Alle Markt-Snapshots |
| `GET` | `/api/v1/market/get/{amount}` | Letzte N Snapshots |
| `POST` | `/api/v1/market` | Ressource kaufen oder verkaufen |

### WebSocket

```
ws://localhost:9876/ws-market
```

Sendet nach jedem Preis-Update automatisch an alle Clients:
```json
[
  {
    "date": "2026-03-15T12:00:00",
    "sugarPrice": 1.04,
    "flourPrice": 1.52,
    "eggsPrice": 1.98,
    "butterPrice": 3.01,
    "chocolatePrice": 4.97,
    "milkPrice": 1.19
  }
]
```

### Trade Request

```http
POST /api/v1/market
Content-Type: application/json

{
  "userId": "76561198xxxxxxxxx",
  "action": "BUY",
  "resource": {
    "name": "SUGAR",
    "amount": 10.0
  }
}
```

`action`: `BUY` oder `SELL`
`name`: `SUGAR` | `FLOUR` | `EGGS` | `BUTTER` | `CHOCOLATE` | `MILK`

### Produce Request

```http
POST /api/v1/game/produce/{steamId}
Content-Type: application/json

{ "amount": 3 }
```

Rezept (pro Batch): 10x jede Zutat → 100 Cookies

---

## Markt-System

Preise reagieren auf Angebot und Nachfrage:

- **Kauf** → Preis steigt, Lagerbestand sinkt
- **Verkauf** → Preis sinkt, Lagerbestand steigt
- **Zufällige Schwankung** alle 2 Sekunden (konfigurierbar)
- Preise werden per **WebSocket** live an alle Clients gepusht

Formel Handelseinfluss:
```
preisänderung = (menge / lagerbestand) × preis × tradeImpactMultiplier
```

---

## Spielmechanik

### Ressourcen
- **Cookies** — Hauptwährung (kaufen/verkaufen/backen)
- **Zucker, Mehl, Eier, Butter, Schokolade, Milch** — handelbare Zutaten

### Rezept (Basis)
```
10x Zucker + 10x Mehl + 10x Eier + 10x Butter + 10x Schokolade + 10x Milch
→ 100 Cookies
```

### Spielstart
Neuer Spieler erhält: `100 Cookies` + `1000x` jede Ressource

---

## Steam Integration

Steam App ID: `2816100`

In `electron/main.js` ist ein Stub hinterlegt. Für echte Steam-Auth:

```bash
cd frontend
npm install steamworks.js
```

Dann in `electron/main.js` den Kommentar-Block mit der echten Implementierung einkommentieren.

---

## Frontend-Struktur

```
frontend/
├── electron/
│   ├── main.js          ← Electron Hauptprozess, Steam IPC
│   └── preload.js       ← contextBridge API für Vue
├── src/
│   ├── App.vue          ← Root, Steam Auth, Navigation
│   ├── views/
│   │   ├── MarketView.vue    ← Preistabelle + Chart + Handel
│   │   └── IdleView.vue      ← Rezept + Produzieren
│   ├── components/
│   │   ├── ResourceBar.vue   ← Ressourcenanzeige (Header)
│   │   ├── MarketTable.vue   ← Klickbare Preistabelle
│   │   ├── PriceChart.vue    ← Chart.js Preisverlauf
│   │   ├── TradePanel.vue    ← Kaufen/Verkaufen
│   │   └── RecipeCard.vue    ← Backen-Interface
│   ├── stores/
│   │   ├── player.js    ← Pinia: Spielerdaten
│   │   └── market.js    ← Pinia: Marktpreise + Historie
│   └── services/
│       ├── api.js        ← Alle HTTP-Calls
│       └── websocket.js  ← WS-Client mit Auto-Reconnect
└── package.json
```

---

## Technologie-Stack

| Bereich | Technologie |
|---|---|
| Backend | Java 21, Spring Boot 3.2, Spring WebSocket |
| Datenbank | PostgreSQL 18, Hibernate JPA |
| Frontend | Vue 3, Pinia, Vue Router, Chart.js |
| Desktop | Electron |
| Build | Maven (Backend), Vite (Frontend) |
| Steam | steamworks.js (geplant) |
