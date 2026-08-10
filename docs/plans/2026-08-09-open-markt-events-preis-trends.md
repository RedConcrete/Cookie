# ⏳ Market Events: echte Preis-Trends ohne Spieler-Zutun

> **Status:** ⏳ Offen

## Context

Der Markt (`MarketService.java`) ist ein AMM mit drei Rückfall-Ebenen:
`stock` → zieht schnell (~2 Min) zu `baseline` → `baseline` wird von echten
Trades verschoben, zerfällt aber langsam (~1h) zurück zu `initialStock`, einem
**festen** Wert. Weil Ebene 3 nie wandert, gibt es ohne Spieler-Trades nur
Rauschen um einen fixen Punkt — Seitwärtstrend, kein echter Drift.

Wunsch: Preise sollen auch ganz ohne Spieler von selbst in eine Richtung
laufen können, aber kontrolliert — kein Absturz in den Keller mit
Verharren dort. Nach Rücksprache mit dem User (AskUserQuestion) ist die
gewünschte Form kein stilles Hintergrund-Drifting, sondern ein sichtbares
**Event-System**: pro Ressource gelegentlich ein benanntes Ereignis
("Schokoladenmangel" / "Schokoladen-Überschuss"), allen Spielern als
Popup/Banner angezeigt (Animal-Crossing-Rüben-Vibe). Nur eine Ressource
gleichzeitig betroffen, ~1-2 Events/Tag insgesamt über alle 6 Ressourcen.
Kein korreliertes "globales Marktklima" (v1) — jede Ressource unabhängig.

**Update (gleiche Session, nach dem Fix eines Bugs der die Preise auf den
Boden gedrückt hatte):** die ursprüngliche v1-Idee unten (Event = fester
Prozent-Multiplikator, danach exakt zurück auf `initialStock`) ist dem User
zu starr. Wunsch jetzt: Ressourcen sollen auch **dauerhaft** von ihrem
Startwert wegdriften können (Beispiel: Zucker von 1 auf 10, Schokolade von
5 auf 1) — über Zeiträume von 10 Minuten (scharfer Spike) bis 5 Stunden
(langsamer Trend), schnelle Bewegungen eher klein, langsame Bewegungen
eher groß. Explizit **kein festes Limit**, nur eine sanfte, mit der
Entfernung stärker werdende Bremse (siehe Kernidee v2 unten) — die
bestehenden numerischen Böden (`STOCK_EPSILON`, `minPrice`) bleiben als
reine Sicherheitsnetze, kein zusätzlicher Gameplay-Deckel.

## Kernidee v1 (verworfen, siehe v2 unten)

Ursprünglicher Ansatz: die **Ebene-3-Konstante** (aktuell hart
`initialStock`) event-abhängig um einen festen Multiplikator verschieben
(`effectiveAnchor = initialStock × (1 ± magnitude)`), danach exakt zurück
auf 1.0. Funktioniert für moderate Ausschläge, aber `Preis = K/stock²` ist
quadratisch — um Zucker von 1 auf 10 zu bringen bräuchte es einen
Stock-Multiplikator von ~0.32 (also `magnitude` ≈ 0.68), um Schokolade von
5 auf 1 zu bringen einen von ~2.24 (`magnitude` ≈ 1.24) — deutlich über
jedem vernünftigen festen Cap, und der User will explizit kein festes Cap.
Durch v2 ersetzt.

## Kernidee v3: zwei getrennte Funktionen auf demselben Anker — Trend (still) und Event (laut)

**Wichtige Klarstellung vom User:** Trend und Event sind zwei
unterschiedliche Funktionen, kein einheitlicher "Anker-Prozess mit
gelegentlich mehr Rauschen". Sauber getrennt halten:

