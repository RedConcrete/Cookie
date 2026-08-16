# Trailer-Storyboard

Plant die Aufnahmen bewusst, bevor wieder aufgenommen wird. Ziel:
30-45 Sekunden fertig geschnittener Trailer. Kein eingebrannter Text mehr
(siehe Entscheidung 2026-08-16) — die Bilder müssen für sich sprechen,
Kamera-Framing und Timing sind deshalb wichtiger als beim Text-Cut.
Cursor-Overlay (weißer Ring, Klick-Puls) bleibt, macht Klicks lesbar ohne
Text.

Faustregel fürs Aufnehmen: jede Szene doppelt so lang roh aufnehmen wie
die geplante Länge im fertigen Schnitt — gibt beim Schneiden Auswahl
(Ein-/Ausstiegspunkt, beste 2-3 Sekunden rausschneiden) statt bei exakter
Ziellänge aufzunehmen und dann nichts mehr wählen zu können.

## Erzählbogen

Hook → Welt → Kernloop → Alleinstellungsmerkmal (Markt) → Tiefe
(Skillbaum) → Payoff (Backen) → Wettbewerb (Rangliste) → Ausstieg.
Der Markt ist das Herzstück (geteilter Online-Markt ist das, was Cookie
von anderen Idle-Spielen unterscheidet) — kriegt den längten Slot und
sitzt in der stärksten Position (nach dem Hook, vor der Tiefe).

## Szenenliste

| # | Szene | Zeigt | Ziel im Schnitt | Rohaufnahme | Kamera/Aktion |
|---|---|---|---|---|---|
| 1 | Cold Open | Voll entwickelter Hof, bereits in Bewegung | 2-3s | 6s | Kein Menü-Screen zu Beginn. Direkt in die Farm-Ansicht schneiden, Kamera pannt bereits (kein statischer erster Frame) |
| 2 | Gebäude-Etablierung | 2-3 Gebäude nacheinander, Arbeiter sichtbar | 3-4s | 8s | Kurze Schwenks/Zooms auf Zuckerteich, Kuhstall, Backofen — Vielfalt zeigen, nicht Vollständigkeit |
| 3 | Hover-Ernte | Ressourcenzahl tickt hoch beim Hover | 2s | 5s | Cursor bewegt sich sichtbar zum Gebäude, hält kurz — der Ring-Puls macht den Moment lesbar |
| 4 | Einsammeln | Collect-Badge-Klick, Betrag springt ins Lager | 2s | 5s | Badge muss im Frame sichtbar+lesbar sein (großer Zahlwert, z. B. 800+) bevor geklickt wird |
| 5 | Markt öffnen | Preisgraph, mehrere Ressourcen-Linien in Bewegung | 3s | 8s | Dialog öffnet, 1-2 Sekunden nur Graph zeigen bevor irgendwas geklickt wird — Graph ist der eigentliche Star, nicht der Klick |
| 6 | Markt-Trade | Ein Kauf, Preis bewegt sich sichtbar | 3-4s | 8s | Ein einzelner klarer Klick auf KAUFEN bei einer Ressource mit sichtbarer %-Änderung. Nicht mehrere Trades hintereinander — verwässert den Moment |
| 7 | Skillbaum | Voller Baum, Zoom/Pan über mehrere Zweige | 4-5s | 10s | Rein zoomen von der Mitte (Root/Stern) nach außen zu einem Keystone-Knoten (Diamant-Icon), zeigt Umfang des Baums |
| 8 | Backen | Zutaten → Rezept → Fortschrittsbalken → fertig | 4s | 10s (nur Anfang+Ende brauchbar, 30s echte Backzeit dazwischen wegschneiden) | Zwei Aufnahmen statt einer: (a) Rezept-Klick + Start, (b) separat den fertigen Zustand mit "+100 Cookies einlösen"-Button anfahren (Bake-Job vorher per Zeit/API weit genug vorspulen, nicht 30s Realzeit mitfilmen) |
| 9 | Rangliste | Eigener Eintrag mit Gesamtwert | 2s | 5s | Kurzer, ruhiger Shot — keine Aktion nötig, Zahl muss nur lesbar sein |
| 10 | Outro | Weiter Hof-Shot, zentriert, ruhige Kamera | 3-4s | 6s | Letzter Frame muss als Standbild funktionieren (Trailer-Enddate/Steam-Wishlist-Card wird oft draufgelegt) |

Gesamt Rohmaterial: ca. 70s, Zielschnitt: 30-45s.

## Bewusst rausgelassen

- **Rathaus/Lager** — informativ, aber visuell wenig Bewegung (Listen/
  Balken), trägt den Trailer nicht. Bleiben Screenshot-Material
  (`docs/steam-website/screenshots/`), kein Trailer-Slot.
- **Hauptmenü** — kein Mehrwert als Trailer-Opener, der Hook (Szene 1)
  ersetzt es bewusst.
- **Mehrere Markt-Trades hintereinander** — aus v1/v2-Aufnahmen gelernt:
  mehrere Käufe/Verkäufe in Folge (mit Cooldown dazwischen) ziehen die
  Szene ohne visuellen Mehrwert in die Länge. Ein einzelner, klarer Trade
  reicht.

## Technische Notiz fürs nächste Recording

`docs/steam-website/trailer/record-clips.js` ist die Basis (Cursor-Ring +
Klick-Puls-Overlay, ProRes-`.mov`-Export). Für diese Runde:
- Alle `caption(...)`-Aufrufe entfernen (kein Text mehr).
- Szene 8 (Backen) in zwei Clips splitten statt einen 30s-Clip — Bake-Job
  vor der zweiten Aufnahme direkt per Backend-API/DB auf `done` setzen
  (`lastSettledAt`/`bakeDurationSeconds` in der Vergangenheit), nicht die
  echten 30 Sekunden mitfilmen.
- Dev-Save-State vor jeder Aufnahme frisch aufsetzen (Gebäude nicht
  `INAKTIV`, Hauptlager nicht voll) — siehe wiederkehrende Fallstricke in
  `docs/steam-website/README.md`.
