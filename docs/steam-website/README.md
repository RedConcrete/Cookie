# Steam-Seite — Assets & Hinweise

Store-Seite: https://store.steampowered.com/app/2816100/Cookie/

Alles, was für die Bearbeitung der Steam-Store-Seite gebraucht wird, liegt
hier. Screenshots wurden am 2026-08-16 per Playwright gegen den lokalen
Dev-Build erstellt (siehe Abschnitt "Wie entstanden" unten).

## Ordnerstruktur

```
docs/steam-website/
├── README.md              ← diese Datei
├── store-text.md           Beschreibungstext DE/EN (lang + kurz), Bild-Platzhalter
├── storepage_656428_all.json   Steamworks-Lokalisierungsexport, 16/30 Sprachen befüllt
├── screenshots/
│   ├── full/                1920×1080, Originalgröße
│   ├── 780px/                780px breit, gleiche Motive
│   └── for-upload/           auf Steam-taugliche Kurznamen umbenannte 780px-Bilder
└── trailer/
    ├── storyboard.md         Szenenplan fürs nächste Recording, vor jeder neuen Aufnahme lesen
    └── record-clips.js       Playwright-Recording-Skript (Cursor-Ring-Overlay, ProRes-Export)
```

Rohaufnahmen selbst (`trailer/raw-clips/*.mov`) liegen NICHT dauerhaft im
Repo — großes Binärmaterial, wird nach jeder Session wieder gelöscht und
bei Bedarf neu aufgenommen (`record-clips.js`).

## Bildgrößen-Regel (Steam)

- **Bilder/GIFs, die IM Beschreibungstext eingebettet werden** (Store-Page-
  Editor → "Description"-Feld): müssen exakt **780 px breit** sein →
  `screenshots/780px/`.
- **Bilder oben auf der Seite** (Screenshot-Galerie, Capsule-Vorschau):
  Breite egal → `screenshots/full/` (1920×1080) verwenden, Steam skaliert
  selbst.

## Screenshot-Übersicht

| Datei | Zeigt |
|---|---|
| `00-main-menu.png` | Hauptmenü, Titelscreen mit wandernden Bewohnern im Hintergrund |
| `01-farm-overview.png` | Hof-Grid-Übersicht: alle 6 Produktionsgebäude + Rathaus/Markt/Lager |
| `02-market.png` | Markt-Dialog: Live-Preisgraph (Angebot/Nachfrage), Kauf/Verkauf pro Rohstoff |
| `03-rathaus.png` | Rathaus-Dialog: Bürgerverwaltung, Einwohner den Gebäuden zuweisen |
| `04-backen.png` | Backstube: Rezeptauswahl mit Live-Kostenberechnung aus Marktpreisen |
| `05-lager.png` | Lager-Dialog: gemeinsamer Vorrats-Topf + Gebäude-Lager im Detail |
| `06-gebaeude-detail.png` | Gebäude-Detaildialog (Kuhstall): einzelne Bürger zuweisen, Ertrag/Lohn |
| `07-hud-menu.png` | Hamburger-Dropdown-Menü im HUD (Profil/Skill-Baum/Statistiken/Rangliste/Einstellungen) |
| `08-skilltree.png` | Passiver Skill-Baum (Path-of-Exile-artig), 4 Zweige + Keystones |
| `09-stats.png` | Statistik-Dialog: Wirtschafts-Überblick, Produktionsraten, aktive Boni, Lifetime-Zähler |
| `10-leaderboard.png` | Rangliste |
| `11-farm-final.png` | Hof-Grid, zentrierte Kamera (Alternative zu `01`) |

Empfehlung für die Screenshot-Galerie oben auf der Seite (Reihenfolge nach
Wirkung): `01` → `02` → `08` → `04` → `03`/`05` → `09`.

## Wie entstanden (für Reproduktion / neue Screenshots)

1. Lokalen Dev-Server gestartet (`scripts/start.sh`), `app.dev-mode=true`
   → Spieler `DEV_PLAYER_001` ohne Steam-Login erreichbar.
