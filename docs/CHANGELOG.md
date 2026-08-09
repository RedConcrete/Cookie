# Cookie — Changelog

Testversionen für die Freundes-Beta. Neueste zuerst.

---

## Testversion 3 — 2026-08-09

### Bugfixes

- **Lohn und Ertrag im Gebäude-Dialog und auf der Hofkarte änderten sich
  nie**, egal wie viele Arbeiter zugewiesen oder wie oft ein Gebäude
  ausgebaut wurde — zeigten immer dieselben Platzhalter-Zahlen wie am
  ersten Tag. Jetzt live und korrekt: Ertrag skaliert mit Arbeiterzahl,
  Lohn ebenso (2 C/min pro Arbeiter). Die Hover-Ernte-Anzeige (ohne Lohn)
  war ebenfalls falsch und zeigt jetzt den echten Wert.
- **"Dev Start" konnte beim Server-Start abstürzen**, wenn eine neue
  Skill-Baum-Funktion dazukam (interne DB-Migrationslücke) — siehe
  `docs/ROADMAP.md` Abschnitt 0. **Live-Server braucht bei diesem Release
  denselben Fix noch von Hand.**
- **Rathaus-Tab "Abrechnung" zeigte immer "Noch keine Abrechnung"**, obwohl
  im Hintergrund längst Lohn abgebucht wurde — interner Server-Fehler beim
  Abrufen der Historie, den das Frontend still verschluckt hat. Behoben
  (Details `docs/ROADMAP.md` Abschnitt 0).

### Neu

- **Dispo-Kredit statt Komplett-Stillstand:** reicht das Cookie-Guthaben
  mal nicht für den Lohn, gehen die Cookies jetzt ins Minus statt dass
  sofort alle Arbeiter stillstehen — Produktion läuft normal weiter. Auf
  das Minus fallen 10 % Zinsen pro Minute an (wie ein Dispokredit),
  reduzierbar über einen neuen Zweig im Skill-Baum. Wird das Minus zu groß
  (mehr als das 8-fache des aktuellen Lohns), stoppt die Produktion doch,
  bis wieder Luft ist.
- Cookie-Anzeige im HUD wird rot, wenn man im Minus ist; eine rote Zahl
  fällt bei jeder Lohnabbuchung sichtbar vom Cookie-Zähler weg.
- **Neuer Rathaus-Tab "Abrechnung"**: zeigt jede Lohnabbuchung mit
  Zeitpunkt, Summe und Aufschlüsselung pro Gebäude — komplette
  Nachvollziehbarkeit, was wie viel gekostet hat.

### Bekannt, noch offen

Fenstermodus/Auflösung, Steam-Deck-Gebäude-Cycling (R1/L1), mehr Skills pro
Rohstoff, Rezept-Minigame, vollständiger Kontrast-Sweep gegen gelbe Schrift,
Markt-Gebühr-Anzeige im Hofkarten-Popup noch statisch — Details in
`docs/ROADMAP.md` Abschnitt 7.2.

---

## Testversion 2 — 2026-08-08

### Bugfixes

- **"Start Spiel" warf für Spieler mit bestehenden Gebäuden einen 500er
  Serverfehler.** Kaputte DB-Migration aus einer früheren Session (fehlende
  Default-Werte auf zwei `version`-Spalten) — siehe `docs/ROADMAP.md`
  Abschnitt 0 für Details. **Live-Server braucht denselben Fix noch von
  Hand**, ddl-auto=update behebt das nicht selbst.

### Verbesserungen

- Markt-Tiefe skaliert jetzt automatisch mit der Zahl aktiver Spieler,
  statt fix bei 1000 Einheiten zu bleiben — ein einzelner Spieler kann den
  Markt damit nicht mehr im Alleingang leerkaufen/durchbewegen. Startwert
  ist eine erste Schätzung, wird nach mehr Spieldaten nachjustiert.
- Verkaufsgebühr-Dokumentation korrigiert (galt live schon 15%, Doku sagte
  noch 5% — jetzt überall einheitlich 15%).

### Bekannt, noch offen

Fenstermodus/Auflösung, Steam-Deck-Gebäude-Cycling (R1/L1), mehr Skills pro
Rohstoff, Rezept-Minigame, vollständiger Kontrast-Sweep gegen gelbe Schrift
— Details in `docs/ROADMAP.md` Abschnitt 7.2.

---

## Testversion 1 — 2026-08-07

Erste Fix-Runde nach dem ersten Multiplayer-Playtest mit Freunden. Basis:
Feedback-Liste aus diesem Test, siehe `docs/ROADMAP.md` Abschnitt 7.

### Bugfixes

- Bürgerzahl sprang nach dem Einsammeln von Gebäude-Ertrag fälschlich auf 0
- Mehr Einwohner kaufen als Rathaus-Platz erlaubt (Folge des Bugs oben)
- Schnelles Einsammeln mehrerer Gebäude konnte Ressourcen falsch verbuchen
  (Race Condition)
- Markt konnte durch schnelle Trades eines einzelnen Spielers abstürzen/
  extreme Preise erzeugen (~200 Einheiten reichten)
- Schließen-Button im Backen-Dialog reagierte nicht
- "Lager voll"-Hinweis und "Einsammeln"-Badge überlagerten sich am Gebäude
- Rangliste zeigte beim Hovern über den Namen ein überflüssiges Zweit-Popup

### Verbesserungen

- Zahlen zeigen nie mehr als 2 Nachkommastellen, immer aufgerundet
- Popups/Tooltips lassen sich jetzt per Klick wegklicken
- Einsammeln von Gebäude-Ertrag hat jetzt eine kurze Sperre gegen Spam
- Ernte-Zahl beim Hovern ist jetzt immer in derselben, neutralen Farbe
  (Gold/Gelb bleibt für spätere kritische Treffer reserviert)
- Kamera-Geschwindigkeit: Standard erhöht, Maximalwert im Einstellungs-Slider
  deutlich höher
- Profil-Ansicht zeigt keine Skill-Baum-Details mehr
- Neuer Stern-Hinweis im HUD, wenn noch Skillpunkte zu vergeben sind
- Verbleibende Skillpunkte sind jetzt direkt im Skill-Baum sichtbar (vorher
  nur in einem extra Popup)

### Bekannt, noch offen

Fenstermodus/Auflösung, Steam-Deck-Gebäude-Cycling (R1/L1), mehr Skills pro
Rohstoff, Rezept-Minigame, vollständiger Kontrast-Sweep gegen gelbe Schrift,
Markt-Balancing nach dem Bugfix — Details in `docs/ROADMAP.md` Abschnitt 7.2.
