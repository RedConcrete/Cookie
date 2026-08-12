# ✅ Skillbaum: Rohstoff-Branches zu echtem PoE-Mesh ausbauen

> **Status:** ✅ Umgesetzt (2026-08-12)

## Context

Die 5 Rohstoff-Branches (SUGAR/FLOUR/EGGS/BUTTER/CHOCOLATE, Gebäude
Zuckerteich/Bauernhof/Hühnerhof/Butterei/Plantage) waren bereits umgesetzt
([[2026-08-10-done-skillbaum-rohstoff-branches]]). Struktur pro Branch:
`<res>_1` → `<res>_2` → `<res>_3` (NOTABLE) → `<res>_4` (KEYSTONE) linear,
plus `<res>_5` als einzelner Fork ab `<res>_2`. Nutzer-Feedback: das ist
"eine lange Kette", kein echter Path-of-Exile-Passiv-Baum. PoE-Gefühl
braucht: Ketten, die in **unterschiedliche Boni verzweigen**, und Pfade, die
**an mehreren Stellen miteinander verbunden** sind (nicht nur ein einzelner
Seiten-Fork). Zusätzliche explizite Vorgabe: **keine NOTABLE-Tier mehr** in
diesen Branches, nur noch PASSIVE (klein) und KEYSTONE (Payoff mit
Tradeoff).

Scope: nur die 5 Rohstoff-Branches. MILK (schon vorher nur PASSIVE+KEYSTONE,
kein NOTABLE) sowie BAKING/MARKET/CORE/DISPO/STORAGE/Cross-Branch-Wheel
bleiben unangetastet.

## Design

**Neue Topologie pro Branch (8 statt bisher 5 Knoten, 2 Keystones statt 1):**

```
root
 └─ <res>_1   PASSIVE  (Radius 150, Branch-Bearing)
     └─ <res>_2   PASSIVE  (Radius 300, Branch-Bearing) -- Fork-Punkt
         ├─ <res>_y1  PASSIVE  (Radius 450, Bearing + 8°)
         │    └─ <res>_y2  PASSIVE  (Radius 600, Bearing + 8°)
         │         └─ <res>_y3  KEYSTONE (Radius 750, Bearing + 8°)
         └─ <res>_w1  PASSIVE  (Radius 450, Bearing − 8°)
              └─ <res>_w2  PASSIVE  (Radius 600, Bearing − 8°)
                   └─ <res>_w3  KEYSTONE (Radius 750, Bearing − 8°)

Cross-Links (zusätzlich zur Baumstruktur): <res>_y1 ↔ <res>_w1, <res>_y2 ↔ <res>_w2
```

- **Y-Pfad** (Ertrag): `_y1`/`_y2` kleine `HARVEST_YIELD`-Boni, `_y3`
  KEYSTONE mit 2 Effekten (großer `HARVEST_YIELD` **plus** kleiner negativer
  `RESOURCE_WAGE_REDUCTION` als Downside).
- **W-Pfad** (Lohn): `_w1`/`_w2` kleine `RESOURCE_WAGE_REDUCTION`-Boni, `_w3`
  KEYSTONE mit 2 Effekten (großer `RESOURCE_WAGE_REDUCTION` **plus** kleiner
  negativer `HARVEST_YIELD` als Downside).
- 2 Cross-Link-Kanten pro Branch machen den Baum an zwei Stellen zu einem
  Mesh statt reinem Baum — Wechsel zwischen den Pfaden ohne Rückkehr zum
  Fork-Punkt. `isAdjacentToAllocated` (OR-Konnektivität) trägt das ohne
  Codeänderung, Kanten sind faktisch ungerichtet
  (`findByFromNodeOrToNode`).
- Alle Knoten `PASSIVE` oder `KEYSTONE` — kein `NOTABLE` mehr in diesen 5
  Branches.
- BUTTER behält seine Eigenheit (Lohn-Knoten zuerst statt Ertrag bei
  `_1`/`_2`), damit die 5 Branches nicht identisch wirken.

**Kalibrierung (Platzhalter, Balancing separater Pass):** `_1` +0.04
`HARVEST_YIELD` (BUTTER: +0.01 `RESOURCE_WAGE_REDUCTION`), `_2` +0.01
`RESOURCE_WAGE_REDUCTION` (BUTTER: +0.04 `HARVEST_YIELD`), `_y1` +0.05,
`_y2` +0.07, `_y3` +0.20/−0.05, `_w1` +0.015, `_w2` +0.02, `_w3`
+0.12/−0.05 (alle `HARVEST_YIELD` bzw. `RESOURCE_WAGE_REDUCTION` wie
Topologie oben).

