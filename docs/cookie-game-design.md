# Cookie — Game Design Dokument

Stand: August 2026 · Basis: Ist-Analyse des Repos `RedConcrete/Cookie`

Dieses Dokument beschreibt den **tatsächlich implementierten** Zustand des
Spiels, nicht mehr einen Plan dafür — die vorige Fassung (Juni 2026) war ein
Vorab-Design; seither ist praktisch der komplette Umfang gebaut, teils anders
als ursprünglich gedacht (z. B. Bürger-System statt Auto-Pflücker-Upgrades,
AMM-Markt statt linearem Preismodell). Wo Code und alter Plan auseinanderlaufen,
gilt hier der Code. Offene Lücken/Bugs stehen explizit in Abschnitt 12.

---

## 1. Core Loop

```
Hof-Grid (Hauptansicht, alle Gebäude fest positioniert, Pixel-Art)
   → Ernten an Rohstoff-Gebäuden (Hover startet automatische Ernte)
      → Bürger anwerben & Gebäuden zuweisen → passive Produktion im Hintergrund
         → Markt-Gebäude: fehlende Zutaten kaufen / Überschuss verkaufen (Gebühr)
            → Backhaus: Rezept wählen & Backen starten (echter Timer)
               → Zwischenzeit: weiter ernten/handeln, Löhne laufen weiter
                  → Backen abschließen → Cookies
                     → Cookies in Upgrades, Bürger & Gebäude-Ausbau investieren
                        → Net Worth steigt (Cookies + Rohstoffwert + Upgrade-Ausgaben)
                           → Prestige (freiwilliger Reset: Multiplikator + Fortschritt)
                              → Season-Ende: globaler Reset, Ergebnis archiviert
```

Voll spielbar von vorne bis hinten. Season-Trigger ist manuell (Admin-Endpoint),
alles andere läuft im laufenden Betrieb.

---

## 2. Ist-Zustand

| System | Backend | Frontend | Status |
|---|---|---|---|
| Hof-Grid (Hauptansicht, Pixel-Art) | ✅ | ✅ | spielbar |
| Ernten (Hover-Sammeln) | ✅ | ✅ | spielbar |
| Bürger-System (anwerben, Gebäuden zuweisen) | ✅ | ✅ | spielbar |
| Gebäude-Ausbau (mehr Bürger-Slots) | ✅ | ✅ | spielbar |
| Lohn / Dispo-Mechanik (2026-08-09) | ✅ | ✅ | spielbar |
| Markt (AMM-Preismodell, Angebot/Nachfrage) | ✅ | ✅ | spielbar |
| Live-Preise via WebSocket | ✅ | ✅ | spielbar |
| Rezept-Varianten + Bake-Timer | ✅ | ✅ | spielbar (3 Rezepte) |
| Upgrade-System (Boosts + Kapazität) | ✅ | ✅ | spielbar |
| Net Worth / History-Graph | ✅ | ✅ | spielbar |
| Prestige | ✅ | – | UI entfernt 2026-08-06, Backend bleibt (Abschnitt 11) |
| Season-Reset | ✅ | ✅ (Admin) | spielbar, siehe Lücke in Abschnitt 12 |
| Leaderboard / Profil | ✅ | ✅ | spielbar |
| Pixel-Art-Rework (Abschnitt 8) | – | ✅ | fertig, kein Plan mehr |
| Sound (Musik + SFX) | – | ✅ | fertig |
| Hotkeys (konfigurierbar) | – | ✅ | fertig |
| Lokalisierung (Deutsch/Englisch) | – | ✅ | fertig, siehe Abschnitt 8.2 |

**Legacy/tot im Frontend** (aus der Vor-Hof-Grid-Ära, nicht mehr geroutet):
`IdleView.vue`, `BakeView.vue`, `MarketTable.vue`, `TradePanel.vue`,
`ResourceBar.vue`, `BuildingTile.vue`. Einzige aktive Route ist `/` →
`FarmGridView.vue`. Aufräumen ist offen (siehe auch CLAUDE.md "Nächste Schritte").

---

## 3. Wirtschaft: Cookie-Quellen & -Senken

**Faucets (Cookies entstehen):**
- Backen im Backhaus (Rezept: Ressourcen → Cookies), skaliert mit
  Backen-Boost-Upgrade und Prestige-Multiplikator
- Verkaufen auf dem Markt (SELL), abzüglich Marktgebühr
- Automatischer Verkauf von Lager-Überschuss (siehe Abschnitt 5)

**Sinks (Cookies verschwinden):**
- Kaufen auf dem Markt (BUY)
- Marktgebühr auf jeden Verkauf (`sellFeeRate`, Standard 15 %, senkbar über
  Markt-Gebäude-Level, siehe Abschnitt 6)
- Bürger anwerben (Kosten pro Bürger wachsen exponentiell)
- Gebäude bauen/ausbauen (Kosten wachsen exponentiell pro Stufe)
- Upgrades kaufen (Kosten wachsen exponentiell pro Stufe)
- Löhne (laufender Sink, jede Minute abgebucht, siehe Abschnitt 5)
- Prestige-Reset (harter Sink pro Spieler)
- Season-Reset (harter Sink für alle)

**Startzustand:** 0 Cookies, 0 Ressourcen, nur Backhaus + Rathaus + Markt +
Lager vorgebaut (alle Stufe 1, siehe Abschnitt 4). Identisch nach Prestige-
und Season-Reset — ein konsistenter Startpunkt.

---

## 4. Hof-Grid & Gebäude

Feste Pixel-Art-Ansicht (Vue + CSS, kein Canvas/Render-Engine nötig —
`BuildingFrame.vue` positioniert jedes Gebäude absolut über `x/y` aus
`farmLayout.js`). Kamera: Pan (Ziehen) + Zoom, Gebäude lassen sich per
Halten+Ziehen frei verschieben (Position wird pro Spieler in `localStorage`
gespeichert).

**Vorgebaute Gebäude** (ab Level 1 für jeden Spieler):
Backhaus (fix, nicht upgradebar), Rathaus, Markt, Lager (alle drei upgradebar).

**Kaufbare Produktionsgebäude** (Zucker/Mehl/Eier/Butter/Schokolade/Milch —
Zuckerteich, Bauernhof, Hühnerhof, Butterei, Plantage, Kuhstall): einmalig
gebaut, danach beliebig oft ausbaubar.

**Kostenformel für Bau/Ausbau aller upgradebaren Gebäude** (identisch,
exponentiell):
```
cost(level) = baseCost × 2^level
```

**Was jede Gebäude-Stufe bringt:**
- Produktionsgebäude: **+1 Bürger-Slot** pro Stufe (Basis-Slots
  variieren pro Gebäude, 1–4)
- Lager: **+1000 Kapazität** pro Stufe (Basis 100)
- Markt: **−2 % Marktgebühr** pro Stufe über Stufe 1 (Minimum 1 %)
- Rathaus: **+4 maximale Bürger** pro Stufe

**Hover-Ernte:** Über einem Produktionsgebäude hovern startet automatisches
Ernten (kein Auto-Pflücker-Upgrade mehr nötig, das gab es früher separat und
ist entfernt).