| | **Trend** | **Event** |
|---|---|---|
| Häufigkeit | dauerhaft aktiv, jeder Tick | selten, ~1-2×/Tag, max. 1 gleichzeitig |
| Stärke | klein/gemächlich | deutlich stärker |
| Meldung | **keine** — komplett still, kein Popup/Banner/WS-Broadcast | Popup + Banner bei Start, WS-Broadcast |
| Zweck | "Preis ist nie für immer bei genau 1.0 eingefroren" | benanntes Ereignis, sichtbares Markt-Drama |
| Code | eigene Methode, eigener Satz Config-Werte | eigener Service, eigener Satz Config-Werte |

Level 3 (`initialStock`, bisher als fixer Rückfallpunkt genutzt) bleibt für
`poolConstant()`/`K` unverändert (bestimmt weiterhin den Preis am
"neutralen" Stock, inkl. bestehender Spieleranzahl-Skalierung über
`getInitialStock()`). Neu: die **Baseline zerfällt nicht mehr Richtung
`initialStock` selbst**, sondern Richtung eines separat geführten,
langsam wandernden **Ankers** (`*StockAnchor`, neues Feld analog zu
`*StockBaseline` in `MarketStockEntity`). Beide Funktionen verschieben
denselben Anker-Wert, aber über **zwei unabhängige, additive Schritte** pro
Tick — nicht eine gemeinsame Formel mit Modus-Umschaltung:

```
anchor_neu = anchor + trendSchritt(resource) + eventSchritt(resource)
```

**Trend-Funktion** `applyTrendDrift(resource)` — läuft für JEDE Ressource
bei JEDEM Tick, unabhängig davon ob gerade ein Event läuft, meldet nie
irgendwas nach außen. Reiner Ornstein-Uhlenbeck-Schritt im Log-Raum:

```
trendSchritt = ruckzugskraft × (log(initialStock) − log(anchor)) × dt + rauschen(dt)
```

Das ist die "sanfte Bremse, die mit der Entfernung stärker zieht": kein
hartes Limit, aber je weiter der Anker von `initialStock` wegdriftet, desto
stärker der Rückzug — statistisch (nicht hart) begrenzt. Zeitkonstante
bewusst lang (deutlich länger als die bestehende ~1h-Baseline-Zeitkonstante,
z.B. 24-48h) und Rausch-Amplitude bewusst klein, damit die alltägliche Drift
gemächlich bleibt ("Schokolade kann auch ganz ohne Event mal auf 1 fallen",
aber langsam, unauffällig, nie mit einer Meldung verbunden).

**Event-Funktion** (`MarketEventService`, wie schon geplant, bleibt eigener
Service/Scheduler/Tabelle) — bei aktivem Event auf einer Ressource kommt
zusätzlich ein spürbar stärkerer, gerichteter `eventSchritt` obendrauf
(Mangel = positiv/teurer, Überschuss = negativ/billiger), nur für die
Event-Dauer (10 Min – 5h, log-uniform gewürfelt, damit kurze und lange
Dauern beide gut vertreten sind). Weil sich die Verschiebung über die Zeit
aufsummiert, ergibt sich "schnell = klein, langsam = groß" automatisch —
ein 10-Minuten-Event hat schlicht wenig Zeit um den Anker weit zu tragen,
ein 5-Stunden-Event kann ihn deutlich weiter bewegen. Popup/Banner nur hier,
nie bei reinem Trend. Zeigt nur Richtung + Ressource ("Schokoladenmangel"),
nie die konkrete Stärke — Spieler merkt erst am tatsächlichen Preisverlauf
wie heftig es diesmal wird (Animal-Crossing-Prinzip).

Trennung hat einen praktischen Vorteil: beide Funktionen können unabhängig
balanciert, getestet und (falls nötig) einzeln abgeschaltet werden, ohne
die jeweils andere zu berühren — z.B. Trend feintunen ohne das
Event-Timing anzufassen, oder Events testweise deaktivieren während der
Trend weiterläuft.

