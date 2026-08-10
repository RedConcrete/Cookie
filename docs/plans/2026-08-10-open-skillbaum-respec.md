# ⏳ Skillbaum: Respec (Punkte zurückgeben)

> **Status:** ⏳ Offen

## Context

V1-Entscheidung im Design-Doc §9 ("Bewusst nicht gebaut"): "Kein
Respec/Un-Allocate-Endpoint (einfacher Folge-Ausbau)". Durch die geplante
Erweiterung wächst der Baum von 22 auf ~60-65 Knoten
([[2026-08-10-open-skillbaum-rohstoff-branches]],
[[2026-08-10-open-skillbaum-crit-system]],
[[2026-08-10-open-skillbaum-lager-branch]],
[[2026-08-10-open-skillbaum-bau-buerger-branch]]) — Fehlallokationen werden
wahrscheinlicher, gleichzeitig führt
[[2026-08-10-open-skillbaum-wheel-keystones]] Keystones mit echten
Nachteilen ein (ein Spieler will einen unpassenden Nachteil evtl. wieder
loswerden). User-Entscheidung: Respec ist möglich, kostet **Cookies, immer
derselbe Betrag** (kein Wachstum wie bei der Skill-Punkt-Kaufkurve).

Baut auf [[2026-08-10-open-skillbaum-wheel-keystones]] auf (nutzt dieselbe
`SkillTreeService`-Struktur, `nodeCache`, `isAdjacentToAllocated`-Logik) —
nach dem Fundament-Pass umsetzen.

## Design-Entscheidungen

- **Flacher Fix-Preis pro entferntem Knoten**, kein Wachstum. Neues
  `GameBalanceConfig`-Feld `respecCostFlat` (Default z. B. 300 Cookies,
  Platzhalter-Balancing wie überall sonst im Baum), live-tunbar über
  `PUT /api/v1/admin/config/balance` (gleiches Muster wie
  `skillPointBaseCost`).
- **Ein Knoten pro Aufruf**, kein Bulk-Respec/Kaskaden-Entfernen. Einfacher,
  vorhersehbarer für den Spieler, kein Risiko versehentlicher
  Massen-Rückerstattung. Wer mehrere Knoten loswerden will, ruft den
  Endpunkt mehrfach auf (zahlt jedes Mal `respecCostFlat`).
- **Konnektivitäts-Schutz**: ein Knoten darf nur entfernt werden, wenn kein
  *anderer* aktuell allozierter Knoten dadurch von `root` abgeschnitten
  würde. Root selbst und nicht-allozierte Knoten sind nicht entfernbar
  (analog zu den bestehenden Checks in `allocateNode`).