**Lager voll (2026-08-07, kurzzeitig Deckel pro Rohstoff 2026-08-09,
zurückgerollt auf gemeinsamen Topf noch am selben Tag):** kein Auto-Verkauf
von Überschuss mehr — weder bei Hover-Ernte noch bei passiver Produktion. Was
über die Kapazität hinausgeht, wird schlicht nicht gutgeschrieben
(`UserService#harvest`, `PassiveIncomeService#collectBuilding`,
`MarketService#trade` beim Markt-Kauf), keine automatische Umwandlung in
Cookies. Die Kapazität (`totalResourceCap`) gilt als **gemeinsamer Topf über
alle sechs Rohstoffe** (`UserEntity#getTotalResources`) — ein einzelner
Rohstoff darf das Lager komplett füllen, und sobald der Topf insgesamt voll
ist, werden alle Produktionsgebäude gleichzeitig inaktiv, unabhängig davon,
welchen Rohstoff sie selbst produzieren. (Zwischenzeitlich lief kurz ein
Modell mit Deckel pro Rohstoff einzeln — das machte zwar Sinn für "Gebäude X
wird nur inaktiv, wenn Rohstoff X voll ist", aber der Spieler wollte
ausdrücklich die Möglichkeit behalten, das ganze Lager mit einem einzigen
Rohstoff zu füllen; explizit zurückgerollt.) Visuelles Feedback im Hof-Grid
(`FarmGridView.vue`/`BuildingFrame.vue`): Hover-Ring wird rot statt grün, ein
kurzes Popover erklärt "Lager voll", Gebäude werden optisch gedimmt
(`.building-idle`), derselbe visuelle Zustand wie bei überzogener
Dispo-Grenze (Abschnitt 5). Ein sinnvoller Ausgleich
für volles Lager (Ressourcen-Umwandlung, Lager-Overflow-Puffer o.ä.) ist als
größere Mechanik im Skill-/Passiv-Baum geplant, siehe `docs/ROADMAP.md` — bis
dahin bewusst hart gestoppt statt automatisch verkauft.

**Start-Balance (neu austariert 2026-08-07):** vorher startete jeder Spieler
mit 1000 von JEDER Rohstoff-Ressource (6000 insgesamt) bei nur 1100
Lagerkapazität (100 Basis + 1000 für das vorgebaute Lager Stufe 1) — das
Lager war also schon vor dem ersten Klick 5-fach überfüllt (`PlayerConfig`).
Jetzt:
- **0 Startressourcen** — Rohstoffe holt man sich aktiv per Hover-Ernte oder
  am Markt.
- **400 Start-Cookies** — reicht für **genau eines** der drei günstigsten
  Produktionsgebäude (Butterei 280, Bauernhof 300, Hühnerhof 350), nicht für
  alle drei; Zuckerteich (500) und Kuhstall (600) bleiben ein späteres Ziel.
  Zwingt zu einer echten Entscheidung direkt am Spielstart statt "kauf
  einfach alles".
- **1 kostenloser Skill-Punkt** bei Accounterstellung (`player.initial-skill-
  points`) — zählt nicht in `totalSkillPointsBought`, verzerrt also nicht die
  Kostenkurve künftiger Käufe. Macht den Skill-Baum von Anfang an Teil der
  Startentscheidung (voll auf eine Ressource + Verkauf, alles selbst backen,
  oder Mischform mit Marktzukauf), nicht erst nach dem ersten Kauf relevant.

---

## 5. Bürger-System

Ersetzt das ursprünglich geplante "Auto-Pflücker"-Upgrade (Abschnitt 6, alte
Fassung) vollständig.

- **Anwerben:** über das Rathaus, gedeckelt durch `Rathaus-Level × 4`
  maximale Bürger. Kosten pro Bürger wachsen exponentiell:
  ```
  cost(n-ter Bürger) = 50 × 1.15^n
  ```
- **Zuweisen:** Bürger werden Produktionsgebäuden zugewiesen (bis zum
  Gebäude-Slot-Limit, Abschnitt 4). Nicht zugewiesene Bürger laufen als Idle-
  Wanderer vor dem Rathaus umher.
- **Produktion (2026-08-07 neu: Ansammeln + manuell einsammeln statt
  Server-Tick):** jeder zugewiesene Bürger erzeugt passiv Ressourcen
  (`passiveRatePerSecPerWorker` je Gebäude). Die Menge sammelt sich lokal
  **im Gebäude** an (`PlayerBuildingEntity#pendingAmount`), gedeckelt durch
  eine gebäudeeigene Lagerkapazität (`BuildingService#BuildingDef
  storageCapacity`, ca. 10 Minuten Produktion bei Basisbesatzung) — ist das
  Gebäude voll, produziert es nichts mehr, bis eingesammelt wird (wie eine
  Miete). Kein Server-Tick mehr, der ständig alle Spieler durchläuft:
  Fortschritt wird lazy anhand der verstrichenen Zeit berechnet, sobald
  irgendetwas das Gebäude anfasst (Lesen, Einsammeln, Arbeiter-/Stufen-
  Änderung, Lohn-Idle-Wechsel — `BuildingService#settle()`). Einsammeln geht
  sowohl über einen Klick-Badge direkt auf der Hofkarte (kein Dialog nötig)
  als auch über einen Button im Gebäude-Dialog
  (`POST /api/v1/farm/buildings/collect/{userId}/{buildingId}`,
  `PassiveIncomeService#collectBuilding`). Was wegen vollem gemeinsamem Lager
  nicht reinpasst, bleibt im Gebäude liegen statt verworfen zu werden.
- **Lohn (2026-08-09 neu: pro Arbeiter statt pauschal pro Gebäude):** jede
  Minute wird die Summe aller Gebäude-Löhne vom Cookie-Konto abgebucht
  (`WageScheduler`, 60s-Takt, `WageService#deductWageForUser`). Jedes
  Produktionsgebäude kostet `zugewiesene Arbeiter × wagePerMinPerWorker`
  (Default 2 C/min/Arbeiter, `balance.wage-per-min-per-worker`) — skaliert
  also mit tatsächlicher Arbeiterzahl und indirekt mit Gebäude-Stufe (mehr
  Stufen = mehr Arbeiter-Slots = mehr potenzieller Lohn, wenn auch besetzt).
  Lager ist eine Ausnahme: dort kostet weiterhin jede Stufe über Stufe 1
  einen festen Betrag (kein Arbeiter-Bezug, siehe Abschnitt 4).
- **Dispo-Kredit statt Komplett-Idle (2026-08-09):** reicht das
  Cookie-Guthaben für den fälligen Lohn nicht, gehen die Cookies ins Minus
  statt dass sofort alle Bürger auf `idle` gesetzt werden — Produktion läuft
  normal weiter. Auf ein bestehendes Minus fallen **10 % Zinsen pro
  Lohn-Tick** an (`balance.debt-interest-rate`), reduzierbar über den
  DISPO-Skill-Baum-Zweig (Abschnitt 9) bis auf einen Mindestsatz von 2 %
  (`balance.debt-interest-rate-floor`). Zinsen laufen jeden Tick, auch wenn
  gerade kein Lohn fällig ist (z. B. keine Arbeiter zugewiesen) — wer einmal
  im Minus ist, muss aktiv gegensteuern (verkaufen, backen, ernten), sonst
  wächst die Schuld exponentiell weiter.
  **Dispo-Grenze:** `aktueller Gesamtlohn/Minute × debtLimitMultiplier`
  (Default ×8, `balance.debt-limit-multiplier`) — würde die nächste
  Lohnabbuchung diese Grenze überschreiten, greift die alte Komplett-Idle-
  Sperre als harter Stopp (alle Bürger `idle`, passive Produktion pausiert
  komplett), bis das Guthaben wieder unter der Grenze liegt. Verhindert eine
  endlose Zins-Spirale ohne Ausweg.
  **Sichtbar für den Spieler:** Cookie-Zahl im HUD färbt sich rot bei
  negativem Kontostand, Rathaus-Dialog zeigt eine "Schulden"-Kachel mit
  aktuellem Zinssatz und Dispo-Grenze, jede tatsächliche Abbuchung lässt eine
  rote Zahl am Cookie-HUD nach unten wegfallen (`WageNumbers.vue`).
  **Abrechnungshistorie:** jede tatsächliche Lohnabbuchung (nicht die
  Zinsen — die sind konzeptionell eine separate Bank-Transaktion, keine
  Gebäude-Ausgabe) landet als Eintrag im Rathaus-Tab "Abrechnung"
  (Zeitpunkt, Summe, Aufschlüsselung pro Gebäude), gespeist aus
  `WageLedgerEntity`. Pro Spieler werden nur die neuesten 200 Einträge
  behalten (`balance.wage-ledger-max-entries`), ältere werden bei jeder neuen
  Abbuchung hart gelöscht statt die Granularität zu vergröbern.

