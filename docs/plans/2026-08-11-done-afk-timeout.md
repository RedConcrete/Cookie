# ✅ AFK-Timeout: Wirtschaft pausieren + zurück ins Hauptmenü

> **Status:** ✅ Umgesetzt (2026-08-11, noch nicht committed)

## Context

Aktuell rechnet der `WageScheduler` (Backend, alle 60s) für JEDEN Spieler Lohn/
Zinsen ab — unabhängig davon, ob der Tab offen, im Hintergrund oder das Spiel
gerade unbeaufsichtigt läuft. Laut Design-Doc (§1, §5) ist Hintergrund-Accrual
grundsätzlich gewollte Idle-Game-Mechanik — aber der User will explizit NICHT
unnötig Cookies verlieren, wenn er tatsächlich am Rechner nichts tut (Tab offen,
keine Interaktion). Abgestimmt: echte Pause der Wirtschaft (nicht nur UI-
Rückkehr ins Menü), Timeout 10 Minuten, Maus-Hover übers Feld (Ernten) zählt
als Aktivität.

Sekundärer Nutzen (User-Begründung): Server entlasten, wenn eh keiner spielt.

## Backend

1. `entity/UserEntity.java` — neues Feld `lastHeartbeatAt` (plain, nullable
   `LocalDateTime`, wie `lastActiveAt` — kein `NOT NULL`, siehe
   `docs/ROADMAP.md` Abschnitt 0 zum `ddl-auto=update`-Fallstrick).
2. `repository/UserRepository.java` — `findByLastHeartbeatAtAfter(cutoff)`.
3. `service/UserService.java` — `recordHeartbeat(userId)`, analog `recordLogin`.
4. `controller/UserController.java` — `POST /{userId}/heartbeat` (Self-Service,
   kein Admin-Token, wie `/hard-reset`).
5. `config/GameBalanceConfig.java` — `afkTimeoutMinutes = 10` (live editierbar
   über bestehendes `/api/v1/admin/config`).
6. `controller/ConfigController.java` — `afkTimeoutMinutes` in `/api/v1/config`.
7. `scheduler/WageScheduler.java` — `deductWages()` nutzt
   `findByLastHeartbeatAtAfter(now - afkTimeoutMinutes)` statt `findAll()`.
   AFK-Spieler bekommen diesen Tick keinen Lohn-/Zins-Tick. Passive Gebäude-
   Produktion bleibt unangetastet (Gewinn-, nicht Verlust-Seite).

## Frontend

1. `services/api.js` — `heartbeatPlayer(steamId)`.
2. Neue `composables/useIdleTimeout.js` (Singleton wie `useAudio.js`):
   globale Aktivitäts-Listener (`mousemove`/`mousedown`/`keydown`/`wheel`),
   eigenes ~20s-Intervall sendet Heartbeat solange aktiv, sonst
   `isAfk.value = true` + eigene Listener/Intervall abräumen. Timeout aus
   `/api/v1/config` (Fallback 10min).
3. `App.vue` — `idle.start()` in `startGame()`, `watch(isAfk)` → 
   `started.value = false` + `bakeStore.stop()` (existiert schon, ungenutzt)
   + `disconnectMarketWebSocket()` (existiert schon, ungenutzt) + `idle.stop()`.
   Rückkehr über bestehenden "Start Game"-Button in `MainMenuView` braucht
   keinen Extra-Code (bestehende Init-Kette ist idempotent).

## Nicht-Ziele

- Kein Warn-Dialog vor dem Rauswurf, stiller Sprung ins Menü.
- Passive Gebäude-Produktion pausiert nicht.
- Geschlossener Tab/App: kein Sonderfall, Heartbeat bleibt einfach aus und
  Spieler gilt nach Ablauf automatisch als AFK.

## Verifikation

1. Backend kompiliert/startet, `/heartbeat` liefert 204, `lastHeartbeatAt`
   wird gesetzt.
2. `afkTimeoutMinutes` testweise klein setzen, im Spiel nichts tun → Sprung
   ins Hauptmenü, Websocket schließt, kein weiteres Wage-Polling.
3. Kein Lohn-/Zins-Tick für den AFK-Spieler im Backend-Log/DB.
4. Mausbewegung/Hover kurz vor Ablauf resettet den Timer.
5. Nach Rauswurf "Start Game" → alles läuft normal wieder an.
