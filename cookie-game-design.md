# Cookie — Game Design Dokument

Stand: Juni 2026 · Basis: Ist-Analyse des Repos `RedConcrete/Cookie`

---

## 1. Core Loop

```
Hof-Übersicht (Hex-Grid mit Gebäuden)
   → Ernten an Rohstoff-Feldern (Hover)
      → Markt-Gebäude: fehlende Zutaten kaufen / Überschuss verkaufen (Gebühr)
         → Backofen-Gebäude: Rezept wählen & Backen starten (Timer läuft)
            → Zwischenzeit: weiter ernten/handeln
               → Backen abschließen → Cookies
                  → Cookies in Upgrades & neue Gebäude investieren
                     → Net Worth steigt (Cookies + Rohstoffe + Upgrade-/Gebäude-Wert)
                        → Prestige (freiwilliger Komplett-Reset: Multiplikator + Kosmetik)
                           → Season-Ende: globaler Reset, Ergebnis wird archiviert
```

Der Kreislauf ist im Code teilweise vorhanden. Phase 1 (Grund-Backen) ist
erledigt, der Rest wird über die folgenden Phasen aufgebaut — beginnend mit
dem Hof-Grid als neue Hauptansicht (Abschnitt 4).

---

## 2. Ist-Zustand (was schon funktioniert)

| System | Backend | Frontend | Status |
|---|---|---|---|
| Ernten (Hover-Sammeln) | ✅ | ✅ | spielbar, wird ins Hof-Grid umgezogen |
| Markt (Kauf/Verkauf, Angebot/Nachfrage) | ✅ | ✅ | spielbar, Gebühr fehlt noch |
| Live-Preise via WebSocket | ✅ | ✅ | spielbar |
| Cookie backen (1 Rezept, instant) | ✅ | ✅ | spielbar, wird durch Varianten+Timer ersetzt |
| Hof-Grid (Haupt-Ansicht) | ❌ | ❌ | neu |
| Rezept-Varianten + Bake-Timer | ❌ | ❌ | neu |
| Markt-Verkaufsgebühr (Sink) | ❌ | – | neu |
| Upgrade-System | ❌ | ❌ | neu |
| Net Worth / Highscore | ❌ | ❌ | neu |
| Prestige | ❌ | ❌ | neu |
| Season-Reset | ❌ | ❌ | neu |
| Leaderboard / Profil | ❌ | ❌ | neu |

Relevante offene Issues aus dem Repo: #19 (Markt-Server-Logik unklar),
#21 (Ressourcenauswahl kaputt), #22 (Upgrade-System geplant — wird mit
diesem Dokument konkretisiert), #24 (Markt-Graph unübersichtlich),
#14 (Linux-Build kaputt).

---

## 3. Wirtschaft: Cookie-Quellen & -Senken

Wichtiger Fund bei der Code-Analyse: Der bestehende Markt hat kein eigenes
Cookie-Konto — BUY vernichtet Cookies (`cookies -= totalCost`), SELL erzeugt
sie aus dem Nichts (`cookies += totalCost`). Ressourcen werden gratis
geerntet, d. h. SELL ist aktuell ein offener Cookie-Faucet ohne hartes Limit.
Damit das Gesamtsystem nicht unkontrolliert inflationiert, hier die volle
Übersicht aller Quellen und Senken:

**Faucets (Cookies entstehen):**
- Backen (Rezept: Ressourcen → Cookies)
- Verkaufen auf dem Markt (SELL)

**Sinks (Cookies verschwinden):**
- Kaufen auf dem Markt (BUY) — bereits vorhanden
- **Markt-Verkaufsgebühr (NEU)** — ein Teil jedes SELL-Erlöses wird vernichtet
  statt ausgezahlt, statt 1:1 Cookies zu erzeugen
- Upgrades (permanenter Sink, Abschnitt 6)
- **Neue Gebäude auf dem Hof** (permanenter Sink, Abschnitt 4 — technisch
  ein eigener Upgrade-Typ, siehe Abschnitt 6 Typ D)
