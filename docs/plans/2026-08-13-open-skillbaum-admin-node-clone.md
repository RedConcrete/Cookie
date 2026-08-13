# ⏳ Skill-Baum Admin-Editor: Nodes klonen

> **Status:** ⏳ Offen

## Context

Aktuell muss jede neue Node im Admin-Editor einzeln von Hand angelegt und
komplett neu befüllt werden (Name, Branch, Tier, Effekte). Für ähnliche
Nodes (z. B. mehrere Rohstoff-Branch-Stufen mit fast identischen Effekten,
siehe `docs/plans/2026-08-12-done-skillbaum-rohstoff-poe-mesh.md`) ist das
viel Klickarbeit. Ziel: bestehende Node per Klon duplizieren (Werte/Effekte
übernehmen, neue ID, leicht versetzte Position, keine Kanten mitkopieren)
statt von Null anzulegen.

Setzt `docs/plans/2026-08-13-open-skillbaum-admin-nodes-crud.md` voraus
(braucht den dort neu geschaffenen `POST /admin/skilltree/nodes`-Endpoint —
kein eigener Backend-Endpoint nötig, Klonen ist rein Frontend-Logik auf
Basis des bestehenden Create-Endpoints).

## Umsetzung

**Backend**: keine Änderung — `POST /api/v1/admin/skilltree/nodes` aus dem
CRUD-Plan reicht (nimmt bereits eine komplette `SkillNodeEntity` inkl.
Effekt-Liste entgegen).

**Frontend** (`SkillTreeAdminDialog.vue`):
- Neuer Button "Klonen" im Info-Panel (`.sta-info`), neben dem in
  `2026-08-13-open-skillbaum-admin-nodes-crud.md` ergänzten Lösch-Button —
  nur sichtbar/aktiv, wenn eine Node ausgewählt ist.
- Klick fragt neue ID ab (gleicher Inline-Prompt wie beim "Node
  erstellen"-Flow aus dem CRUD-Plan — dieselbe Eingabe-UI wiederverwenden,
  kein zweites Muster einführen).
- Baut ein neues Node-Objekt:
  - `id`: die abgefragte neue ID.
  - `nameDe/nameEn/descriptionDe/descriptionEn/branch/nodeTier/requiresAllPrereqs`:
    1:1 von der Quell-Node übernommen.
  - `x/y`: Quell-Position + fester Versatz (z. B. `+40/+40`), damit die
    neue Node nicht exakt übereinander liegt und sofort sichtbar/greifbar
    ist.
  - `effects`: tiefe Kopie der Effekt-Liste der Quell-Node (neue Array-
    Objekte, kein geteilter Objekt-Reference — sonst würde Editieren der
    Kopie im Info-Panel versehentlich das Original mitändern, solange
    beide im selben `nodes.value`-Array reaktiv sind).
  - `isRoot`: immer `false` — ein geklonter Root würde einen zweiten
    Baum-Einstiegspunkt erzeugen, das ist nie gewollt.
  - Keine Edges — Kanten müssen bewusst neu gezogen werden (Klon-Node hat
    absichtlich noch keine Verbindung in den Baum).
- Ruft `adminCreateSkillNode(clone)` (aus dem CRUD-Plan) auf, hängt das
  Ergebnis an `nodes.value` an, selektiert die neue Node direkt
  (`selectedId.value = clone.id`) — Admin kann sofort weiter Position/
  Effekte feinjustieren, ohne erst manuell danach zu suchen.
- i18n: neue Keys in `skillTreeAdminDialog.json` (de/en) für Button-Label,
  ID-Prompt-Text, Erfolgsmeldung ("Node geklont").

## Verification

- Bestehende Node klonen, neue ID vergeben → neue Node erscheint versetzt
  neben dem Original, mit identischen Effekten/Branch/Tier, ohne Kanten.
- Effekt-Wert an der Kopie ändern und speichern → Original bleibt
  unverändert (verifiziert die tiefe statt flache Kopie).
- Root-Node klonen → Klon hat `isRoot: false` (kein zweiter Root im Baum).
- ID-Konflikt (bereits vergebene ID eingegeben) → gleiche 409-Fehlermeldung
  wie beim regulären Node-Erstellen, Klon bricht sauber ab statt die
  Original-Node zu überschreiben.
- `npm run check:palette` bleibt grün.

## Nicht im Scope

- Mehrfach-Klonen/Batch-Erstellung mehrerer Kopien auf einmal.
- Klonen inklusive Kanten (bewusste Entscheidung, siehe oben).
