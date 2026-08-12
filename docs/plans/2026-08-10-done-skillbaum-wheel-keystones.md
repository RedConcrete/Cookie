# ✅ Skillbaum-Fundament: Mehrfach-Effekte, Node-Tiers, i18n, Cross-Branch-Wheel

> **Status:** ✅ Umgesetzt (2026-08-10, Commit aae56b5), Marker war nicht
> nachgezogen worden.

## Context

Dieser Plan ist das **Fundament** für den gesamten Skillbaum-Ausbau — alle
anderen Pläne ([[2026-08-10-open-skillbaum-rohstoff-branches]],
[[2026-08-10-open-skillbaum-crit-system]],
[[2026-08-10-open-skillbaum-lager-branch]],
[[2026-08-10-open-skillbaum-bau-buerger-branch]],
[[2026-08-10-open-skillbaum-respec]],
[[2026-08-10-open-skillbaum-suche-buildplanung]],
[[2026-08-10-open-skillbaum-automatisierung]]) setzen auf dem hier
gebauten Schema auf. **Deshalb zuerst umsetzen.**

Auslöser: ein vom User mit Perplexity erstelltes Anforderungs-Dokument
(`cookie-skilltree-claude-prompt.md`) orientiert sich an komplexen
Passivbäumen (PoE-Prinzipien, keine PoE-Inhalte) und fordert u. a. echte
Keystones mit Vor- **und** Nachteil, eine Notable-Zwischenstufe, zweisprachige
Knoteninhalte und geschützte IDs. Direkt am Code verifiziert (vollständiger
Read von `SkillTreeService.java`, Stand 22 Knoten + Wurzel):

- **Schema erlaubt nur einen Effekt pro Knoten.** `SkillNodeEntity` hat genau
  ein `effectType`/`targetResource`/`effectValue`-Tripel als Spalten,
  `node(...)`-Helper (Zeilen 140-155) nimmt genau ein Tripel entgegen. Ein
  Keystone mit Nachteil braucht einen **zweiten** Effekt auf demselben
  Knoten — nicht abbildbar ohne Schema-Änderung.
- **Kein `isKeystone`/Tier-Feld.** "Keystone" steht nur im Beschreibungstext
  (z. B. `milk_4`, Zeile 90: `"... (Keystone)"`).
- **Keine Lokalisierung.** `SkillTreeView.vue:27` rendert `n.name`/
  `n.description` direkt, kein `t()`. Alle 22 Knotennamen/-beschreibungen
  sind deutscher Klartext aus der DB — im Gegensatz zum Rest der App (die
  laut `CLAUDE.md` durchgängig `vue-i18n` nutzt) läuft der Skillbaum auf
  Englisch identisch zu Deutsch.
- **Keine Cross-Branch-Kanten.** `buildEdges()` (Zeilen 121-138) verbindet
  jeden Branch ausschließlich mit `root`, nie mit einem anderen Branch.

## Design-Entscheidungen

### 1. Mehrfach-Effekte pro Knoten (neue Tabelle statt Spalten)

Neue Entity `SkillNodeEffectEntity` (Tabelle `skill_node_effects`):
```
id (auto), nodeId (FK → skill_nodes.id), effectType (String, siehe
Abschnitt 2 -- bewusst kein typed Enum), targetResource (nullable),
effectValue (double)
```
`SkillNodeEntity` verliert die drei Spalten `effectType`/`targetResource`/
`effectValue`, bekommt stattdessen
`@OneToMany(mappedBy = "nodeId", cascade = CascadeType.ALL, orphanRemoval =
true) List<SkillNodeEffectEntity> effects` — eager geladen (Baum hat auch
nach vollem Ausbau nur ~65 Knoten, passt komplett in den bestehenden
In-Memory-`nodeCache`, gleiche Philosophie wie heute).

**`getEffectTotal` wird zum Flat-Map über alle Effekte:**
```java
public double getEffectTotal(String userId, EffectType type, String targetResource) {
    Set<String> allocated = allocatedNodeIds(userId);
    return nodeCache.values().stream()
        .filter(n -> allocated.contains(n.getId()))
        .flatMap(n -> n.getEffects().stream())
        .filter(e -> e.getEffectType().equals(type.name())) // effectType ist String, siehe Abschnitt 2
        .filter(e -> e.getTargetResource() == null || e.getTargetResource().equalsIgnoreCase(targetResource))
        .mapToDouble(SkillNodeEffectEntity::getEffectValue)
        .sum();
}
```
Bestehendes Aufruf-Verhalten bleibt identisch (Summe über alle passenden
Effekte) — kein Call-Site-Change bei `UserService`/`BakeService`/
`BuildingService` nötig, nur die interne Berechnung ändert sich.

