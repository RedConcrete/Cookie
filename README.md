# Cookie Game

Ein Idle-Economy-Spiel. Spieler produzieren Zutaten, backen Kekse und handeln Ressourcen auf einem globalen spielergetriebenen Markt.

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

| Software | Version |
|---|---|
| Java (JDK) | 21+ |
| Node.js | 20+ |
| PostgreSQL | 16+ |

---

## Datenbank einrichten

Standard-Zugangsdaten (lokal):
```
Host:     localhost
Port:     5432
User:     postgres
Password: 1234
DB:       cookie
```

Datenbank anlegen: In pgAdmin → **Databases** → **Create** → Name: `cookie`

Schema wird automatisch per Hibernate `ddl-auto=update` beim ersten Backend-Start angelegt.

Oder manuell:
```bash
psql -U postgres -d cookie -f database/setup.sql
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
```

---

## Build & Start

### start.sh — Schnellstart

```bash
./start.sh
```

```
  Cookie Game — Dev Start
  ========================
  [1/3] Stoppe alte Prozesse ... OK
  [2/3] Backend starten      ... OK  (PID 1234)
  [3/3] Frontend starten     ... OK  (PID 1235)
  ========================
  Backend:  http://localhost:9876
  Frontend: http://localhost:5173
```

Logs: `.logs/backend.log` und `.logs/frontend.log`

---

### build.sh — Interaktives Menü

```bash
./build.sh
```

```
  1  Dev starten    (Backend + Frontend)
  2  Linux bauen    (AppImage)
  3  Windows bauen  (.exe Installer)
  4  Beides bauen
  5  Docker starten
  6  Beenden
```

**Windows-Build** direkt auf Windows:
```powershell
cd frontend
npm run build:win
```

Build-Ausgabe in `frontend/release/`:

| Plattform | Datei |
|---|---|
| Windows | `Cookie Setup x.x.x.exe` (NSIS Installer) |
| Linux | `Cookie-x.x.x.AppImage` |

---

### Docker

```bash
docker compose up --build
docker compose down        # stoppen
docker compose down -v     # + Datenbank löschen
```

---

### Manuell

```bash
# Backend
cd backend/cookie-server-spring-boot
./mvnw spring-boot:run

# Frontend
cd frontend
npm install
npm run dev              # Browser (http://localhost:5173)
npm run electron:dev     # Electron Desktop-Fenster
```

---

## API

Basis-URL: `http://localhost:9876`

### Spieler / Spiel

| Method | Endpoint | Beschreibung |
|---|---|---|
| `GET` | `/api/v1/game/init/{steamId}` | Spieler + Marktdaten laden |
| `POST` | `/api/v1/game/harvest/{steamId}` | Ressource ernten |
| `POST` | `/api/v1/game/produce/{steamId}` | Ressource produzieren |
| `POST` | `/api/v1/bake/start/{userId}` | Backvorgang starten |
| `GET` | `/api/v1/bake/status/{userId}` | Backstatus abrufen |
| `POST` | `/api/v1/bake/claim/{userId}` | Fertige Kekse abholen |

### Markt

| Method | Endpoint | Beschreibung |
|---|---|---|
| `GET` | `/api/v1/market/current` | Aktueller Markt-Snapshot |
| `GET` | `/api/v1/market/get/{amount}` | Letzte N Snapshots |
| `GET` | `/api/v1/market/history` | Vollständige Preis-Historie |
| `POST` | `/api/v1/market/buy/{userId}` | Ressource kaufen |

### Net Worth & Leaderboard

| Method | Endpoint | Beschreibung |
|---|---|---|
| `GET` | `/api/v1/leaderboard` | Rangliste aller Spieler |
| `GET` | `/api/v1/players/{steamId}/networth` | Net Worth eines Spielers |
| `GET` | `/api/v1/players/{steamId}/networth/history` | Net-Worth-Verlauf (aggregiert) |
| `GET` | `/api/v1/players/{steamId}/profile` | Vollständiges Spielerprofil |

