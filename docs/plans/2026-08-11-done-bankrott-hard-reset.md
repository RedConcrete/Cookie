# ✅ Bankrott-Meldung + Hard Reset (Bankrott-Trigger & manuell in Settings)

> **Status:** ✅ Umgesetzt (2026-08-11, noch nicht committed)

## Context

Ein Spieler kann über den Dispo-Kredit (negative Cookies, Zinsen laufen weiter)
so tief in die Miesen geraten, dass seine Schulden den Restwert seines Accounts
(Ressourcen + Skill-Baum-Investition) übersteigen — er kommt rechnerisch nie
wieder raus. Aktuell gibt's dafür keine Meldung und keinen Ausweg außer dem
Dev-only Admin-Reset-Endpoint.

Ziel: automatische Bankrott-Meldung bei diesem Zustand, mit Bestätigung →
Hard Reset (alles zurück auf Start). Zusätzlich soll der Spieler jederzeit
selbst einen Hard Reset in den Einstellungen auslösen können (gleicher
Mechanismus, andere Trigger-Quelle).

**Abgestimmt mit dem User:**
- Reset-Umfang: **alles weg außer Lifetime-Stats** (Rangliste-Basis:
  `lifetimeSugarHarvested` etc., `lifetimeCookiesSpentOnMarket`,
  `lifetimeCookiesEarnedFromMarket`). Prestige-Level/totalPrestiges werden
  explizit MIT zurückgesetzt ("das System gibt es so nicht mehr").
  Steam-Identität (displayName/avatar/steamId/token) bleibt unangetastet.
- Bankrott-Dialog ist **wegklickbar** und kommt beim nächsten Wage-Poll-Tick
  (alle 15s) wieder, solange der Zustand anhält — kein hartes Blockieren.
- Bankrott-Bedingung: **NetWorth < 0**. Das ist mathematisch identisch zu
  "Schulden > Restwert" (NetWorth = Cookies(negativ bei Schulden) +
  Ressourcenwert + Skill-Baum-Wert; wird < 0 genau dann, wenn die Schulden den
  Rest übersteigen). NetWorth wird im Frontend bereits live client-seitig
  berechnet (`frontend/src/stores/player.js:78`) — kein neuer Backend-Endpoint
  nötig für die Erkennung.

## Backend

**1. Neue Repository-Methoden (derived, je ein Einzeiler):**
- `repository/BakeJobRepository.java`: `void deleteByUserId(String userId);`
- `repository/NetWorthHistoryRepository.java`: `void deleteByUserId(String userId);`
- `repository/WageLedgerRepository.java`: `void deleteByUserId(String userId);`

**2. Neuer Service `service/PlayerResetService.java`** mit `hardReset(userId)`
(`@Transactional`): setzt Cookies/Ressourcen/Skillpunkte/Prestige auf
`PlayerConfig`-Startwerte zurück, löscht Skill-Baum/Gebäude/Bake-Jobs/
Net-Worth-Verlauf/Lohn-Historie. Lifetime-Felder und Steam-Identität bleiben
unangetastet.

**3. Neuer Endpoint `POST /api/v1/users/{userId}/hard-reset`** in
`UserController.java`, Self-Service ohne Admin-Token (gleiches Muster wie
`createUser`/`getUser`/`deleteUser`).

## Frontend

1. `api.js`: `hardResetPlayer(steamId)`.
2. `player.js`: `isBankrupt = computed(() => netWorth.value < 0)`.
3. Neue `HardResetDialog.vue` (Overlay/Panel-Pattern wie `CreditsDialog.vue`),
   Prop `mode: 'bankruptcy' | 'manual'`, Bestätigen-Button (rot, `--px-red`)
   ruft `hardResetPlayer` + `playerStore.init()` auf, Abbrechen schließt nur.
4. `FarmGridView.vue`: in `pollWageStatus()` bei `playerStore.isBankrupt`
   Dialog öffnen (reappearing bei jedem Tick, solange Zustand anhält).
5. `SettingsDialog.vue`: neue "Danger Zone"-Sektion mit manuellem Hard-Reset-
   Button, öffnet denselben Dialog im `manual`-Modus.
6. i18n: `hardResetDialog.json` (DE/EN) neu, `settings.json` um
   `dangerZone`/`hardResetButton` ergänzt, `common.confirm`/`common.cancel`
   (bisher ungenutzt) für die Dialog-Buttons wiederverwendet.

## Nicht-Ziele

- Kein neuer Backend-Poll/DTO für Bankrott-Status, rein client-seitig aus
  vorhandenem NetWorth abgeleitet.
- Keine Steam-Auth-Verifizierung für den neuen Endpoint (bestehende,
  dokumentierte Lücke, siehe `docs/ROADMAP.md` Abschnitt 0).
- Bestehender Dev-Reset (`/api/v1/admin/reset/{userId}`) bleibt unverändert,
  keine Vermischung mit dem neuen Self-Service-Endpoint.

## Verifikation

1. Neuer Endpoint erreichbar, Backend kompiliert/startet fehlerfrei.
2. Testaccount in die Miesen bringen → Bankrott-Dialog erscheint automatisch,
   nach Wegklicken kommt er beim nächsten Tick wieder.
3. Bestätigen → alles zurück auf Start, Lifetime-Stats bleiben, Verlaufsgraph/
   Lohn-Historie geleert.
4. Settings-Button separat testen (ohne vorherigen Bankrott).
5. `cd frontend && npm run check:palette` bleibt grün.