**Downsides sind ein zweiter Effekt mit negativem Wert, kein neuer
Mechanismus.** Da `getEffectTotal` einfach summiert, ist ein "Nachteil"
schlicht ein zweiter `SkillNodeEffectEntity`-Eintrag auf demselben
Keystone-Knoten mit negativem `effectValue`, oft sogar **desselben** Typs
wie ein anderer Knoten im Baum (z. B. eine negative `WAGE_REDUCTION`
erhöht effektiv den Lohn). Kein neuer `EffectType` nötig, solange der
Nachteil sich in Begriffen eines bereits vorhandenen additiven Dials
ausdrücken lässt.

> **Harte Regel (User-Vorgabe): Nachteile wirken ausschließlich auf den
> eigenen Account, nie auf den gemeinsamen Markt-Pool.** `MarketService`s
> AMM-Pool (Constant-Product) ist **shared state** — alle Spieler handeln
> gegen denselben Pool. Ein Keystone-Nachteil darf niemals Pool-Reserven,
> Preiskurve oder sonstigen globalen Zustand verändern (das würde jedem
> anderen Spieler den Nachteil aufzwingen). Erlaubt ist ausschließlich ein
> Effekt, der **nur beim Berechnen der eigenen Trade-Proceeds/Gebühr des
> handelnden Users** ausgewertet wird — exakt das bestehende Muster von
> `BuildingService.getEffectiveSellFeeRate(userId, ...)`
> (`BuildingService.java:245-255`, verifiziert): der Skill-Discount wird
> dort pro `userId` aus `getEffectTotal` gezogen und nur auf **die eigene**
> Gebühr angewendet, der Pool selbst bleibt unberührt. Ein späterer
> MARKET-Branch-Keystone (in diesem Pass **nicht** gebaut, siehe
> ursprüngliche Perplexity-Idee "Großhändler") muss exakt diesem Muster
> folgen — z. B. eine negative `MARKET_FEE_REDUCTION` auf dem eigenen
> Konto, niemals ein Eingriff in `MarketService`s Pool-Mathematik.

### 2. `effectType` als reiner String, nicht als typed Enum-Spalte

**Auslöser:** die Postgres-CHECK-Constraint-Falle ist in diesem Projekt
bereits zweimal aufgetreten (`docs/ROADMAP.md` Abschnitt 0, zuletzt beim
Hinzufügen von `WAGE_INTEREST_REDUCTION`) — jeder neue `EffectType`-Wert
riss bisher den kompletten Server-Boot ab, bis manuell `ALTER TABLE
skill_nodes DROP CONSTRAINT IF EXISTS skill_nodes_effect_type_check;`
ausgeführt wurde. Da durch diesen Ausbau (Rohstoff-Branches, Krit, Storage,
Construction) und weitere künftige Branches (siehe Effekt-Backlog unten)
absehbar **deutlich mehr** `EffectType`-Werte dazukommen, wird dieser
Reibungspunkt jetzt einmal dauerhaft beseitigt statt bei jedem künftigen
Content-Plan erneut manuell umschifft zu werden.

**Lösung:** `SkillNodeEffectEntity.effectType` wird als **reiner
`String`** gespeichert, nicht über `@Enumerated(EnumType.STRING)` an den
Java-Enum-Typ gebunden:
```java
@Entity
@Table(name = "skill_node_effects")
public class SkillNodeEffectEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nodeId;
    private String effectType;      // KEIN @Enumerated -- plain VARCHAR, nie wieder CHECK-Constraint
    private String targetResource;  // war schon immer String, unverändert
    private double effectValue;
    // Getter/Setter roh auf String
}
```
`EffectType` bleibt als Java-Enum bestehen (Typsicherheit im Service-Code,
`switch`/Vollständigkeits-Checks möglich) — die Umwandlung passiert nur an
den zwei Rändern:
- **Schreiben** (`SkillTreeService.node(...)`-Helper beim Seed-Aufbau, und
  der Admin-PUT-Endpoint): `effectType.name()` beim Ablegen in die Entity.
- **Lesen** (`getEffectTotal(...)`): `EffectType.valueOf(effectEntity.getEffectType())`
  beim Vergleich gegen den angefragten Typ — oder einfacher, Vergleich direkt
  als String (`effectEntity.getEffectType().equals(type.name())`), spart die
  `valueOf`-Konvertierung im Hot Path komplett.

