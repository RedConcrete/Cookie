# ⏳ Skillbaum: Markt-Limit-Orders (Order-Buch, letzter Tick sichtbar)

> **Status:** ⏳ Offen

## Context

User-Anfrage (2026-08-14): der Markt soll Kauf-/Verkaufsaufträge im letzten
Tick einsehbar machen. Das Feature hängt am bestehenden offenen Plan
[[2026-08-10-open-skillbaum-automatisierung]] ("Automatisierung"), der bereits
einen `market_5`-Keystone "Handelsvollmacht" (`AUTO_SELL_UNLOCK`, prozentualer
Auto-Verkauf gegen die AMM-Kurve bei Lager-Füllstand) und `market_6`
"Großhändler-Kontakte" (`AUTO_SELL_MAX_PERCENT_BONUS`) plant. Dieser Plan hier
ist eine **eigene Erweiterung des MARKET-Branches** hinter diesen beiden
Knoten, kein Ersatz — Handelsvollmacht bleibt exakt wie dort beschrieben.

Rückfragen vor diesem Plan wurden per Auswahl geklärt:

1. **Mechanik:** Limit-Orders gegen den gemeinsamen AMM-Pool — kein
   Peer-to-Peer-Matching zwischen Spielern. Ein Auftrag löst automatisch aus,
   sobald der AMM-Spotpreis die vom Spieler gesetzte Ziel-Schwelle erreicht.
2. **"Im letzten Tick einsehen":** Snapshot der aktuell offenen Aufträge
   (eigene + fremde), aktualisiert im selben Takt wie der bestehende
   Markt-Preis-Tick.
3. **Skill-Gating:** kein Umwidmen von `market_5`/`market_6` — neuer,
   eigenständiger Teil des Baumes, der hinter den bereits geplanten Knoten
   weitergeht (nicht an der Wurzel neu ansetzen). Genauer Zuschnitt unten.
4. **Preisbildung:** an den AMM-Spotpreis gebunden — der Spieler setzt nur
   die Auslöse-Schwelle, der tatsächliche Fill-Preis ist immer der reale
   AMM-Preis im Auslöse-Moment (`buyCost`/`sellPayout`), kein frei erfundener
   Preis, keine Schatten-Ökonomie neben dem Hauptmarkt.

**Wichtige eigene Interpretation, die beim Bauen nochmal gegengecheckt werden
sollte:** da es *kein* Peer-Matching gibt, kann "von anderen Spielern
einsehen und **nutzen**" (Original-Anfrage) nicht heißen, fremde Orders direkt
auszuführen. Gemeint ist hier: das Order-Buch zeigt allen Skill-Inhabern die
offenen Kauf-/Verkaufsschwellen aller Spieler an (aggregierte Markttiefe/
-stimmung), das ist die "Nutzung" — als Signal für die eigenen Trades, nicht
als direkte Interaktion mit fremden Aufträgen. Falls das nicht gemeint war
(z. B. doch echtes Peer-Matching gewünscht), vor dem Bauen nochmal klären.

Verifizierter Ist-Zustand (vollständig recherchiert):

- **Kein Order-Buch, keine Limit-Orders existieren bisher.** Jeder Trade ist
  ein sofortiger, synchroner Request-Response-Vorgang
  (`MarketService.performAction`, Zeilen 231-314).
- **AMM-Kernformeln:** `poolConstant`/`spotPrice`/`buyCost`/`sellPayout`
  (Zeilen 558-624) — `price = K / stock²`, Kauf/Verkauf verschieben `stock`
  und leiten den neuen Preis direkt daraus ab
  (`createNewMarketEntryAfterTrade`, Zeilen 320-357). Diese Funktionen sind
  wiederverwendbar für die Order-Ausführung, kein neuer Preis-Pfad nötig.
