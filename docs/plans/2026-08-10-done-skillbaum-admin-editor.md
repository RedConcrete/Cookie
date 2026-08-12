# ✅ Skill-Baum Admin-Editor: Nodes draggen + Verbindungen setzen/löschen

> **Status:** ✅ Umgesetzt (2026-08-10, Commit 7124ec6; seither um
> Panning-Fixes (851ea47) und einen Effekt-Editor im Info-Panel erweitert).

## Context

Der Skill-Baum wird radial per Formel positioniert (`SkillTreeService.buildNodes()`,
11 Branches auf einem Kreis, Radien 150/300/450/600). Bei Screenshots überlappen
Nodes/Labels sichtbar — jede neue Node/Branch erfordert manuelles Nachrechnen von
Winkel/Radius in Java-Kommentaren, das ist fehleranfällig und pro Season/Fix mühsam.

Ziel: ein Admin-Tool zum manuellen Verschieben von Nodes per Drag (x/y landet
direkt in der DB, bleibt nach Neustart erhalten — `seedTree()` überschreibt
bestehende Zeilen nie) sowie zum Setzen/Löschen von Verbindungen (Edges)
zwischen Nodes. Werte-Editing (Name/Effekte) ist bewusst nicht Teil dieses
Plans — das Backend kann das bereits (`PUT /skilltree/nodes/{id}`), UI dafür
kommt später als eigener Schritt.

Zugriff: reaktivierter `isDev`-Menüpunkt im Hamburger-Menü (`FarmGridView.vue`),
gleiches Muster wie das am 2026-08-06 gelöschte `AdminDialog.vue` — nur für
`DEV_PLAYER_001` sichtbar.

## Umsetzung

**Backend** (`AdminConfigController.java`):
- `SkillEdgeRepository` injiziert.
- `GET/POST/DELETE /api/v1/admin/skilltree/edges` (Validierung: beide
  Node-IDs müssen existieren, keine Self-Loops, 409 bei Duplikat).
- `PUT /skilltree/nodes/{id}` setzt jetzt auch `requiresAllPrereqs`.

**Frontend**:
- `api.js`: `adminListSkillNodes/Edges`, `adminUpdateSkillNode`,
  `adminCreateSkillEdge`, `adminDeleteSkillEdge`.
- Neue Komponente `SkillTreeAdminDialog.vue` (eigenständig, nicht
  `SkillTreeView.vue` wiederverwendet) — Pan/Zoom-Canvas 1:1 aus dem
  Spieler-Baum übernommen. Node-Drag vs. Klick über Bewegungs-Schwelle
  (5px) unterschieden; Drag speichert sofort per PUT, Klick im
  "Verbinden"-Modus verknüpft zwei Nodes per POST, Klick auf eine Kante
  löscht sie sofort per DELETE. Kein separater Save-Button.
- Hamburger-Menü-Eintrag "SKILL-BAUM ADMIN" (isDev) in `FarmGridView.vue`.
- i18n: `skillTreeAdminDialog.json` (de/en) + neuer Key in `farmGridView.json`.

## Gefundener Bug (behoben)

Der Dialog-Root hat `@mousemove.stop` (verhindert Bubbling zum
HUD-Pan-Handler der dahinterliegenden `FarmGridView`). Das hat auch die
eigenen `window.addEventListener('mousemove', ...)`-Listener des
Node-Drags stumm blockiert (Bubble-Phase erreicht `window` nie, wenn ein
Vorfahre `stopPropagation()` aufruft). Fix: Drag-Listener im Capture-Modus
registrieren (`addEventListener(..., true)`), der läuft vor dem Stop.

## Verification

End-to-End per Playwright (`playwright-core` + gecachtes Chromium unter
`/var/cache/ms-playwright`) gegen die laufenden Dev-Server getestet:
Menüeintrag erscheint, Dialog öffnet fullscreen mit Pan/Zoom, Node-Drag
speichert Position (nach Reload bestätigt persistent), Verbinden-Modus
erstellt neue Kante (+ 409-Fehlerfall bei Duplikat korrekt abgefangen),
Klick auf Kante löscht sie sofort (nach Reload bestätigt persistent),
keine Konsolenfehler. `npm run check:palette` bleibt grün (keine neuen
Farben).

## Nicht im Scope

- Werte-Editor (Name/Beschreibung/Effekte/Branch/Tier).
- Neue Nodes anlegen/löschen.
