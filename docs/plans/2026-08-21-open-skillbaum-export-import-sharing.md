# ⏳ Skill-Baum: Dev-Export/Import + Spieler-Build-Sharing

> **Status:** ⏳ Offen

## Kontext

Zwei unterschiedliche Features, die beide "Skill-Baum" + "Export/Import" heißen,
aber komplett unterschiedliche Daten bewegen — deshalb hier zusammen geplant,
aber als zwei unabhängige Arbeitspakete:

1. **Dev-Case** (bereits offener Punkt in `docs/ROADMAP.md:589-599`): der
   ganze Baum (Nodes + Edges, die Struktur) als JSON exportieren, lokal
   bearbeiten, vor Season-Start wieder importieren — Ersatz fürs Live-
   Rumklicken im Admin-Editor.
2. **Spieler-Case** (neu, bisher nirgends dokumentiert): ein Spieler
   exportiert, welche Nodes *er* alloziert hat (Teilmenge des Baums, keine
   Struktur) als Code, ein anderer Spieler importiert den Code und bekommt
   denselben Build (Respec zu dieser Auswahl).

Referenz-Recherche (Datenmodell, bestehende Endpoints, Respec-Flow,
Frontend-Patterns) siehe Analyse in dieser Planungs-Session — Kernpunkte
unten direkt eingearbeitet.

## Feature 1: Dev-Baum-Export/Import

### Endpoints (neu, in `AdminConfigController.java`, gleiches
`badToken`/`isDevMode`-Gate wie die bestehenden Node/Edge-CRUD-Endpoints)

- `GET /api/v1/admin/skilltree/export` → `{ nodes: [...], edges: [...] }`,
  Snapshot aus `skillNodeRepository.findAll()` +
  `skillEdgeRepository.findAll()` (inkl. `effects` pro Node).
- `POST /api/v1/admin/skilltree/import` — Body: dasselbe Format. **Ersetzt**
  den kompletten Baum (nicht upsert-missing wie `seedTree()` — sonst
  verschwinden alte Nodes nie, siehe ROADMAP-Notiz).

### Validierung/Transaktion (neue Methode in `SkillTreeService`, nicht
`seedTree()` wiederverwenden)

1. Struktur-Validierung vor jedem DB-Write: jede Node hat gültige `id`
   (nicht blank), jeder `effectType` via `EffectType.valueOf(...)` gültig
   (400 mit Liste aller ungültigen Zeilen, nicht nur der ersten), jede Edge
   referenziert zwei IDs die im gelieferten `nodes`-Array existieren, genau
   eine Node hat `isRoot=true`.
2. `@Transactional`: alte `skill_edges` löschen, alte `skill_nodes` löschen,
   neue Nodes+Effects+Edges einfügen.
3. **Verwaiste `player_skill_nodes`**: kein DB-FK zwischen
   `player_skill_nodes.nodeId` und `skill_nodes.id` (siehe Recherche) — nach
   einem Import auf Nodes zeigen, die es nicht mehr gibt. Import-Endpoint
   räumt das defensiv mit auf: `playerSkillNodeRepository.deleteAll()` als
   Teil derselben Transaktion, **oder** Endpoint hart daran koppeln, dass er
   nur direkt im Rahmen eines Season-Starts aufrufbar ist (Season-Reset
   löscht `player_skill_nodes` ohnehin schon, siehe
   `cookie-game-design.md` Abschnitt 11). Empfehlung: explizit mitlöschen
   statt sich auf "wird schon vorher/nachher aufgerufen" zu verlassen — ein
   Import mitten in einer laufenden Season (z.B. zum Testen) darf keine
   kaputten Spielerstände hinterlassen.
4. Nach Import: `skillTreeService.refreshCache()`.

### Frontend

`SkillTreeAdminDialog.vue`: zwei neue Buttons in der Toolbar,
"Baum exportieren" (lädt `GET .../export`, triggert Datei-Download als
`.json`) und "Baum importieren" (`<input type="file">`, liest JSON, zeigt
Bestätigungsdialog mit Diff-Zusammenfassung — X Nodes neu/geändert/entfernt
— bevor `POST .../import` geschickt wird). Kein neues Dialog-Grundgerüst
nötig, reiner Zusatz zum bestehenden Editor.

