# ✅ Steam-Auth-Pflicht für Produktion (app.dev-mode=false)

> **Status:** ✅ Umgesetzt (2026-08-14)

## Context

Bis dahin prüfte der Server bei keinem Gameplay-Endpunkt, ob der Aufrufer
wirklich der Steam-Account ist, den er in `userId`/`steamId` behauptet zu
sein — `AppConfig.isDevMode()` gate'te nur zwei Admin-Endpunkte
(`X-Admin-Token`-Bypass), nicht die eigentlichen Spielaktionen (siehe
`docs/ROADMAP.md` Abschnitt 0, jetzt abgehakt). Jeder mit Netzwerkzugriff
auf den Produktiv-Server hätte fremde Accounts leerkaufen/-verkaufen,
Ressourcen abräumen etc. können, einfach indem er eine fremde `steamId`
einsetzt.

User-Vorgabe (2026-08-14): in Produktion (`app.dev-mode=false`) muss ein
echter Steam-Auth-Check existieren — niemand außer dem tatsächlichen
Steam-Account-Inhaber darf das Spiel starten oder irgendeinen Call nutzen.
Im Gespräch erweitert um Web-Login (Steam OpenID, volles Browser-Gameplay
statt nur Electron) und einen Ownership-Check dafür (OpenID bestätigt nur
Identität, nicht Kauf). `app.dev-mode=true` bleibt exakt wie zuvor (kein
Auth-Check, `DEV_PLAYER_001`, `tools/mcp-testing-server/` unverändert
nutzbar) — die gesamte neue Logik greift nur bei `devMode=false`.

## Mechanismus

**Electron/Ticket-Flow:** Client holt beim Start ein Steam-Auth-Ticket
(`steamworks.js auth.getAuthTicketForWebApi`) → `POST /api/v1/auth/steam`
→ Server verifiziert gegen `ISteamUserAuth/AuthenticateUserTicket` → bei
Erfolg kurzlebige Server-Session (in-memory, 24h TTL, kein DB-Schema-Change
— User-Entscheidung, Server-Neustart zwingt zum Neu-Einloggen, unkritisch).
Client hängt den Session-Token danach an jeden Request (`X-Session-Token`).

**Web-Login-Flow (Steam OpenID):** Browser navigiert zu
`GET /api/v1/auth/steam/login` → Redirect zu Steams OpenID-Login →
`GET /api/v1/auth/steam/callback` verifiziert (`check_authentication`
gegen `steamcommunity.com/openid/login`, extrahiert SteamID64 aus
`claimed_id`) → zusätzlich `CheckAppOwnership`-Check (Steam Web API) —
verhindert, dass ein Steam-Account ohne Spielkauf sich per Browser
einloggt — → Session erstellen → Redirect zurück zum Frontend mit
Session-Token + steamId + Anzeigename als Query-Params.

**Durchsetzung:** neuer globaler `SteamAuthInterceptor` prüft bei jedem
`/api/v1/**`-Call (außer `/api/v1/config`, `/api/v1/auth/**`,
`/api/v1/admin/**`, die eigene Mechanismen haben) einen gültigen
Session-Token. Trägt der Request eine `userId`/`steamId` in Pfad/Query,
muss sie mit der Session übereinstimmen — sonst 401. Kein öffentlicher
Whitelist-Ausnahmefall (User-Entscheidung): auch Rangliste, Rezepte,
Season-Info brauchen eine Session, nur reine Profil-Ansichten
(`/players/{id}/networth`, `/profile`, `/networth/history`) sind von der
Owner-Match-Pflicht ausgenommen, brauchen aber weiterhin eine Session.

## Umgesetzt

**Backend** (`backend/cookie-server-spring-boot/src/main/java/cookie/server/`):
- `service/SteamAuthService.java` (neu) — `verifyTicket()`,
  `verifyOpenIdCallback()`, `ownsGame()` (CheckAppOwnership),
  `buildOpenIdLoginUrl()`. Fail-closed bei jedem Fehler, `@PostConstruct`-
  Check loggt laut, wenn `devMode=false` ohne `steamWebApiKey`.
