# ✅ Feature: Dispo-Kredit statt Idle bei zu wenig Cookies für Lohn

> **Status:** ✅ Umgesetzt (09.08.2026, commit f01f54d)

## Context

Bisher: reicht das Cookie-Guthaben nicht für den Lohn (`WageService#deductWageForUser`),
gehen **alle** Arbeiter auf einmal idle, Produktion pausiert komplett, bis wieder genug
Cookies da sind. User will das durch einen Dispo-Kredit ersetzen: Cookies dürfen ins Minus
gehen, Arbeiter laufen normal weiter, aber auf das Minus fallen 10 % Zinsen pro Lohn-Tick an
(wie ein Dispokredit) — reduzierbar über einen neuen Zweig im Passiv-Skill-Baum. Damit eine
Zins-Spirale nicht endlos wächst, gibt es eine Dispo-Grenze (Vielfaches des aktuellen
Lohns/Minute); wird die überschritten, greift die bisherige Idle-Sperre als harter Stopp.

Entschieden (User-Antworten):
- Arbeiter laufen im Dispo normal weiter (kein Idle mehr nur wegen Zahlungsunfähigkeit)
- Dispo-Grenze vorhanden (Vielfaches des aktuellen Lohns/Minute), darüber alte Idle-Sperre
- Zinsen laufen im selben 60s-Lohn-Tick mit (kein eigener Scheduler)

## Wichtiger Fund: Skill-Baum-Seeding-Falle

`SkillTreeService#seedTree()` (`@PostConstruct`) befüllt `skill_node`/`skill_edge` nur, wenn
die Tabelle **komplett leer** ist (`skillNodeRepository.count() == 0`). Auf der bestehenden
Dev-DB und dem Live-Beta-Server ist sie das nicht mehr — ein neuer Skill-Zweig in
`buildNodes()` würde also nie in der DB landen. Muss vorher auf ein Upsert-Verfahren
umgestellt werden (fehlende IDs nachziehen, vorhandene/allozierte Knoten unangetastet lassen).

## Backend-Änderungen

**`SkillTreeService.java`** — `seedTree()` robust machen:
```java
@PostConstruct
public void seedTree() {
    List<SkillNodeEntity> defined = buildNodes();
    Set<String> existingIds = skillNodeRepository.findAll().stream()
            .map(SkillNodeEntity::getId).collect(Collectors.toSet());
    List<SkillNodeEntity> missingNodes = defined.stream()
            .filter(n -> !existingIds.contains(n.getId())).toList();
    if (!missingNodes.isEmpty()) skillNodeRepository.saveAll(missingNodes);

    Set<String> existingEdgeIds = skillEdgeRepository.findAll().stream()
            .map(SkillEdgeEntity::getId).collect(Collectors.toSet());
    List<SkillEdgeEntity> missingEdges = buildEdges().stream()
            .filter(e -> !existingEdgeIds.contains(e.getId())).toList();
    if (!missingEdges.isEmpty()) skillEdgeRepository.saveAll(missingEdges);
    refreshCache();
}
```
Neuer Zweig "DISPO" (Nordost-Diagonale, bisher ungenutztes Feld im Layout — MILK ist Norden
`x=0`, BAKING Osten `y=0/150`, MARKET Süden `x=0`, CORE Westen `y=0/100`):
```java
node("dispo_1", "Guter Draht zur Bank", "-1% Dispo-Zinsen", "DISPO", EffectType.WAGE_INTEREST_REDUCTION, null, 0.01, 150, -150, false),
node("dispo_2", "Bonitätsprüfung bestanden", "-1% Dispo-Zinsen", "DISPO", EffectType.WAGE_INTEREST_REDUCTION, null, 0.01, 300, -300, false),
node("dispo_3", "Verhandelter Rahmen", "-1.5% Dispo-Zinsen", "DISPO", EffectType.WAGE_INTEREST_REDUCTION, null, 0.015, 450, -450, false),
node("dispo_4", "Goldener Kredit (Keystone)", "-2% Dispo-Zinsen", "DISPO", EffectType.WAGE_INTEREST_REDUCTION, null, 0.02, 600, -600, false),
```
Edges: `root→dispo_1→dispo_2→dispo_3→dispo_4` (Kette wie MARKET-Zweig). Gesamt-Reduktion
5.5 % (10 % Basis → 4.5 % min. über Skill-Baum), zusätzlich Hard-Floor 2 % im Code (siehe
unten) als Sicherheitsnetz.

