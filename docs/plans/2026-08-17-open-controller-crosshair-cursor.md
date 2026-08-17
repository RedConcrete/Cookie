# ⏳ Controller: Fadenkreuz-/Cursor-Modus für FarmGrid + Skilltree

> **Status:** ⏳ Offen (Code steht, noch nicht committed / live mit Controller getestet)

## Context

Teil 1 (Controller-Hotkey-Icons: Text-Badges wechseln automatisch auf
Controller-Symbole, B schließt Dialoge, Y zentriert die Kamera) ist bereits
gebaut, committed und live mit echtem Controller getestet
(`docs/plans/2026-08-17-done-controller-hotkey-icons.md`).

Dieser Plan ist der nächste Schritt aus `docs/ROADMAP.md`
§"Steam-Deck-Controller-Steuerung": festes, rundes Fadenkreuz in
Bildschirmmitte (Welt bewegt sich per linkem Stick darunter), A öffnet was
drunter liegt, R3 togglet in einen freien Cursor-Modus (rechter Stick bewegt
ihn frei, z.B. um in einem Dialog etwas anzuklicken). Gleiches Verhalten
zusätzlich im Skilltree (eigene Pan/Zoom-Welt, bisher Maus-only).

Während der Umsetzung per Chat dazugekommen (schon mit reingenommen):
Skill-Stern-/Hamburger-Menü-Badges (X/Start), Net-Worth auf D-Pad-Rechts,
D-Pad-Zoom auch im Skilltree, einstellbare Controller-Zoom-Geschwindigkeit,
Fix für ein kaputtes (falsch positioniertes, unnötig verzögertes)
Tooltip-Popup auf zwei HUD-Buttons, Toast-Meldung bei Controller-Verbindung.

**Nicht Teil dieses Plans (separates Follow-up):** L1/R1 zyklisch durch
Gebäude springen, Settings-Hotkey-Liste als zwei beschriftete Spalten
umbauen, `NestedTooltip.vue`s 1s-Timer generell für kurze Namens-Tooltips
entfernen, Y/Center-Bindung fürs Skilltree-eigene "Kamera zentrieren".

## Umsetzung

- `frontend/src/composables/useGamepadCursor.js` (neu) — Singleton
  `mode` ('fixed'/'free'), `cursorX/Y`, `currentPoint`, `toggle()`,
  `reset()`, `tick(pad, dt)`.
- `frontend/src/components/pixel/GamepadCursor.vue` (neu) — rundes
  Ring-Cursor-Overlay, CSS-only Bildschirmmitte im `fixed`-Modus, JS-Position
  im `free`-Modus, Klick-Puls-Animation (Opacity/Scale, Palette-Farben).
- `frontend/src/components/pixel/GamepadToast.vue` (neu) — Toast unten
  mittig bei Controller-Connect (`useInputMethod.js`s neuer `connectNonce`),
  kein Appear-Delay, 2.5s Auto-Fade.
- `frontend/src/views/FarmGridView.vue` — A (Gebäude-Hit-Test via
  `buildingFrameEls`-BoundingBoxes / generischer `elementFromPoint().click()`
  für alle anderen Dialoge außer Skilltree), R3 (Cursor-Toggle), Start
  (Hamburger-Menü, Badge, immer aktiv), Skill-Stern + Hamburger jetzt ohne
  kaputtes `NestedTooltip`, Skill-Stern per CSS unter den Hamburger-Button
  verschoben, `zoomSpeed` aus `useCameraControls` statt hartkodierter
  Konstante.
- `frontend/src/components/SkillTreeView.vue` — eigene kleine
  Gamepad-rAF-Schleife (Stick-Pan, D-Pad-Zoom, A-Klick via
  `elementFromPoint`), unabhängig von FarmGridView, teilt sich aber
  `useGamepadCursor`s Zustand.
- `frontend/src/composables/useActionHotkeys.js` — Default-Gamepad-Buttons
  `{ networth: 15 (D-Pad rechts), skilltree: 2 (X) }`.
- `frontend/src/composables/useCameraControls.js` — neuer `zoomSpeed` ref
  (persistiert), `frontend/src/components/SettingsDialog.vue` — Slider dafür.
- i18n: `gamepadToast.json` (de/en, neu), `settings.json` `zoomSpeed`-Key
  (de/en).

## Offene Punkte / Risiken

- **Noch nicht live mit Controller getestet** (nur `npm run build` +
  `check:palette` grün) — R3-Toggle, A-Hit-Test auf Gebäude, Skilltree-
  Gamepad-Pan/Zoom/Klick, Toast alle ungetestet mit echter Hardware.
- Skill-Stern-Button sitzt jetzt CSS-relativ unter dem Hamburger-Button
  (`top: calc(100% + 8px)` in `.hud-menu-wrap`) — bei geöffnetem Menü
  überlappt er mit dem Dropdown an derselben Stelle (Dropdown hat höheren
  z-index, deckt ihn ab); nicht weiter angepasst, da nur beim geschlossenen
  Menü sichtbar relevant.

## Verifikation

- `cd frontend && npm run check:palette` + `npm run build` — beide grün.
- Noch zu tun: Live-Test mit echtem Controller (siehe "Offene Punkte").