## Feature 2: Spieler-Build-Sharing

### Export — reiner Frontend-Vorgang, kein Backend-Call nötig

`playerStore.skillTree.nodes` hat pro Node schon das `allocated`-Flag (siehe
`SkillNodeStatusDto`). Export = `{ nodeIds: [...], season: "...", author:
"..." }` (Node-IDs ohne Root, die ist implizit), JSON, Base64-kodiert zu
einem kompakten String. Kein Server-Roundtrip fürs reine Encodieren — die
Node-Auswahl ist schon im Client.

**Meta-Infos** (Design-Entscheidung, siehe unten): `season` kommt aus der
aktiven Season, `author` optional aus dem Spieler-Anzeigenamen (Steam-Name,
falls vorhanden — sonst leer). Die aktive Season ist heute nirgends im
Frontend verfügbar; `SkillTreeDto`/`GET /api/v1/skilltree` bekommt dafür ein
zusätzliches Feld `activeSeasonName` (billig, da eh schon bei jedem
Skill-Baum-Laden abgefragt — kein neuer Endpoint nötig). Beide Meta-Felder
sind reine Anzeige beim Import (siehe unten), fließen **nicht** in die
serverseitige Validierung ein — die prüft ausschließlich die Node-ID-Liste,
Season-Angabe im Code kann veraltet/falsch sein und wird nicht vertraut.

### Import — MUSS über Backend, bewegt Skillpunkte/Cookies

Neuer Endpoint `POST /api/v1/skilltree/import-build/{userId}`,
Body `{ nodeIds: string[] }` (decodierte Liste aus dem Client). Client-Werte
nie vertrauen (CLAUDE.md-Konvention) — komplette Validierung serverseitig,
neue Methode `SkillTreeService.importBuild(userId, targetNodeIds)`:

1. **Unbekannte IDs tolerant behandeln, nicht hart failen.** Build-Codes
   können nach einem Season-Reset (Feature 1!) auf Nodes zeigen, die nicht
   mehr existieren. Response meldet `unknownNodeIds: [...]` zurück, wendet
   den Rest trotzdem an — nicht komplett verwerfen.
2. Ziel-Set = `targetNodeIds` (bekannte) ∪ `{root}`. Vollständige
   Konnektivitäts-Prüfung: **jede** Node im Ziel-Set muss von root aus
   erreichbar sein unter Berücksichtigung von `requiresAllPrereqs`
   (AND-Semantik bei Bridge-Nodes) — Wiederverwendung/Verallgemeinerung der
   bestehenden Prüf-Logik aus `deallocateNode` (Zeilen 636-658), aber für
   das ganze Ziel-Set statt nur eine entfernte Node. Bei Verletzung: 400 mit
   der Liste der nicht erreichbaren Nodes (Import wird komplett
   abgelehnt, kein Teil-Apply bei ungültiger Struktur).
3. Diff berechnen: `toRemove = aktuell alloziert − Ziel`,
   `toAdd = Ziel − aktuell alloziert`.
4. **Kosten** (siehe Design-Entscheidungen unten) — Summe aus den
   bestehenden Kosten-Bausteinen, kein neuer Preis:
   - `toRemove.size() × respecCostFlat` Cookies (wie Einzel-Respec), dafür
     `toRemove.size()` Skillpunkte zurück.
   - Diese zurückerhaltenen Punkte + evtl. schon vorhandene ungenutzte
     `user.getSkillPoints()` decken zuerst `toAdd`. Erst der **Rest**
     (`pointsToBuy = max(0, toAdd.size() − verfügbare Punkte)`) wird über
     die normale `nextPointCost`-Kurve (`GameBalanceConfig.skillPointBaseCost
     × skillPointCostGrowth^n`, fortlaufend ab aktuellem
     `totalSkillPointsBought`) in Cookies gekauft — kein doppeltes Bezahlen
     für Punkte, die der Spieler schon hatte oder sich gerade zurückgeholt
     hat.
   - Vorab-Prüfung: reicht `user.getCookies()` für Respec-Kosten + Punkte-
     Kauf zusammen? Sonst 400 mit der berechneten Gesamtsumme in der
     Antwort (Frontend zeigt sie in der Vorschau, siehe unten — kein Raten
     nötig, kein Teil-Apply bei zu wenig Cookies).