- **Markt-Tick ist real vorhanden:** `MarketScheduler`
  (`@Scheduled(fixedRateString = "${market.update-interval-ms:2000}")`, Standard
  2000ms) ruft `MarketService.applyRandomPriceFluctuation()` — das ist der
  Takt, an den sich "im letzten Tick" natürlich anlehnt, sowohl für die
  Order-Auslösung als auch für die Frontend-Anzeige.
- **`marketLock` (ReentrantLock)** schützt jeden Stock-/Preis-Schreibzugriff,
  ist reentrant (verschachtelte `lock()`-Aufrufe blockieren sich nicht
  gegenseitig) — Order-Prüfung/-Ausführung muss im selben Lock laufen wie
  `performAction`/`applyRandomPriceFluctuation`.
- **Trade-Cooldown** (`marketConfig.getTradeCooldownMs()`, geprüft in
  `performAction` Zeilen 252-257) gilt nur für manuelle Spieler-Trades. Die
  Automatisierungs-Plan-Konvention ("Automatisierung soll nicht am
  Spieler-Cooldown scheitern") gilt hier genauso für Order-Ausführung.
- **MARKET-Branch aktueller Stand:** `market_1`–`market_4` bereits gebaut
  (Gebührenreduktion, `SkillTreeService.java` Zeilen 266-277), `market_5`/
  `market_6` erst geplant (siehe oben). Dieser Plan hängt seine neuen Knoten
  **hinter `market_6`** an — baut also zwingend auf dem
  Automatisierungs-Plan auf, muss nach ihm umgesetzt werden (sonst gibt es
  noch keine `market_6`-Kante zum Anhängen).
- **Sechs Ressourcen:** SUGAR, FLOUR, EGGS, BUTTER, CHOCOLATE, MILK
  (`frontend/src/views/MarketView.vue` Zeile 202, `ResourceName`-Enum
  Backend-seitig identisch).
- **Kein WebSocket-Push für Marktpreise** — Frontend holt Preise/Config über
  REST (`services/api.js`), kein Extra-Kanal nötig für die neue
  Order-Liste, einfaches Polling im selben Takt reicht.

## Design-Entscheidungen

### 1. Neue Skill-Knoten hinter `market_6`

- `market_7` "Terminkontrakte" / "Forward Contracts", **KEYSTONE** (dritter
  Keystone im MARKET-Branch — `market_4` und `market_5` sind bereits welche;
  "höchstens 1-2 Keystones pro Branch" aus der Fundament-Konvention ist damit
  eigentlich ausgereizt. **Zu klären beim Bauen:** entweder als Ausnahme
  begründen (Order-Buch ist mechanisch klar abgegrenzt von Auto-Verkauf) oder
  `market_7` als NOTABLE statt KEYSTONE einstufen). Schaltet
  `EffectType.MARKET_ORDER_UNLOCK` frei (Bool-Effekt, `value = 1.0`, wie die
  bestehenden Unlock-Typen aus dem Automatisierungs-Plan).
- `market_8` "Erweiterte Kontingente" / "Extended Order Capacity",
  **PASSIVE**, linear hinter `market_7`: `EffectType
  .MARKET_ORDER_SLOT_BONUS` (+1 gleichzeitig offene Order je Knoten,
  Basis-Kontingent ohne diesen Knoten z. B. 1 — Balancing-Platzhalter, wie
  in den anderen Skillbaum-Plänen üblich).
- Koordinaten nicht vorgegeben — wie in allen bisherigen Plänen erst beim
  Bauen anhand des dann aktuellen Layouts bestimmen, danach durchs
  Kollisions-/Kreuzungs-Skript gegen alle bestehenden Knoten/Kanten prüfen.

### 2. Order-Modell: Limit-Order gegen den AMM-Pool, kein Escrow

- Neue Entity `MarketOrderEntity`: `id`, `userId`, `resource`
  (`ResourceName`), `action` (`BUY`/`SELL`, wiederverwendet
  `MarketAction`-Enum), `triggerPrice` (double), `amount` (double),
  `status` (`OPEN`/`FILLED`/`CANCELLED`), `createdAt`, `resolvedAt`.
- **Bewusst kein Escrow** (keine Vorab-Reservierung von Ressourcen/Cookies
  bei Order-Anlage) — passt zum bestehenden Automatisierungs-Muster (Auto-
  Verkauf reserviert auch nichts vorab, prüft nur zum Ausführungszeitpunkt).
  Löst die Order zum Zeitpunkt des Auslösens aus und im Lager/Kontostand
  fehlt was Nötiges (z. B. weil der Spieler die Ressource zwischenzeitlich
  manuell verkauft hat), wird die Order **kommentarlos auf `CANCELLED`
  gesetzt** (kein Fehler-Log-Spam, analog zur "stilles Skip"-Konvention aus
  dem Automatisierungs-Plan für Auto-Backen).
- **Validierung bei Order-Anlage** (serverseitig, Client-Wert nie
  vertrauen):
  - `MARKET_ORDER_UNLOCK`-Effekt muss > 0 sein, sonst 403.
  - `amount > 0`, `triggerPrice > 0`.
  - Anzahl offener Orders des Spielers < `maxOpenOrders` (Basis-Kontingent +
    `MARKET_ORDER_SLOT_BONUS`-Summe), sonst 400.
  - **Richtungsprüfung gegen aktuellen Spotpreis** (verhindert Orders, die
    sofort auslösen würden und den normalen Kauf/Verkauf umgehen): bei
    `BUY` muss `triggerPrice` < aktueller Spotpreis sein (kaufen, wenn
    Preis fällt), bei `SELL` muss `triggerPrice` > aktueller Spotpreis sein
    (verkaufen, wenn Preis steigt). Verstoß → 400 mit Hinweis, stattdessen
    den normalen Kauf/Verkauf zu nutzen.
- Kein separater Fill-Preis-Parameter — Ausführung nutzt exakt dieselben
  `buyCost`/`sellPayout`-Formeln wie ein manueller Trade zum
  Ausführungszeitpunkt (inkl. Slippage durch die Order-Menge selbst), damit
  entsteht kein zweiter Preis-Pfad neben der AMM-Kurve.

### 3. Auslösung im Markt-Tick

- Neue `MarketService`-Methode `checkAndExecutePendingOrders(ResourceName
  resource, double newSpotPrice)`, aufgerufen sowohl aus
  `applyRandomPriceFluctuation` (Hintergrund-Tick) als auch aus
  `createNewMarketEntryAfterTrade` (nach jedem echten Spieler-Trade) — jede
  Preisänderung kann eine Schwelle überschreiten, nicht nur der 2s-Tick,
  sonst verzögert sich die Auslösung bei einem großen manuellen Trade
  unnötig um bis zu 2s.
- Läuft innerhalb des bestehenden `marketLock` (reentrant, siehe Ist-Zustand
  oben) — liest alle `OPEN`-Orders der betroffenen Ressource, prüft
  Richtung+Schwelle gegen `newSpotPrice`, führt Treffer über dieselbe
  Kosten-/Gebührenlogik wie `performAction` aus (bei `SELL`:
  `getEffectiveSellFeeRate` + `sellPayout`, bei `BUY`: `buyCost`), **ohne**
  Trade-Cooldown-Check (Automatisierung, nicht Spieler-Klick — analog zur
  Auto-Verkauf-Begründung im Automatisierungs-Plan).
- Bei Ausführung: `status = FILLED`, `resolvedAt` setzen. Bei fehlenden
  Mitteln/Ressourcen: `status = CANCELLED` (siehe oben).
- Mehrere Orders derselben Ressource können in einem Tick auslösen (Preis
  kann mehrere Schwellen in einer Bewegung durchlaufen) — sequenziell
  abarbeiten, jede Ausführung aktualisiert `stock`/Preis für die nächste
  Prüfung in derselben Schleife (keine Wiederverwendung eines veralteten
  Zwischenstands).

### 4. Einsehen: reines Read-Modell, kein zusätzlicher "Tick-Speicher"

- `GET /api/v1/market/orders` liefert alle aktuell `OPEN`-Orders aller
  Spieler direkt aus `MarketOrderRepository` — die DB ist durch den
  synchronen Write bei Anlage/Ausführung bereits konsistent, es braucht
  **keine** separate "letzter Tick"-Snapshot-Tabelle. "Im letzten Tick
  einsehen" wird zur Frontend-Polling-Kadenz: alle
  `market.update-interval-ms` (Standard 2000ms, derselbe Wert wie
  `MarketScheduler`) neu abfragen.
- Endpoint selbst ist **serverseitig** hinter `MARKET_ORDER_UNLOCK` gesperrt
  (403 ohne Unlock) — nicht nur Frontend-Ausblendung, sonst könnte ein
  Spieler ohne Skill die Marktdaten trotzdem per Devtools/direktem Request
  einsehen. Deckt sich mit "vorher sieht man diese Option im Markt nicht"
  aus der Anfrage.
- `DELETE /api/v1/market/orders/{orderId}` — nur der Besitzer darf eine
  eigene `OPEN`-Order stornieren (`status = CANCELLED`), 403 bei
  Fremdversuch.

## Backend-Änderungen

- `enums/EffectType.java`: `MARKET_ORDER_UNLOCK`, `MARKET_ORDER_SLOT_BONUS`
  ergänzen.
- Neue Entity `entity/MarketOrderEntity.java` + `repository
  /MarketOrderRepository.java` (`findByStatusAndResource`,
  `countByUserIdAndStatus`, `findByUserId`).
- `MarketService.java`: neue Methoden `placeOrder(...)`, `cancelOrder(...)`,
  `getOpenOrders()`, `checkAndExecutePendingOrders(resource, newSpotPrice)`
  — letztere aus `applyRandomPriceFluctuation` (Zeile ~371+) und
  `createNewMarketEntryAfterTrade` (Zeilen 320-357) aufgerufen, alles
  innerhalb `marketLock`.
- Neuer/erweiterter Controller: `GET/POST /api/v1/market/orders`,
  `DELETE /api/v1/market/orders/{orderId}` — Validierung analog zum
  bestehenden Muster in `AdminConfigController`/dem
  Automatisierungs-Plan-Controller (Skill-Unlock + Kontingent-Check vor
  jeder Anlage).
- `SkillTreeService.java`: `market_7`, `market_8` in `buildNodes()`/
  `buildEdges()` ergänzen (Kante `market_6` → `market_7` → `market_8`).

## Frontend-Änderungen

- `frontend/src/services/api.js`: neue Funktionen `getOpenOrders()`,
  `placeOrder(...)`, `cancelOrder(orderId)`.
- `frontend/src/views/MarketView.vue`: neuer Order-Buch-Bereich, **nur
  gerendert** (nicht nur ausgegraut — bewusste Abweichung von der
  "ausgegraut mit Hinweis"-Konvention der anderen Automatisierungs-Features,
  da explizit gewünscht: "vorher sieht man diese Option im Markt nicht")
  wenn `MARKET_ORDER_UNLOCK` im geladenen Skilltree-State alloziert ist.
  Enthält: Order-Anlage-Mini-Formular (Ressource, Kauf/Sell-Toggle,
  Ziel-Preis, Menge, Client-seitige Richtungsprüfung vor Submit als
  UX-Vorabfilter, echte Prüfung bleibt serverseitig), Liste aller offenen
  Orders (eigene + fremde) mit Polling im `market.update-interval-ms`-Takt,
  Stornieren-Button nur bei eigenen Orders.
- `SkillTreeView.vue`: `EFFECT_LABEL_KEY` um `MARKET_ORDER_UNLOCK`/
  `MARKET_ORDER_SLOT_BONUS` erweitern, Bool-Unlock-Sonderfall wiederverwenden
  (bereits für die Automatisierungs-Effekte vorgesehen). Neues Keystone-Icon
  `keystone_market7.svg` (`market_8` ist PASSIVE, kein eigenes Icon nötig).
- i18n: neue Keys in `frontend/src/i18n/locales/{de,en}/marketView.json`
  (Order-Formular, Order-Liste, Fehlermeldungen) — Namespace existiert
  bereits.

## Verifikationsplan

1. **Vor dem Implementieren:** Reihenfolge mit
   [[2026-08-10-open-skillbaum-automatisierung]] klären — `market_5`/
   `market_6` müssen zuerst gebaut sein, sonst gibt es keine Kante zum
   Anhängen. Kollisions-/Kreuzungs-Skript für `market_7`/`market_8`-
   Koordinaten laufen lassen, bevor sie in `buildNodes()` landen.
2. `GET /api/v1/skilltree?userId=...` — beide neuen Knoten korrekt
   angebunden, `MARKET_ORDER_UNLOCK` als Bool-Effekt (`value = 1`).
3. Ohne `market_7` alloziert: `GET /api/v1/market/orders` und
   `POST /api/v1/market/orders` liefern 403; Order-Buch-Bereich in
   `MarketView.vue` ist nicht im DOM (nicht nur `display:none`).
4. Mit `market_7` alloziert: Order anlegen (BUY unter Spotpreis, SELL über
   Spotpreis) — Order erscheint in `GET /api/v1/market/orders` für alle
   Spieler (nicht nur den Ersteller).
5. Richtungsprüfung: BUY-Order mit `triggerPrice` über Spotpreis bzw.
   SELL-Order mit `triggerPrice` unter Spotpreis → 400.
6. Auslösung: Preis über manuelle Trades oder Hintergrund-Fluktuation über
   die Schwelle treiben, Order wechselt auf `FILLED`, Cookies/Ressourcen
   korrekt verbucht, Verkaufsgebühr identisch zu einem manuellen Verkauf
   gleicher Menge zum selben Preis, kein Trade-Cooldown blockiert die
   Ausführung.
7. Fehlende Mittel bei Auslösung (Ressource zwischenzeitlich manuell
   verkauft): Order wird `CANCELLED`, kein Fehler im Log-Rauschen, kein
   Absturz des Ticks für andere Orders derselben Ressource.
8. Kontingent: `market_8` nicht alloziert → zweite gleichzeitige Order
   → 400. Mit `market_8` alloziert → zwei gleichzeitige Orders erlaubt.
9. Stornieren: eigene Order per DELETE entfernen, fremde Order-Stornierung
   → 403.
10. `npm run check:palette` grün nach dem neuen Icon.
11. Regressionscheck: bestehender manueller Kauf/Verkauf (`performAction`)
    unverändert, Auto-Verkauf aus dem Automatisierungs-Plan weiterhin
    unbeeinflusst von der neuen Order-Prüfung im selben Tick.

## Kritische Dateien

- `backend/.../service/MarketService.java:231-357,558-624` (`performAction`,
  `createNewMarketEntryAfterTrade`, `buyCost`/`sellPayout`/`spotPrice`)
- `backend/.../scheduler/MarketScheduler.java` (Tick-Kadenz,
  `market.update-interval-ms`)
- `backend/.../enums/EffectType.java`
- `backend/.../service/SkillTreeService.java` (`buildNodes`/`buildEdges`,
  MARKET-Branch Zeilen 265-277)
- Neu: `backend/.../entity/MarketOrderEntity.java`,
  `repository/MarketOrderRepository.java`
- `backend/.../controller/MarketController.java`
- `frontend/src/views/MarketView.vue`
- `frontend/src/services/api.js`
- `frontend/src/components/SkillTreeView.vue`
- `frontend/src/i18n/locales/{de,en}/marketView.json`

**Abhängigkeit:** setzt [[2026-08-10-open-skillbaum-automatisierung]]
(`market_5`/`market_6`) voraus — vor diesem Plan hier umsetzen.