---

## 6. Markt: AMM-Preismodell

Kein einfacher "Preis ± Einfluss"-Mechanismus mehr, sondern ein
**Constant-Product-AMM** wie bei Uniswap — jede Ressource ist ein Pool aus
Lagerbestand gegen eine virtuelle Cookie-Reserve.

```
K (pro Ressource, fix)  = initialStock² × initialPrice
Preis(stock)             = K / stock²
Kauf-Kosten(amount)      = K/(stock−amount) − K/stock
Verkaufs-Erlös(amount)   = K/stock − K/(stock+amount)
```

**Warum so:** kein künstlicher Preisdeckel nötig — ein Kauf kann den Pool nie
vollständig leerkaufen, die Kosten gehen asymptotisch gegen unendlich, je
näher man dem verbleibenden Bestand kommt. Genau wie in einem echten
liquiden Markt gibt es kein hartes "ausverkauft", nur "zunehmend teurer".
Die Kurve ist konvex: ein einzelner Großtrade bewegt den Preis
überproportional stärker als viele kleine Trades mit derselben
Gesamtmenge — "nur die Masse bewegt den Markt spürbar" ergibt sich also
automatisch aus der Formel, ohne separaten Spieleranzahl-Faktor.

**Hintergrund-Volatilität:** alle 2 Sekunden wird der Stock jeder Ressource
um einen kleinen zufälligen Betrag verschoben (Phantom-Kauf/-Verkauf ohne
echten Spieler dahinter, `stockFluctuationRatio` = bis zu 2 % vom
Ausgangsbestand). Der Preis wird danach aus der Kurve neu abgeleitet — ein
einziger Formel-Pfad für echte Trades wie Hintergrundrauschen.

Der Zufalls-Tick verzerrt dabei nicht direkt Richtung `initialStock`,
sondern Richtung einer pro Ressource geführten **Baseline**
(`MarketStockEntity.*StockBaseline`, 2026-08-07):

- **Nur echte Trades** verschieben die Baseline, anteilig um ihre eigene
  Stock-Änderung (`stockBaselineTradeTransferRatio`, Standard 0.5). So
  "merkt" sich der Markt anhaltendes Kaufen/Verkaufen als neue vorläufige
  Gleichgewichtslage — der Preis-Effekt bleibt über die konfigurierte
  Zeitkonstante bestehen, statt binnen weniger Minuten wieder auf den
  Ausgangswert zurückzuspringen.
- Die Baseline selbst zerfällt unabhängig davon bei jedem Zufalls-Tick
  langsam zurück Richtung `initialStock` (`stockBaselineTimeConstantSeconds`,
  Standard 3600s = 1h) — reiner, rauschfreier Anker-Zerfall gegen einen
  festen Zielwert, daher unbedingt stabil.
- Der Zufalls-Tick selbst darf die Baseline **nie** bewegen (nur den Stock,
  Richtung Baseline) — sonst könnte reines Hintergrundrauschen die Baseline
  unbegrenzt wegtreiben lassen. Diese Trennung ist bewusst: schnelle,
  selbst-stabilisierende Rückkorrektur des Stocks gegen die Baseline (~2 min
  Halbwertszeit, unverändert seit v1), aber eine träge, ausschließlich
  trade-getriebene Baseline mit eigener, unabhängig stabiler Verfallszeit.

Ergebnis: ein einzelner Trade hebt/senkt den Preis sofort (AMM, unverändert)
und der Effekt klingt über ~1–3h graduell ab; anhaltendes Trading in eine
Richtung zieht die Baseline mit und der Markt bleibt entsprechend länger
verschoben. Reines Hintergrundrauschen ohne jeden Trade bleibt exakt so
stabil wie vorher (Baseline bleibt bei `initialStock`).

**Marktgebühr:** `sellFeeRate` (Standard 15 %, config `MarketConfig`),
reduzierbar durch Markt-Gebäude-Level (Abschnitt 4). Nur beim SELL fällig,
BUY ist gebührenfrei.

**Markt-Tiefe skaliert mit aktiven Spielern (2026-08-08):** `initialStock`
(und damit `K`) ist kein fixer Wert mehr, sondern eine Untergrenze —
tatsächlich verwendet wird
`max(initialStock, stockPerActivePlayer × aktiveSpielerzahl)`. Grund:
Freund-Playtest zeigte, dass ein einzelner Spieler den Markt bei
`initialStock = 1000` innerhalb kurzer Zeit spürbar allein bewegen konnte.
"Aktiv" zählt, wer innerhalb von `activePlayerWindowDays` (Standard 7 Tage)
einen Spielstart hatte (`UserEntity.lastActiveAt`, gesetzt in
`GameController#initializeGame` — bewusst nicht bei jedem beliebigen
API-Call, sonst würde z. B. das Ansehen eines fremden Profils dessen
Aktiv-Status verfälschen). Ein Hintergrund-Job
(`MarketService#recalculateDynamicStockBase`, alle 5 Minuten) zählt aktive
Spieler neu und skaliert dabei Stock **und** Baseline jeder Ressource um
denselben Faktor mit — dadurch bleibt der aktuelle Spotpreis beim
Umschalten unverändert, nur künftige Trades wirken sich absolut gesehen
schwächer aus (tieferer Pool). Gilt einheitlich für alle sechs Ressourcen,
kein Ressourcen-spezifischer Faktor. `stockPerActivePlayer` (Startwert
20000) ist bewusst hoch angesetzt und braucht noch Fein-Tuning mit echten
Spielerzahlen — siehe `docs/ROADMAP.md` Abschnitt 7.2.

**Admin:** `POST /api/v1/admin/market/reset` setzt Stock+Preise aller
Ressourcen auf die aktuellen Ausgangswerte zurück (Preise: Konfig-Werte,
Stock: aktueller effektiver Wert inkl. Spielerzahl-Skalierung, dev-mode ohne
Token nötig).

---

## 7. Resource & Recipe System

Drei Rezepte, unterschiedliche Profile — hält den Markt über alle 6
Rohstoffe hinweg relevant statt auf 1–2 Ressourcen konzentriert:

| Rezept | Profil | Output | Backzeit |
|---|---|---|---|
| Standard | ausgewogen, je 10 | 100 Cookies | 30 s |
| Milchcookie | viel Milch (25), wenig Rest | 130 Cookies | 60 s |
| Sparrezept | insgesamt wenig (je 5) | 40 Cookies | 15 s |

**Bake-Timer-Mechanik:**
- Ressourcen werden beim Start sofort abgezogen (kein Exploit durch Abbrechen)
- Bake-Job läuft serverseitig mit `startedAt`/`completesAt` — Spieler kann
  währenddessen normal weiterspielen
- Output = `recipe.output × batches × (1 + bakeBoostLevel × 0.10) × prestigeMultiplier`
- Claim schreibt Cookies gut, sobald der Timer abgelaufen ist
- Slots: bewusst nur 1 gleichzeitiger Bake-Job, kein Mehrfach-Slot-Upgrade
  (früher "Zweiter Ofen", entfernt 2026-08-06) — Backhaus wird später über
  Geschwindigkeit/Ressourcenverbrauch balanciert statt über parallele Slots