**Validierung nur an der einen Stelle, die wirklich externen Input
entgegennimmt** (Admin-PUT) — dort `EffectType.valueOf(body.getEffectType())`
aufrufen und bei `IllegalArgumentException` einen 400 zurückgeben. Der
Seed-Pfad (`buildNodes()`) kommt ausschließlich aus Java-Code mit
kompiliertem Enum, kann nie einen ungültigen String erzeugen — keine
zusätzliche Prüfung dort nötig (kein Validieren an Stellen, die nicht
passieren können).

**Ergebnis:** ab diesem Pass braucht ein neuer `EffectType`-Wert **nie
wieder** einen `ALTER TABLE ... DROP CONSTRAINT`-Schritt, weder lokal noch
live — einfach Enum-Konstante ergänzen und an der einen Call-Site
verwenden, fertig. Der ROADMAP-Eintrag zu dieser Falle (Abschnitt 0) kann
nach Umsetzung dieses Plans als erledigt/obsolet markiert werden.

`nodeTier` (siehe nächster Abschnitt) bleibt bewusst ein **typed** Enum mit
`@Enumerated` — anders als `effectType` ist das ein kleines, stabiles
3-Werte-Set (`PASSIVE/NOTABLE/KEYSTONE`), das absehbar nicht wächst. Der
einmalige Constraint-Drop dafür beim ersten Boot ist unkritisch, eine
String-Umstellung dort wäre unnötiger Aufwand für ein Problem, das nicht
wiederkehrt.

### 3. Node-Tier statt binärem `isKeystone`

Neues Feld `SkillNodeEntity.nodeTier` (Enum `NodeTier { PASSIVE, NOTABLE,
KEYSTONE }`, `@Enumerated(EnumType.STRING)`, gleiche Enum-Constraint-Falle
wie `EffectType` — nach dem ersten Boot mit befüllter Tabelle `ALTER TABLE
skill_nodes DROP CONSTRAINT IF EXISTS skill_nodes_node_tier_check;`
mitdenken, falls Postgres dafür ebenfalls automatisch eine CHECK-Constraint
anlegt). Jeder neue Branch (siehe Content-Pläne) bekommt **1 Notable**
(mittelstarker Einzeleffekt, eigene aber schlichtere Optik als Keystone) und
**höchstens 1-2 Keystones** — die bestehenden 4 "(Keystone)"-Knoten
(`milk_4`, `bake_4`, `market_4`, `dispo_4`) werden auf `KEYSTONE` migriert,
`core_4` (aktuell größter CORE-Wert, faktisch schon ein Notable) auf
`NOTABLE`.

### 4. Zweisprachige Knoteninhalte (i18n-Fix)

`SkillNodeEntity` bekommt `nameDe`, `nameEn`, `descriptionDe`,
`descriptionEn` statt `name`/`description`. **Kein** `vue-i18n`-JSON-Key-
Pattern wie bei statischen UI-Texten (`CLAUDE.md` "Lokalisierung") — das
passt hier nicht, weil Knoteninhalt admin-editierbarer **Datenbank-Inhalt**
ist, kein statischer Code-Text. Stattdessen liefert das Backend beide
Sprachen im DTO, das Frontend wählt reaktiv nach aktuellem `locale`:
```js
const nodeName = computed(() => locale.value === 'de' ? n.nameDe : n.nameEn)
```
Kein Tree-Refetch beim Sprachwechsel nötig.

**Migrationstabelle für die 22 bestehenden Knoten** (aus dem verifizierten
Ist-Text von `buildNodes()` übersetzt, als Startpunkt für die Migration):

