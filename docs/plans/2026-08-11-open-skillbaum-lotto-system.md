# ⏳ Lotto-System (stündliche Zahlen-Lotterie, Skill-Baum-Freischaltung)

> **Status:** ⏳ Offen

## Context

Der Spieler wünscht ein Lotto-System als neuen Cookie-Sink/Zeitvertreib: über den
Passiv-Skill-Baum freischaltbar (analog DISPO/STORAGE-Branches), stündliche Runden,
gemeinsamer Pot, Zahlen-Rateformat, Live-Benachrichtigung. Es existiert noch **kein**
Notification-System im Spiel (kein Postfach, kein Toast) — das ist Teil dieses Plans.

Im Gespräch geklärte Design-Entscheidungen (Nutzer-Antworten auf Rückfragen):
- **Zahlenformat:** 4 Zahlen aus einem Pool von 16 (Basis-Chance 1/C(16,4) = 1/1820).
  Pool wächst mit Teilnehmerzahl (macht Gewinnen schwerer, wie vom Nutzer gefordert).
- **Pot/Ticketpreis:** Pot-Basiswert pro Runde zufällig zwischen 10.000 und
  100.000.000 Cookies (log-verteilt gewürfelt), Ticketpreis wird aus diesem Wert
  abgeleitet — höherer Rundenwert = teureres Ticket. Alle Zahlen sind
  Balancing-Platzhalter (Projekt-Konvention, siehe `docs/ROADMAP.md` §4), live über
  Admin-API nachjustierbar.
- **Zusatz-Tickets:** über weitere Skill-Baum-Knoten (nicht käuflich).
- **Benachrichtigung:** Live-WebSocket-Popup (kein persistiertes Postfach) — neuer
  Broadcast-Kanal, Frontend filtert client-seitig, ob der Popup angezeigt wird (Spieler
  muss den Skill haben). Wer offline ist, verpasst die Nachricht — akzeptiert.
- **2 gleiche Gewinnzahlen → Pot halbiert** wird verallgemeinert: bei K Gewinnern wird
  der Pot gleichmäßig durch K geteilt.
- **Niemand nimmt teil / niemand trifft →** Pot verfällt als reiner Wirtschafts-Sink,
  ein zufälliger "imaginärer Spieler"-Flavor-Name wird als Gewinner verkündet.

## Backend

### Config (`config/GameBalanceConfig.java`)
Neue Felder im bestehenden `balance.*`-Bean (gleiches Muster wie `debtInterestRate` etc.,
mit Javadoc-Kommentar "Balancing-Platzhalter"):
`lottoTicketPriceRatio` (0.005), `lottoMinTicketPrice`, `lottoMaxTicketPrice`,
`lottoBasePoolSize` (16), `lottoMaxPoolSize` (40), `lottoEntriesPerPoolGrowth` (3),
`lottoRoundBasePotMin` (10000), `lottoRoundBasePotMax` (100000000).
`application.properties`: neuer Abschnitt `# LOTTO-KONFIGURATION` mit den `balance.lotto-*`-Keys.

### Enum (`enums/EffectType.java`)
Neuer Wert `LOTTO_TICKET_BONUS`. Unkritisch bzgl. des dokumentierten CHECK-Constraint-Bugs
(`docs/ROADMAP.md` §0), da `SkillNodeEffectEntity.effectType` bereits ein reiner `String`
ohne `@Enumerated` ist.

### Entities (neu, Package `entity/`)
- `LottoRoundEntity` — id (UUID-String wie `MarketEntity`), `status` (Plain-String
  `"OPEN"`/`"CLOSED"`, bewusst kein `@Enumerated` — gleicher Grund wie oben), `roundBasePot`,
  `ticketPrice`, `poolSize`, `potCookies`, `openedAt`/`drawAt` (`LocalDateTime`),
  `winningNumbers` (CSV-String, 4 Werte), `imaginaryWinnerKey` (nullable String),
  `@Version private long version = 0` (von Anfang an gesetzt — vermeidet das dokumentierte
  NOT-NULL-ohne-Default-Nachrüst-Problem von `market_stock`/`player_buildings`).
