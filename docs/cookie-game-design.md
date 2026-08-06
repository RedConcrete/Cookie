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
| Lohn / Idle-Mechanik | ✅ | ✅ | spielbar |
| Markt (AMM-Preismodell, Angebot/Nachfrage) | ✅ | ✅ | spielbar |
| Live-Preise via WebSocket | ✅ | ✅ | spielbar |
| Rezept-Varianten + Bake-Timer | ✅ | ✅ | spielbar (3 Rezepte) |
| Upgrade-System (Boosts + Kapazität) | ✅ | ✅ | spielbar |
| Net Worth / History-Graph | ✅ | ✅ | spielbar |
| Prestige | ✅ | ✅ | spielbar |
| Season-Reset | ✅ | ✅ (Admin) | spielbar, siehe Lücke in Abschnitt 12 |
| Leaderboard / Profil | ✅ | ✅ | spielbar |
| Pixel-Art-Rework (Abschnitt 8) | – | ✅ | fertig, kein Plan mehr |
| Sound (Musik + SFX) | – | ✅ | fertig |
| Hotkeys (konfigurierbar) | – | ✅ | fertig |

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
- Marktgebühr auf jeden Verkauf (`sellFeeRate`, Standard 5 %, senkbar über
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
ist entfernt). Wenn das Lager voll ist, wird der Überschuss automatisch zum
aktuellen Marktpreis (abzüglich Gebühr) verkauft statt verloren zu gehen —
gilt für Hover-Ernte wie für passive Produktion.

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
- **Produktion:** jeder zugewiesene Bürger erzeugt passiv Ressourcen
  (`passiveRatePerSecPerWorker` je Gebäude), alle 5 Sekunden gutgeschrieben.
- **Lohn:** jede Minute wird die Summe aller Gebäude-Löhne (`wagePerMin`)
  vom Cookie-Konto abgebucht. Reicht das Guthaben nicht, werden **alle**
  Bürger auf `idle` gesetzt — passive Produktion pausiert komplett, bis
  wieder genug Cookies da sind (kein Teilausfall, alles oder nichts).

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
Ausgangsbestand), leicht Richtung Ausgangsbestand vorgespannt, damit der
Markt langfristig nicht wegdriftet. Der Preis wird danach aus der Kurve neu
abgeleitet — ein einziger Formel-Pfad für echte Trades wie Hintergrundrauschen.

**Marktgebühr:** `sellFeeRate` (Standard 5 %, config `MarketConfig`),
reduzierbar durch Markt-Gebäude-Level (Abschnitt 4). Nur beim SELL fällig,
BUY ist gebührenfrei.

**Admin:** `POST /api/v1/admin/market/reset` setzt Stock+Preise aller
Ressourcen auf die Ausgangswerte zurück (dev-mode ohne Token nötig).

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

## 9. Upgrade-System (Cookie-Sink)

Zwei verbliebene Typen (Automatisierung/Typ-B ist mit dem Bürger-System
entfallen, Gebäude/Typ-D läuft jetzt über Abschnitt 4, nicht mehr hier):

**A) Boosts** — permanente Stat-Multiplikatoren
- *Schärferes Werkzeug*: +0.5 Ressourcen pro Ernte-Tick
- *Turbopflücker*: −100 ms Ernte-Intervall pro Stufe (Basis 1000 ms, Min 200 ms, max Stufe 8)
- *Große Schüssel*: +10 % Cookie-Ausbeute pro Backen-Batch

Kapazität/Typ-C ("Zweiter Ofen") entfernt (2026-08-06) — nur 1 Bake-Job-Slot,
bewusst kein Mehrfach-Slot-Upgrade mehr (siehe Abschnitt 7).

**Kostenkurve** (alle Upgrades hier):
```
cost(level) = baseCost × 1.15^level
```

**Wert für Net Worth:** kumulierter Kaufpreis aller Upgrade-Stufen (siehe
Lücke zu Gebäuden in Abschnitt 12).

---

## 10. Net Worth, Leaderboard & Profil

```
netWorth = cookies
         + Σ (resourceAmount_i × currentMarketPrice_i)
         + Σ (totalSpent_j)   // über alle gekauften Upgrade-Stufen
```

