# Cookie — Changelog

Testversionen für die Freundes-Beta. Neueste zuerst.

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
