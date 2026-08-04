# Deployment

Stand: 2026-08-03. Beschreibt, wie das Backend produktiv läuft, welche
Bugs dabei aufgefallen sind, und welche Sicherheitslücken vor dem
öffentlichen Rollout geschlossen wurden. Enthält bewusst keine
Server-Adressen/Zugangsdaten — nur das, was für zukünftige Deployments
oder Contributor relevant ist.

---

## 1. Produktions-Setup (Backend)

Zwei-Container-Stack: PostgreSQL + Spring-Boot-Backend, per Docker Compose.

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: cookie
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASS}       # starkes Secret, nie committen
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./database/setup.sql:/docker-entrypoint-initdb.d/01_setup.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d cookie"]
      interval: 5s
      timeout: 5s
      retries: 10
    # kein Host-Port nötig -- nur intern im Compose-Netz erreichbar

  backend:
    build:
      context: ./backend/cookie-server-spring-boot
    restart: unless-stopped
    ports:
      - "9876:9876"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/cookie
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: ${DB_PASS}
      APP_DEV_MODE: "false"
      APP_ADMIN_TOKEN: ${ADMIN_TOKEN}      # starkes Secret, nie committen
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres_data:
```

`.env` mit `DB_PASS`/`ADMIN_TOKEN` bleibt außerhalb von Git (`.gitignore`).
Beide Werte per `openssl rand -hex 32` o.ä. erzeugen, nicht die
Default-Werte aus der lokalen `application.properties` (`postgres`/`1234`,
`change-me-in-production`) übernehmen.

### Reverse Proxy / TLS

Das Backend selbst spricht nur Klartext-HTTP auf Port 9876. In Produktion
läuft davor ein TLS-terminierender Reverse Proxy (nginx oder Caddy) mit
Let's-Encrypt-Zertifikat, der Host-Header-basiert auf die Subdomain
weiterleitet. Wichtig für den WebSocket-Endpunkt (`/ws-market`): der
Proxy muss `Upgrade`/`Connection: upgrade`-Header durchreichen.

Beispiel (nginx):
```nginx
location / {
    proxy_pass http://<backend-host>:9876;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

### `app.dev-mode=false` — was es tut und was nicht

- Deaktiviert den Dev-Fallback-Spieler (`DEV_PLAYER_001`)
- Erzwingt `APP_ADMIN_TOKEN` auf `/api/v1/admin/**`
- **Tut nicht:** echte Steam-Auth auf den normalen Gameplay-Endpunkten
  (`game/init`, `farm/*`, `market/*`) — die nehmen `steamId` weiterhin
  ungeprüft entgegen. Siehe `ROADMAP.md` Abschnitt 0, offener Punkt zur
  Ticket-Auth-Verifizierung. Für eine geschlossene Beta akzeptiert, vor
  Public/Early-Access zwingend nachziehen.

---

## 2. Bekannter Build-Bug: `pom.xml` fehlte `spring-boot-maven-plugin`

`mvn clean package` (das der Dockerfile-Build in
`backend/cookie-server-spring-boot/Dockerfile` verwendet) erzeugte ein
Jar ohne `Main-Class`-Manifest-Eintrag —
`java -jar app.jar` scheiterte mit `no main manifest attribute, in app.jar`.

Lokal fällt das nicht auf, weil `./mvnw spring-boot:run` das Plugin auch
ohne Deklaration unter `<build><plugins>` per Plugin-Prefix findet und
ausführt — der `repackage`-Schritt, der die eigentliche Fat-Jar-Erzeugung
übernimmt, lief dabei aber nie.

**Fix:** `spring-boot-maven-plugin` explizit im `<build>`-Block ergänzt.

---

## 3. Sicherheitshärtung vor dem öffentlichen Rollout

Beim ersten produktiven Test fielen zwei Dinge auf, die vor jedem
weiteren öffentlichen Deployment geprüft werden sollten:

1. **Swagger UI / OpenAPI-Docs waren live erreichbar**
   (`/swagger-ui/index.html`, `/v3/api-docs`). Damit konnte jeder ohne
   jede Hürde die komplette API durchklicken und ausführen — inklusive
   admin-geschützter Endpunkte wie `/api/v1/admin/market/reset`.
   **Fix:** in `application.properties`:
   ```properties
   springdoc.swagger-ui.enabled=false
   springdoc.api-docs.enabled=false
   ```

2. **Alter manueller "API Tester" unter `static/index.html`** — ein
   Dev-Hilfsmittel mit UI-Buttons für User anlegen/löschen und
   Markt-Buy/Sell für beliebige `userId`, ohne eigene Auth auf der Seite.
   Wurde entfernt und durch eine reine Coming-Soon-Seite ersetzt, die
   keine API-Aufrufe macht.

Die eigentlichen Admin-Endpunkte prüften den Admin-Token schon vorher
korrekt (401 ohne gültigen Token) — das Risiko lag in der
Auffindbarkeit/Bedienbarkeit über eine fertige UI, nicht in einer
fehlenden Prüfung im Code selbst.

**Empfehlung für künftige Deployments:** vor jedem Rollout kurz prüfen,
ob `/swagger-ui/**`, `/v3/api-docs/**` und `static/**` wirklich nur das
enthalten, was öffentlich sein soll.

---

## 4. Offen (siehe `ROADMAP.md` Abschnitt 0)

- Keine echte serverseitige Steam-Auth-Verifizierung auf den
  Gameplay-Endpunkten — für eine geschlossene Freundes-Beta akzeptiert,
  vor Public/Early-Access zwingend nachziehen.
- Browser-Zugang ohne Electron (Steam-OpenID-Login) ist geplant, aber
  noch nicht implementiert — die aktuelle Coming-Soon-Seite unter `/`
  kündigt das an, führt aber noch keinen echten Login durch.

---

## 5. Windows-Client-Build & Steam-Upload

### Client ist duenn, Backend laeuft zentral

`electron/main.js` spawnt seit 2026-08-03 keinen lokalen Backend-Prozess
mehr (kein `java -jar` im Client, kein gebuendeltes JAR, kein Java auf
Spieler-Seite noetig). Server-Adresse kommt zur Build-Zeit aus
`frontend/.env.production` (`VITE_API_BASE_URL`, `VITE_WS_URL`) — vor
jedem Steam-Build pruefen/aktualisieren, wird fest in den Build gebacken.

### `npm run build:win` unter Linux (kein natives Windows)

`electron-builder --win` braucht Wine für den `rcedit`-Schritt (setzt
Icon/Version-Strings im `.exe`) und für den NSIS-Installer selbst.

- System-Wine bevorzugt (`sudo dnf install wine` o.ä.).
- Ersatzweise geht ein **Lutris**-Wine-Runner
  (`~/.local/share/lutris/runners/wine/<version>/bin/wine64`), falls kein
  32-bit-Unterbau (`i386`-Libs) verfügbar ist: der mitgelieferte
  32-bit-`wine`-Binary scheitert dann mit *"Datei oder Verzeichnis nicht
  gefunden"* (fehlender `ld-linux.so.2`-Interpreter). Workaround: ein
  `wine`-Wrapper-Skript auf PATH, das alles über `wine64` laufen lässt und
  `*-ia32.exe`-Pfade automatisch auf die daneben liegende `*-x64.exe`
  umbiegt (funktioniert für `rcedit-ia32.exe`/`rcedit-x64.exe`, beide
  liegen im `electron-builder`-Cache unter `winCodeSign/`).
- Der NSIS-Installer-Schritt selbst (`Cookie Setup x.x.x.exe`) scheitert
  in einer Wine-Umgebung ohne echten X-Server/Display trotzdem
  (`ShellExecuteEx fehlgeschlagen`) — **das ist für Steam irrelevant**:
  `frontend/release/win-unpacked/` (der eigentliche App-Ordner) wird davor
  bereits vollständig und korrekt gebaut. Steam braucht nur diesen
  Ordner, keinen Installer.

### Bug: `preload.js` mit ESM-Syntax + Sandbox = stiller Fail

`package.json` hat `"type": "module"`. Ein `electron/preload.js` mit
`import { contextBridge } from 'electron'` lädt in einer gepackten
Electron-30-App mit `contextIsolation: true` (Sandbox per Default an)
nicht zuverlässig — `window.electronAPI` bleibt `undefined`, ohne
sichtbaren Fehler. Symptom im Spiel: die Steam-Version zeigt "Bitte das
Spiel über Steam starten." obwohl sie über Steam gestartet wurde (App.vue
faellt auf den Browser-Fallback-Pfad zurück, weil `window.electronAPI`
fehlt).

