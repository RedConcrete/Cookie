# ⏳ MCP-Schnittstelle für KI-gesteuertes Testen (Dev-only)

> **Status:** ⏳ Offen (Idee, noch kein Code)

## Context

Idee eines Kollegen (2026-08-11): mehrere MCP-Schnittstellen bauen, über die
KI-Agents Spielaktionen ausführen können, um das Spiel automatisiert zu
testen (Bugs, Balance, insbesondere die AMM-Markt-Wirtschaft mit
Angebot/Nachfrage-Preisbildung, siehe `cookie-game-design.md` Abschnitt 6).
Ziel laut User: **alles im Spiel soll später per KI-Tools testbar sein**.
**Ausdrücklich nur Dev-Umgebung, nie aktive Nutzung in Produktiv.**

Roadmap-Eintrag: `docs/ROADMAP.md` Abschnitt 8.

## Ziel

Ein KI-Agent (z. B. Claude über MCP-Client) kann Spielaktionen ausführen wie
ein echter Spieler — Gebäude kaufen, ernten, backen, am Markt handeln,
Skill-Punkte setzen — und so automatisiert Spielverläufe durchspielen, die
sich sonst nur durch manuelles Playtesting finden lassen (siehe Abschnitt 7
in der Roadmap: die bisherigen Markt-Crash-/Balance-Bugs kamen alle erst
durch echte Spieler-Playtests ans Licht).

## Architektur-Idee (v1)

**MCP-Server wrappt die bestehende REST-API**, kein eigener Spiel-Zugriff
nötig — die Backend-Validierung (Server prüft Kosten/Guthaben/Cooldowns neu,
siehe `CLAUDE.md`: "Client-Werte nie vertrauen") gilt für den KI-Agent
genauso wie für den echten Client. Kandidat: eigener Node-Prozess
(`@modelcontextprotocol/sdk`) neben `frontend/`/`backend/`, z. B.
`tools/mcp-testing-server/` — separates Package, kein Teil des
Electron-Builds oder des Spring-Boot-Deployments.

**Spielt als Dev-Player.** Nutzt dieselbe Mechanik wie der bestehende
Web-Fallback (`App.vue`, `DEV_PLAYER_001` bei `app.dev-mode=true`, siehe
Abschnitt 0/§ Browser-Zugang in der Roadmap) statt einer neuen Auth-Variante
— der Agent braucht keinen echten Steam-Login, nur einen laufenden
Dev-Server mit `app.dev-mode=true`.

**Harte Leitplanke:** MCP-Server verweigert den Start bzw. jede Aktion, wenn
das Ziel-Backend nicht erkennbar im Dev-Modus läuft (z. B. Check gegen einen
Health-/Info-Endpoint, der `dev-mode` zurückgibt, oder schlicht Whitelist auf
`localhost`-Basis-URLs). Kein Admin-Token im MCP-Server hinterlegt — Aktionen
laufen ausschließlich über die normalen Gameplay-Endpunkte, die ein Spieler
auch hätte.

## Scope v1 (Vorschlag, nicht final)

Erste Tool-Auswahl auf die fragilste Systemgruppe konzentrieren statt sofort
komplette API-Abdeckung:

- `market_buy` / `market_sell` (wrapt `MarketController`) — genau die Stelle,
  die bisher die kritischsten Bugs hatte (Negative-Amount-Exploit,
  Markt-Crash bei ~200 Einheiten, siehe Roadmap Abschnitt 0/7.1).
- `farm_harvest`, `farm_collect_building` — Kern-Loop.
- `game_get_state` (liest `UserInformationDto`) — damit der Agent zwischen
  Aktionen den tatsächlichen Serverstand sieht statt zu raten.

Bewusst NICHT in v1: Skill-Baum-Allokation, Backen/Rezepte, Season-Reset —
kommt erst, wenn sich das Grundmuster (Tool-Definition → REST-Call →
Ergebnis zurück an den Agent) bewährt hat.

## Mehrspieler-Simulation & Pentest (v2, nach v1)