- Prestige-Reset (harter Sink pro Spieler, Abschnitt 9)
- Season-Reset (harter Sink für alle, Abschnitt 10)

Die Gebühr wirkt laufend, Upgrades/Gebäude mittelfristig, die Resets
periodisch hart — sich ergänzende Bremsen gegen Inflation.

**Startzustand: 0 Cookies, 0 Ressourcen, 0 Upgrades, nur Markt + Backofen
gebaut.** Damit ist der Zustand eines brandneuen Spielers identisch zum
Zustand direkt nach einem Prestige-Reset oder Season-Reset — ein einziger,
konsistenter Startpunkt für alle Fälle. Net Worth beginnt bei jedem bei
exakt 0, Rang-Fortschritt spiegelt damit echten Fortschritt wider.
Harvesting ist gratis, also kein Soft-Lock: erster Schritt ist immer
einfach ernten.

**Mechanik Markt-Verkaufsgebühr:**
```
payout = totalCost × (1 - sellFeeRate)
```
`sellFeeRate` wird ein konfigurierbarer Wert in `MarketConfig` (wie
`tradeDivisor`, `minPrice` etc.). Genaue Höhe: Balancing später.

---

## 4. Hof-Grid (Haupt-Ansicht)

**Ersetzt die bisherige Tab-Navigation (Markt/Produktion/Backen) komplett.**
Beim Einloggen sieht der Spieler direkt seinen Hof als Hex-Grid — eine
Übersicht aller Gebäude, fest positioniert. Technischer Zwischenschritt vor
dem vollen Pixel-Art-Rework (Abschnitt 16): bleibt DOM-basiert (Vue + CSS),
nutzt das bereits bestehende Hex-Positionierungssystem aus `IdleView.vue`,
keine Render-Engine nötig.

**Layout (v1):**
- **Markt** — Zentrum, von Anfang an gebaut
- **Backofen** — direkt darüber, von Anfang an gebaut. Läuft ein Bake-Job,
  wird der Fortschritt/Restzeit direkt über dem Gebäude angezeigt
- **6 Rohstoff-Felder** (Zucker, Mehl, Eier, Butter, Schokolade, Milch) —
  an den Seiten verteilt, von Anfang an verfügbar (bestehende
  Hover-Ernten-Mechanik, nur umgezogen vom separaten Grid hierher).
  Optische Gebäude-Zuordnung (z. B. Kuhstall = Milch, Weizenfeld = Mehl)
  ist späteres Polishing, nicht Teil von v1
- **Weitere Gebäude** (Upgrade-Shop, Leaderboard-Turm, …) erscheinen
  **erst, sobald das jeweilige System fertig gebaut ist** — keine
  Platzhalter für unfertige Systeme. Sobald verfügbar, werden sie wie ein
  Upgrade mit Cookies "gebaut" (Abschnitt 6, Typ D)

**Interaktion:**
- Klick auf ein gebautes Gebäude öffnet einen Dialog mit der zugehörigen
  Funktion (z. B. Markt-Klick → Handels-Dialog mit aktuellem `MarketView.vue`-Inhalt;
  Backofen-Klick → Dialog mit allen Rezepten + aktuell laufendem Bake-Job)
- Kamera: rudimentäres Pan (Ziehen/Pfeile), keine Zoom-Mechanik in v1

**Reset-Verhalten:** Prestige- und Season-Reset entfernen alle zusätzlich
gebauten Gebäude wieder — übrig bleiben nur Markt + Backofen (siehe
Abschnitt 9/10).

---

## 5. Resource & Recipe System

**Design-Ziel:** Mehrere Rezepte mit unterschiedlichen Ressourcen-Profilen,
damit nicht alle Spieler denselben "optimalen" Mix einkaufen. Das hält den
globalen Markt über alle 6 Rohstoffe hinweg lebendig statt auf 1–2
Ressourcen konzentriert.

**Tradeoff-Achsen pro Rezept:**
- Ressourcenmix (welche Zutat dominiert)
- Gesamtkosten (günstig & wenig Output vs. teuer & viel Output)
- Backzeit (echter Timer/Cooldown, kein Insta-Klick mehr)

