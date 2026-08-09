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
- Das gesamte Frontend-Aussehen (CSS, Vue-Komponenten, neue Pixel-Art) nutzt
  ausschließlich Farben aus der Fruitpunch24-Palette
  (`frontend/src/assets/colorpalate/fruitpunch24.hex`), siehe
  `docs/cookie-game-design.md` Abschnitt 8.1 — keine anderen Farben, auch
  keine rgba()/hsl()-Fremdfarben. Check: `cd frontend && npm run
  check:palette` (scannt `.vue`/`.css`/`.js` unter `src/` auf Hex-Werte
  außerhalb der Palette, exit 1 bei Verstoß). Bestehende Gebäude-SVGs sind
  Platzhalter und davon ausgenommen (siehe `docs/ROADMAP.md`).

## Aktueller Stand (2026-08-09)

Implementiert: Hof-Grid, Gebäude, Skill-Baum (Path-of-Exile-artiger Passiv-
Baum, ersetzt altes Upgrade-System), Prestige, Backsystem, Rangliste,
Net-Worth-Dialog mit Verlaufsgraph (Zoom/Pan, Toggle, Live-Updates alle 10s/30s),
Markt-Preisgraph mit %-Modus und Zoom, Season-Reset (manuell via Admin-Endpoint),
Lokalisierung DE/EN (vue-i18n, Sprachumschalter in den Einstellungen), custom
Pixel-Scrollbar (`PixelScrollBox.vue`) + animierte Lade-Anzeige
(`LoadingIndicator.vue`) in allen scrollenden Dialogen. Lohn skaliert live mit
Arbeiterzahl, Dispo-Kredit statt Komplett-Idle bei zu wenig Cookies (Zinsen,
reduzierbar über Skill-Baum-Zweig DISPO) + Abrechnungshistorie im Rathaus —
Details `docs/cookie-game-design.md` Abschnitt 5 + 9.

## Lokalisierung (i18n)

Frontend nutzt `vue-i18n` (Composition API, `legacy:false`). Setup:
`frontend/src/i18n/index.js`. Texte liegen als ein JSON-Paar pro Komponente
unter `frontend/src/i18n/locales/{de,en}/<namespace>.json` (Namespace =
Dateiname = Komponentenname in camelCase, automatisch per
`import.meta.glob` gemergt). Sprache umschaltbar über Einstellungen
(`SettingsDialog.vue`), Wahl wird in `localStorage` gespeichert.

Neue Texte: in der jeweiligen Komponente `const { t } = useI18n()`
verwenden, Key in beiden Locale-Dateien (de + en) ergänzen. Bei
Text-Daten außerhalb von `.vue`-Dateien (z. B. `buildingInfo.js`) den Key
in der Datenstruktur ablegen und erst in der konsumierenden Komponente
über `t()` auflösen (siehe `buildingTitle()`/`resourceLabel()` dort als
Vorbild) — reine JS-Module haben keinen eigenen i18n-Kontext.

**Vollständige Fix-/Roadmap-Liste (Bugs, Aufräumarbeiten, Build/Deployment,
Design-Doc-Pflege): `docs/ROADMAP.md`.** Bei jeder Aufgabe dort nachsehen, ob der
Punkt schon abgehakt ist — Duplizierung vermeiden, Datei ist die einzige
Quelle für offene Baustellen.

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

Offene GitHub-Issues: Status-Check in `docs/ROADMAP.md` Abschnitt 1, nicht
hier duplizieren.

## Skripte

Alle Build-/Start-Skripte liegen in `scripts/` (nicht im Repo-Root):
`scripts/start.sh`, `scripts/build.sh`, `scripts/docker-start.sh` (+ `.bat`-
Pendants für Windows ohne WSL).