Ergänzung (2026-08-11): der MCP-Server soll nicht nur einen einzelnen
Dev-Player steuern können, sondern **mehrere simulierte Spieler parallel**
— genau die Bug-Klasse, die bisher am schwersten zu finden war, kam immer
erst bei echter Nebenläufigkeit zutage (Roadmap Abschnitt 7.1: Race-Condition
beim gleichzeitigen Einsammeln mehrerer Gebäude, Markt-Crash bei ~200
Einheiten durch schnelle Trade-Folgen desselben Spielers — mit mehreren
*gleichzeitig* handelnden Spielern ist das Fenster für Lost-Update-artige
Bugs deutlich größer).

- **Mehrere Dev-Player-Identitäten.** `DEV_PLAYER_001` reicht nicht mehr —
  braucht eine kleine Menge fester Test-SteamIDs (`DEV_PLAYER_001..00N`) oder
  einen Erzeuge-Endpoint für Wegwerf-Testspieler, jeweils mit eigenem
  Ressourcen-/Gebäude-Stand. Nur unter `app.dev-mode=true` erreichbar, exakt
  dieselbe Leitplanke wie oben.
- **Parallele Aktionen erzwingen.** MCP-Tool, das N simulierte Spieler
  gleichzeitig dieselbe Aktion ausführen lässt (z. B. alle kaufen im selben
  Tick dieselbe Ressource, oder alle sammeln gleichzeitig ein) — gezielt auf
  Race Conditions/Lost Updates in genau den Services, die das schon einmal
  hatten (`MarketService.performAction`, `PassiveIncomeService.collectBuilding`).
  Danach Konsistenz-Check: Summe der abgebuchten/gutgeschriebenen Beträge
  muss über alle Spieler stimmen, kein negativer Stock, kein doppelt
  gezähltes Einsammeln.
- **Pentest im engeren Sinn** (Exploit-Versuche, nicht nur Last): gezielt
  Grenzfälle/ungültige Eingaben gegen die Gameplay-Endpunkte fahren, wie sie
  in der Vergangenheit echte Bugs waren (negativer `amount`, siehe Roadmap
  Abschnitt 0 "Negative-Amount-Exploit" — der Fix davon ist der Maßstab: ein
  MCP-Pentest-Tool sollte solche Exploits *vor* dem nächsten Feature-Release
  automatisch wiederfinden, als eine Art Regressionstest für bereits gefixte
  Sicherheitslücken). Explizit nur gegen den Dev-Server, siehe Leitplanke
  oben — kein Pentest-Tooling, das je gegen den Live-Beta-Server laufen darf.

## Offene Fragen (vor Umsetzung zu klären)

- Ein MCP-Server für alles, oder mehrere kleine (Markt/Farm/Skillbaum
  getrennt), wie ursprünglich vom Kollegen vorgeschlagen? Ein einzelner
  Server mit klar benannten Tools ist einfacher zu pflegen, mehrere kleine
  wären unabhängig deploybar/testbar. Tendenz: mit einem Server starten,
  splitten falls er unübersichtlich wird.
- Soll der Agent auch UI-Zustand prüfen können (Playwright-Browser-Steuerung,
  wie schon einmal für die Skill-Baum-Admin-Editor-Verifikation genutzt,
  siehe `docs/plans/2026-08-10-open-skillbaum-admin-editor.md`), oder reicht
  reiner REST-API-Zugriff für v1? Reiner REST-Zugriff deckt Wirtschafts-/
  Balance-Bugs ab, aber keine reinen Frontend-Bugs (z. B. Klick-Overlaps wie
  in Abschnitt 7.1 der Roadmap).
- Wo laufen MCP-Server-Konfiguration/Zugangsdaten (falls später doch ein
  Dev-Token nötig wird)? Nie ins Repo committen, analog zum bestehenden
  Admin-Token-Handling.

## Nicht im Scope

- Jede Nutzung gegen den Live-Beta-Server (`https://cookie.r3dconcrete.de`).
- Automatisiertes CI-Testing darüber (denkbare Folgeidee, aber erst nach v1
  als manuelles Dev-Tool bewährt).
