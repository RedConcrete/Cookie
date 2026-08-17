# Trailer-Storyboard

**Richtung (2026-08-17 korrigiert):** Kein ruhiger, erklärender Trailer —
ein schnelles, meme-getriebenes Video. Harte Schnitte, Escalation/Punchline-
Struktur statt "System X erklären, System Y erklären". Ziel: 15-25
Sekunden Kernmaterial, Editor (DaVinci) packt drumherum Musik/Timing.
Die vorherige Version dieses Dokuments (ruhiger Feature-Showcase mit
langen Haltezeiten pro Dialog) passt nicht zu diesem Ziel — verworfen,
nicht nur ergänzt.

Aufnahme bleibt textfrei (Cursor-Ring-Overlay ja, eingebrannte Captions
nein, siehe letzte Entscheidung) — Meme-Text/Impact-Schrift kommt beim
Schnitt in DaVinci dazu, nicht aus dem Playwright-Skript. Spalte
"Text-Idee" unten ist ein Vorschlag fürs Editing, keine Aufnahme-Vorgabe.

Faustregel: pro Beat 3-4 Sekunden roh aufnehmen, auch wenn der Schnitt nur
0.5-1.5 Sekunden davon nutzt — harte Schnitte brauchen den einen perfekten
Frame, nicht die ganze Aktion in Echtzeit.

## Beat-Liste (Escalation → Punchline → Tag)

| # | Beat | Zeigt | Länge im Schnitt | Text-Idee (optional, im Editing) |
|---|---|---|---|---|
| 1 | Cold Open, Zahl explodiert | Extreme Nahaufnahme auf HUD-Cookie-Zahl, tickt beim Ernten/Einsammeln schnell hoch | 0.5-1s | — (kein Text, Zahl spricht für sich) |
| 2 | Klick-Spam | Schneller Schnitt: Hover-Ernte + 2-3 Collect-Badges direkt hintereinander, Cursor-Ring pulst mehrfach | 1-1.5s | "cookie go brrr" o.ä. |
| 3 | Markt-Chart Rakete | Zoom auf Preisgraph, grüne Linie schießt hoch (Kauf-Trade timen, damit der Sprung im Frame liegt) | 1-1.5s | "STONKS" |
| 4 | Harter Cut: Meme-Bild | Vollbild STONKS-Recreation (`screenshots/full/STONKS.png`), 1 Frame Freeze-Gefühl | 0.5-0.8s | (Bild ist der Text) |
| 5 | Subversion: Chart crasht | Sofort zurück zum Graph, jetzt rote Linie runter (Verkauf/Preisfall timen) | 0.8-1s | "not stonks" |
| 6 | Gebäude-Montage | 3-4 harte Schnitte zwischen Gebäuden (Zuckerteich → Kuhstall → Backofen → Plantage), je < 0.5s | 1.5-2s | — |
| 7 | Skillbaum-Reveal | Schneller Zoom-out von der Mitte (Stern) auf den vollen Baum in einer Bewegung | 1-1.5s | "brain expands" / Galaxy-Brain-Meme-Timing |
| 8 | Cookie-Payoff | Harter Cut direkt auf "+100 COOKIES EINLÖSEN"-Button-Klick (Bake-Job vorher fast-forwarden, siehe unten) | 0.8-1s | — |
| 9 | Rangliste-Flash | Kurzer Frame auf #1-Platzierung | 0.5-0.8s | "#1" |
| 10 | Logo-Slam | Cookie-Wordmark, harter Zoom-Punch rein, letzter Frame steht | 1-1.5s | — |

Gesamt Zielschnitt: ca. 12-16 Sekunden. Mit Puffer/Wiederholung beim
Schneiden realistisch 15-25s Endlänge.

## Bewusst rausgelassen (für dieses Video)

- **Rathaus, Lager, Statistiken** — zu ruhig/listig für Meme-Tempo, bleiben
  reines Screenshot-Material.
- **Mehrere Markt-Trades, Recipe-Auswahl im Detail, Backzeit-Warten** — aus
  der vorherigen (verworfenen) Storyboard-Version übernommen: alles, was
  "Erklärung" statt "Payoff" ist, raus.
- **Hauptmenü als Opener** — Cold Open direkt auf Beat 1 ist der Hook,
  kein Aufwärmen.

## Technische Notiz fürs Recording

`docs/steam-website/trailer/record-clips.js` als Basis, angepasst auf
diese Beat-Liste:
- Keine `caption(...)`-Aufrufe (siehe oben).
- Beat 4 braucht keine eigene Aufnahme — `STONKS.png` wird beim Schnitt
  als Standbild eingefügt, nicht gefilmt.
- Beat 8 (Bake-Payoff): Bake-Job direkt fertig aufsetzen statt 30s
  Echtzeit filmen — `POST /api/v1/game/bake/start/{userId}` aufrufen,
  danach `last_settled_at`/Timing in der DB so weit zurückdatieren, dass
  `GET /api/v1/game/bake/status/{userId}` sofort `"done":true` zeigt, dann
  erst mit der Aufnahme starten (Dialog zeigt direkt den Claim-Button).
- Dev-Save-State-Fallstricke (Hauptlager voll, Gebäude-Lager voll,
  unabgeholter Back-Job) vor jeder Aufnahme prüfen — siehe
  `docs/steam-website/README.md`.
- Cursor-Bewegungen für Beat 2/3/5 bewusst SCHNELLER als die bisherige
  550ms-Gleitzeit im Overlay (`moveCursor()` in `record-clips.js`) — für
  Meme-Tempo eher 150-200ms Übergang, sonst wirkt der Cursor selbst wieder
  gemütlich statt hektisch.