- **`totalSkillPointsBought` und `totalSkillPointCookiesSpent` bleiben
  unverändert** — exakt die gleiche Logik wie beim bestehenden
  Prestige-Reset-Verhalten (§9: "Prestige... behält die Kostenkurve nicht
  billiger"). Der zurückgegebene Skill-Punkt ist sofort neu ausgebbar, aber
  die historischen Zähler (Netto-Wert-relevant, Kostenkurve-relevant)
  rühren sich nicht — ein Respec darf weder Net Worth rückwirkend senken
  noch die nächste Skill-Punkt-Kaufkurve künstlich verbilligen.
- **Transaktional + gegen Doppel-Spend abgesichert**, gleiches Muster wie
  `buySkillPoint`/`allocateNode` (`@Transactional`, Cookie-Abzug und
  Node-Löschung in derselben Transaktion — kein neues Locking-Konzept
  nötig, nur die bestehende Methode kopieren).

## Backend-Änderungen

**`GameBalanceConfig.java`**: neues Feld `respecCostFlat` + Getter/Setter,
Eintrag in `AdminConfigController`s Balance-PUT-Copy-Liste (gleiches Muster
wie bei jedem bisherigen neuen Balance-Feld, siehe z. B.
`skillPointBaseCost`).

**`SkillTreeService.deallocateNode(String userId, String nodeId)`** (neu,
`@Transactional`):
```java
@Transactional
public SkillTreeDto deallocateNode(String userId, String nodeId) {
    SkillNodeEntity nodeEntity = nodeCache.get(nodeId);
    if (nodeEntity == null) throw new NoSuchElementException("Skill node not found: " + nodeId);
    if (nodeEntity.isRoot()) throw new IllegalArgumentException("Root kann nicht entfernt werden.");

    UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

    Set<String> allocated = allocatedNodeIds(userId);
    if (!allocated.contains(nodeId)) {
        throw new IllegalStateException("Knoten ist nicht freigeschaltet.");
    }
    if (user.getCookies() < balance.getRespecCostFlat()) {
        throw new IllegalArgumentException("Nicht genug Cookies für Respec.");
    }

    Set<String> remaining = new HashSet<>(allocated);
    remaining.remove(nodeId);
    for (String other : allocated) {
        if (other.equals(nodeId) || other.equals(ROOT_ID)) continue;
        if (!isReachableFromRoot(other, remaining)) {
            throw new IllegalStateException(
                "Knoten kann nicht entfernt werden -- " + other + " würde vom Baum abgeschnitten.");
        }
    }

    user.setCookies(user.getCookies() - balance.getRespecCostFlat());
    user.setSkillPoints(user.getSkillPoints() + 1);
    userRepository.save(user);
    playerSkillNodeRepository.deleteById(userId + "#" + nodeId);

    return getTreeStatus(userId);
}
```
Neue private Helper-Methode `isReachableFromRoot(String nodeId, Set<String>
allocatedSet)` — BFS/DFS über `skillEdgeRepository`-Kanten, nur über Knoten
in `allocatedSet` (+ root) laufend. Wiederverwendbar: die bestehende
`isAdjacentToAllocated` prüft nur direkte Nachbarschaft (reicht für
Allokation), Respec braucht eine echte Erreichbarkeits-Traversierung über
den ganzen verbleibenden Baum, deshalb eine neue Methode statt Wieder-
verwendung der bestehenden.

**REST**: neuer Endpunkt `POST /api/v1/skilltree/deallocate/{userId}`
`{nodeId}` → `deallocateNode(...)`, gleiches Response-Shape wie
`allocate`/`buy-point` (volles `SkillTreeDto`).

## Frontend-Änderungen

- Popover eines **allozierten** Knotens (`PixelInfoPopover`,
  `SkillTreeView.vue:27`) bekommt einen zusätzlichen "Respec"-Button mit
  Preis-Anzeige (`balance.respecCostFlat` aus dem `SkillTreeDto`/einer neuen
  Konfig-Antwort) — analog zum bestehenden Kauf-Button-Muster.
  Bestätigungs-Dialog vor dem eigentlichen Aufruf (irreversible
  Cookie-Ausgabe, ähnlich kritische Aktion wie ein Gebäude-Verkauf, falls es
  dafür bereits ein Bestätigungs-Popup-Muster im Projekt gibt — sonst ein
  einfaches "Sicher?"-Popup neu bauen).
- Bei Serverfehler ("würde X abschneiden") Fehlermeldung aus der
  Response direkt anzeigen (i18n-Key mit Platzhalter für den betroffenen
  Knotennamen, oder generische Meldung "Dieser Knoten kann aktuell nicht
  zurückgegeben werden").
- `services/api.js`: neue Funktion `deallocateSkillNode(userId, nodeId)`.
- i18n: neue Keys in `skillTreeView.json`/`skillTreeDialog.json` (de+en) für
  Button, Preis-Label, Bestätigungstext, Fehlermeldung.

## Verifikationsplan

1. Knoten ohne Abhängigkeiten entfernen (z. B. ein Fork-Endknoten) →
   Cookies abgezogen, `skillPoints +1`, Knoten wieder `allocatable` statt
   `allocated`.
2. Knoten mit abhängigem Kind entfernen (z. B. `milk_2`, während `milk_3`
   alloziert ist) → 400/Fehler, nichts geändert.
3. Root entfernen → 400.
4. Nicht allozierten Knoten entfernen → 400.
5. Zu wenig Cookies → 400, kein Teil-Abzug.
6. `totalSkillPointsBought`/`totalSkillPointCookiesSpent` unverändert nach
   Respec (Net-Worth-Wert bleibt stabil).
7. Danach den zurückgegebenen Punkt auf einen anderen Knoten legen —
   normaler `allocate`-Flow funktioniert unverändert.
8. Admin: `respecCostFlat` per PUT änderbar, wirkt sofort.

## Kritische Dateien

- `backend/.../service/SkillTreeService.java` — neue `deallocateNode`,
  `isReachableFromRoot`
- `backend/.../config/GameBalanceConfig.java`
- `backend/.../controller/SkillTreeController.java`,
  `AdminConfigController.java`
- `frontend/src/components/SkillTreeView.vue`
- `frontend/src/services/api.js`