**`enums/EffectType.java`** — neuer Wert `WAGE_INTEREST_REDUCTION`.

**`config/GameBalanceConfig.java`** — drei neue Felder (+ Getter/Setter, gleiches Muster wie
`wagePerMinPerWorker`):
- `debtInterestRate = 0.10` (Basis-Zinssatz pro Lohn-Tick)
- `debtInterestRateFloor = 0.02` (Mindestsatz, auch mit allen Skill-Knoten)
- `debtLimitMultiplier = 8.0` (Dispo-Grenze = aktueller Gesamtlohn × diesen Faktor)
Alle drei in `application.properties` (`balance.debt-interest-rate=0.10` etc.) und in
`AdminConfigController#updateBalanceConfig` ergänzen (echte Wirtschafts-Balance-Werte,
gleiche Kategorie wie `wagePerMinPerWorker` — dort wird admin-exponiert, anders als die rein
operationalen `collectCooldownMs`/`wageLedgerMaxEntries`).

**`service/WageService.java`** — `deductWageForUser` umbauen:
```java
@Transactional
public void deductWageForUser(String userId) {
    UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

    boolean wasIdle = user.isWorkersIdle();

    // Zinsen auf bestehende Schulden -- läuft JEDEN Tick, unabhängig vom aktuellen Lohn
    // (auch wer gerade keine Arbeiter zugewiesen hat, muss abbezahlen).
    boolean cookiesChanged = false;
    if (user.getCookies() < 0) {
        double rate = Math.max(balance.getDebtInterestRateFloor(),
                balance.getDebtInterestRate() - skillTreeService.getEffectTotal(userId, EffectType.WAGE_INTEREST_REDUCTION, null));
        user.setCookies(user.getCookies() * (1 + rate));
        cookiesChanged = true;
    }

    Map<String, Double> breakdown = buildingService.getWageBreakdown(userId);
    double wage = breakdown.values().stream().mapToDouble(Double::doubleValue).sum();
    boolean nowIdle;
    if (wage <= 0) {
        nowIdle = false;
    } else {
        double debtLimit = balance.getDebtLimitMultiplier() * wage;
        double tentative = user.getCookies() - wage;
        if (tentative >= -debtLimit) {
            user.setCookies(tentative);
            cookiesChanged = true;
            nowIdle = false;
            user.setLastWageAmount(wage);
            user.setLastWageAt(LocalDateTime.now());
            recordLedgerEntry(userId, wage, breakdown);
        } else {
            nowIdle = true; // Dispo-Grenze erreicht -- harter Stopp wie bisher
        }
    }

    if (nowIdle == wasIdle && !cookiesChanged) return;
    if (nowIdle != wasIdle) buildingService.settleAllForIdleTransition(userId, wasIdle);
    user.setWorkersIdle(nowIdle);
    userRepository.save(user);
}
```
Braucht `SkillTreeService` als neue Konstruktor-Abhängigkeit (bisher nicht injiziert) —
kein Zyklus, `SkillTreeService` hängt nicht von `WageService` ab.

## Frontend-Änderungen (Sichtbarkeit des neuen Mechanismus)

Ohne UI-Hinweis ist der Dispo unsichtbar (Cookies werden einfach negativ) — das wirkt wie
ein Bug, wenn es keiner erklärt.

- **`frontend/src/views/FarmGridView.vue`**: Cookie-HUD-Chip (`hud-chip-cookie`,
  `fmt(playerStore.cookies)`) bekommt eine `rh-cost`-artige rote Färbung, wenn
  `playerStore.cookies < 0` (Klasse `hud-chip-debt` o.ä., Farbe `var(--px-red)`).