- `service/SteamSessionService.java` (neu) — in-memory Session-Map, 24h TTL.
- `service/SteamAvatarService.java` (erweitert) — `fetchProfile()` liefert
  jetzt zusätzlich `personaname` (für den Web-Login, der keinen lokalen
  Namen wie der Electron-Client hat).
- `controller/AuthController.java` (neu) — `POST /steam`,
  `GET /steam/login`, `GET /steam/callback`.
- `config/SteamAuthInterceptor.java` + `config/SteamAuthWebConfig.java`
  (neu) — Durchsetzung, siehe oben.
- `exception/AuthException.java` (neu) + `GlobalExceptionHandler` um
  401-`{"error":...}`-Fall ergänzt.
- `controller/MarketController.java` (erweitert) — einziger Endpunkt mit
  Identität im Body statt Pfad (`MarketRequestDto.userId`), bekommt den
  Owner-Check deshalb explizit statt generisch über den Interceptor.
- `config/AppConfig.java` — neue Felder `frontendBaseUrl`, `publicBaseUrl`.

**Frontend:**
- `electron/main.js` — holt Auth-Ticket, reicht es über den bestehenden
  `steam-auth`-IPC-Kanal mit durch.
- `src/services/api.js` — `authenticateSteamSession()`, `setSessionToken()`,
  `request()` hängt `X-Session-Token` an, wenn gesetzt.
- `src/App.vue` — `onSteamAuth`-Handler tauscht Ticket gegen Session;
  neuer Zweig liest `?webSession=`/`?steamId=`/`?name=` vom OpenID-Rückweg.
- `src/components/LandingView.vue` — vorbereiteter, bis dahin
  `disabled`er "MIT STEAM ANMELDEN"-Button aktiviert, navigiert zu
  `/api/v1/auth/steam/login`. i18n-Texte (DE+EN) angepasst, "Browser-Login
  noch nicht verfügbar" entfernt.

## Verification

- Backend kompiliert sauber (`mvnw compile`).
- Live gegen echten Dev-Server (`app.dev-mode=false` erzwungen) getestet:
  - Call ohne `X-Session-Token` gegen `GET /api/v1/game/init/DEV_PLAYER_001`
    → 401 `{"error":"Keine gültige Steam-Session..."}`.
  - `POST /api/v1/auth/steam` mit Fantasie-Ticket gegen die **echte** Steam
    Web API → 401 `{"error":"Steam-Ticket ungültig oder abgelaufen."}` —
    bestätigt korrekte Anbindung.
  - `GET /api/v1/leaderboard` ohne Session → 401 (bestätigt: kein
    öffentlicher Whitelist-Fall wie entschieden).
  - `GET /api/v1/config` weiterhin offen (200).
  - `GET /api/v1/auth/steam/login` → korrekter 302 mit allen `openid.*`-
    Params zu `steamcommunity.com/openid/login`.
  - Encoding-Bug in den Interceptor-Fehlermeldungen gefunden und gefixt
    (Umlaute kaputt ohne explizites `setCharacterEncoding("UTF-8")` vor
    `getWriter()`).
- Zurück auf `app.dev-mode=true`: kompletter MCP-Testserver-Verify-Lauf
  (`game_get_state`, `farm_harvest`) 1:1 wiederholt, unverändertes
  Verhalten bestätigt — Dev-Pfad und `tools/mcp-testing-server/` bleiben
  unangetastet.
- **Nicht automatisch testbar (kein echter Steam-Client hier verfügbar):**
  der positive Ticket-Pfad (echtes gültiges Ticket → Session → Owner-Check)
  und der komplette OpenID-Rückweg (echter Steam-Login im Browser →
  Callback → Redirect mit Session). Beides braucht einen manuellen
  End-to-End-Test durch den User (Electron-Build normal starten;
  Web-Login einmal durchklicken) vor Verlass in Produktion.

## Nicht im Scope

- Öffentliches Hosting des Frontend-Builds unter der Produktions-Domain
  (nginx/Reverse-Proxy, DNS) — bestehendes Deployment-Terrain, siehe
  `CLAUDE.md` Server-Deployment-Plan. Hier nur der Code-Pfad, lokal
  (`http://localhost:5173`) durchgetestet.
- Automatisches Session-Refresh/Retry bei abgelaufener Session im Frontend
  (aktuell: Fehler wird angezeigt, Spieler muss neu einloggen).