- `LottoEntryEntity` — id (UUID), `roundId`, `userId`, `numbers` (CSV, 4 distinkte Ints),
  `pricePaid`, `enteredAt`, `won`/`payout` (für Historie).

### Repositories (neu)
- `LottoRoundRepository` — `findByStatus(String)`, `findAllByOrderByOpenedAtDesc(Pageable)`.
- `LottoEntryRepository` — `findByRoundIdAndUserId(...)`, `findByRoundId(...)`,
  `countByRoundIdAndUserId(...)`.

### DTOs (neu, Package `dto/`)
`LottoRoundStatusDto` (pot, ticketPrice, poolSize, drawAt, playerTicketsAvailable,
playerTicketsUsed, playerEntries), `LottoEntryRequestDto` (`List<Integer> numbers`),
`LottoRoundHistoryDto` (roundId, closedAt, winningNumbers, finalPot, winners, imaginaryWinnerKey),
`LottoWinnerDto` (displayName, payout), WS-Nachrichten mit Discriminator-Feld `type`
(`ROUND_OPEN`/`ROUND_RESULT`).

### Service (neu `service/LottoService.java`)
Injiziert `LottoRoundRepository`, `LottoEntryRepository`, `UserRepository`,
`SkillTreeService` (Ticket-Kontingent über `getEffectTotal(userId, LOTTO_TICKET_BONUS, null)`),
`GameBalanceConfig`, `LottoWebSocketHandler`. `ReentrantLock` analog `MarketService.marketLock`
gegen Race Conditions bei `poolSize`-Neuberechnung.

- `closeCurrentRoundAndOpenNew()` — vom Scheduler aufgerufen: schließt offene Runde (Ziehung,
  Gewinner-Ermittlung, Pot-Split, Broadcast `ROUND_RESULT`), öffnet danach sofort eine neue
  (Pot-Basiswert log-uniform würfeln, Ticketpreis ableiten, `poolSize` = Basis zurücksetzen,
  Broadcast `ROUND_OPEN`).
- `getCurrentStatus(userId)` — baut `LottoRoundStatusDto`.
- `enterRound(userId, numbers)` — validiert Runde OPEN, 4 distinkte Zahlen in `[1..poolSize]`,
  freies Ticket-Kontingent, ausreichend Cookies (Ablehnung via `IllegalArgumentException`,
  gleiches Muster wie `MarketService`/`SkillTreeService.buySkillPoint`, landet automatisch als
  400 im `GlobalExceptionHandler`). Bei Erfolg: Cookies abziehen, Entry speichern, Pot erhöhen,
  `poolSize` neu berechnen (`min(maxPoolSize, basePoolSize + floor(entryCount/entriesPerPoolGrowth))`)
  und persistieren — die Ziehung nutzt später den zu Rundenschluss aktuellen Wert, einheitlich
  für alle Teilnehmer der Runde.
- `getHistory(limit)`.
- Ziehungslogik: 4 distinkte Zufallszahlen aus `[1..poolSize]`, Entries auf exakte
  Mengen-Übereinstimmung prüfen, Pot gleichmäßig auf K Gewinner aufteilen (Rundungsrest bleibt
  unverteilt, bewusst nicht weiter optimiert). Bei 0 Treffern: zufälliger Flavor-Key aus einem
  kleinen festen Array (z. B. `"baker_bob"`, `"night_owl"`, ...) — **kein** Klartext, reiner Key
  (siehe i18n unten).