**Wichtige Implementierungs-Nebenbedingung:** `recalculateDynamicStockBase()`
(MarketService.java:464-499) skaliert bei Änderung der aktiven Spielerzahl
`stock` und `baseline` proportional mit, damit der Spotpreis beim Umschalten
nicht springt (weil `K` an `getInitialStock()` hängt). Der neue Anker-Feld
muss in dieser Methode um denselben Faktor mitskaliert werden — sonst
verschiebt sich seine relative Position gegenüber `K` bei jedem
Spieleranzahl-Sprung künstlich.

## Backend-Änderungen

**Neue Entity `MarketEventEntity`** (eigene Tabelle `market_event`, nicht in
`MarketStockEntity` — bewusst getrennt, damit spätere Event-Historie/News
möglich ist ohne die Singleton-Stock-Tabelle zu verkomplizieren):
`id, resource, type (SHORTAGE/SURPLUS), biasStrength, volatilityMultiplier,
startedAt, endsAt`. Nur noch Metadaten für Popup/Historie/Admin-Debugging —
bestimmt NICHT mehr direkt einen Ziel-Preis, siehe Kernidee v2. Ergänzen in
`schema.sql` (Tabelle + Kommentar, analog `market_stock`-Block,
schema.sql:57-68) — Hibernate `ddl-auto=update` legt sie zusätzlich automatisch an.
Zusätzlich neue Felder `*StockAnchor` (6x, ein Wert pro Ressource) auf
`MarketStockEntity`, analog zum bestehenden `*StockBaseline`-Muster.

**Neuer Service `MarketEventService`**:
- In-memory Cache `Map<ResourceName, MarketEventEntity>` aktiver Events
  (Pattern wie `cachedActivePlayerCount`, MarketService.java:58) — vermeidet
  DB-Query im 2s-Preis-Tick.
- `getActiveBias(ResourceName)` / `getActiveVolatilityMultiplier(ResourceName)`
  — liest den Cache, neutral (0 / 1.0) wenn kein Event.
- `tick()`: abgelaufene Events schließen (endsAt < now → aus Cache raus,
  DB-Eintrag bleibt als Historie, WS "ended"-Broadcast), dann falls
  `activeCount < eventMaxConcurrent` mit kalibrierter Wahrscheinlichkeit ein
  neues Event auf einer freien, zufälligen Ressource starten (Resource
  zufällig aus den 6, Typ 50/50, `biasStrength`/`volatilityMultiplier`
  uniform aus konfiguriertem Bereich, Dauer **log-uniform**
  `[eventMinDurationMinutes=10, eventMaxDurationMinutes=300]` — log-uniform
  statt linear-uniform, sonst wären kurze 10-30-Min-Events gegenüber dem
  5h-Ende der Spanne krass unterrepräsentiert).
- `forceStartEvent(...)` für Admin-Trigger (Testing).
- `getActiveEvents()` für Init-Payload/REST.

**Neuer Scheduler `MarketEventScheduler`** (Pattern wie `MarketScheduler`,
scheduler/MarketScheduler.java) — ruft `marketEventService.tick()` alle
`eventCheckIntervalMinutes` (config, Default 10 Min).

**MarketConfig.java** — neue Properties, klar nach Funktion getrennt
benannt (kein Ressourcen-spezifischer Faktor, wie gewünscht):
- **Trend** (Prefix `trend*`, immer aktiv, komplett unabhängig von Events):
  `trendTimeConstantHours=36` (Rückzugskraft-Zeitkonstante, deutlich länger
  als `stockBaselineTimeConstantSeconds`), `trendNoisePerTick` (Grund-
  Volatilität — kalibriert per Playtest so, dass sich über Tage spürbare,
  aber nicht tägliche Extremwerte ergeben). Kein Bezug zu irgendeiner
  Event-Property.