- **`frontend/src/components/RathausDialog.vue`**: neue Stat-Kachel neben "GESAMTLOHN" (z.B.
  "SCHULDEN" / "DEBT"), zeigt `-cookies` wenn negativ, sonst "—", plus kleiner Hinweistext
  mit aktuellem effektivem Zinssatz. Bezieht Zinssatz aus `playerStore` (neues Feld, siehe
  unten) oder direkt aus dem nächsten `wage-status`/`init`-Response.
- **`UserInformationDto`** (Backend) bekommt kein neues Feld nötig für den Zinssatz, da er
  aus Skill-Baum-Status ableitbar ist — einfacher: `WageStatusDto` (existiert schon fürs
  Polling) um `effectiveInterestRate` und `debtLimit` erweitern, `UserService#getWageStatus`
  füllt sie über `skillTreeService.getEffectTotal(...)` + `buildingService.getTotalWage(...)`.
  Frontend-Polling (`pollWageStatus` in FarmGridView.vue, existiert schon aus dem letzten
  Feature) speichert das zusätzlich in einem neuen `playerStore`-Feld `debtInterestRate`, das
  RathausDialog liest.
- **`frontend/src/components/SkillTreeView.vue`**: Branch-Icon-Map ist hartkodiert
  (`BRANCH_ICON = { MILK: 'milch', BAKING: 'ofen', MARKET: 'stand', CORE: 'einw' }`,
  Zeile ~120) — `DISPO: 'lohn'` ergänzen (passendes Münz-Icon existiert bereits in
  `pixel/iconData.js`, bisher ungenutzt). Branch-Färbung selbst ist NICHT hartkodiert
  (`stv-branch-${branch}`-Klasse wird erzeugt, aber nirgends per CSS gestylt — nur
  Node-Status treibt die Farbe), also kein CSS-Zusatz nötig. Restliches Rendering
  (Positionierung nach `x`/`y`, Kanten) ist branch-agnostisch, funktioniert automatisch.

## Nicht angefasst

- Wage-Ledger (Abrechnungshistorie im Rathaus) bleibt rein auf Gebäude-Lohn beschränkt,
  Zinsen fließen NICHT in die Breakdown-Summe ein (sonst stimmt die Invariante
  "Summe Breakdown == totalAmount" nicht mehr) — Zinsen sind konzeptionell kein
  Gebäude-Lohn, sondern eine separate Bank-Transaktion.
- Andere Ausgabe-Checks (Gebäude kaufen, Skillpunkte, Markt, Backen) prüfen weiterhin
  `cookies < cost` — funktioniert mit negativen Cookies automatisch korrekt (kein Change).
- Net Worth (`NetWorthService`) summiert `cookies` bereits ungeklammert — Schulden senken
  automatisch korrekt den Net Worth, kein Change nötig.

## Verification

1. Backend compile: `cd backend/cookie-server-spring-boot && ./mvnw -q -o compile` (via WSL,
   `wsl bash -lc "..."`, siehe letzte Session — Java/Maven nur dort verfügbar, nicht in
   Git Bash/PowerShell direkt).
2. Skill-Baum-Migration prüfen: App gegen die bestehende (nicht-leere) Dev-DB starten, neuer
   Branch "DISPO" muss im Skill-Baum-Dialog auftauchen, ohne dass vorhandene Spieler ihre
   bereits allozierten Knoten verlieren.
3. Dispo-Szenario durchspielen: Cookies auf sehr wenig setzen (Dev-Reset o.ä.), so viele
   Gebäude/Arbeiter zuweisen, dass Lohn > Guthaben. Nächster Lohn-Tick (≤60s): Cookies gehen
   negativ, Arbeiter bleiben aktiv (kein Idle-Grau), HUD-Chip färbt sich rot. Weiter warten:
   Zinsen lassen Minus wachsen, bis Dispo-Grenze erreicht → dann greift Idle wie bisher.
4. Skill-Punkt in DISPO-Zweig allozieren, prüfen dass der effektive Zinssatz sinkt (RathausDialog-Anzeige
   und tatsächliches Zins-Delta über zwei Ticks vergleichen).