5. `@Transactional`: `toRemove` deallozieren (Cookie-Gutschrift
   `respecCostFlat` pro Node, wie bei `deallocateNode`, aber ohne den
   Einzel-Node-Connectivity-Re-Check — das Ziel-Set wurde in Schritt 2
   schon als Ganzes geprüft), `toAdd` allozieren (Skillpunkt-Abzug wie bei
   `allocateNode`), `totalSkillPointsBought`/`totalSkillPointCookiesSpent`
   für `toAdd` **normal hochzählen** (im Gegensatz zum Einzel-Respec, wo das
   bewusst unverändert bleibt — hier werden ja tatsächlich neue Punkte
   gekauft, kein reiner Punkt-Rückerstattungs-Fall).

### Frontend

Neuer Dialog `BuildShareDialog.vue` (Pixel-Stil, kein natives
prompt/alert — Projekt-Konvention), erreichbar über neuen Button in
`SkillTreeView.vue`:

- **Export-Tab**: zeigt den generierten Code in einem `readonly`-Textfeld +
  Copy-Button (`navigator.clipboard.writeText` — bisher nirgends im Projekt
  verwendet, hier neu einführen). Darunter Klartext-Zeile "Season X ·
  von {Anzeigename}" als Kontext übers rohe Code-Feld.
- **Import-Tab**: Textarea zum Einfügen eines Codes. Nach dem Einfügen
  sofort clientseitig decodiert: Season/Autor aus dem Code als Info-Zeile
  angezeigt (reine Anzeige, s.o.). "Vorschau"-Button ruft den Import-
  Endpoint im **Dry-Run-Modus** auf (`?dryRun=true` oder eigenes Feld im
  Body — liefert Diff + Gesamtkosten, ändert aber nichts), erst nach
  Bestätigung geht der eigentliche (schreibende) Request raus. Unbekannte
  IDs aus der Server-Antwort (`unknownNodeIds`) werden als Warnung
  angezeigt ("N Nodes aus diesem Code gibt's nicht mehr, Rest wurde
  übernommen"), berechnete Kosten (Cookies) klar sichtbar vor der
  Bestätigung — Import kostet echte Ressourcen, braucht denselben
  Bestätigungs-Schutz wie `HardResetDialog`.

## Design-Entscheidungen

1. **Kosten-Modell**: Summe aus bestehenden Werten (kein neuer Balance-
   Wert) — `respecCostFlat` pro entfernter Node, normale
   `nextPointCost`-Kurve für tatsächlich neu gekaufte Punkte (nach Abzug
   zurückerhaltener/schon vorhandener Punkte). Details siehe Kosten-Schritt
   oben.
2. **Import jederzeit erlaubt**, nicht nur bei leerem Baum — voller Respec
   mitten im Spiel, über den Kosten-Diff aus Punkt 1 abgedeckt.
3. **Build-Code trägt Meta-Infos** (Season-Name, optional Autor-Anzeigename)
   — reine Anzeige beim Import, fließt nicht in die Server-Validierung ein.

## Reihenfolge

Feature 1 zuerst (kleiner Scope, Datenmodell + Endpoints schon skizziert,
kein neues UI-Konzept). Feature 2 danach — Design-Entscheidungen oben sind
jetzt getroffen, direkt umsetzbar.

## Betroffene Dateien

**Feature 1:**
- `backend/.../controller/AdminConfigController.java` — zwei neue Endpoints
- `backend/.../service/SkillTreeService.java` — Import-Methode (Replace,
  nicht upsert)
- `frontend/src/components/SkillTreeAdminDialog.vue` — Export/Import-Buttons

**Feature 2:**
- `backend/.../controller/SkillTreeController.java` — neuer Endpoint
- `backend/.../service/SkillTreeService.java` — `importBuild()`,
  verallgemeinerte Konnektivitäts-Prüfung fürs ganze Ziel-Set
- `backend/.../dto/` — Request/Response-DTOs (`unknownNodeIds`, Kosten etc.)
- `backend/.../dto/SkillTreeDto.java` — neues Feld `activeSeasonName`
- `frontend/src/components/BuildShareDialog.vue` — neu
- `frontend/src/views/SkillTreeView.vue` — Button zum Öffnen
- `frontend/src/services/api.js` — neue API-Funktion
