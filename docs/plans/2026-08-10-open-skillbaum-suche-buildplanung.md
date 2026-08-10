# ⏳ Skillbaum: Suche/Filter + Tooltip-Ausbau (Build-Vorschau-Vorbereitung)

> **Status:** ⏳ Offen

## Context

Reines Frontend-Feature, unabhängig von den Content-Branches — wird aber
sinnvoller, je größer der Baum wird (aktuell 22 Knoten, nach den
Content-Plänen ~60-65, siehe
[[2026-08-10-open-skillbaum-wheel-keystones]]). Ohne Suche muss man den
gesamten Baum abpannen, um z. B. "wo ist nochmal der Lohn-Knoten"
herauszufinden.

**Scope-Klarstellung:** dieser Plan baut Suche/Filter + erweiterte Tooltips
vollständig. Ein echtes "Build-Preset speichern & laden"-System (Perplexity-
Vorschlag "Build-Preset und Pfadvorschau vorbereiten") wird hier bewusst
**nicht** gebaut, nur als Anschlussstelle dokumentiert — "vorbereiten" heißt
hier: nichts im Weg bauen, was ein späteres Preset-System erschweren würde,
nicht: ein Preset-System mitliefern. Kein eigener Roadmap-Punkt nötig, die
Anschlussstelle steht unten.

Baut auf [[2026-08-10-open-skillbaum-wheel-keystones]] auf (braucht
`nameDe`/`nameEn`/`descriptionDe`/`descriptionEn` fürs Durchsuchen in der
aktuellen Sprache, und die `effects[]`-Liste für den erweiterten Tooltip).

## Design-Entscheidungen

- **Client-seitige Suche**, kein neuer Backend-Endpunkt — der komplette Baum
  ist ohnehin schon geladen (`tree.nodes`), Filtern ist ein reines
  Anzeige-Problem.
- Suche filtert **nicht** durch Ausblenden (würde die Baum-Struktur/Kanten
  optisch zerreißen), sondern durch **Hervorheben/Abdunkeln**: passende
  Knoten bekommen einen Highlight-Rahmen (neue Puls-Animation, Fruitpunch24-
  Palette), nicht-passende Knoten werden gedimmt (Wiederverwendung der
  bestehenden "locked"-Optik-Sprache, `filter: saturate(0.5)
  brightness(0.85)`, statt einer neuen vierten visuellen Sprache).
- Suchfeld durchsucht `nameDe`/`nameEn`/`descriptionDe`/`descriptionEn`
  (beide Sprachen gleichzeitig, nicht nur die aktuell aktive — ein Spieler
  soll "Milch" auch im EN-Modus finden können), case-insensitive Substring-
  Match.
- Tooltip-Erweiterung (`nodeRows(n)`) zeigt zusätzlich zum reinen
  Effekt-Wert:
  - alle Einträge aus `n.effects` (mehrere möglich seit dem Fundament-Pass),
    Vorteile/Nachteile farblich unterschieden (siehe Fundament-Plan)
  - die Namen der **direkt erreichbaren Folgeknoten** (per Kanten-Lookup,
    clientseitig aus `tree.edges` ableitbar, kein neuer Backend-Wert nötig)
- **Anschlussstelle für ein späteres Preset-System** (nicht gebaut, nur
  benannt): ein Preset wäre ein Snapshot der aktuell allozierten Node-IDs
  (`Set<String>`) — spätere Arbeit müsste nur eine neue Entity
  (`SkillBuildPresetEntity: id, userId, name, nodeIds`) plus Save/Load-
  Endpunkte ergänzen, ohne an der hier gebauten Suche/Tooltip-Logik etwas
  ändern zu müssen (beide Features sind unabhängig vom Datenmodell des
  jeweils anderen).

## Frontend-Änderungen

**`SkillTreeView.vue`**:
- Neues Such-Input als fixes HUD-Element, gleiches Positionsmuster wie
  `.stv-points-badge` (Zeile 47-51) oder `.stv-cam-controls` (Zeile 54-57) —
  eigene Ecke der Canvas, pannt nicht mit.
- `searchQuery` (ref) + `matchesSearch(n)`-Computed-Helper:
  ```js
  function matchesSearch(n) {
    if (!searchQuery.value.trim()) return true
    const q = searchQuery.value.toLowerCase()
    return [n.nameDe, n.nameEn, n.descriptionDe, n.descriptionEn]
      .some(s => (s || '').toLowerCase().includes(q))
  }
  ```
- Neue Klasse `stv-node-search-match`/`stv-node-search-dim` zusätzlich zur
  bestehenden State-Klasse (`nodeState(n)`), gesteuert über
  `matchesSearch(n)`.
- `nodeRows(n)` erweitern: Effekt-Liste + Folgeknoten-Namen (Lookup über
  `tree.edges.filter(e => e.from === n.id || e.to === n.id)`, gefiltert auf
  noch nicht allozierte Ziel-Knoten, Namen in aktueller Sprache).
- Leeres Suchfeld → alles normal (kein Performance-Problem bei ~65 Knoten,
  keine Debounce/Virtualisierung nötig).

**i18n**: neue Keys in `skillTreeView.json` (de+en) — Platzhaltertext fürs
Suchfeld, ggf. "keine Treffer"-Hinweis falls gewünscht (optional, kein
Pflichtteil).

## Verifikationsplan

1. Suche nach "Milch"/"Milk" (je nach Sprache) → nur MILK-Branch-Knoten
   hervorgehoben, Rest gedimmt, in beiden Sprachen auffindbar unabhängig
   von der UI-Sprache.
2. Suche nach Teil-Begriff, der in mehreren Branches vorkommt (z. B. "Lohn"
   nach Umsetzung von
   [[2026-08-10-open-skillbaum-rohstoff-branches]]/[[2026-08-10-open-skillbaum-bau-buerger-branch]])
   → alle passenden Branches gleichzeitig hervorgehoben.
3. Tooltip zeigt bei einem Mehrfach-Effekt-Keystone (nach Fundament-Pass)
   beide Effekte korrekt, Nachteil optisch abgesetzt.
4. Tooltip zeigt korrekte Folgeknoten-Namen in aktueller Sprache.
5. `npm run check:palette` grün (neue Highlight-Farbe muss aus der Palette
   stammen).

## Kritische Dateien

- `frontend/src/components/SkillTreeView.vue`
- `frontend/src/i18n/locales/{de,en}/skillTreeView.json`
