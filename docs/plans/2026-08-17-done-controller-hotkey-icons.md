# ✅ Controller-Hotkey-Icons: automatisches Umschalten Tastatur ↔ Gamepad

> **Status:** ✅ Umgesetzt (2026-08-17)

## Context

Cookie hat schon teilweise Gamepad-Support (`FarmGridView.vue`: Stick-Pan,
D-Pad-Zoom, 2 rebindbare Actions via `useActionHotkeys.js`), aber Hotkeys
werden überall nur als Text-Badge (`ShortcutSlot.vue`) angezeigt — nie als
Controller-Button. `docs/ROADMAP.md` §"Steam-Deck-Controller-Steuerung"
kennt das Thema bereits, ist aber zurückgestellt.

User-Wunsch: sobald ein Controller-Input reinkommt (Knopf/Stick über
Deadzone), sollen alle sichtbaren Hotkey-Badges auf das passende
Controller-Symbol wechseln (Xbox ABXY / PlayStation ✕○□△ / generisch je
nach angeschlossenem Pad). Sobald wieder Tastatur/Maus benutzt wird, sofort
zurück auf Text — alles automatisch, ohne Einstellung nötig.

Klärung mit User: Icons werden als CSS-Badge gebaut (kein neues SVG-Asset-
Set), und Escape/Space bekommen echte Gamepad-Bindung (B = Dialog
schließen, Y = Kamera zentrieren) statt nur kosmetisch zu bleiben.

## Ansatz

### 1. Neuer Composable `frontend/src/composables/useInputMethod.js`

Singleton-Pattern analog `useIdleTimeout.js` (explizites `start()`/`stop()`,
von `App.vue` aus gesteuert statt Modul-weit immer aktiv):

- `activeMethod` ref: `'keyboard'` | `'gamepad'`, Default `'keyboard'`.
- `controllerFamily` ref: `'xbox'` | `'playstation'` | `'generic'`.
- `detectFamily(padId)`: String-Heuristik auf `Gamepad.id` (enthält
  Vendor/Produktname vom Browser) — `054c`/`sony`/`dualshock`/`dualsense`/
  `playstation` → `'playstation'`; `045e`/`xbox`/`xinput` → `'xbox'` (deckt
  auch Steam Deck ab, das sich per Steam Input als XInput-Pad meldet).
- `keydown`/`mousedown`/`mousemove`/`wheel` setzen `activeMethod =
  'keyboard'`.
- rAF-Polling-Loop (nur aktiv solange ein Pad verbunden ist) prüft jeden
  Frame alle `navigator.getGamepads()`-Pads auf gedrückten Button oder
  Achse über Deadzone (0.5, höher als die Kamera-Pan-Deadzone) → setzt
  `activeMethod = 'gamepad'` und `controllerFamily` vom auslösenden Pad.

`App.vue` startet/stoppt den Composable neben `useIdleTimeout` in
`startGame()`/AFK-Watcher.

### 2. Neue Komponente `frontend/src/components/pixel/ControllerButtonIcon.vue`

Rendert je nach `useInputMethod().controllerFamily` ein farbiges Label statt
Icon-Grafik — Face-Buttons (0-3) in Palette-Farben
(`--px-green/--px-red/--px-blue/--px-gold`), alle anderen Buttons neutral,
Label wechselt nur Text zwischen Xbox-/PlayStation-Naming.

### 3. `ShortcutSlot.vue` erweitert

Neue Prop `gamepadButton`. Zeigt `ControllerButtonIcon` statt Text, wenn
`activeMethod === 'gamepad'` und `gamepadButton` gesetzt ist.

### 4. Verdrahtete Call-Sites (`FarmGridView.vue`)

Net-Worth-Badge, Skilltree-Badge, Kamera-zentrieren-Badge bekommen
`gamepad-button`. Alle anderen (bisher leeren) `ShortcutSlot`-Stellen
bleiben unverändert.

### 5. Neue feste Gamepad-Funktion in `FarmGridView.vue`

`CLOSE_GAMEPAD_BUTTON = 1` (B/Circle) schließt Dialoge,
`CENTER_GAMEPAD_BUTTON = 3` (Y/Triangle) zentriert die Kamera — Rising-Edge-
Tracking analog zu den bestehenden rebindbaren Actions in
`readGamepadActions()`.

## Betroffene Dateien

- `frontend/src/composables/useInputMethod.js` (neu)
- `frontend/src/components/pixel/ControllerButtonIcon.vue` (neu)
- `frontend/src/components/pixel/ShortcutSlot.vue`
- `frontend/src/App.vue`
- `frontend/src/views/FarmGridView.vue`

## Verifikation

- `cd frontend && npm run check:palette` — grün.
- `cd frontend && npm run build` — baut sauber.
- Live-Test mit echtem Controller (Xbox/PlayStation/Steam Deck) sowie
  Zurückwechseln per Tastatur/Maus noch offen — kein Browser/Gamepad in
  dieser Umgebung verfügbar, siehe ROADMAP.md.
