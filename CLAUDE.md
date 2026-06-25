# Cookie

Cookie-Clicker-artiges Idle-Game. Steam-Spiel, Vue 3 + Electron Frontend,
Java Spring Boot Backend, PostgreSQL, geteilter Online-Markt mit
Angebot/Nachfrage-Preisbildung.

## Design-Dokument

Vollständiges Game Design (Wirtschaft, Hof-Grid, Rezepte, Upgrades, Net
Worth, Prestige, Season): `docs/cookie-game-design.md`

Bei jeder Aufgabe zuerst dieses Dokument lesen, besonders Abschnitt
"Implementierungs-Reihenfolge" für den aktuellen Phasen-Stand.

## Stack

- Frontend: `frontend/` — Vue 3, Vue Router, Pinia, Vite, Electron
- Backend: `backend/cookie-server-spring-boot/` — Spring Boot, JPA, WebSocket
- DB: PostgreSQL (Docker Compose vorhanden)

## Konventionen

- Vollständige, eigenständige Dateien liefern statt Teil-Snippets bei
  strukturellen Änderungen
- Kommentare/Code-Erklärungen knapp halten (keine Füllwörter)
- Backend-Validierung ist Pflicht bei allem, was Ressourcen/Cookies bewegt
  (Client-Werte nie vertrauen)

## Aktueller Stand (2026-06-25)

Implementiert: Hof-Grid, Gebäude, Upgrades, Prestige, Backsystem, Rangliste,
Net-Worth-Dialog mit Verlaufsgraph (Zoom/Pan, Toggle, Live-Updates alle 10s/30s),
Markt-Preisgraph mit %-Modus und Zoom.

Nächste Schritte (nächste Session):
1. **Unity-Reste aufräumen** — `.meta`-Dateien (149 Stück), `.sfk`-Dateien,
   ungenutzte Sprites/Assets aus `src/assets/` entfernen
2. **Steam-Upload vorbereiten** — Build testen, Depots konfigurieren, Upload via
   SteamCMD (siehe Plan unten)
3. **Server-Deployment** — produktiven Server aufsetzen/aktualisieren

## Steam-Upload-Plan

```
1. Windows-Build erstellen:
   cd frontend && npm run build:win
   → frontend/release/Cookie Setup x.x.x.exe

2. SteamCMD-Upload:
   steamcmd +login <user>
            +run_app_build app_build_2816100.vdf
            +quit

3. app_build_2816100.vdf braucht:
   - AppID 2816100
   - Depot für Windows-Client (Electron + eingebettetes Backend-JAR)
   - Depot für Server-Binary (falls Server via Steam deployiert)

4. Testen: Steam-Branch "beta" zuerst, dann auf "default" promoten
```

## Server-Deployment-Plan

```
1. Backend-JAR bauen: ./mvnw package -DskipTests
2. JAR + application.properties auf Server kopieren
3. PostgreSQL auf Server einrichten (schema via Hibernate auto)
4. Systemd-Service oder Docker Compose starten
5. Firewall: Port 9876 öffnen (oder hinter Reverse Proxy)
6. app.dev-mode=false setzen (nur Steam-Auth erlaubt)
```

Offene Issues im Repo (#19, #21, #24, #14) bei Gelegenheit mit einplanen.