## Umgesetzte Änderungen

**Backend** (`backend/.../service/SkillTreeService.java`): `buildNodes()`/
`buildEdges()` für alle 5 Branches umgebaut — `<res>_3`/`_4`/`_5` entfernt,
durch `_y1,_y2,_y3,_w1,_w2,_w3` + 10 Kanten pro Branch (8 Baum + 2
Cross-Link) ersetzt. Positionen per Bearing/Radius-Formel
(`x=r·sin(θ)`, `y=−r·cos(θ)`) mit ±8° Fanning ab dem Fork-Punkt berechnet,
Kollisionsfreiheit gegen alle Nachbar-Branches von Hand nachgerechnet
(auch EGGS↔BUTTER, das einzige direkt benachbarte Rohstoff-Paar). Keine
Änderung an `EffectType`/`NodeTier`-Enums nötig, kein Code-Change in
`BuildingService`/`getEffectTotal`.

**Frontend:** `WORLD_SIZE` in `SkillTreeView.vue` 1500→1800 (Radius-750-
Keystones brauchen mehr Canvas, bestehende Branches unverändert da als
Offset zu `CENTER` gespeichert). `KEYSTONE_ICON`-Map erweitert: Y-Keystones
nutzen die bestehenden 5 Icons weiter (Key von `<res>_4` auf `<res>_y3`
umbenannt), 5 neue Icons für die W-Keystones
(`keystone_<res>_w3.svg`, dunkles Outline + grün/gelbes Münzen-Farbschema
zur Abgrenzung vom Ertrags-Keystone) unter `frontend/src/assets/icons/`
angelegt und in `PixelIcon.vue`s `ICONS`-Map registriert.

**Design-Doc:** `docs/cookie-game-design.md` §9 auf neue Topologie
aktualisiert (Knoten-/Kantenzahl, Mesh-Beschreibung, kein NOTABLE mehr).

**DB:** lokale Postgres-DB geleert (`skill_nodes`, `skill_node_effects`,
`skill_edges`, `player_skill_nodes`) und über `seedTree()` neu befüllt, da
die alten `<res>_3/_4/_5`-IDs sonst verwaist in der DB stehen bleiben
(DB gilt als disposable, siehe `CLAUDE.md`). **Live-Beta-DB braucht
denselben Schritt vor dem nächsten Deploy** — noch offen, siehe unten.

## Verifikation

- `./mvnw compile` fehlerfrei.
- `npm run check:palette` grün nach den 5 neuen Icons.
- Backend lokal gestartet (WSL, `./mvnw spring-boot:run`), `seedTree()` hat
  70 Knoten / 81 Kanten neu angelegt (vorher 55/58) — per SQL
  (`skill_nodes`/`skill_edges` Counts) und `GET /admin/skilltree/nodes`
  bestätigt: jede der 5 Rohstoff-Branches hat 8 Knoten, nur Tiers
  `PASSIVE`/`KEYSTONE`, BUTTER `_1`/`_2`-Tausch korrekt übernommen.
- Vollständiger Allokations-Flow gegen die laufende API getestet
  (`DEV_PLAYER_001`, SUGAR-Branch): `sugar_2` alloziert → `sugar_y1` **und**
  `sugar_w1` werden `allocatable` (Fork funktioniert). Danach nur `sugar_y1`
  + `sugar_y2` alloziert (W-Pfad nie angefasst) → `sugar_w2` wird trotzdem
  `allocatable` **rein über den Cross-Link** `sugar_y2`↔`sugar_w2` — die
  Mesh-Konnektivität (Pfadwechsel ohne Rückkehr zum Fork-Punkt) funktioniert
  wie im Design vorgesehen, ohne Codeänderung an `isAdjacentToAllocated`.
- Frontend-Rendering (visueller Look im Client-Baum) nicht separat
  geprüft — nächster Schritt bei Bedarf: `scripts/start.sh`, Skill-Baum im
  Client öffnen.

## Offene Punkte

- Live-Beta-DB (`skill_nodes`/`skill_node_effects`/`skill_edges`/
  `player_skill_nodes`) vor dem nächsten Live-Deploy ebenfalls leeren.
- Balancing der Platzhalter-Werte (siehe `docs/ROADMAP.md` §4).
