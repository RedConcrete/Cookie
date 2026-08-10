# ⏳ Gebäude(-Käufe/-Upgrades) in Net Worth einrechnen

> **Status:** ⏳ Offen

## Context

Net Worth wird aktuell aus `cookies + resourceValue + skillTreeValue` berechnet
(`NetWorthService#calculateForUser`, siehe `docs/cookie-game-design.md` §10).
Geld, das in Gebäude gesteckt wurde (Kauf `0→1` und jedes Level-Up), fließt
nicht ein — dokumentierter, bekannter Gap in §12 des Design-Docs und in
`docs/ROADMAP.md`. Es gibt kein separates "Gebäude-Upgrade"-System mehr zu
berücksichtigen (das alte 3-Upgrade-Regal wurde bereits durch den Skill-Baum
ersetzt, siehe ROADMAP "Passiver Skill-Baum ersetzt Upgrade-System") — "Käufe
und Upgrades der Gebäude" heißt hier: Kaufpreis + alle Level-Up-Kosten über
den bestehenden `buyOrUpgrade`-Flow (`BuildingService`, Kosten = `baseCost ×
buildingCostGrowth^level`, exponentiell pro Stufe).

**Design-Entscheidung:** Der Geld-Wert eines Gebäudes wird **nicht** als neuer
persistenter Zähler auf `UserEntity` getrackt (anders als
`totalSkillPointCookiesSpent` beim Skill-Baum), sondern **aus Level +
bestehender Kostenformel abgeleitet** — Kosten pro Stufe sind deterministisch,
es gibt nichts zu tracken. Vorteil: `PlayerBuildingEntity`-Zeilen werden beim
Prestige bereits komplett gelöscht (`PrestigeService`,
`playerBuildingRepository.deleteByUserId`) — ein abgeleiteter Wert reset sich
dadurch automatisch mit, ohne dass man eine zusätzliche Zähler-Spalte manuell
zurücksetzen muss.

**Nebenbefund (User hat Fix mitbeauftragt):** Season-Reset (`SeasonService
#closeSeason`) löscht aktuell `player_buildings` nicht, obwohl Cookies,
Ressourcen, Skill-Baum und Bake-Jobs zurückgesetzt werden — bekannter,
dokumentierter Bug (§12 Design-Doc). Sobald Gebäude in Net Worth zählen, macht
dieser Bug den Season-Reset spürbar unfair (Gebäude-Wert würde über den Reset
hinweg bestehen bleiben, alles andere geht auf 0). Wird in diesem Zug
mitgefixt.

## Backend

**`BuildingService.java`**
- Neue private Helper-Methode `totalSpent(BuildingDef def, int level)`: Summe
  von `computeCost(def, i)` für `i = 0..level-1` (einfache Schleife, kein
  geschlossenes Geometrische-Reihe-Format nötig — Level bleiben klein, gleicher
  simpler Stil wie der Rest der Klasse). Gebäude mit `baseCost == 0` (Ofen)
  ergeben automatisch 0.
- Neue public Methode `getBuildingValue(String userId)`: summiert
  `totalSpent(def, level)` über alle `BUILDINGS`-Defs anhand der
  `ownedMap(userId)`-Level — genutzt von `NetWorthService` und
  `getStats()`/`PlayerStatsDto`.
- In `toDto(BuildingDef def, PlayerBuildingEntity ent)`: neues Feld
  `cookiesSpent = totalSpent(def, level)` setzen — für Frontend-seitige
  Live-Berechnung ohne Extra-Request (siehe unten).

**`PlayerBuildingDto.java`**
- Neues Feld `double cookiesSpent` + Getter/Setter.

**`NetWorthService.java`**
- `calculateForUser(UserEntity user, MarketEntity market)`: `double
  buildingValue = buildingService.getBuildingValue(user.getSteamId())`
  ergänzen, in die `netWorth`-Summe einrechnen, auf `LeaderboardEntryDto`
  setzen.
- `getStats(String userId)`: `dto.setBuildingValue(buildingService
  .getBuildingValue(userId))` ergänzen (analog zu den anderen
  Wirtschafts-Werten im Statistik-Dialog).

**`LeaderboardEntryDto.java`**, **`PlayerProfileDto.java`**,
**`PlayerStatsDto.java`**: je ein neues Feld `buildingValue` (double) +
Getter/Setter, gesetzt in `NetWorthService#calculateForUser` /
`#getProfile` / `#getStats`.

**`NetWorthHistoryEntity.java`** + **`NetWorthHistoryDto.java`**: neues Feld
`buildingValue`, in `NetWorthService#recordSnapshots()` beim Erstellen des
Snapshots und in `#aggregate()` beim Averaging ergänzen (exakt der gleiche
Pfad wie `skillTreeValue`).

⚠️ **Migrations-Falle (bereits einmal passiert, siehe ROADMAP.md):** Eine neue
primitive `double`-Spalte auf der bereits befüllten `networth_history`-Tabelle
lässt Hibernates `ddl-auto=update` mit `ALTER TABLE ... ADD COLUMN ... NOT
NULL` scheitern, sobald Bestandsdaten drin sind — genau der Bug, der beim
`upgradeValue`→`skillTreeValue`-Rename schon mal den 30s-Snapshot-Job dauerhaft
gecrasht hat. Fix (wie letztes Mal, DB ist laut Vereinbarung disposable):
nach dem Deploy einmalig `DROP TABLE networth_history;` — lokal und auf dem
Live-Beta-Server — Hibernate legt sie beim nächsten Start sauber neu an.
Als expliziter Schritt in der Deploy-Checkliste, kein Code-Fix nötig.