| ID | DE Name | EN Name | DE Beschreibung | EN Beschreibung |
|---|---|---|---|---|
| root | Ursprung | Origin | Startpunkt des Skill-Baums | Starting point of the skill tree |
| milk_1 | Bessere Melkkannen | Better Milk Pails | +5% Milch pro Ernte-Tick | +5% milk per harvest tick |
| milk_2 | Sanftere Hand | Gentler Hand | +5% Milch pro Ernte-Tick | +5% milk per harvest tick |
| milk_3 | Weidewissen | Pasture Knowledge | +7% Milch pro Ernte-Tick | +7% milk per harvest tick |
| milk_4 | Meister-Melker | Master Milker | +10% Milch pro Ernte-Tick | +10% milk per harvest tick |
| milk_5 | Zweite Kanne | Second Pail | +7% Milch pro Ernte-Tick | +7% milk per harvest tick |
| bake_1 | Warmer Ofen | Warm Oven | +2% Cookie-Ausbeute beim Backen | +2% cookie yield when baking |
| bake_2 | Gleichmäßige Hitze | Even Heat | +2% Cookie-Ausbeute beim Backen | +2% cookie yield when baking |
| bake_3 | Süßes Händchen | Sweet Touch | +3% Cookie-Ausbeute beim Backen | +3% cookie yield when baking |
| bake_4 | Meisterbäcker | Master Baker | +5% Cookie-Ausbeute beim Backen | +5% cookie yield when baking |
| bake_5 | Geheimrezept | Secret Recipe | +4% Cookie-Ausbeute beim Backen | +4% cookie yield when baking |
| market_1 | Verhandlungsgeschick | Negotiation Skill | -0.5% Markt-Verkaufsgebühr | -0.5% market sell fee |
| market_2 | Guter Ruf | Good Reputation | -0.5% Markt-Verkaufsgebühr | -0.5% market sell fee |
| market_3 | Marktkenner | Market Expert | -0.75% Markt-Verkaufsgebühr | -0.75% market sell fee |
| market_4 | Händlerlizenz | Trader's License | -1% Markt-Verkaufsgebühr | -1% market sell fee |
| core_1 | Fleißige Hände | Diligent Hands | +4% Ernte-Ertrag (alle Ressourcen) | +4% harvest yield (all resources) |
| core_2 | Ausdauer | Stamina | +1.5% Cookie-Ausbeute beim Backen | +1.5% cookie yield when baking |
| core_3 | Sparsamkeit | Frugality | -0.5% Markt-Verkaufsgebühr | -0.5% market sell fee |
| core_4 | Alleskönner | Jack of All Trades | +6% Ernte-Ertrag (alle Ressourcen) | +6% harvest yield (all resources) |
| dispo_1 | Guter Draht zur Bank | Good Bank Connections | -1% Dispo-Zinsen | -1% overdraft interest |
| dispo_2 | Bonitätsprüfung bestanden | Passed Credit Check | -1% Dispo-Zinsen | -1% overdraft interest |
| dispo_3 | Verhandelter Rahmen | Negotiated Credit Line | -1.5% Dispo-Zinsen | -1.5% overdraft interest |
| dispo_4 | Goldener Kredit | Golden Credit | -2% Dispo-Zinsen | -2% overdraft interest |

`"(Keystone)"`-Suffix aus den Beschreibungen entfernen — die neue
`nodeTier`-Optik macht das Textsuffix überflüssig.

### 5. Cross-Branch-Wheel

1-2 neue Brücken-Knoten zwischen benachbarten Branches statt direkter
Kanten über leere Canvas-Fläche (Positionierung: x/y-Mittelwert der beiden
Arme). Konkret: `bridge_milk_bake` zwischen MILK (Norden, negative x) und
BAKING (Osten, positive x bei y=0) — exakte Anker-Knoten und Koordinaten
beim Bauen anhand der tatsächlichen `buildNodes()`-Werte prüfen. Darauf ein
neuer genereller Keystone `keystone_alleskoenner` (`HARVEST_YIELD`,
`targetResource=null`, kleiner globaler Bonus z. B. +0.05) — nur
erreichbar, wenn in **beiden** angrenzenden Branches vorgearbeitet wurde.
Das ist die konkrete Umsetzung von "nur selten ein Bonus für alle
Ressourcen auf einmal" — kostet hier bewusst Punkte in zwei Branches.

Weitere Brücken sind optional pro Content-Plan ergänzbar, kein Pflichtteil
dieses Fundament-Passes über die eine Beispiel-Brücke hinaus.

### 6. Geschützte IDs (nie umbenennen)

```
root
milk_1, milk_2, milk_3, milk_4, milk_5
bake_1, bake_2, bake_3, bake_4, bake_5
market_1, market_2, market_3, market_4
core_1, core_2, core_3, core_4
dispo_1, dispo_2, dispo_3, dispo_4
```
Grund: `PlayerSkillNodeEntity.nodeId` referenziert diese IDs direkt in
existierenden Spielerständen (`player_skill_nodes`-Zeilen). Ein Rename
würde jede bestehende Allokation auf diesen Knoten verwaisen lassen. Neue
Knoten aus den Content-Plänen bekommen komplett neue ID-Präfixe
(`sugar_`, `flour_`, `egg_`, `butter_`, `choc_`, `crit_`, `lager_`/
`storage_`, `bau_`/`construction_` — je nach finalem Branch-Key, siehe die
einzelnen Content-Pläne) und kollidieren nicht.