2. Spielstand künstlich befüllt (frischer Dev-Account hat sonst 0 Gebäude,
   400 Cookies, sieht für Screenshots leer aus) — direkt per SQL auf die
   lokale Postgres-DB (`cookie`), da es **bewusst keinen Admin-Cheat-
   Endpoint** mehr gibt (siehe `docs/ROADMAP.md` Abschnitt 0). Gesetzt:
   Cookies, Rohstoffe, Gebäude-Level/Worker, `owned_citizens`, Skillpunkte
   allokiert über die echte `/api/v1/skilltree/allocate`-API (respektiert
   Konnektivitätsregeln).
   **Fallstricke dabei (jedes Mal wieder aufgetreten, hier für die
   nächste Runde gesammelt):**
   - Das Hauptlager ist ein gemeinsamer Topf über alle 6 Rohstoffe
     (`totalResourceCap`) — sobald der voll ist, zeigen ALLE Gebäude
     gleichzeitig "INAKTIV" (kein Bug, by design). Fix: `lager`-Gebäude-
     Level großzügig hochsetzen (z. B. 15, `totalResourceCap` skaliert
     linear mit, siehe `BuildingService`), dann Rohstoffbestand niedrig
     halten.
   - Jedes Produktionsgebäude hat zusätzlich ein EIGENES lokales Lager
     (`storageCapacity`, unabhängig vom Hauptlager) — füllt sich über
     echte Realzeit zwischen Sessions von selbst wieder auf `pendingAmount
     == storageCapacity` (Produktionsrate läuft weiter, auch wenn niemand
     zuschaut) und zeigt dann ebenfalls "INAKTIV", auch wenn das
     Hauptlager längst nicht voll ist. Fix direkt vor jeder Aufnahme:
     `POST /api/v1/farm/buildings/collect/{userId}/{buildingId}` für jedes
     Gebäude aufrufen (Reihenfolge: erst Hauptlager-Headroom sicherstellen,
     s.o., sonst bricht das Collect am Hauptlager-Cap ab).
   - Ein abgeschlossener, aber nicht abgeholter Back-Auftrag blockiert die
     Rezeptauswahl im Backen-Dialog (zeigt nur noch den Claim-Screen,
     `BACKEN STARTEN`-Button dadurch nicht auffindbar). Vor jeder Aufnahme
     prüfen: `GET /api/v1/game/bake/status/{userId}` — falls
     `"done":true,"claimed":false`, erst `POST /api/v1/game/bake/claim/
     {userId}` aufrufen.
3. Playwright (Chromium headless, `npm install playwright` in einem
   Scratch-Verzeichnis, `npx playwright install chromium` — auf diesem
   System ohne `--with-deps`, da kein root/su verfügbar) hat die echte UI
   unter `http://localhost:5173` bedient: Hauptmenü → Spiel starten →
   Gebäude anklicken → Dialoge → Screenshot bei 1920×1080.
4. 780px-Varianten per Chromium-Downscale (`<img style="width:780px">` +
   `page.screenshot()`) aus den Originalen erzeugt, keine separate
   Aufnahme nötig.

**Wichtig:** der `DEV_PLAYER_001`-Spielstand auf der lokalen Dev-DB ist
seit dieser Session künstlich (Cookies/Gebäude/Skillpunkte hochgesetzt) —
kein echter Spielfortschritt, vor echten Balance-/Gameplay-Tests ggf.
zurücksetzen (`POST /api/v1/admin/reset/DEV_PLAYER_001`, siehe
`docs/ROADMAP.md`).

## Noch offen

- [x] Store-Beschreibungstext (kurz + lang, DE/EN) — `store-text.md`,
  2026-08-16
- [x] Lokalisierung ins Steamworks-JSON übernommen — `storepage_656428_all.json`,
  16/30 Sprachen (EN, DE + 14 EU-Sprachen), Rest bewusst offen gelassen
  (nicht EU)
- [ ] Trailer — Storyboard steht (`trailer/storyboard.md`), Aufnahme mit
  Cursor-Overlay ohne Text noch ausstehend, Schnitt macht der Dev selbst
  in DaVinci Resolve
- [ ] Capsule-Grafiken (Header/Small/Main Capsule, feste Maße von Valve
  vorgegeben, hier noch nicht recherchiert) — eigenes Artwork nötig,
  Gameplay-Screenshot reicht dafür nicht
- [ ] Tags/Kategorien im Steamworks-Backend setzen — Achtung:
  "MMO"/"Multiplayer" nicht setzen, solange kein für Reviewer auffindbarer
  Online-Einstiegspunkt existiert. Build-Review lehnte genau das ab
  (BuildID 24616131, 2026-08-16, war zu dem Zeitpunkt bereits ein alter
  Build, Ablehnungsgrund bleibt aber für jedes künftige Upload relevant).