On-demand berechnet (Leaderboard-/Profilabfrage). Snapshot-History wird
zusätzlich alle 30 s pro Spieler gespeichert und gestuft komprimiert
(< 1 h: roh, 1–24 h: minütlich, > 24 h: stündlich) — Grundlage für den
History-Graph im Net-Worth-Dialog.

**Leaderboard:** sortiert nach aktueller Net Worth, mit Rang.
**Profil:** Steam-ID, Rang, Net Worth (+ Aufschlüsselung), Prestige-Level,
Lifetime gebackene Cookies, Upgrade-Liste, Season-Historie.

---

## 11. Prestige & Season

**Prestige** — freiwillig pro Spieler, vom Rang komplett entkoppelt:
```
threshold(level)  = 100.000 × 1.5^level
multiplier        = 1 + 0.1 × prestigeLevel
```
Ab `netWorth ≥ threshold(level)` auslösbar. Reset: Cookies, alle Rohstoffe,
alle Upgrades, alle Bake-Jobs, **alle Gebäude** (fallen auf "nicht gebaut"
zurück, Backhaus/Rathaus/Markt/Lager werden beim nächsten Laden automatisch
wieder auf Stufe 1 angelegt). Bleibt erhalten: Prestige-Level (+1),
`totalPrestiges`, der permanente Multiplikator (wirkt auf Backen-Output und
Ernte-Menge).

**Season** — globaler Reset aller Spieler, manuell ausgelöst
(`POST /api/v1/admin/season/start`):
- Aktuelles Leaderboard wird pro Spieler als `SeasonResult` archiviert
  (Rang, Net Worth, Prestige-Level) — erscheint später in der Profil-Historie
- Reset: Cookies, Rohstoffe, Upgrades, Bake-Jobs, Prestige-Level **aller**
  Spieler

---

## 12. Bekannte Lücken / Diskrepanzen zum ursprünglichen Plan

- **Gebäude fließen nicht in Net Worth ein** — nur `PlayerUpgradeEntity.totalSpent`
  wird summiert, Gebäude-Kaufpreise (`PlayerBuildingEntity`) nicht. Ursprünglicher
  Plan sah beides vor.
- **Season-Reset löscht keine Gebäude** (`player_buildings`) — nur Cookies,
  Ressourcen, Upgrades, Bake-Jobs, Prestige-Level. Prestige-Reset macht es
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
                       totalPrestiges, workersIdle, ownedCitizens
PlayerBuildingEntity   userId, buildingId, level, workers
PlayerUpgradeEntity    userId, upgradeId, level, totalSpent
UpgradeEntity          id, name, description, type, targetResource,
                       baseCost, effectPerLevel, maxLevel
RecipeEntity           id, name, sugar, flour, eggs, butter, chocolate, milk,
                       output, bakeDurationSeconds
BakeJobEntity          id, userId, recipeId, batches, startedAt,
                       completesAt, claimed
MarketEntity           id, date, sugarPrice…milkPrice (Zeitreihen-Eintrag)
MarketSnapshotEntity   komprimierte Langzeit-Preishistorie
MarketStockEntity      Singleton-Zeile, Lagerbestand pro Ressource (AMM-Pool)
NetWorthHistoryEntity  userId, timestamp, netWorth, cookies, resourceValue,
                       upgradeValue
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
| POST | `/api/v1/farm/citizens/buy/{userId}` | Bürger anwerben |
| GET | `/api/v1/upgrades?userId=` | Upgrade-Liste + Preise |
| POST | `/api/v1/upgrades/buy/{userId}` | Upgrade kaufen |
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

---

## 15. Offene Designfragen

- [ ] Kosmetik/Titel/Badges konkret ausbauen oder Plan streichen
      (aktueller `OrdenDialog`/Badge-Ansatz vs. ursprüngliche Season-übergreifende
      Kosmetik-Idee)
- [ ] Gebäude-Wert in Net Worth aufnehmen? (Abschnitt 12)
- [ ] Season-Reset soll wahrscheinlich auch Gebäude zurücksetzen (Abschnitt 12)
- [ ] Balancing aller Zahlen (Preise, Löhne, Backzeiten, Boost-Stärken,
      Prestige-Schwelle) weiterhin offen, bewusst nicht hier festgeschrieben