### Prestige

| Method | Endpoint | Beschreibung |
|---|---|---|
| `GET` | `/api/v1/game/prestige/status/{userId}` | Prestige-Status |
| `POST` | `/api/v1/game/prestige/{userId}` | Prestige ausführen |

### WebSocket

```
ws://localhost:9876/ws-market
```

Sendet nach jedem Preis-Update an alle Clients:
```json
[{ "date": "2026-06-25T12:00:00", "sugarPrice": 1.04, "flourPrice": 1.52, ... }]
```

---

## Spielmechanik

### Ressourcen & Produktion
- **Cookies** — Hauptwährung
- **Zucker, Mehl, Eier, Butter, Schokolade, Milch** — handelbare Zutaten
- Ressourcen über Hover im Hof-Grid ernten oder über Gebäude automatisch produzieren

### Backen
Rezepte kombinieren Zutaten zu Cookies. Backvorgang hat Timer, fertige Kekse manuell abholen.

### Upgrades
Shop mit Upgrades für Produktionsboosts. Kosten in Cookies, wirken permanent.

### Prestige
Setzt Ressourcen zurück, erhöht dauerhaften Multiplikator. Freischalten ab Net-Worth-Schwelle.

### Net Worth
`Cookies + Ressourcenwert (zu Marktpreisen) + Upgrade-Ausgaben`

Net-Worth-Verlauf im Dialog: roh (<1h), minutenweise (1–24h), stündlich (>24h).

### Markt
Preise reagieren auf Angebot/Nachfrage. Kauf → Preis steigt, Verkauf → Preis sinkt.

---

## Steam Integration

Steam App ID: `2816100`

Electron lädt beim Start automatisch Steam via `steamworks.js`. Ohne Steam: `DEV_PLAYER_001` als Fallback.

Dev-Test außerhalb Steam: `frontend/steam_appid.txt` (enthält `2816100`) ablegen, dann `npm run electron:dev`.

---

## Frontend-Struktur

```
frontend/
├── electron/
│   ├── main.js          ← Electron Hauptprozess, Steam Auth, Backend-Start
│   └── preload.js       ← contextBridge API für Vue
├── src/
│   ├── App.vue          ← Root, Navigation, globale Dialoge
│   ├── views/
│   │   ├── MarketView.vue      ← Preisgraph + Inline-Handel
│   │   ├── IdleView.vue        ← Produktion
│   │   ├── FarmGridView.vue    ← Hof-Grid mit Gebäuden
│   │   ├── BakeView.vue        ← Backen
│   │   ├── PrestigeView.vue    ← Prestige
│   │   └── UpgradeShopView.vue ← Upgrade-Shop
│   ├── components/
│   │   ├── PriceChart.vue        ← Markt-Chart (Zoom/Pan, Toggle, %-Modus)
│   │   ├── NetWorthDialog.vue    ← Net-Worth-Dialog mit Verlaufsgraph
│   │   ├── MarketDialog.vue      ← Markt-Popup
│   │   ├── LeaderboardDialog.vue ← Rangliste
│   │   ├── PlayerProfileDialog.vue
│   │   ├── PrestigeDialog.vue
│   │   ├── UpgradeDialog.vue
│   │   ├── BakeDialog.vue
│   │   ├── ResourceBar.vue       ← Header-Ressourcenanzeige
│   │   └── CookieSpinner.vue     ← Lade-Animation
│   ├── stores/
│   │   ├── player.js    ← Pinia: Spielerdaten, Net-Worth-Polling (10s)
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
| Frontend | Vue 3, Pinia, Vue Router, Chart.js, chartjs-plugin-zoom |
| Desktop | Electron 30 |
| Build | Maven, Vite 5, electron-builder |
| Steam | steamworks.js 0.4 |