- **Dev-Mode:** Backzeit ist auf 5s fix (nicht mehr instant) — instant ließ
  einen Job sofort "fertig" sein, bevor die Fortschrittsanzeige je etwas zu
  zeigen bekam

---

## 8. Pixel-Art-UI (fertig, kein Plan mehr)

Ehemals "Langzeit-Vision" (alte Abschnitt 16) — inzwischen komplett
umgesetzt, DOM-basiert (Vue + CSS), keine Render-Engine (PixiJS/Phaser) nötig:

- Design-System `pixel.css`: 4px dunkle Rahmen, 0px Radien, ausschließlich
  Silkscreen-Font (monospace, pixelig) für die gesamte UI
- Pixel-Icons: eigenes 8×8-Sprite-System (`PixelIcon.vue`), keine Emoji
- Gebäude als individuelle Sprites (Kuhstall, Zuckerteich, Backhaus, …),
  frei auf dem Hof verschiebbar
- Bürger als animierte Pixel-Worker (`PixelWorker.vue`), laufen idle vor
  dem Rathaus umher, wenn nicht zugewiesen
- Custom Pixel-Scrollbar, Custom-Tooltips (`PixelInfoPopover.vue`)
- Sound: Musik-Playlist + SFX (Hover, Klick, Buch auf/zu, Münzen, Ernte),
  Lautstärke getrennt regelbar, Mute-Toggle je Kanal
- Hotkeys: konfigurierbar in den Einstellungen (Zentrieren, Ernten-Halten,
  Backhaus/Markt/Upgrades/Rangliste öffnen, etc.)

### 8.1 Farbpalette (Pflicht für neue Pixel-Art)

Verbindliche Palette: **Fruitpunch24** (24 Farben), Referenzdatei
`frontend/src/assets/colorpalate/fruitpunch24.hex`. Jede neue
Pixel-Art-Datei (SVG, PNG-Sprite) darf **ausschließlich** diese Farben
verwenden — keine Zwischentöne, kein Anti-Aliasing, keine Verläufe.

| | | | |
|---|---|---|
| `#aea47e` | `#6f6e72` | `#534664` | `#349c58` |
| `#6dba79` | `#2a7d75` | `#24505f` | `#2a2942` |
| `#120e23` | `#3a1b40` | `#7a2849` | `#b74132` |
| `#e67146` | `#ebb85b` | `#c78539` | `#a15c34` |
| `#764032` | `#402e2b` | `#56642e` | `#7e9432` |
| `#c9c03d` | `#fff1a9` | `#e67a84` | `#c23753` |

### 8.2 Lokalisierung

Deutsch + Englisch über `vue-i18n`, Umschalter in den Einstellungen
(persistiert in `localStorage`). Ein JSON-Locale-Paar pro Komponente unter
`frontend/src/i18n/locales/{de,en}/`, automatisch gemergt. Details/Konventionen
für neue Texte: `CLAUDE.md` Abschnitt "Lokalisierung (i18n)". Backend-Texte
(Fehlermeldungen, DB-Namen) bleiben deutsch, nicht Teil der Umstellung.

**Ist-Stand:** Die bestehenden Gebäude-SVGs
(`frontend/src/assets/buildings/*.svg`, z. B. `kuh.svg`, `huhn.svg`,
`lager.svg`) sind Platzhalter aus der Frühphase und halten sich **nicht**
an diese Palette — bewusst unangetastet, werden erst beim UI-Rebuild
nacheinander ersetzt (Tracking: `ROADMAP.md` Abschnitt 2). Neue
Referenzbilder für das Lager-Gebäude liegen bereits unter
`frontend/src/assets/buildings/StorageBuildng/`, sind aber noch nicht
palette-konform und noch nicht in eine Szene verdrahtet (Testlauf mit
`house3.png`/`StorageBuilding.png` wurde verworfen, `WarehouseScene.vue`
zeigt weiterhin `lager.svg`).

---

## 9. Skill-Baum (Cookie-Sink)

Ersetzt das alte Upgrade-System vollständig (2026-08-06) — Path-of-Exile-
artiger Passiv-Baum: Spieler kaufen mit Cookies **Skill-Punkte**, die dann
gegen einzelne **Knoten** im Baum eingetauscht werden (binäre Freischaltung,
keine Stufen/Level). Ein Knoten ist nur wählbar, wenn er über eine Kante an
einen bereits freigeschalteten Knoten angrenzt (PoE-Connectivity-Regel,
Wurzel `root` ist implizit für jeden Spieler freigeschaltet).

**Skill-Punkt-Kosten** (unabhängig vom Knotenpreis, 1 Knoten = 1 Punkt):
```
cost(n) = skillPointBaseCost × skillPointCostGrowth^n   // n = totalSkillPointsBought
        = 150 × 1.4^n                                     // Default-Werte
```
Bewusst deutlich teurer und steiler als Bürger-/Gebäude-Wachstum (Basis 50,
Wachstum 1.15) — der Skill-Baum ist der zentrale Cookie-Sink und soll sich
von Anfang an nach einer echten Investition anfühlen, nicht nach einem
Nebenbei-Kauf, und auch nach vielen Punkten noch ein spürbares, langfristiges
Ziel bleiben. Die Kehrseite: die Pro-Knoten-Effekte sind bewusst klein
gehalten (siehe unten) — der Baum lebt vom Sammeln vieler Punkte über
längere Spielzeit, nicht von 2–3 Käufen mit riesigem Einzeleffekt.

**Effekt-Typen** (`enums/EffectType`): `HARVEST_YIELD`, `BAKE_OUTPUT`,
`MARKET_FEE_REDUCTION`, `WAGE_INTEREST_REDUCTION` (2026-08-09, senkt den
Dispo-Zinssatz, Abschnitt 5), `RESOURCE_WAGE_REDUCTION` (2026-08-10, senkt
den Arbeiter-Lohn **eines einzelnen Produktionsgebäudes**, `targetResource`
= die zugehörige Ressource, z. B. `SUGAR` → Zuckerteich). `HARVEST_YIELD`-
und `RESOURCE_WAGE_REDUCTION`-Knoten haben ein optionales `targetResource`
(z. B. `MILK`) — `null` heißt global (gilt für jede Ressource/jedes
Gebäude), gesetzt heißt nur für diese eine Ressource. Alle Effekte eines
Typs (+ passender Ressource) addieren sich; zentral aufgelöst über
`SkillTreeService#getEffectTotal(userId, type, targetResource)` statt
verstreuter Einzel-Lookups wie im alten System.
`RESOURCE_WAGE_REDUCTION` wird in `BuildingService#effectiveWage(def, ent,
userId)` ausgewertet (nur für Gebäude mit `def.passiveResource() != null`,
d. h. die 6 Produktionsgebäude — Lager/Ofen/Rathaus/Markt bleiben
unberührt), mit Floor/Cap `[-0.5, 0.9]` (kann durch Keystone-Downsides auch
negativ werden, siehe unten). `STORAGE_CAP_BONUS`/`BUILDING_BUFFER_BONUS`
(2026-08-10, Lager-Branch) wirken auf `BuildingService#getTotalCap(...)`
(Hauptlager-Cap, Floor `-0.5`) bzw. `#settle(...)` (Pro-Gebäude-
Zwischenspeicher `storageCapacity`, Floor `0`) — beide global
(`targetResource=null`), da ein Lager-Bonus einheitlich für alle
Ressourcen/Gebäude gilt statt pro Ressource zu variieren.