### 7. Effekt-Backlog (nicht Teil dieses Passes, nur vorgemerkt)

Dank der String-Spalte (Abschnitt 2) und der Mehrfach-Effekt-Tabelle
(Abschnitt 1) ist das Hinzufügen eines neuen `EffectType`-Werts ab jetzt
immer derselbe kleine Schritt: Enum-Konstante ergänzen, an einer Call-Site
verrechnen, Knoten-Daten anlegen — keine Architektur-Änderung mehr nötig.
Kandidaten für künftige, eigene Content-Pläne (analog zu den 4 bestehenden
Branch-Plänen), hier nur gesammelt, damit sie nicht doppelt erfunden
werden:

| Kandidat | Zielsystem | Hook-Punkt (grob) | Spielweise |
|---|---|---|---|
| `PRESTIGE_REWARD` | Bonus-Skill-Punkte pro Prestige | `PrestigeService` (bereits als ROADMAP-Folgepunkt vermerkt: "+3 Skill-Punkte pro Prestige") | Langfristiger Prestige-Fortschritt |
| `DEBT_LIMIT_INCREASE` | Höhere Dispo-Grenze (`wagePerMin×8`-Hardstop) | `WageService`/`GameBalanceConfig` | Aggressives Wirtschaften mit Dispo |
| `BAKE_DURATION_REDUCTION` | Kürzerer serverseitiger Back-Timer | `BakeService` (Rezept-Timer) | Schnelles Backen |
| `RECIPE_RESOURCE_EFFICIENCY` | Weniger Zutaten pro Batch | `BakeService`/Rezept-Verbrauch | Selbstversorgung fürs Backhaus |
| `WORKER_SLOT_BONUS` | Mehr Arbeiter-Slots pro Gebäude, on top von `workersPerLevel` | `BuildingService.effectiveMaxWorkers()` | Viele Bürger, große Gebäude |
| `CITIZEN_RECRUIT_COST` | Günstigere neue Bürger | noch zu verifizieren, ob es einen eigenen Rekrutierungspreis getrennt vom Rathaus-Level gibt — **vor Einplanung erst gegenchecken**, nicht blind übernehmen | Viele Bürger |

Jeder dieser Kandidaten braucht einen eigenen kleinen Plan (Hook-Punkt exakt
verifizieren, Knotenliste, DE/EN-Texte, Keystone-Tradeoff), analog zu
[[2026-08-10-open-skillbaum-crit-system]] — hier bewusst nicht weiter
ausgearbeitet, nur damit spätere Planungsrunden nicht bei null anfangen.

## Backend-Änderungen

- Neue Entity `SkillNodeEffectEntity` + Repository (oder als
  `@OneToMany`-Collection ohne eigenes Repository, direkt über
  `SkillNodeEntity` verwaltet — einfacher, kein separates CRUD nötig da
  Effekte nur über den Elternknoten editiert werden).
- `SkillNodeEntity`: `effectType`/`targetResource`/`effectValue`-Spalten →
  `effects`-Collection; `name`/`description` → `nameDe`/`nameEn`/
  `descriptionDe`/`descriptionEn`; neues `nodeTier`-Feld.
- `SkillTreeService.node(...)`-Helper: Signatur ändert sich auf
  `node(id, nameDe, nameEn, descDe, descEn, branch, nodeTier, x, y, isRoot,
  List<Effect> effects)` mit einem kleinen lokalen `record Effect(EffectType
  type, String targetResource, double value)` als Kurzschreibweise beim
  Aufbau der Liste.
- `getEffectTotal(...)`: Flat-Map-Umbau wie oben.
- `SkillNodeStatusDto`/`SkillTreeDto`: `effects: List<EffectDto>` statt
  dreier Einzelfelder, `nodeTier: String`, `nameDe/nameEn/descriptionDe/
  descriptionEn` statt `name/description`.
- Admin-Endpoint (`GET/PUT /api/v1/admin/skilltree/nodes[/{id}]`): PUT-Body
  nimmt jetzt eine Effekt-Liste + beide Sprachfelder entgegen — Shape ändert
  sich, curl-Beispiele in `docs/cookie-game-design.md` §9 nach dem Bauen
  aktualisieren.
