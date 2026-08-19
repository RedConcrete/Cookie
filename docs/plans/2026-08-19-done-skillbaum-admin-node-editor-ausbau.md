# ✅ Skill-Baum Admin-Editor: Name/Branch/Tier/Icon editierbar, native Dialoge raus

> **Status:** ✅ Umgesetzt (2026-08-19, Commit 140734c)

## Context

Drei zusammenhängende Punkte, alle beim eigenen Testen des Admin-Editors
aufgefallen:

1. **Name nicht editierbar.** Selbst angelegte Node zeigte nur die ID statt
   eines Namens — `nameDe`/`nameEn` ließen sich im Editor nirgends setzen.
   Bekannter, in `docs/ROADMAP.md` dokumentierter Gap ("Werte-Editor:
   Name/Beschreibung/Branch/Tier").
2. **Effekt "MARKET FEE (EGGS) +100%" war tot.** Verifiziert im Code:
   `BuildingService.getEffectiveSellFeeRate` ruft
   `skillTreeService.getEffectTotal(userId, MARKET_FEE_REDUCTION, null)` —
   IMMER mit `targetResource=null`. `getEffectTotal`s Filter matcht nur
   `targetResource==null || equalsIgnoreCase(...)` — bei gesetztem `EGGS`
   und Query-Parameter `null` matcht das nie. Betrifft `BAKE_OUTPUT`,
   `MARKET_FEE_REDUCTION`, `WAGE_INTEREST_REDUCTION`, `STORAGE_CAP_BONUS`,
   `BUILDING_BUFFER_BONUS` — nur `HARVEST_YIELD` und
   `RESOURCE_WAGE_REDUCTION` sind wirklich pro-Ressource. Der Effekt-Editor
   ließ aber für jeden Effekttyp jede Ressource wählen → stille Dead-Nodes.
3. **Native Browser-Dialoge** (`window.prompt`/`window.confirm`) für
   ID-Eingabe und Lösch-Bestätigung — vom Nutzer per Screenshot zweimal
   explizit als "nicht mehr machen, halte dich ans Game-Design" moniert.

Zusätzlich: Node-Icon frei wählbar statt nur aus `branch` abgeleitet
(Nutzer-Entscheidung).

## Umsetzung

**Backend:**
- `SkillNodeEntity`: neues nullable Feld `icon` + Getter/Setter.
- `AdminConfigController.updateSkillNode`: `existing.setIcon(update.getIcon())`
  ergänzt (PUT kopiert Felder einzeln, war sonst nie persistiert worden).
- `SkillNodeStatusDto` (Spieler-Baum-DTO) + `SkillTreeService.getTreeStatus()`:
  `icon`-Feld ergänzt/gemappt, sonst kommt das Icon nie beim Spieler an.
- targetResource-Problem: kein Server-Fix (Verhalten bleibt), reines
  Frontend-UX-Problem behoben (Editor bietet ungültige Kombinationen nicht
  mehr an).

**Frontend:**
- Neue Komponente `frontend/src/components/pixel/PixelConfirmDialog.vue`
  (Vorbild `HardResetDialog.vue`: `.px-dialog-overlay` > `.px-panel` >
  `.px-titlebar` + Body + Cancel/Confirm), generisch parametrisiert
  (`title`, `body`, `confirmLabel`, `danger`), emits `confirm`/`close`.
- `SkillTreeAdminDialog.vue`:
  - Info-Panel: Name DE/EN (Text-Inputs), Beschreibung DE/EN (Textareas),
    Branch/Tier (Dropdowns), Icon (Dropdown, kuratierte Liste aus
    `PixelIcon.vue`s Icon-Set, "(automatisch)" = `null` → Branch-Fallback)
    — alles per `v-model` direkt auf `selectedNode`, bestehender
    "Speichern"-Button (`saveNode`, vormals `saveEffects`) persistiert das
    ganze Objekt wie schon zuvor.
  - `nodeIcon(n)`: prüft `n.icon` zuerst, danach wie bisher Root/Branch.
  - Effekt-Editor: `GLOBAL_ONLY_EFFECTS`-Set der 5 identifizierten Typen,
    targetResource-Dropdown wird für diese deaktiviert + Hinweistext,
    Reset auf `null` bei Typwechsel in einen global-only Typ.
  - `window.prompt` für ID raus: `generateNodeId()` (`node_<timestamp36>`
    bzw. `<source>_copy_<timestamp36>`) — Name wird direkt danach im
    editierbaren Panel gesetzt, kein Eingabedialog nötig.
  - `window.confirm` fürs Löschen raus: `pendingDelete`-Ref +
    `<PixelConfirmDialog>`.
- `SkillTreeView.vue` (Spieler-Baum): gleicher `nodeIcon()`-Fix, sonst
  wirkt ein im Admin gewähltes Icon nie beim Spieler.
- i18n (`skillTreeAdminDialog.json`, de/en): neue Keys für Name-/
  Beschreibung-/Icon-Label, `effectGlobalOnlyHint`, `nodeSavedNotice`
  (ersetzt `effectsSavedNotice`), Prompt-Keys entfernt (nicht mehr nötig).

## Verifikation

- `./mvnw -q -o compile` (Backend) — clean.
- `npm run build` + `npm run check:palette` (Frontend) — clean.
- Manueller Live-Test mit laufendem Dev-Stack steht aus (Sandbox ohne
  Postgres) — vor nächstem Einsatz: Node anlegen (kein Popup, Name direkt
  setzbar), MARKET_FEE_REDUCTION-Effekt → Ressourcen-Dropdown gesperrt,
  Löschen → Pixel-Dialog statt Browser-Popup, Icon wählen → erscheint auch
  im Spieler-Baum.

## Nicht im Scope

- Automatische Migration bestehender Nodes auf ein explizites `icon`
  (bleibt `null`, Branch-Fallback unverändert).
- Weitere `alert()`/`confirm()`/`prompt()`-Stellen — laut Recherche gab es
  im restlichen Frontend keine weiteren.