**Beispielhafte Rezept-Typen** (Zahlen Platzhalter, Balancing später beim Testen):

| Rezept | Profil | Output | Backzeit |
|---|---|---|---|
| Standard | ausgewogen, je 10x | 100 Cookies | kurz |
| Milchcookie | viel Milch, wenig Mehl/Zucker | mehr Cookies | länger |
| Sparrezept | insgesamt wenig Ressourcen | weniger Cookies | kurz |

Genaue Zutatenmengen, Output-Werte und Sekunden pro Rezept: Balancing-Phase,
nicht jetzt im Design festlegen.

**Bake-Timer-Mechanik:**
- Beim Start eines Backvorgangs werden Ressourcen sofort abgezogen (verhindert Exploits durch Abbrechen)
- Ein Bake-Job läuft serverseitig mit `startedAt` / `completesAt`
- Spieler kann während der Wartezeit normal weiter ernten/handeln
- Nach Ablauf: Cookies werden per Claim gutgeschrieben (oder automatisch beim nächsten Laden)
- Pro Spieler standardmäßig **1 aktiver Bake-Job gleichzeitig**. Mehr
  parallele Slots sind ein kaufbares Upgrade (siehe Abschnitt 6, Typ C)

---

## 6. Upgrade-System (Cookie-Sink)

Vier Typen, alle mit Cookies gekauft, alle mit Levels (außer D, binär):

**A) Boosts** — permanente Stat-Multiplikatoren
- *Schärferes Werkzeug*: Ernte-Rate pro Hover (+X% pro Level)
- *Große Schüssel*: Cookie-Ausbeute pro Backen-Batch (+X% pro Level)

**B) Automatisierung** — passive Produktion ohne Hover
- *Auto-Pflücker* (pro Rohstoff): generiert automatisch Ressourcen im
  Hintergrund, auch ohne dass der Spieler aktiv hovert

**C) Kapazität** — Backofen-Slots
- *Zweiter Ofen*: +1 paralleler Bake-Job-Slot (Basis: 1 Slot ohne Upgrade)

**D) Gebäude** — neue Hof-Gebäude freischalten
- Einmaliger Cookie-Kauf, schaltet ein neues Gebäude auf dem Hof-Grid frei
  (z. B. Upgrade-Shop-Gebäude, Leaderboard-Turm), sobald das zugehörige
  System fertig implementiert ist

**Kostenkurve** (Vorschlag, klassisches Idle-Game-Pattern, gilt für A–C):
```
cost(level) = baseCost × 1.15^level
```

**Wert für Net Worth:** voller kumulierter Kaufpreis aller bisher
gekauften Upgrade-Stufen und Gebäude (kein Wiederverkaufsabschlag).

Konkrete Upgrade-/Gebäude-Liste, baseCost-Werte und Boost-Prozentsätze: Balancing-Phase.

---

## 7. Net Worth (Highscore-Basis)

```
NetWorth = cookies
         + Σ (resourceAmount_i × currentMarketPrice_i)
         + Σ (purchasePrice_j)   // über alle gekauften Upgrades + Gebäude
```

Wird **on-demand** berechnet (bei Leaderboard-Abfrage oder Profilaufruf) —
kein zusätzlicher Live-Tracking-Aufwand nötig, da Marktpreise eh schon
zentral vorliegen.

---

## 8. Leaderboard & Profil

- Rangliste sortiert nach Net Worth (aktuelle Season)
- Klick auf Eintrag → Spieler-Profil mit:
  - Steam-Name/Avatar
  - Aktuelle Net Worth, Prestige-Level
  - Lifetime gebackene Cookies
  - Season-Historie (vergangene Platzierungen/Ergebnisse)
  - Freigeschaltete Kosmetik (Titel, Badges)

---

## 9. Prestige-System

**Prestige ist vom Rang/Highscore komplett entkoppelt.** Der Rang basiert
ausschließlich auf der aktuellen Net Worth — ein hohes Prestige-Level gibt
keinen Rang-Bonus, sondern hilft nur, Net Worth nach einem Reset schneller
wieder aufzubauen.

