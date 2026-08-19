# ✅ Markt-Gebühr live statt statisch (8%)

> **Status:** ✅ Umgesetzt (2026-08-19)

## Context

`docs/ROADMAP.md` Abschnitt 2 listet: Das Markt-Gebäude zeigt im Hof-Popup
und als Overlay-Badge eine fest eincodierte "8%"-Marktgebühr
(`frontend/src/components/buildings/buildingInfo.js`), obwohl die echte
Gebühr serverseitig mit Markt-Level sinkt (`BuildingService
#getEffectiveSellFeeRate`, −2%/Stufe ab Stufe 2) und zusätzlich durch den
DISPO-Skillbaum-Zweig (`MARKET_FEE_REDUCTION`) reduziert werden kann.

Gleiches Bug-Muster wie beim bereits gefixten Lohn/Ertrag-Anzeige-Bug
(2026-08-09, siehe Roadmap Abschnitt 4): Anzeige war nie an echte Werte
gekoppelt. Zusätzlich gefunden: der Default-Basiswert ist serverseitig
tatsächlich `0.15` (`MarketConfig.sellFeeRate`), nicht `0.08` — der
statische Text war also nicht nur unveränderlich, sondern selbst als
Basiswert bereits falsch.

Ziel: Popup-Zeile und Overlay-Badge auf dem Markt-Gebäude zeigen den echten,
live berechneten effektiven Satz — analog zum bestehenden Muster für
Produktionsgebäude in `FarmGridView.vue`.

## Backend

**`BuildingService.java`**
- `getEffectiveSellFeeRate` in eine dritte Overload mit dem eigentlichen
  Kern zerlegen (nimmt direkt `marktLevel` statt einer Owned-Map), die
  bestehenden zwei Overloads delegieren nur noch:
  ```java
  public double getEffectiveSellFeeRate(String userId, double baseRate) {
      return getEffectiveSellFeeRate(userId, ownedMap(userId), baseRate);
  }
  public double getEffectiveSellFeeRate(String userId, Map<String, PlayerBuildingEntity> owned, double baseRate) {
      int marktLevel = owned.containsKey("markt") ? owned.get("markt").getLevel() : 0;
      return getEffectiveSellFeeRate(userId, marktLevel, baseRate);
  }
  public double getEffectiveSellFeeRate(String userId, int marktLevel, double baseRate) {
      double discount = Math.max(0, marktLevel - 1) * 0.02;
      double skillDiscount = skillTreeService.getEffectTotal(userId, EffectType.MARKET_FEE_REDUCTION, null);
      return Math.max(0.01, baseRate - discount - skillDiscount);
  }
  ```
- Konstruktor: `MarketConfig marketConfig` als neue Dependency injizieren
  (kein zirkulärer Bean-Bezug — `MarketConfig` ist ein reiner
  `@ConfigurationProperties`-Bean ohne eigene Service-Dependencies;
  `MarketService` hängt bereits von `BuildingService` ab, nicht umgekehrt).
- `toDto(BuildingDef def, PlayerBuildingEntity ent, String userId)`: für
  `def.id().equals("markt")` zusätzlich
  `dto.setFeeRate(getEffectiveSellFeeRate(userId, level, marketConfig.getSellFeeRate()))`
  setzen (0 für alle anderen Gebäude, Default bleibt implizit `0.0`).

**`PlayerBuildingDto.java`**
- Neues Feld `private double feeRate;` + Getter/Setter, gleiche Struktur wie
  die bestehenden Felder (`wagePerMin` etc.).

## Frontend

**`FarmGridView.vue`** (`buildings` computed, aktuell Zeilen ~345–368)
- Analog zum bestehenden `info.resource`-Branch (live Lohn/Ertrag-Zeilen)
  einen Branch für `id === 'markt'` ergänzen: Popup-`rows` aus
  `owned.feeRate` statt `info.rows` bauen (`Marktgebühr` Zeile mit
  `(owned.feeRate * 100).toFixed(1)}%`).
- `overlayRate` für Markt ebenfalls aus `owned.feeRate` überschreiben (wird
  aktuell nur aus dem statischen `info`-Spread übernommen), Format an
  bestehende Overlay-Badges der anderen Gebäude angleichen (z. B.
  `GEB. ${(owned.feeRate * 100).toFixed(0)}%`).
- Kommentar an der bestehenden Erklärung (Zeilen 345–348) ergänzen: Markt
  ist jetzt kein rein-statischer Fall mehr.

**`buildingInfo.js`**
- Statischer Fallback-Text bleibt als Platzhalter für den Ladezustand
  bestehen (gleiches Muster wie bei den Produktionsgebäuden), aber Basiswert
  auf den echten Default `15 %` korrigieren, damit der kurze Flash vor dem
  ersten Store-Load nicht mit falscher Zahl aufschlägt.

## Bewusst außerhalb des Scopes

`MarketView.vue` (Kauf/Verkauf-Dialog) zeigt die Verkaufsvorschau
(`netPayout`) ebenfalls mit dem flachen Config-`sellFeeRate` statt dem
effektiven Satz — gleiche Bug-Klasse, aber ein anderer Ort/eigener Fix
(braucht den Fee-Rate-Wert im Dialog-Kontext, nicht nur am Gebäude-DTO).
Nicht Teil des Roadmap-Eintrags, der sich explizit nur auf das
Hof-Popup bezieht — als Folge-Punkt in der Roadmap ergänzen statt hier
mitzuziehen.

## Verifikation

1. Backend + Frontend im Dev-Modus starten (`scripts/start.sh` o. ä.).
2. Markt-Gebäude im Hof hovern → Popup zeigt `15.0%` bei Stufe 1 (Standard-
   Default, kein Discount).
3. Markt-Gebäude ein paar Stufen hochziehen (Admin-Token oder normaler
   Kauf-Flow) → Popup-Wert sinkt sichtbar um 2%-Punkte pro Stufe,
   Overlay-Badge auf dem Gebäude selbst zieht mit.
4. Optional: einen `MARKET_FEE_REDUCTION`-Skillknoten aktivieren → Wert
   sinkt zusätzlich.
5. `cd backend/cookie-server-spring-boot && ./mvnw -q compile` (Compile-
   Check für die neue Overload/DTO-Feld).

## Danach

Nach Commit: `docs/ROADMAP.md` Zeile 203–208 abhaken (Häkchen + Datum/Commit
laut CLAUDE.md-Konvention), diese Datei auf `open`→`done` umbenennen, beide
Status-Marker auf ✅ umstellen.