### Scheduler (neu `scheduler/LottoScheduler.java`)
`@Scheduled(cron = "0 0 * * * *")`, ruft `lottoService.closeCurrentRoundAndOpenNew()` — exakt
im Stil von `MarketScheduler`. Zusätzlich `@PostConstruct` in `LottoService` (analog
`SkillTreeService.seedTree()`), das beim Boot eine Runde öffnet, falls keine `OPEN`-Runde
existiert (sonst ist die allererste Stunde nach Deploy ohne Runde).

### WebSocket
- Neu `handler/LottoWebSocketHandler.java` — 1:1-Struktur von `MarketWebSocketHandler`
  (`CopyOnWriteArrayList<WebSocketSession>`, Broadcast-an-alle, kein Session-zu-User-Mapping
  nötig, da rein informativ und Frontend selbst filtert).
- `config/WebSocketConfig.java` — `LottoWebSocketHandler` injizieren,
  `registry.addHandler(lottoWebSocketHandler, "/ws-lotto")` ergänzen (analog `/ws-market`,
  Zeile ~21).

### Controller
Neu `controller/LottoController.java`, `@RequestMapping("/api/v1/lotto")`, `userId` als
Pfad-/Query-Parameter ungeprüft entgegengenommen (bestehende Lücke, `docs/ROADMAP.md` §0,
hier nicht mit angegangen):
- `GET /current?userId=` → `LottoRoundStatusDto`
- `POST /enter/{userId}` (Body `LottoEntryRequestDto`) → aktualisierter Status
- `GET /history?limit=` → `List<LottoRoundHistoryDto>`

`controller/AdminController.java` — neuer Dev-Endpoint `POST /api/v1/admin/lotto/tick`
(gleiches Dev-Mode/Token-Gating wie `/market/reset`), ruft `closeCurrentRoundAndOpenNew()`
direkt auf — einziger Weg, das System zu testen, ohne eine Stunde zu warten.

### Skill-Baum-Erweiterung (`service/SkillTreeService.java`)
- `buildNodes()`: `lotto_1`..`lotto_4` (PASSIVE/PASSIVE/PASSIVE/KEYSTONE), linear, kein Fork
  (mirrort MARKET-Branch). Jeder Knoten: `Effect(LOTTO_TICKET_BONUS, null, 1)`. Name/Beschreibung
  DE+EN direkt als Seed-Literal (DB-Content-Konvention, §9 "Zweisprachige Knoteninhalte").
