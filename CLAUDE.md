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

## Aktueller Stand

Phase 1 (RecipeCard eingebunden) ist fertig. Nächste Schritte laut
Implementierungs-Reihenfolge im Design-Dokument: Phase 1b (Markt-
Verkaufsgebühr), 1c (Rezept-Varianten + Bake-Timer), 1d (Hof-Grid als
Hauptansicht).

Offene Issues im Repo (#19, #21, #24, #14) bei Gelegenheit mit einplanen.