**Mehrfach-Effekte pro Knoten (seit 2026-08-10):** ein Knoten kann mehrere
`SkillNodeEffectEntity`-Zeilen haben (Tabelle `skill_node_effects`, FK
`nodeId → skill_nodes.id`, eager als `SkillNodeEntity#effects` geladen).
`getEffectTotal` iteriert flach über alle Effekte aller alloziierten Knoten.
Ein Keystone-**Nachteil** ist schlicht ein zweiter Effekt mit negativem
`effectValue` auf demselben Knoten, kein eigener Mechanismus — z. B. eine
negative `WAGE_INTEREST_REDUCTION` erhöht effektiv den Zinssatz. **Harte
Regel:** Nachteile wirken ausschließlich auf den eigenen Account (analog
`BuildingService#getEffectiveSellFeeRate`), nie auf `MarketService`s
gemeinsamen AMM-Pool (shared state).

`SkillNodeEffectEntity.effectType` ist bewusst ein **reiner String**, kein
`@Enumerated`-Feld — dadurch legt Postgres für diese Spalte **nie** eine
CHECK-Constraint an, ein neuer `EffectType`-Enum-Wert braucht seither keinen
manuellen `ALTER TABLE ... DROP CONSTRAINT`-Schritt mehr (vorher, bis
2026-08-10: traf beim Hinzufügen von `WAGE_INTEREST_REDUCTION` zu, Details
noch in `docs/ROADMAP.md` Abschnitt 0 als historische Notiz). Validierung
passiert nur an der einen Stelle mit echtem externen Input, dem Admin-PUT
(`EffectType.valueOf(...)`, 400 bei unbekanntem Wert) — der Seed-Pfad kommt
immer aus kompiliertem Enum-Code und braucht keine Prüfung.

**Node-Tiers:** `SkillNodeEntity.nodeTier` (`PASSIVE`/`NOTABLE`/`KEYSTONE`,
typed Enum mit `@Enumerated` — anders als `effectType` ein kleines, stabiles
3-Werte-Set, der einmalige CHECK-Constraint-Drop nach dem allerersten Boot
ist hier unkritisch). Frontend zeigt Tiers über Knotengröße + Rahmen/Glow
(`NODE_SIZE`/`NOTABLE_SIZE`/`KEYSTONE_SIZE`, CSS-Klassen
`.stv-node-notable`/`.stv-node-keystone` in `SkillTreeView.vue`). **Seit
2026-08-12 nutzt der gesamte Seed-Baum nur noch `PASSIVE`/`KEYSTONE`** —
`NOTABLE` bleibt als Enum-Wert bestehen (Frontend-Styling/Admin-Editor
unterstützen ihn weiterhin), wird aber von keinem aktuellen Knoten mehr
gesetzt. Grund: der mittlere Tier fühlte sich wie eine willkürliche
Zwischenstufe an statt einer echten Design-Entscheidung; jeder Branch
besteht jetzt aus kleinen `PASSIVE`-Bausteinen, die auf ein bis zwei
`KEYSTONE`-Payoffs (großer Bonus, meist mit Tradeoff) hinlaufen.

**Zweisprachige Knoteninhalte:** `nameDe`/`nameEn`/`descriptionDe`/
`descriptionEn` statt `name`/`description` — Knoteninhalt ist
admin-editierbarer DB-Content, kein statischer UI-Text, deshalb **kein**
`vue-i18n`-JSON-Key-Pattern. Backend liefert beide Sprachen im DTO, Frontend
wählt reaktiv nach `locale` (`nodeName()`/`nodeDesc()` in
`SkillTreeView.vue`) — kein Tree-Refetch beim Sprachwechsel nötig.

**V1-Baum:** 70 Knoten (Wurzel + 69) in 11 Zweigen + 1 Cross-Branch-Wheel-
Beispiel, 81 Kanten (Seed in `SkillTreeService#buildNodes/buildEdges`,
admin-editierbar zur Laufzeit). Stand nach dem PoE-Mesh-Umbau der 5
Rohstoff-Branches (2026-08-12, +15 Knoten/+25 Kanten ggü. der 2026-08-10-
Fassung) — Kollisionsfreiheit dabei per Hand nachgerechnet (Bearing/Radius-
Formel + Mindestabstand-Check gegen alle Nachbar-Branches, kein
Python-Skript-Lauf wie beim vorherigen Pass), nicht zusätzlich gegen die
Live-API verifiziert.

**Radiales 11-Branch-Layout (2026-08-10, zweite Fassung):** alle Zweige
liegen gleichmäßig alle 360°/11 ≈ 32.7° auf einem Kreis um `root`
(Kompass-Bearing, 0°=Norden=−y, 90°=Osten=+x), Radius 150/300/450/600 pro
Tier. Reihenfolge im Kreis: MILK(0°) SUGAR(32.7°) DISPO(65.5°)
FLOUR(98.2°) BAKING(130.9°) MARKET(163.6°) EGGS(196.4°) BUTTER(229.1°)
CORE(261.8°) CHOCOLATE(294.5°) STORAGE(327.3°). **Alle** bereits
bestehenden Zweige wurden dafür ein zweites Mal neu positioniert (erste
Fassung war 36°-Abstand für 10 Zweige beim Rohstoff-Branches-Pass) — `x`/`y`
ist reiner Anzeigewert ohne Spiellogik-Bezug (Allokation läuft
ausschließlich über Kanten), Repositionieren bestehender Knoten-IDs ist
also unkritisch. Grund: 10 Zweige belegten bereits jeden 36°-Slot, für den
neuen STORAGE-Zweig (Lager-Branch-Plan) war kein Platz mehr — radiales
Neuverteilen auf 360°/n ist ab jetzt die Standard-Vorgehensweise bei jedem
weiteren Branch, nicht das Suchen einer Lücke (das hatte beim
Cross-Branch-Wheel schon zu einer Kollision mit DISPO geführt). Vor jeder
Layout-Änderung: Kollisions-/Kreuzungs-Skript (Python, Node-Boxen als
Kreise mit Radius 28/34/40 je Tier, Kantensegmente auf Schnitt) erst lokal
simuliert, dann gegen die Live-API verifiziert — 0 Treffer über alle 55
Knoten × 56 Kanten.

- **MILK** (ressourcen-spezifisch): `milk_1`…`milk_4` linear (+0.05/+0.05/
  +0.07/+0.10) + `milk_5` als Fork ab `milk_2` (+0.07), Keystone `milk_4`
- **SUGAR/FLOUR/EGGS/BUTTER/CHOCOLATE** (2026-08-10, PoE-Mesh-Umbau
  2026-08-12, ressourcen-spezifisch, je 8 Knoten, Gebäude-Zuordnung:
  Zuckerteich/Bauernhof/Hühnerhof/Butterei/Plantage): `<res>_1`
  `HARVEST_YIELD` (+0.04) → `<res>_2` `RESOURCE_WAGE_REDUCTION` (+1 %
  Lohn-Reduktion am zugehörigen Gebäude) — Fork-Punkt. Ab hier **echtes
  Mesh statt Baum**: Ertrags-Pfad `<res>_y1` (+0.05) → `<res>_y2` (+0.07) →
  `<res>_y3` **KEYSTONE mit 2 Effekten** (`HARVEST_YIELD` +0.20 **und**
  negative `RESOURCE_WAGE_REDUCTION` −0.05); parallel dazu Lohn-Pfad
  `<res>_w1` (+1.5 %) → `<res>_w2` (+2 %) → `<res>_w3` **KEYSTONE mit 2
  Effekten** (`RESOURCE_WAGE_REDUCTION` +0.12 **und** negativer
  `HARVEST_YIELD` −0.05). Zusätzlich **2 Cross-Link-Kanten** zwischen den
  Pfaden (`<res>_y1`↔`<res>_w1`, `<res>_y2`↔`<res>_w2`) — ein Spieler kann
  vom Ertrags- auf den Lohn-Pfad wechseln (und umgekehrt), ohne zum
  Fork-Punkt zurückzumüssen (`isAdjacentToAllocated`s OR-Konnektivität
  unterstützt das ohne Codeänderung, Kanten sind faktisch ungerichtet).
  **Kein `NOTABLE`-Tier mehr** in diesen 5 Branches (nur `PASSIVE`/
  `KEYSTONE`) — bewusste Abkehr vom alten Einzel-Fork-Muster, das sich wie
  eine lange Kette statt einem echten PoE-Passiv-Baum anfühlte. Radius pro
  Branch jetzt bis 750 (2. Keystone-Ring, `WORLD_SIZE` in `SkillTreeView.vue`
  dafür 1500→1800), Pfade fächern ±8° vom Branch-Bearing. BUTTER hat `_1`/
  `_2` vertauscht (Lohn zuerst) als bewusste Abweichung vom Muster, damit
  nicht alle 5 Zweige identisch wirken.