- `buildEdges()`: `root→lotto_1→lotto_2→lotto_3→lotto_4`.
- **Radiales Layout: 11→12 Branches.** Bestehende 11 Branches liegen bei 360°/11≈32,7°-Abständen
  (siehe `docs/cookie-game-design.md` §9). Für LOTTO als 12. Branch: **alle** Branches auf
  360°/12=30° neu verteilen (exakt das Vorgehen, das beim Hinzufügen von STORAGE als 11. Branch
  bereits einmal angewendet wurde — "radiales Neuverteilen auf 360°/n ist die Standard-
  Vorgehensweise bei jedem weiteren Branch"). Radius pro Tier unverändert (150/300/450/600).
  Vor dem Festlegen: Kollisions-/Kreuzungs-Check wie beim letzten Layout-Pass (§9 beschreibt das
  Skript-Vorgehen).
- `docs/cookie-game-design.md` §9 aktualisieren: LOTTO-Branch-Eintrag + neue Bearing-Tabelle,
  im Stil der bestehenden Branch-Liste.
- **Wichtig:** `seedTree()` zieht nur *fehlende* IDs nach — die x/y-Neupositionierung der
  bestehenden 11 Branches greift auf einer bereits befüllten DB **nicht** automatisch (siehe
  Migrations-Hinweis unten).

### Frontend-Icon für Keystone
Neues Keystone-Icon (Ticket/Würfel-Glyphe, ausschließlich Fruitpunch24-Palette) in
`PixelIcon.vue`'s Icon-Map + `KEYSTONE_ICON`-Map in `SkillTreeView.vue` (`lotto_4: 'keystoneLotto'`),
plus `BRANCH_ICON`-Eintrag für `LOTTO`.

## Frontend

### WebSocket-Client (`services/websocket.js`)
Neues Funktionspaar `connectLottoWebSocket(onMessage)`/`disconnectLottoWebSocket()`, eigener
Socket (separate Modul-Variablen), gleiches Auto-Reconnect-Verhalten wie
`connectMarketWebSocket`. URL über `VITE_WS_URL`-Basis + `/ws-lotto`.

### Store (`stores/player.js`)
- In `init()`: `connectLottoWebSocket(...)` neben `connectMarketWebSocket` aufrufen, leitet
  Nachrichten an den neuen Lotto-Store weiter (`type === 'ROUND_OPEN'` /
  `'ROUND_RESULT'` → Toast auslösen + `currentRound` aktualisieren).
- Neuer Computed `lottoTicketsOwned`: `n.effects.some(e => e.effectType === 'LOTTO_TICKET_BONUS')`
  über alle allozierten Knoten summiert, **nicht** das flache `n.effectType`-Muster aus
  `FarmGridView.vue#harvestBonus()` kopieren — Gegencheck der DTOs
  (`SkillNodeStatusDto`/`SkillEffectDto`) zeigt, dass Effekte verschachtelt unter `n.effects[]`
  liegen, `n.effectType` existiert auf dem Node selbst gar nicht. `harvestBonus()` scheint damit
  ein bestehender, von diesem Plan unabhängiger Bug zu sein (Ernte-Boni greifen im Client
  vermutlich nie) — **nicht Teil dieses Plans**, aber separat erwähnenswert für den Nutzer.

### Neuer Store (`stores/lotto.js`)
Pinia-Store analog `stores/market.js`: `currentRound`, `history`, Actions `loadCurrent()`,
`enter(numbers)`, `loadHistory()`, WS-Handler halten `currentRound` live aktuell.

### Toast-Primitive (neu)
`composables/useToast.js` + `components/ToastStack.vue` — bewusst generisch (nicht
Lotto-spezifisch), da der Mehraufwand minimal ist und zukünftige Features (Achievements,
Fehleranzeigen) das wiederverwenden können. Struktur mirrort `useWageNumbers.js` (Modul-weiter
gemeinsamer `ref([])`, hier aber `setTimeout`-Auto-Dismiss statt rAF-Animation, da diskretes
Show/Hide statt kontinuierlicher Bewegung). `ToastStack.vue` fixed-position Stack, ausschließlich
Fruitpunch24-Farben. Einbindung in `FarmGridView.vue` neben `<WageNumbers />`.

### Dialog + View (neu)
`components/LottoView.vue` (Pot, Ticketpreis, Pool-Größe, Countdown bis `drawAt`,
1..poolSize Zahlen-Grid zum Anklicken von 4 Zahlen, Ticket-Kontingent used/available,
scrollbare Historie via `PixelScrollBox` wie in `StatsDialog.vue`) +
`components/LottoDialog.vue` (dünner Wrapper, gleiches Muster wie `StatsDialog.vue`).

### Menü-Einbindung (`views/FarmGridView.vue`)
- Neuer Button `v-if="lottoTicketsOwned > 0"` bei den anderen `hud-menu-item`-Buttons (~Zeile 63),
  `selectMenu('lotto')`.
- `<LottoDialog v-if="dialog === 'lotto'" @close="dialog = null" />` bei den anderen Dialogen
  (~Zeile 153).
- `<ToastStack />` neben `<WageNumbers />`.
- Neuer i18n-Key `lottoLabel` in `farmGridView.json` (de+en).

### i18n
Neu `i18n/locales/{de,en}/lottoDialog.json` — flache camelCase-Keys (Konvention siehe
`statsDialog.json`): title, potLabel, ticketPriceLabel, poolSizeLabel, drawInLabel,
ticketsAvailableLabel, historyTitle, submitButton, sowie `imaginaryWinner_<key>`-Einträge für
jeden Backend-Flavor-Key (Backend sendet nur den Key, nie Klartext — gleiche Konvention wie
`buildingInfo.js`, siehe `CLAUDE.md`-Abschnitt Lokalisierung). Toast-Texte (Rundenstart/-ergebnis)
entweder in derselben Datei oder eigenes `lottoToast.json`, je nach Umfang.

## DB-Migrations-Hinweise

- Neue Tabellen `lotto_rounds`/`lotto_entries` sind unter `ddl-auto=update` unkritisch (starten
  leer, kein NOT-NULL-Nachrüst-Problem). `@Version` von Anfang an gesetzt.
- Die x/y-Neupositionierung der 11 bestehenden Skill-Branches wird von `seedTree()` **nicht**
  rückwirkend angewendet (nur fehlende IDs werden nachgezogen). Lokal: `skill_nodes`/
  `skill_edges`/`player_skill_nodes` droppen und neu seeden lassen (DB gilt laut Projekt-
  Konvention als disposable). **Live-Server:** gleicher Schritt nötig, sobald deployed — dort
  gehen dabei alle Spieler-Allokationen im Skill-Baum verloren (wie bei früheren
  Skill-Baum-Umbauten dokumentiert, z. B. 2026-08-10).

## Verifikation

1. Backend im Dev-Modus starten, sauberer Boot (kein CHECK-Constraint-Crash durch den neuen
   `EffectType`-Wert, siehe oben).
2. `GET /api/v1/lotto/current?userId=DEV_PLAYER_001` — Runde ist automatisch offen (Boot-Open).
3. `lotto_1` im Skill-Baum freischalten (Dev), Menüpunkt "Lotto" erscheint im Hamburger-Menü.
4. `POST /api/v1/lotto/enter/DEV_PLAYER_001` mit 4 Zahlen — Cookies abgezogen, Pot gestiegen,
   `poolSize` ggf. gewachsen.
5. `POST /api/v1/admin/lotto/tick` wiederholt aufrufen (kein Token nötig im Dev-Modus) —
   erzwingt Ziehung+Neustart ohne Warten. Verifiziert: `ROUND_RESULT`-Broadcast über `/ws-lotto`
   (Browser-DevTools oder simpler WS-Client), Gewinner-/Imaginär-Gewinner-Logik bei 0 vs. 1+
   Teilnehmern.
6. Frontend: `LottoDialog` öffnen, Zahlen einreichen, nach Admin-Tick Toast-Popup beim
   `ROUND_RESULT` beobachten.
7. `cd frontend && npm run check:palette` nach neuem Toast-/Dialog-CSS und Keystone-Icon.
8. Visueller Check des 12-Branch-Skill-Baum-Layouts auf Überlappungen (Kollisions-Skript aus
   `docs/plans/2026-08-10-*skillbaum*` falls vorhanden wiederverwenden).

### Kritische Dateien
- `backend/.../service/SkillTreeService.java` (Branch-Layout, Node-Seeds)
- `backend/.../config/GameBalanceConfig.java` (neue Balance-Felder)
- `backend/.../handler/MarketWebSocketHandler.java` (Vorlage für `LottoWebSocketHandler`)
- `backend/.../service/MarketService.java` (Validierungs-/Lock-Muster)
- `frontend/src/views/FarmGridView.vue` (Menü, Dialog-Liste, ToastStack-Einbindung)
- `frontend/src/stores/player.js` (WS-Wiring, `lottoTicketsOwned`)
- `frontend/src/composables/useWageNumbers.js` (Vorlage für `useToast.js`)

Nach Umsetzung: Datei umbenennen (`open`→`done`), beide Status-Marker aktualisieren, Commit-Hash
ergänzen — Datei landet final unter `docs/plans/` (Projekt-Konvention).