- `seedTree()`-Upsert-Logik bleibt unverändert (funktioniert weiter pro
  Knoten-ID), migriert aber nichts automatisch für **bestehende** Zeilen —
  da DB disposable ist (bestätigte Policy), einfachster Weg: lokale und
  Live-`skill_nodes`/`skill_node_effects`-Tabellen einmalig leeren statt
  eine Migrations-Query zu schreiben, danach zieht `seedTree()` alles neu
  aus dem aktualisierten `buildNodes()` (inkl. Migrationstabelle oben) nach.
  **Achtung:** das setzt bestehende `player_skill_nodes`-Zuordnungen nicht
  zurück (andere Tabelle) — Spieler behalten ihre Allokationen, nur die
  Knoten-Stammdaten (Name/Effekt-Struktur) werden neu geseedet.

## Frontend-Änderungen

- `NODE_SIZE`/`KEYSTONE_SIZE`/`NOTABLE_SIZE`-Abstufung in
  `nodeWrapStyle(n)` (`SkillTreeView.vue:191-199`) je nach `n.nodeTier`.
- Neue CSS-Klassen `.stv-node-keystone` (auffälligster Rahmen/Glow) und
  `.stv-node-notable` (mittlerer Rahmen), zusätzlich zu den bestehenden
  Status-Klassen.
- `branchIcon(n)` → `nodeIcon(n)`: `KEYSTONE_ICON`-Map (Node-ID → eigenes
  Icon) für `nodeTier === 'KEYSTONE'`, sonst weiter `BRANCH_ICON[n.branch]`
  (Notables bekommen **kein** eigenes Icon pro Knoten, nur die größere
  Rahmen-Optik — sonst müsste jeder Notable jedes künftigen Branches ein
  Custom-Icon bekommen, das sprengt den Content-Plänen ihren Scope).
- Node-Name/-Beschreibung: `nodeName(n)`/`nodeDesc(n)`-Computed-Helper wie
  oben skizziert, ersetzt direkte `n.name`/`n.description`-Zugriffe.
- Neue Effekt-Anzeige im Tooltip (`nodeRows(n)`, aktuell Zeilen ~141-147):
  iteriert über `n.effects` (mehrere Zeilen möglich), zeigt Vorteile und
  Nachteile farblich unterschieden (z. B. grün/rot).
- Neue Icons: `keystone_milk4.svg`, `keystone_bake4.svg`,
  `keystone_market4.svg`, `keystone_dispo4.svg`,
  `keystone_alleskoenner.svg` (8×8-Raster, Fruitpunch24-Palette,
  `PixelIcon.vue`-Registrierung wie bestehende Icons).

## Verifikationsplan

1. `GET /api/v1/skilltree?userId=...` — jeder Knoten liefert `effects[]`
   (mind. 1 Eintrag), `nodeTier`, `nameDe/nameEn/descriptionDe/
   descriptionEn`.
2. Bestehende Effekte unverändert wirksam: Ernte/Backen/Markt-Gebühr/
   Dispo-Zinsen vor/nach Migration identisch (Regressionscheck gegen die
   alten Werte).
3. Ein Test-Keystone mit 2 Effekten (z. B. temporär über Admin-API) —
   `getEffectTotal` summiert beide korrekt inkl. negativem Wert.
4. Sprachumschalter (Settings DE↔EN) — Knotennamen/-beschreibungen wechseln
   ohne Tree-Neuladen.
5. `keystone_alleskoenner` erst allocatable, wenn beide angrenzenden
   Branches bis zur Brücke frei sind.
6. Visuell: Keystone/Notable/Passive optisch klar unterscheidbar, alle 3
   Node-States weiterhin erkennbar.
7. `npm run check:palette` grün nach neuen SVGs.

## Kritische Dateien

- `backend/.../entity/SkillNodeEntity.java` — Feld-Umbau
- `backend/.../entity/SkillNodeEffectEntity.java` — neu
- `backend/.../service/SkillTreeService.java` — komplett (82-288, siehe
  vollständig verifizierter Quelltext oben als Grundlage)
- `backend/.../dto/SkillTreeDto.java`, `SkillNodeStatusDto.java`
- `frontend/src/components/SkillTreeView.vue:27,120-199,357-379`
- `frontend/src/components/pixel/PixelIcon.vue`
- `docs/cookie-game-design.md` §9 — nach Umsetzung aktualisieren (Schema,
  Node-Tiers, i18n-Hinweis)
