# Damage Number Counter — Spec

## Was es ist
HTML/CSS/JS Prototyp. Counter zählt hoch, spawnt fliegende DMG-Zahlen wie in nem RPG/Idle-Game. Aktuell single-file, Vanilla JS, kein Framework.

## Aktueller Stand
Funktionierender Prototyp: `damage-counter.html` (siehe Anhang/Repo).

**Kernlogik:**
- `#arena` div = Spielfeld, 600x320px
- Maus-Hover-getriggert: Schaden läuft NUR solange Maus über `#arena` ist (`mouseenter`/`mouseleave` toggeln `isHovered`)
- `setInterval` Tick-Rate via Speed-Slider (1-5 Ticks/Sek)
- Pro Tick: `counter += stepValue`, spawnt `spawnNumber(value, x, y)` an aktueller Mausposition
- Mausposition wird laufend per `mousemove` getrackt (`mouseX`, `mouseY`)

**spawnNumber() Physik:**
- Flugwinkel: random -30° bis +30° (horizontale Drift via `Math.sin(angle)`)
- Textrotation: random -5° bis +5° (CSS `rotate()`, bleibt über gesamte Anim erhalten)
- Vertikale Bewegung: Aufwärtskraft + Schwerkraft (`vy * progress + 60 * progress²`)
- Fade-out über `opacity`, läuft via eigenes `setInterval` @ ~60fps (16ms)
- Crit-Logic: wenn `value >= stepValue * 3` → größer (34px), rot, leichtes Scale-Up während Flug

**Controls:**
- Speed-Slider (1-5)
- Damage-Slider (1-50)
- Reset-Button (Counter + alle DOM-Elemente löschen)

## Bekannte Limitierungen / TODO
- [ ] Kein Cleanup falls Tab im Hintergrund (setInterval drosselt, kein requestAnimationFrame)
- [ ] Physik nutzt `setInterval(16ms)` statt `requestAnimationFrame` → nicht ans Display-Refresh gekoppelt
- [ ] Keine Mobile/Touch-Unterstützung (nur `mouseenter`/`mousemove`/`mouseleave`)
- [ ] Kein State-Persistence (Counter resettet bei Reload)
- [ ] Arena-Größe hardcoded (600x320px), nicht responsive

## Mögliche nächste Schritte (zur Diskussion)
- Umbau auf `requestAnimationFrame` für Animation-Loop
- Touch-Events für Mobile ergänzen
- In Vue 3 Komponente überführen (passt zu belos5/Cookie Stack)
- Sound-Effekte bei Spawn/Crit
- Konfigurierbare Arena-Größe / Responsive Layout
- Object-Pooling für DMG-Elemente statt DOM create/remove pro Spawn (Performance bei hoher Tick-Rate)

## Datei
`damage-counter.html` — komplett selbst-enthalten, kein Build-Step nötig, einfach im Browser öffnen.