- Freiwilliger Reset, sobald eine Mindest-Net-Worth erreicht ist
- **Reset:** Cookies, alle Rohstoffe, alle Upgrades UND alle zusätzlich
  gebauten Hof-Gebäude — kompletter Wipe auf den Startzustand (nur
  Markt + Backofen übrig)
- **Bleibt erhalten (permanent, übersteht auch Season-Resets):**
  Prestige-Level (+1), permanenter Multiplikator, freigeschaltete Kosmetik

**Strukturformeln (Platzhalter, Balancing in der Testphase):**
```
threshold(level)  = 100.000 × 1.5^level
multiplier        = 1 + (0.1 × prestigeLevel)
```

---

## 10. Season-System

- Globaler Reset **aller** Spieler (Cookies, Ressourcen, Upgrades, Gebäude, Prestige)
- **Trigger: manuell**, vom Dev bei größeren Updates ausgelöst (entschieden)
- Vor dem Reset: aktuelle Rangliste wird als Season-Ergebnis pro Spieler archiviert
  (taucht später in der Profil-Historie auf)
- Kosmetik/Account-Achievements bleiben über Seasons hinweg erhalten

---

## 11. Offene Designfragen

- [ ] Kosmetik konkret: Titel-Text? Profil-Rahmen? Icons? — später, wenn Design klarer
- [ ] Welches Gebäude steht optisch für welchen Rohstoff (Kuhstall=Milch,
      Weizenfeld=Mehl, …) — Polishing-Frage, nicht blockierend für v1
- [ ] Alle Balancing-Zahlen (sellFeeRate, Prestige-Schwelle/Multiplikator,
      Upgrade-/Gebäude-Kosten, Boost-Werte, Rezept-Mengen/Output/Backzeit) —
      bewusst erst in der Testphase, nicht im Design

---

## 12. Datenmodell-Erweiterungen (Backend)

**`UserEntity` erweitern:**
```
prestigeLevel         int
totalPrestiges        int
lifetimeCookiesBaked  double
currentSeasonId       String
```

**`MarketConfig` erweitern:**
```
sellFeeRate    double   // Anteil des Verkaufserlöses, der vernichtet wird
```

**Neue Entities:**
```
RecipeEntity          (id, name, sugar, flour, eggs, butter, chocolate, milk, output, bakeDurationSeconds)
BakeJobEntity         (id, userId, recipeId, batches, startedAt, completesAt, claimed)
UpgradeEntity         (id, type [BOOST|AUTOMATION|CAPACITY|BUILDING], targetResource, gridPosition, baseCost, effectPerLevel)
PlayerUpgradeEntity   (userId, upgradeId, level, totalSpent)
SeasonEntity          (id, startDate, endDate, active)
SeasonResultEntity    (seasonId, userId, finalNetWorth, finalRank, prestigeLevelAtEnd)
PlayerCosmeticEntity  (userId, cosmeticId, unlockedAt)
```
`gridPosition` nur relevant für Typ `BUILDING` — feste Hex-Koordinate auf
dem Hof-Grid, wo das Gebäude erscheint, sobald gebaut.

---

## 13. Neue / geänderte API-Endpunkte

| Method | Endpoint | Zweck |
|---|---|---|
| GET | `/api/v1/farm/layout` | Hof-Grid-Layout: Positionen + Baustatus aller Gebäude |
| GET | `/api/v1/recipes` | Liste verfügbarer Rezepte |
| POST | `/api/v1/game/bake/start/{userId}` | Bake-Job starten (recipeId, batches), Ressourcen abziehen |
| GET | `/api/v1/game/bake/status/{userId}` | Aktueller Bake-Job + Restzeit |
| POST | `/api/v1/game/bake/claim/{userId}` | Fertigen Bake-Job einlösen → Cookies |
| GET | `/api/v1/upgrades` | Liste verfügbarer Upgrades/Gebäude + Preise |
| POST | `/api/v1/game/upgrade/{userId}` | Upgrade/Gebäude kaufen/leveln |
| GET | `/api/v1/leaderboard?seasonId=current` | Rangliste nach Net Worth |
| GET | `/api/v1/players/{steamId}/profile` | Vollständiges Profil + Historie |
| POST | `/api/v1/game/prestige/{userId}` | Prestige-Reset ausführen |
| POST | `/api/v1/admin/season/start` | Neue Season auslösen (geschützt) |

