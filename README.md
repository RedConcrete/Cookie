# Cookie Game

Ein Idle-Economy-Spiel. Spieler produzieren Zutaten und handeln Ressourcen auf einem globalen Spieler-getriebenen Markt.

---

## Projektstruktur

```
Cookie/
├── backend/
│   └── cookie-server-spring-boot/   ← Java Spring Boot Backend (Port 9876)
├── frontend/                         ← Vue 3 + Electron Frontend (Port 5173)
├── database/
│   └── setup.sql                     ← Datenbank-Schema (einmalig ausführen)
├── start.sh                          ← Schnellstart Backend + Frontend (WSL/Linux)
├── build.sh                          ← Interaktives Build-Menü (WSL/Linux)
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
Password: 1234
DB:       cookie
```

### 2. Datenbank anlegen

In pgAdmin: Rechtsklick auf **Databases** → **Create** → **Database** → Name: `cookie`

### 3. Schema einrichten

Option A — Automatisch (beim ersten Backend-Start via Hibernate `ddl-auto=update`)

Option B — Manuell via psql:
```bash
psql -U postgres -d cookie -f database/setup.sql
```

### Datenbank-Schema

**`players`** — Spielerdaten
```sql
steamid TEXT PRIMARY KEY
cookies FLOAT
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
spring.datasource.password=1234

# Dev-Modus: true = Browser-Zugriff erlaubt (DEV_PLAYER_001)
#            false = nur Electron/Steam erlaubt
app.dev-mode=true

# Marktpreise alle 2 Sekunden aktualisieren
market.update-interval-ms=2000

# Startpreise pro Ressource (Cookies)
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

### start.sh — Schnellstart mit Health-Check

```bash
./start.sh
```

Zeigt nur Status-Zusammenfassung — keine rohen Logs:
```
  Cookie Game — Dev Start
  ========================

  [1/3] Stoppe alte Prozesse ... OK
  [2/3] Backend starten      ... OK  (PID 1234)
  [3/3] Frontend starten     ... OK  (PID 1235)

  ========================
  Backend:  http://localhost:9876
  Frontend: http://localhost:5173
  Logs:     .logs/
  ========================
```

Logs landen in `.logs/backend.log` und `.logs/frontend.log`.

---

### build.sh — Interaktives Menü

```bash
./build.sh
```

```
==========================================
 Cookie Game — Build & Start
==========================================

  1  Dev starten    (Backend + Frontend)
  2  Linux bauen    (AppImage)
  3  Windows bauen  (.exe Installer)
  4  Beides bauen
  5  Docker starten
  6  Beenden
```

**Windows-Build** läuft direkt auf Windows (kein WSL nötig):
```powershell
cd frontend
npm run build:win
```

#### Build-Ausgabe

Nach Build liegt das fertige Paket in `frontend/release/`:

| Plattform | Datei |
|---|---|
| Windows | `Cookie Setup x.x.x.exe` (NSIS Installer) |
| Linux | `Cookie-x.x.x.AppImage` |

Der Build-Prozess:
1. Baut das Backend zu einer JAR (`mvnw package`)
2. Baut das Frontend mit Vite (`vite build`)
3. Packt alles mit electron-builder zusammen
4. Die Backend-JAR wird automatisch eingebettet und beim Start geladen

---

### Docker (alle Services in Containern)

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:9876 |
| PostgreSQL | localhost:5432 |

Stoppen:
```bash
docker compose down
```

Datenbank zurücksetzen:
```bash
docker compose down -v
```

---

### Manuell

**Backend:**
```bash
cd backend/cookie-server-spring-boot
./mvnw spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev              # Browser (http://localhost:5173)
npm run electron:dev     # Electron Desktop-Fenster
```

---

## API

Basis-URL: `http://localhost:9876`

### Config

| Method | Endpoint | Beschreibung |
|---|---|---|
| `GET` | `/api/v1/config` | Gibt `{"devMode": true/false}` zurück |

### Spieler

| Method | Endpoint | Beschreibung |
|---|---|---|
| `GET` | `/api/v1/users/{steamId}` | Spieler laden (auto-erstellt wenn neu) |
| `DELETE` | `/api/v1/users/{steamId}` | Spieler löschen |

### Spiel

| Method | Endpoint | Beschreibung |
|---|---|---|
| `GET` | `/api/v1/game/init/{steamId}?marketHistoryAmount=20` | Spieler + Marktdaten in einem Call |
| `POST` | `/api/v1/game/harvest/{steamId}` | Ressource ernten (+1) |

### Markt

| Method | Endpoint | Beschreibung |
|---|---|---|
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

### Harvest Request

```http
POST /api/v1/game/harvest/{steamId}
Content-Type: application/json

{ "resource": "SUGAR" }
```

---

## Markt-System

Preise reagieren auf Angebot und Nachfrage:

- **Kauf** → Preis steigt, Lagerbestand sinkt
- **Verkauf** → Preis sinkt, Lagerbestand steigt
- **Zufällige Schwankung** alle 2 Sekunden (konfigurierbar)
- Preise werden per **WebSocket** live an alle Clients gepusht
- Preisänderungen durch Trades werden **nicht** sofort gesendet — nur beim nächsten Scheduler-Tick

Formel Handelseinfluss:
```
preisänderung = (menge / lagerbestand) × preis × tradeImpactMultiplier
```

---

## Spielmechanik

### Ressourcen
- **Cookies** — Hauptwährung (kaufen/verkaufen)
- **Zucker, Mehl, Eier, Butter, Schokolade, Milch** — handelbare Zutaten

### Produktion
Im **Produktion**-Tab können Spieler durch Hover über eine Ressource diese ernten (+1/s solange gehovered).

### Rezept (geplant)
```
10x Zucker + 10x Mehl + 10x Eier + 10x Butter + 10x Schokolade + 10x Milch
→ 100 Cookies
```

### Spielstart
Neuer Spieler erhält: `100 Cookies` + `1000x` jede Ressource

---

## Steam Integration

Steam App ID: `2816100`

Electron lädt beim Start automatisch Steam via `steamworks.js`. Falls Steam nicht verfügbar ist (z.B. Dev-Umgebung), wird `DEV_PLAYER_001` als Fallback verwendet.

Für Dev-Tests außerhalb Steam: `frontend/steam_appid.txt` (enthält `2816100`) im `frontend/`-Verzeichnis ablegen, dann via `npm run electron:dev` starten.

---

## Frontend-Struktur

```
frontend/
├── electron/
│   ├── main.js          ← Electron Hauptprozess, Steam Auth, Backend-Start
│   └── preload.js       ← contextBridge API für Vue
├── src/
│   ├── App.vue          ← Root, Steam Auth, Navigation
│   ├── views/
│   │   ├── MarketView.vue    ← Hauptseite: Preistabelle + Chart + Inline-Handel
│   │   └── IdleView.vue      ← Produktion: Hex-Grid Ressourcen (Hover = Ernte)
│   ├── components/
│   │   ├── ResourceBar.vue   ← Ressourcenanzeige (Header)
│   │   ├── PriceChart.vue    ← Chart.js Preisverlauf
│   │   └── CookieSpinner.vue ← Lade-Animation (Sprite-Sheet)
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
| Datenbank | PostgreSQL 16+, Hibernate JPA |
| Frontend | Vue 3, Pinia, Vue Router, Chart.js |
| Desktop | Electron 30 |
| Build | Maven (Backend), Vite 5 (Frontend), electron-builder |
| Steam | steamworks.js 0.4 |