- **BAKING** (global): `bake_1`…`bake_4` linear (+0.02/+0.02/+0.03/+0.05) +
  `bake_5` als Fork ab `bake_2` (+0.04), Keystone `bake_4`
- **MARKET** (global, Gebühren-Reduktion): `market_1`…`market_4` linear
  (−0.5%/−0.5%/−0.75%/−1%), Keystone `market_4`, kein Fork
- **CORE** (generalistisch, günstige Früh-Picks): `core_1` (+0.04 Ernte,
  global) → `core_2` (+0.015 Backen) **und** `core_3` (−0.5% Markt) →
  `core_4` (+0.06 Ernte, global, konvergierender Fork mit 2 eingehenden
  Kanten, testet Mehrfach-Eltern-Konnektivität, Tier `PASSIVE`)
- **DISPO** (global, Dispo-Zinsreduktion, 2026-08-09): `dispo_1`…`dispo_4`
  linear (−1%/−1%/−1.5%/−2%), Keystone `dispo_4`, kein Fork, Gesamt-
  Reduktion 5.5 Prozentpunkte (10 % Basis → 4.5 % Minimum über den Baum,
  harter Code-Floor bei 2 % zusätzlich, siehe Abschnitt 5)
- **STORAGE** (global, Lager-Branch, 2026-08-10): `storage_1` (+0.05
  `STORAGE_CAP_BONUS`) → `storage_2` (+0.10 `BUILDING_BUFFER_BONUS`) →
  `storage_3` (+0.08 `STORAGE_CAP_BONUS`, Tier `PASSIVE`) → `storage_4`
  (**KEYSTONE mit 2 Effekten**: `BUILDING_BUFFER_BONUS` +0.25 **und**
  `STORAGE_CAP_BONUS` −0.10 — Gebäude sammeln deutlich länger ungestört
  weiter, das Hauptlager selbst schrumpft aber, echter Tradeoff) +
  `storage_5` als Fork ab `storage_2` (+0.10 `STORAGE_CAP_BONUS`)
- **Cross-Branch-Wheel (2026-08-10):** `bridge_bake_market` (Tier `PASSIVE`,
  +0.03 Ernte global) verbindet `bake_3` und `market_3` und verlangt
  **beide** alloziert (AND statt der sonst üblichen OR-Konnektivität, Feld
  `SkillNodeEntity.requiresAllPrereqs` + Sonderfall in
  `SkillTreeService#isAdjacentToAllocated`). **Nicht** MILK-BAKING (die
  ursprüngliche Wahl) — deren gemeinsame NO-Diagonale ist bereits von DISPO
  belegt (`dispo_1`…`dispo_4` laufen exakt `y=-x`), jede direkte Verbindung
  zwischen den beiden Ästen hätte zwangsläufig DISPO-Kanten gekreuzt bzw.
  Knoten überlappt (per Spielertest 2026-08-10 gefunden). Der SO-Quadrant
  zwischen BAKING/MARKET ist dagegen komplett frei. Dahinter der generelle
  Keystone `keystone_alleskoenner` (+0.05 Ernte, global, keine Nachteile) —
  nur erreichbar, wenn in **beiden** angrenzenden Branches vorgearbeitet
  wurde. Neue Brücken **vor dem Festlegen der Koordinaten** gegen alle
  bestehenden Node-Positionen und Kanten prüfen (Kollision: Abstand zweier
  Knotenmittelpunkte < Summe ihrer halben Kantenlängen je Tier — PASSIVE 28,
  NOTABLE 34 (aktuell ungenutzt, siehe oben), KEYSTONE 40; Kreuzung:
  Kantensegmente auf Schnitt prüfen, nicht nur Endpunkte). `bridge_bake_market`
  war ursprünglich als `NOTABLE` angelegt, weil die Verbindung deutlich über
  der üblichen Schrittweite von 150/212 liegt — seit dem 2026-08-12-Umbau
  (nur noch `PASSIVE`/`KEYSTONE` im ganzen Baum) läuft sie als `PASSIVE`
  weiter, die Zwischenstation ist dadurch optisch kleiner als vorher, aber
  kein Sonderfall mehr im Tier-System. Weitere Brücken sind pro künftigem
  Content-Plan optional ergänzbar.

Effektwerte bewusst klein (siehe Kostenkurve oben) — aktuelle Zahlen per
Admin-API live nachgezogen 2026-08-06, ursprüngliche Erstwerte lagen beim
2–3-fachen.

Kalibrierung ist Platzhalter (siehe `docs/ROADMAP.md` §4, Balancing ist ein
separater späterer Pass).

**Wert für Net Worth:** `totalSkillPointCookiesSpent` (kumulierte
Cookie-Ausgaben für Skill-Punkte, serverseitig auf `UserEntity` geführt) —
siehe Abschnitt 10.

**Anpassen bestehender Werte (live, ohne Neustart):** `GET
/api/v1/admin/skilltree/nodes` (Liste) + `PUT
/api/v1/admin/skilltree/nodes/{id}` — Body ist seit 2026-08-10 `SkillNodeEntity`
mit `nameDe`/`nameEn`/`descriptionDe`/`descriptionEn`/`branch`/`nodeTier`/`x`/`y`
plus einer `effects`-Liste (`effectType`/`targetResource`/`effectValue` je
Eintrag, komplett ersetzt statt gemerged). Server validiert jeden
`effectType`-String gegen `EffectType.valueOf(...)` und antwortet mit 400 bei
unbekanntem Wert, Cache wird nach jedem Edit aktualisiert. Plus
`skillPointBaseCost`/`skillPointCostGrowth` über `PUT
/api/v1/admin/config/balance`. **Kein volles CRUD** — es gibt kein `POST`
(neuer Knoten) und kein `DELETE`, Kanten (`SkillEdgeEntity`) sind über die
Admin-API überhaupt nicht editierbar.

**Erweitern (neue Knoten/Branches/Kanten) — nur im Code:**
- Knoten: `SkillTreeService#buildNodes()` — Liste von
  `node(id, nameDe, nameEn, descDe, descEn, branch, nodeTier, x, y, isRoot,
  effects)`, wobei `effects` eine `List<Effect>` ist (`Effect` = lokaler
  Record `(EffectType type, String targetResource, double value)`, ein
  Eintrag pro Effekt/Downside). Ein achter Parameter `requiresAllPrereqs`
  (Overload) markiert Cross-Branch-Brücken, die alle eingehenden Kanten
  statt nur einer alloziert brauchen.