Alter `POST /api/v1/game/produce/{userId}` wird durch das Bake-Job-Trio ersetzt.
`MarketService.performAction()` (SELL-Zweig) wird um die Gebühr ergänzt.

---

## 14. Frontend-Erweiterungen

- `FarmGridView.vue` (NEU) — Haupt-Ansicht, ersetzt `App.vue`-Tab-Navigation;
  rendert Hex-Grid mit `BuildingTile.vue` pro Gebäude/Feld, einfaches
  Pan (Drag/Pfeile)
- `BuildingTile.vue` (NEU) — einzelnes Gebäude/Feld: gebaut, Hover-Erntefeld,
  oder unsichtbar (nicht freigeschaltet); öffnet bei Klick den passenden Dialog
- `MarketDialog.vue` — wrappt bestehenden `MarketView.vue`-Inhalt als Modal
- `BakeDialog.vue` (vormals `RecipeCard.vue`/`BakeView.vue`) — Rezeptauswahl
  (Tabs/Dropdown), Zutatenanzeige je Rezept, Fortschrittsbalken/Countdown,
  Claim-Button
- `UpgradeShopView.vue` — eigenes Gebäude, Liste der Upgrades, Kaufen-Buttons
- `LeaderboardView.vue` — eigenes Gebäude, Rangliste, Klick → Profil
- `PlayerProfileView.vue` — Profilansicht/Modal
- `PrestigePanel.vue` — zeigt Net Worth, Schwelle, Multiplikator, Reset-Bestätigung

---

## 15. Implementierungs-Reihenfolge

| Phase | Inhalt | Aufwand |
|---|---|---|
| 1 | ✅ RecipeCard einbinden → Grund-Loop spielbar (1 instant Rezept) | klein |
| 1b | Markt-Verkaufsgebühr (Sink) | klein |
| 1c | Rezept-Varianten + Bake-Timer (ersetzt Phase-1-Mechanik) | mittel-groß |
| 1d | Hof-Grid als Hauptansicht (Markt + Backofen + Rohstoff-Felder, ersetzt Tabs) | mittel-groß |
| 2 | Upgrade-System inkl. Gebäude-Typ D (Backend + Frontend) | mittel |
| 3 | Net-Worth-Berechnung + einfaches Leaderboard (eigenes Hof-Gebäude) | mittel |
| 4 | Spieler-Profil-Ansicht | mittel |
| 5 | Prestige-System (Backend + Frontend) | mittel-groß |
| 6 | Season-System (Reset + Archivierung) | groß, kann zeitlich entkoppelt werden |

Bestehende Bugs (#19, #21, #24, #14) je nach Dringlichkeit parallel einstreuen.

---

## 16. Langfristige Vision: Visueller Pixel-Art-Rework (nach v1)

Referenz: "Forage Wizard" — Vogelperspektive, Pixel-Art, freie Kamera,
individuelle Gebäude-Sprites pro Ressource (z. B. Kuhstall für Milch,
Weizenfeld für Mehl).

- **Baut direkt auf dem Hof-Grid aus Abschnitt 4 auf** — gleiche Struktur
  (feste Gebäude-Positionen, Klick öffnet Dialog), nur die Render-Schicht
  wird ausgetauscht
- Technisch ein eigener Schnitt: das Hof-Grid (v1) ist DOM-basiert
  (Vue + CSS). Für echte Pixel-Art-Gebäude mit freier Kamera braucht es
  eine echte Render-Schicht (z. B. PixiJS oder Phaser) im
  Electron-Frontend — Sprites, Kamera, Gebäude-Platzierung statt
  Vue-Templates
- Wird als eigene Design-Iteration angegangen, sobald v1 (Phase 1–6) steht
