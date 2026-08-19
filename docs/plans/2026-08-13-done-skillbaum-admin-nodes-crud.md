# ✅ Skill-Baum Admin-Editor: Neue Nodes erstellen/löschen

> **Status:** ✅ Umgesetzt (2026-08-19, Commit 7bea3fb)

**Umsetzungs-Hinweise (Abweichungen vom ursprünglichen Plan):**
`SkillEdgeRepository.findByFromNodeOrToNode` existierte bereits (in
`SkillTreeService.isAdjacentToAllocated` genutzt) — keine neue Query nötig.
`PlayerSkillNodeRepository` bekam neu `existsByNodeId(String)`. Neue
Endpunkte folgen dem `ResponseEntity`-Direkt-Stil der bestehenden
Edge-Endpunkte (kein `GlobalExceptionHandler`). effectType-Validierung aus
`updateSkillNode` in private `validateEffectTypes()`-Helper extrahiert, von
`createSkillNode` mitgenutzt. Frontend: Node-Erstellung per `prompt()` für
die ID, Weltkoordinaten aus Klick-Position via Pan/Zoom-Rücktransformation
(`panEnd` in `SkillTreeAdminDialog.vue`).

## Context

Der Admin-Editor (`SkillTreeAdminDialog.vue`, siehe
`docs/plans/2026-08-10-done-skillbaum-admin-editor.md`) kann bestehende
(geseedete) Nodes nur verschieben, verbinden und deren Effekte editieren.
Komplett neue Nodes anlegen oder welche löschen geht bisher nicht — dafür
muss man weiterhin `SkillTreeService#buildNodes()` in Java anfassen und neu
deployen. Ziel dieses Plans: das direkt im Editor erledigen können, live
gegen die DB, ohne Neustart.

Vorstufe für `docs/plans/2026-08-13-open-skillbaum-admin-node-clone.md`
(Klonen baut auf dem hier neu geschaffenen `POST`-Endpoint auf).

## Umsetzung

**Backend** (`AdminConfigController.java`):
- `POST /api/v1/admin/skilltree/nodes` — Body: komplette `SkillNodeEntity`
  (gleiche Form wie `PUT`, inkl. frei wählbarer `id`). Validierung:
  - `id` nicht leer, noch nicht vergeben (409 bei Duplikat wie beim
    Edge-Endpoint).
  - `effectType` jedes Effekts gegen `EffectType`-Enum validieren (selbe
    Prüfung wie in `updateSkillNode`, ggf. in private Helper-Methode
    extrahieren statt duplizieren).
  - `x`/`y` optional, Default `0/0` falls nicht mitgeschickt (Node landet
    im Ursprung, Admin schiebt sie danach per Drag an ihren Platz).
  - Nach dem Speichern `skillTreeService.refreshCache()` aufrufen (wie bei
    `PUT`).
- `DELETE /api/v1/admin/skilltree/nodes/{id}`:
  - 404 falls `id` unbekannt.
  - Root-Node (`isRoot() == true`) darf nicht gelöscht werden (400) — der
    Spieler-Baum braucht mindestens einen Root-Einstiegspunkt.
  - Alle Edges löschen, die diese Node als `fromNode`/`toNode` referenzieren
    (`SkillEdgeRepository` hat aktuell keine Query dafür — neue Methode
    `findByFromNodeOrToNode` ergänzen, sonst bleiben tote Kanten-Zeilen mit
    Verweis auf eine nicht mehr existierende Node).
  - Falls bereits Spieler diese Node alloziert haben
    (`PlayerSkillNodeRepository` hat Zeilen mit `nodeId == id`): Löschen
    verweigern (409 mit Hinweis), nicht automatisch entfernen — sonst
    hätten Spieler stillschweigend Skillpunkte "verloren", ohne dass sie
    zurückerstattet wurden. Dev-Tool, betrifft in der Praxis nur
    Test-Accounts, aber sauberer als ungefragt Spielerdaten zu verändern.
  - Danach `skillTreeService.refreshCache()`.

**Frontend**:
- `api.js`: `adminCreateSkillNode(node)` (POST), `adminDeleteSkillNode(id)`
  (DELETE).
- `SkillTreeAdminDialog.vue`:
  - Neuer Toolbar-Button "+ Node" (analog zum bestehenden
    "Verbinden"-Modus-Button) schaltet einen "Node erstellen"-Modus frei;
    Klick auf leere Canvas-Fläche (bisheriger `panEnd`-Leerklick-Pfad, der
    aktuell nur die Auswahl aufhebt) legt dort eine neue Node an. ID-Eingabe
    über einfachen `prompt()`-artigen Inline-Dialog oder Textfeld im
    Toolbar-Bereich (kein neues Modal — Stil des restlichen Editors ist
    Inline-Panels, kein Overlay-über-Overlay).
  - Neue Node startet mit sinnvollen Defaults (`branch: 'CORE'`,
    `nodeTier: 'PASSIVE'`, leere Effekt-Liste) — Feinschliff läuft über den
    bestehenden Effekt-Editor im Info-Panel, sobald die Node ausgewählt ist.
  - Lösch-Button im Info-Panel (`.sta-info`) neben den bestehenden Zeilen,
    mit Bestätigung (`confirm()` reicht, kein Custom-Dialog nötig für ein
    Dev-only-Tool) — verhindert versehentliches Löschen durch Fehlklick.
  - Nach erfolgreichem Löschen: Node aus `nodes.value` und betroffene Edges
    aus `edges.value` lokal entfernen (kein Full-Reload nötig, Editor macht
    das beim Node-Drag/Edge-Löschen bereits so).
  - i18n: neue Keys in `skillTreeAdminDialog.json` (de/en) für
    Button-Label, ID-Prompt, Lösch-Bestätigungstext, Erfolgsmeldungen.

## Verification

- Backend: `POST` mit neuer ID legt Node an, `GET /skilltree/nodes` zeigt
  sie; `POST` mit vergebener ID → 409; `POST` mit unbekanntem `effectType`
  → 400 (wie beim `PUT`-Pfad).
- `DELETE` auf frisch angelegte Node → weg aus `GET`-Liste, referenzierende
  Edges ebenfalls weg; `DELETE` auf Root-Node → 400; `DELETE` auf Node mit
  Spieler-Allokation → 409.
- Frontend end-to-end (Playwright wie beim Vorgänger-Plan): Node per Klick
  auf leere Fläche erstellen, Effekte im Info-Panel befüllen, Node löschen,
  nach Reload bestätigt persistent weg. `npm run check:palette` bleibt
  grün.

## Nicht im Scope

- Nodes klonen (siehe `docs/plans/2026-08-13-open-skillbaum-admin-node-clone.md`).
- Ganzer Baum als JSON exportieren/importieren (separater Roadmap-Punkt).