- Kanten: `SkillTreeService#buildEdges()` — welcher Knoten an welchen
  angrenzt (PoE-Konnektivitätsregel; bei `requiresAllPrereqs`-Knoten zeigen
  **alle** Praereq-Kanten mit `toNode == diese ID` darauf, siehe
  `isAdjacentToAllocated`).
- `seedTree()` (`@PostConstruct`, Upsert statt reinem `count == 0`-Check)
  zieht beim nächsten Start automatisch nur die **fehlenden** IDs aus
  `buildNodes()`/`buildEdges()` nach — bestehende Spieler-Allokationen
  (`PlayerSkillNodeEntity`) bleiben unangetastet. Ändert sich Name/Effekt
  eines **bestehenden** Knotens (wie beim 2026-08-10-Umbau), zieht das
  **nicht** automatisch nach — dafür einmalig `skill_nodes` (Postgres droppt
  sie neu über `ddl-auto=update`, siehe unten) bzw. `skill_node_effects`
  leeren, da die DB als disposable gilt (siehe `CLAUDE.md`/ROADMAP).
- **Kein CHECK-Constraint-Risiko mehr bei neuen `EffectType`-Werten**
  (seit 2026-08-10, siehe oben) — einfach Enum-Konstante ergänzen und an
  einer Call-Site verrechnen, fertig. `nodeTier` ist die einzige verbliebene
  `@Enumerated`-Spalte im Skill-Baum-Schema; ein neuer Tier-Wert bräuchte
  wieder den einmaligen `ALTER TABLE skill_nodes DROP CONSTRAINT
  IF EXISTS skill_nodes_node_tier_check;`-Schritt, ist aber ein
  stabiles 3-Werte-Set und wächst nicht.
- Frontend (`SkillTreeDialog.vue`/`SkillTreeView.vue`) zeichnet den Baum
  automatisch aus den Backend-Daten, braucht für neue Knoten keine Änderung
  — außer das Branch-Icon (`BRANCH_ICON`-Map in `SkillTreeView.vue`) für
  einen komplett neuen Branch-Namen, oder ein eigenes Icon in der
  `KEYSTONE_ICON`-Map für einen neuen Keystone.

**Bewusst nicht gebaut (v1):**
- Kein Respec/Un-Allocate-Endpoint (einfacher Folge-Ausbau).
- Keine serverseitige Re-Verifikation der Allokations-Kette gegen
  Manipulation (Anti-Cheat) — Client bekommt nur Server-berechnete
  `allocated`/`allocatable`-Flags und tut selbst keine Konnektivitäts-Logik,
  aber es gibt keinen periodischen Job, der bestehende Allokationen erneut
  gegen die Kanten validiert. Folgearbeit, siehe `docs/ROADMAP.md`.
- Prestige gibt noch keine Bonus-Punkte (ursprünglich mal angedachtes
  "+3 Skill-Punkte pro Prestige") — separates Roadmap-Item.

---

## 10. Net Worth, Leaderboard & Profil

```
netWorth = cookies
         + Σ (resourceAmount_i × currentMarketPrice_i)
         + totalSkillPointCookiesSpent   // kumulierte Skill-Punkt-Ausgaben
```

On-demand berechnet (Leaderboard-/Profilabfrage). Snapshot-History wird
zusätzlich alle 30 s pro Spieler gespeichert und gestuft komprimiert
(< 1 h: roh, 1–24 h: minütlich, > 24 h: stündlich) — Grundlage für den
History-Graph im Net-Worth-Dialog.

**Leaderboard:** sortiert nach aktueller Net Worth, mit Rang.
**Profil:** Steam-ID, Rang, Net Worth (+ Aufschlüsselung), Prestige-Level,
Lifetime gebackene Cookies, Liste freigeschalteter Skill-Knoten,
Season-Historie.

**Statistik-Dialog (2026-08-07):** eigener Vollbild-Dialog (`StatsDialog.vue`
+ `StatsView.vue`, gleiches Vollbild-Muster wie der Skill-Baum), erreichbar
über das Hauptmenü. **Rein privat** — anders als das Profil (über die
Rangliste auch für andere Spieler abrufbar) gibt es dafür keinen
Fremdaufruf-Pfad im Frontend; serverseitig aber nicht durchgesetzt, da es
noch kein echtes Auth-System gibt (`docs/ROADMAP.md` Abschnitt 0). Neuer
Endpoint `GET /api/v1/players/{steamId}/stats` (`PlayerStatsDto`,
`NetWorthService#getStats()`), drei Bereiche:
- **Wirtschaft:** Cookies/Ressourcenwert/Skill-Baum-Wert/Net Worth (aus dem
  bereits geladenen `playerStore`, kein Extra-Request), plus Lifetime
  Markt-Umsatz (gekauft/verkauft in Cookies).
- **Produktion im Überblick:** Tabelle aller Produktionsgebäude (Stufe,
  Arbeiter, passive Rate, Lohn) + "Aktive Boni"-Übersicht — aktueller
  Ernte-Ertrags-Bonus pro Ressource, Back-Ausbeute-Bonus,
  effektive Markt-Gebühr (Gebäude+Skill-Baum kombiniert,
  `BuildingService#getEffectiveSellFeeRate`), Prestige-Multiplikator.
- **Lifetime-Zähler:** insgesamt geerntete Menge pro Ressource (neue
  `UserEntity`-Felder `lifetime{Sugar,Flour,Eggs,Butter,Chocolate,Milk}
  Harvested`, hochgezählt in `UserService#harvest()` und
  `PassiveIncomeService#collectBuilding()` mit dem tatsächlich gutgeschriebenen
  Betrag, nicht dem nominellen vor Lager-Deckel), insgesamt gebackene
  Cookies, Anzahl Prestiges.

---

## 11. Prestige & Season

**Prestige-UI aktuell entfernt (2026-08-06)** — Spieler-seitiger Dialog/HUD-
Button wurden aus dem Frontend genommen (Details: `ROADMAP.md` Abschnitt 4),
das System wird neu gebaut. Backend (Endpoints, `UserEntity`-Felder,
`GameBalanceConfig`) und `playerStore.prestigeMultiplier` (fließt weiter in
die Ernte-Formel ein, bleibt bis zum ersten Prestige bei `1`) bestehen
weiter — unten beschriebene Mechanik ist der Ist-Stand des Backends, nicht
mehr über die UI erreichbar.

**Prestige** — freiwillig pro Spieler, vom Rang komplett entkoppelt:
```
threshold(level)  = 100.000 × 1.5^level
multiplier        = 1 + 0.1 × prestigeLevel
```
Ab `netWorth ≥ threshold(level)` auslösbar. Reset: Cookies, alle Rohstoffe,
der komplette Skill-Baum (`player_skill_nodes` geleert, `skillPoints` und
`totalSkillPointsBought` auf 0 — die Skill-Punkt-Kostenkurve geht wieder auf
billig, anders als `totalPrestiges`), alle Bake-Jobs, **alle Gebäude**
(fallen auf "nicht gebaut" zurück, Backhaus/Rathaus/Markt/Lager werden beim
nächsten Laden automatisch wieder auf Stufe 1 angelegt). Bleibt erhalten:
Prestige-Level (+1), `totalPrestiges`, der permanente Multiplikator (wirkt
auf Backen-Output und Ernte-Menge).

**Season** — globaler Reset aller Spieler, manuell ausgelöst
(`POST /api/v1/admin/season/start`):
- Aktuelles Leaderboard wird pro Spieler als `SeasonResult` archiviert
  (Rang, Net Worth, Prestige-Level) — erscheint später in der Profil-Historie
- Reset: Cookies, Rohstoffe, kompletter Skill-Baum (inkl.
  `totalSkillPointsBought`/`totalSkillPointCookiesSpent`), Bake-Jobs,
  Prestige-Level **aller** Spieler