**Fix:** Preload als `.cjs` mit `require()`-Syntax (`electron/preload.cjs`,
in `main.js` referenziert) — CommonJS ist für Preload-Skripte unabhängig
vom `type`-Feld immer sicher unterstützt.

### Steam-Upload (`scripts/steam/`)

- `app_build_2816100.vdf` + `depot_build_2816102.vdf` (2816102 = Windows-
  Depot, 2816101 wäre Linux — im Steamworks-Portal unter App → SteamPipe →
  Installation nachsehen falls sich das mal ändert).
- `ContentRoot`/`BuildOutput` als absolute Pfade, damit es egal ist von wo
  `steamcmd` aufgerufen wird.
- Steamworks-SDK lokal unter `~/steamworks_sdk/` entpacken (nicht ins
  Repo — Lizenz). Linux-`steamcmd` liegt unter
  `sdk/tools/ContentBuilder/builder_linux/steamcmd.sh`, ruft intern ein
  32-bit-Binary auf — gleiches i386-Lib-Problem wie oben möglich, dann
  bleibt nur ein echtes Terminal mit vollem System-Zugriff (kein
  Sandbox-Container).
- **`"SetLive"` in der vdf schlägt bei diesem Account/dieser App immer
  fehl** (`ERROR! Failed to commit build ... : Failure`), unabhängig vom
  Branch-Namen — Upload selbst funktioniert trotzdem jedes Mal einwandfrei.
  `SetLive` deshalb aus der vdf entfernt: Build hochladen, danach manuell
  im Steamworks-Portal unter App → SteamPipe → "Ihre Builds" die neue
  BuildID per Dropdown auf den Branch (`default`) liveschalten.
- Nur ein Branch (`default`) existiert für diese App bisher — es gibt
  keinen separaten `beta`-Branch zum Vorab-Testen, müsste im Portal-Tab
  "Veröffentlichen" erst angelegt werden.