**`SeasonService.java`** (Season-Bug-Fix)
- `PlayerBuildingRepository` als Konstruktor-Dependency ergänzen (Pattern wie
  in `PrestigeService` bereits vorhanden).
- In `closeSeason()` neben `playerSkillNodeRepository.deleteAll();` und
  `bakeJobRepository.deleteAll();` ergänzen: `playerBuildingRepository
  .deleteAll();`. Vorgebaute Gebäude (Ofen/Rathaus/Markt/Lager) werden beim
  nächsten Login automatisch wieder auf Stufe 1 angelegt
  (`BuildingService#ensurePreBuiltBuildings`, existiert schon, wird von
  `GameController#initializeGame` bei jedem Login aufgerufen) — exakt das
  gleiche Verhalten wie der bestehende Prestige-Reset.

## Frontend

**`stores/player.js`**
- Neues Computed `nwBuildingValue = computed(() => ownedBuildings.value
  .reduce((s, b) => s + (b.cookiesSpent ?? 0), 0))` — rein aus bereits
  gehaltenem Client-State abgeleitet, gleiches Muster wie `nwResources`
  (Kommentar-Block dort referenziert bereits diese Philosophie). Kein neuer
  API-Call nötig: `ownedBuildings` wird schon bei `init()`, `loadBuildings()`
  und jedem `buyBuilding()`-Response aktuell gehalten (z. B.
  `BuildShopDialog.vue` spleißt die Antwort direkt in
  `playerStore.ownedBuildings`) — das neue `cookiesSpent`-Feld kommt
  automatisch mit, sobald es im DTO ist.
- `netWorth`-Computed um `+ nwBuildingValue.value` erweitern.
- `nwBuildingValue` im Store-Return exportieren.

**`components/NetWorthDialog.vue`**
- Neue Breakdown-Zeile (Icon `haus`, Label-Key `netWorthDialog.buildingsLabel`,
  Balkenfarbe `#e67146` aus der Fruitpunch24-Palette — bisher unbenutzt neben
  den schon verwendeten `#aea47e`/`#c78539`/`#349c58`/`#6f6e72`).
- Neuer Eintrag im `DATASETS`-Array (`key: 'buildingValue'`, gleiche Farbe) —
  taucht dann automatisch als Toggle-Button und History-Chart-Linie auf.

**`components/StatsView.vue`** ("Wirtschaft"-Kachel-Grid)
- Neue Kachel analog zu den bestehenden (Icon `haus`, `statsView
  .buildingValue`, `playerStore.nwBuildingValue`).

**`components/PlayerProfileView.vue`**
- Neuer Eintrag in der `stats`-Computed-Liste analog zu `statResourceValue`
  (Icon/Label `statBuildingValue`, Wert `data.value.buildingValue` aus dem
  jetzt erweiterten `PlayerProfileDto`).

**i18n** (`frontend/src/i18n/locales/{de,en}/`)
- `netWorthDialog.json`: `buildingsLabel`.
- `statsView.json`: `buildingValue`.
- `playerProfileView.json`: `statBuildingValue`.

## Docs

**`docs/cookie-game-design.md`**
- §10: Formel-Block um `+ Σ buildingCookiesSpent_i` (Summe über alle
  gebauten/geupgradeten Gebäude) erweitern.
- §12: Bullet "Gebäude fließen nicht in Net Worth ein" entfernen (behoben).
  Bullet "Season-Reset löscht keine Gebäude" ebenfalls entfernen (behoben durch
  den `SeasonService`-Fix oben).

**`docs/ROADMAP.md`**
- Offenen Punkt "Gebäude-Wert in Net Worth aufnehmen? (Abschnitt 12)" abhaken.
- Kurzen Eintrag ergänzen, dass der Season-Reset-Gebäude-Bug im selben Zug
  mitgefixt wurde (Datum, kurze Begründung — Muster wie bestehende
  `[x]`-Einträge in der Datei).

## Verification

- Backend: `cd backend/cookie-server-spring-boot && ./mvnw test` (bestehende
  Tests, falls vorhanden für NetWorthService/SeasonService/PrestigeService).
- Manuell im laufenden Dev-Server (`scripts/start.sh`):
  1. Gebäude kaufen/upgraden → Net-Worth-Dialog öffnen, neue "Gebäude"-Zeile
     in der Breakdown prüfen, Summe stimmt mit `netWorth`-Titel überein.
  2. Statistik-Dialog (`StatsView.vue`) → neue Kachel zeigt denselben Wert.
  3. Rangliste/Profil eines anderen Spielers mit Gebäuden öffnen → Wert kommt
     serverseitig aus `PlayerProfileDto.buildingValue` korrekt an.
  4. Prestige auslösen → Gebäude-Wert fällt auf 0 (Gebäude gelöscht, Vorgebaute
     kommen beim nächsten Laden zurück).
  5. `POST /api/v1/admin/season/start` (Admin-Endpoint) auslösen → prüfen dass
     `player_buildings` für alle Spieler geleert wird und Vorgebaute beim
     nächsten Login wieder auf Stufe 1 landen.
  6. Nach dem Deploy `DROP TABLE networth_history;` nicht vergessen (lokal +
     Live-Beta), sonst crasht der 30s-Snapshot-Job.