---

## 12. Bekannte Lücken / Diskrepanzen zum ursprünglichen Plan

- **Gebäude fließen nicht in Net Worth ein** — nur
  `UserEntity.totalSkillPointCookiesSpent` wird addiert, Gebäude-Kaufpreise
  (`PlayerBuildingEntity`) nicht. Ursprünglicher Plan sah beides vor.
- **Season-Reset löscht keine Gebäude** (`player_buildings`) — nur Cookies,
  Ressourcen, Skill-Baum, Bake-Jobs, Prestige-Level. Prestige-Reset macht es
  korrekt (siehe Abschnitt 11). Vermutlich ein Bug, nicht bewusst so designt.
- **Legacy-Frontend-Dateien** aus der Vor-Hof-Grid-Ära sind noch im Repo,
  aber nicht mehr geroutet (siehe Abschnitt 2) — Aufräumen offen.
- **Kosmetik/Titel/Badges** aus dem ursprünglichen Plan (Abschnitt 8 alt)
  sind nicht umgesetzt; es gibt ein `OrdenDialog.vue`/Badge-System
  (`useBadges.js`), das lose in diese Richtung geht, aber nicht als
  "Kosmetik-Reset-Ausnahme" wie ursprünglich geplant behandelt wird.

---

## 13. Datenmodell (Backend, Ist-Stand)

```
UserEntity            steamId, token, cookies, sugar, flour, eggs, butter,
                       chocolate, milk, lifetimeCookiesBaked, prestigeLevel,
                       totalPrestiges, workersIdle, ownedCitizens,
                       skillPoints, totalSkillPointsBought,
                       totalSkillPointCookiesSpent, lastWageAmount,
                       lastWageAt (2026-08-09, Dispo-Abbuchungs-Historie)
PlayerBuildingEntity   userId, buildingId, level, version, workers,
                       pendingAmount, lastSettledAt, lastCollectedAt
WageLedgerEntity       id, userId, totalAmount, breakdownJson
                       (buildingId→Betrag), createdAt — Abrechnungshistorie
                       fürs Rathaus, max. 200 Einträge/Spieler (2026-08-09)
SkillNodeEntity        id, name, description, branch, effectType,
                       targetResource, effectValue, isRoot, x, y
SkillEdgeEntity        id, fromNode, toNode (eine gerichtete Zeile pro Paar,
                       Konnektivitäts-Check behandelt sie als ungerichtet)
PlayerSkillNodeEntity  id (userId+"#"+nodeId), userId, nodeId
                       (binäre Freischaltung, Wurzel wird nicht gespeichert)
RecipeEntity           id, name, sugar, flour, eggs, butter, chocolate, milk,
                       output, bakeDurationSeconds
BakeJobEntity          id, userId, recipeId, batches, startedAt,
                       completesAt, claimed
MarketEntity           id, date, sugarPrice…milkPrice (Zeitreihen-Eintrag)
MarketSnapshotEntity   komprimierte Langzeit-Preishistorie
MarketStockEntity      Singleton-Zeile, Lagerbestand pro Ressource (AMM-Pool)
NetWorthHistoryEntity  userId, timestamp, netWorth, cookies, resourceValue,
                       skillTreeValue
SeasonEntity           id, name, startDate, endDate, active
SeasonResultEntity     seasonId, userId, finalNetWorth, finalRank,
                       prestigeLevelAtEnd
```

---

## 14. API-Endpunkte (Ist-Stand)

| Method | Endpoint | Zweck |
|---|---|---|
| GET | `/api/v1/config` | devMode, sellFeeRate |
| POST | `/api/v1/users/{userId}` | Spieler anlegen/Login |
| GET | `/api/v1/users/{userId}` | Spielerdaten |
| GET | `/api/v1/game/init/{userId}` | Kompletter Init-Payload (User, Markt, Rezepte, Gebäude) |
| POST | `/api/v1/game/harvest/{userId}` | Hover-Ernte |
| POST | `/api/v1/game/bake/start/{userId}` | Bake-Job starten |
| GET | `/api/v1/game/bake/status/{userId}` | Aktueller Bake-Job |
| POST | `/api/v1/game/bake/claim/{userId}` | Bake-Job einlösen |
| GET | `/api/v1/game/prestige/status/{userId}` | Prestige-Status/Schwelle |
| POST | `/api/v1/game/prestige/{userId}` | Prestige-Reset |
| GET | `/api/v1/recipes` | Rezeptliste |
| GET | `/api/v1/farm/buildings/{userId}` | Gebäudeliste + Status |
| POST | `/api/v1/farm/buildings/buy/{userId}` | Gebäude bauen/ausbauen |
| POST | `/api/v1/farm/buildings/workers/{userId}` | Bürger zuweisen/entfernen |
| POST | `/api/v1/farm/buildings/collect/{userId}/{buildingId}` | Passive Produktion eines Gebäudes einsammeln |
| GET | `/api/v1/farm/wage-status/{userId}` | Cookies, letzte Lohnabbuchung, effektiver Dispo-Zinssatz + -grenze (Polling für die fallende rote HUD-Zahl, 2026-08-09) |
| GET | `/api/v1/farm/wage-history/{userId}?limit=` | Abrechnungshistorie, neueste zuerst (Rathaus-Tab, 2026-08-09) |
| POST | `/api/v1/farm/citizens/buy/{userId}` | Bürger anwerben |
| GET | `/api/v1/skilltree?userId=` | Skill-Baum (Knoten+Kanten) + Spielerstatus |
| POST | `/api/v1/skilltree/buy-point/{userId}` | 1 Skill-Punkt kaufen |
| POST | `/api/v1/skilltree/allocate/{userId}` | Skill-Knoten freischalten |
| GET | `/api/v1/market/get/{amount}` | Letzte N Marktpreise |
| GET | `/api/v1/market/all` | Marktpreise (bis 500) |
| GET | `/api/v1/market/history` | Aggregierte Preishistorie (Chart) |
| POST | `/api/v1/market` | Kaufen/Verkaufen |
| GET | `/api/v1/leaderboard` | Rangliste |
| GET | `/api/v1/players/{steamId}/networth` | Net Worth + Rang |
| GET | `/api/v1/players/{steamId}/networth/history` | Net-Worth-Verlauf |
| GET | `/api/v1/players/{steamId}/profile` | Vollständiges Profil |
| GET | `/api/v1/seasons/current` | Aktuelle Season |
| WS | `/ws/market` | Live-Preis-Broadcast |
| POST | `/api/v1/admin/reset/{userId}` | Spieler zurücksetzen (dev/Token) |
| POST | `/api/v1/admin/market/reset` | Markt zurücksetzen (dev/Token) |
| POST | `/api/v1/admin/season/start` | Neue Season starten (Token) |
| GET/PUT | `/api/v1/admin/skilltree/nodes[/{id}]` | Skill-Knoten live editieren (dev/Token) — kein Frontend-Zugang mehr (Admin-Dialog entfernt 2026-08-06, nur noch curl/Token) |

---

## 15. Offene Designfragen

- [ ] Kosmetik/Titel/Badges konkret ausbauen oder Plan streichen
      (aktueller `OrdenDialog`/Badge-Ansatz vs. ursprüngliche Season-übergreifende
      Kosmetik-Idee)
- [ ] Gebäude-Wert in Net Worth aufnehmen? (Abschnitt 12)
- [ ] Season-Reset soll wahrscheinlich auch Gebäude zurücksetzen (Abschnitt 12)
- [ ] Balancing aller Zahlen (Preise, Löhne, Backzeiten, Boost-Stärken,
      Prestige-Schwelle) weiterhin offen, bewusst nicht hier festgeschrieben