- **Event** (Prefix `event*`, unverändert eigener Block):
  `eventMinDurationMinutes=10`, `eventMaxDurationMinutes=300`,
  `eventMinBiasStrength`/`eventMaxBiasStrength`,
  `eventMinVolatilityMultiplier`/`eventMaxVolatilityMultiplier`,
  `eventsPerDayTarget=1.5` (daraus die Trigger-Wahrscheinlichkeit pro Check:
  `eventsPerDayTarget / checksPerDay`), `eventCheckIntervalMinutes=10`,
  `eventMaxConcurrent=1`.
- Bewusst **keine** `eventMaxMagnitude`/Preis-Deckel-Property mehr — nur die
  bestehenden numerischen Böden `minPrice`/`STOCK_EPSILON` bleiben als
  Sicherheitsnetz, kein Gameplay-Cap (User-Wunsch: "kein festes Limit, nur
  sanfte Bremse"). Gilt für beide Funktionen gleichermaßen.

**MarketService.java** — `decayBaselineTowardAnchor` (Zeile 755) ändert sein
Ziel von `getInitialStock(resource)` auf den neuen, separat geführten
`*StockAnchor`-Wert. Zwei neue, unabhängige Methoden (beide vor
`decayBaselineTowardAnchor` im 2s-Tick aufgerufen):
- `applyTrendDrift(resource)` — reiner OU-Schritt, läuft immer, für alle 6
  Ressourcen, sendet nie eine Nachricht. Kennt `MarketEventService` gar
  nicht.
- `applyEventDrift(resource)` — fragt `MarketEventService.getActiveBias/
  VolatilityMultiplier(resource)` ab, no-op wenn kein Event aktiv. Reine
  Ergänzung, nicht Teil von `applyTrendDrift`.

**WebSocket** — `MarketWebSocketHandler.java` bisher: rohes Array ohne
Envelope (broadcastMarketUpdate, Zeile 40-56). Wird umgestellt auf
`{type: "marketSnapshot", data: [...]}` und neu
`{type: "marketEvent", data: {resource, type, endsAt, kind: "started"|"ended"}}`
(Stärke bewusst NICHT mitgeschickt — Spieler soll sie nicht vorher kennen,
nur Richtung + dass gerade was los ist). Kleiner, kontrollierter Schnitt:
ein Handler, ein Consumer.

**REST**:
- `GameController` init-Endpoint (`/api/v1/game/init/{userId}`) →
  `UserMarketDataDto` bekommt Feld `activeEvents` (Init-Sync ohne auf WS zu
  warten).
- Neu `GET /api/v1/market/events/active` (einfacher Re-Sync z.B. nach
  WS-Reconnect).
- Neu `POST /api/v1/admin/market/event` in `AdminController.java`
  (Pattern wie `resetMarket`, Zeile 102-109: `badToken`/dev-mode-Gate),
  Body optional `{resource, type, biasStrength, volatilityMultiplier,
  durationMinutes}` — leer = komplett zufällig, zum Testen der Timings.

## Frontend-Änderungen

**`services/websocket.js`** bleibt dummer Transport, gibt geparste Nachricht
unverändert weiter. **`stores/player.js`** (init(), Zeile ~84) branched neu
auf `data.type`: `marketSnapshot` → bestehende Logik, `marketEvent` →
`marketStore.applyEventMessage(data.data)`.

**`stores/market.js`** — neuer State `activeEvents` (Map resource→event),
Actions `setActiveEvents` (aus Init-Payload), `applyEventMessage` (WS
started/ended).

**Anzeige** (bewusst schlank, kein neues Toast-Framework):
- Kleines Toast bei Event-Start (neue Komponente, Corner-Position,
  Auto-Dismiss ~7s) — nicht blockierend, Spieler kann weiterspielen.
- Badge/Icon in `MarketView.vue` an der betroffenen Ressourcen-Zeile
  (`.mv-row`/`.mv-name`, Zeile 31-37) solange Event aktiv, mit Hover-Tooltip
  (bestehendes `PixelInfoPopover.vue`, Pattern wie MarketView.vue:66) für
  Restzeit/Richtung.
- Farben aus der Palette: Rot (`#b74132`/`#c23753`) für Mangel/Preis-rauf,
  Grün (`#349c58`/`#56642e`) für Überschuss/Preis-runter — bereits genutzte
  Töne (siehe NetWorthDialog.vue:22,31).
- Ressourcennamen: bestehendes `resourceLabel(name, t)` aus
  `components/buildings/buildingInfo.js` wiederverwenden, nicht neu bauen.

**i18n** — neuer Namespace `marketEventToast` (de/en JSON-Paar,
`frontend/src/i18n/locales/{de,en}/marketEventToast.json`), Keys für
Shortage/Surplus-Titel+Text mit `{resource}`-Interpolation (ICU-Pattern wie
in `marketView.json:23`).

**Palette-Check**: `cd frontend && npm run check:palette` nach Umsetzung.

## Betroffene Dateien (Kern)

Backend: `entity/MarketEventEntity.java` (neu), `repository/MarketEventRepository.java` (neu),
`service/MarketEventService.java` (neu), `scheduler/MarketEventScheduler.java` (neu),
`service/MarketService.java` (decayBaselineTowardAnchor), `config/MarketConfig.java`,
`controller/AdminController.java`, `controller/GameController.java`,
`dto/UserMarketDataDto.java`, `dto/MarketEventDto.java` (neu),
`handler/MarketWebSocketHandler.java`, `resources/schema.sql`, `application.properties`.

Frontend: `services/websocket.js`, `stores/player.js`, `stores/market.js`,
`views/MarketView.vue`, neue `components/MarketEventToast.vue`,
`i18n/locales/{de,en}/marketEventToast.json`.

## Verification

- Backend hochfahren, `POST /api/v1/admin/market/event` (dev-mode, kein
  Token nötig) mit fester Ressource/Richtung/Bias/Dauer triggern, Preis in
  DB/Logs über die konfigurierte Dauer beobachten: Anker driftet in die
  erwartete Richtung, klingt nach Event-Ende graduell wieder ab statt
  abrupt zurückzuspringen (kein fixer Zielwert mehr). Kein negativer/Null-
  Preis, kein Hängenbleiben am Rand.
  Kontrolle auch dass `getInitialStock`/Poolkonstante (Spieleranzahl-Skalierung)
  unangetastet bleibt.
- Langzeit-Beobachtung (mehrere Stunden laufen lassen, ohne Events): Anker
  sollte sich auch rein durch die Grund-Volatilität sichtbar aber gemächlich
  bewegen — "auch ohne Event mal ein Ausreißer" — und nie gegen `STOCK_EPSILON`
  bzw. `minPrice` klatschen. Falls doch: `anchorNoisePerTick` zu hoch relativ
  zur `anchorTimeConstantHours`-Rückzugskraft, nachjustieren.
- Stichprobe: mehrere `forceStartEvent`-Aufrufe mit `durationMinutes=10` vs.
  `durationMinutes=300` bei gleichem `biasStrength` vergleichen — die
  10-Minuten-Variante sollte spürbar kleiner ausschlagen als die
  5-Stunden-Variante (Kernprinzip "schnell = klein, langsam = groß").
- Frontend `npm run dev`, zwei Browser-Fenster offen, Admin-Event auslösen,
  prüfen dass Toast + Badge in beiden live über WS erscheinen, Badge nach
  Event-Ende verschwindet, DE/EN-Umschaltung zeigt korrekten Text.
- `npm run check:palette` — keine Palette-Verstöße.
- Kurzer manueller Trade-Test während eines aktiven Events: Kauf/Verkauf
  funktioniert weiterhin normal, Event-Preis-Bias addiert sich sauber zur
  bestehenden AMM-Kurve statt sie zu brechen.
