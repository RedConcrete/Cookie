#!/usr/bin/env bash
REPO="$(cd "$(dirname "$0")/.." && pwd)"

# nvm (falls vorhanden)
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"

# --ngrok: Backend (9876) + Frontend (5173) zusaetzlich per ngrok-Tunnel oeffentlich
# erreichbar machen (Freundes-Tests gegen den lokalen Dev-Server). Roher Port bleibt
# dabei zu (keine Firewall-Freigabe noetig) -- ngrok gibt eigene https-URLs aus.
# Jeder Tester bekommt ueber die Tunnel-URL automatisch eine eigene Test-ID statt des
# lokalen DEV_PLAYER_001-Saves, siehe frontend/src/App.vue#devPlayerId.
NGROK_MODE=0
if [ "$1" = "--ngrok" ]; then
  NGROK_MODE=1
  shift
fi
if [ "$NGROK_MODE" = "1" ] && ! command -v ngrok >/dev/null 2>&1; then
  NGROK_NPM_BIN="$(npm root -g 2>/dev/null)/../bin"
  [ -x "$NGROK_NPM_BIN/ngrok" ] && export PATH="$NGROK_NPM_BIN:$PATH"
fi
if [ "$NGROK_MODE" = "1" ] && ! command -v ngrok >/dev/null 2>&1; then
  echo "  FEHLER: ngrok nicht gefunden (npm install -g ngrok)"
  exit 1
fi
if [ "$NGROK_MODE" = "1" ]; then
  # Authtoken-Config-Datei -- muss bei jedem ngrok-Aufruf explizit mitgegeben werden,
  # sobald wir zusaetzlich per --config eine eigene web_addr setzen (mehrere --config
  # werden gemerged, aber der Default-Pfad wird NICHT automatisch mitgeladen sobald
  # --config einmal explizit gesetzt ist).
  NGROK_DEFAULT_CFG="$(ngrok config check 2>&1 | sed -n 's/^Valid configuration file at //p')"
  if [ -z "$NGROK_DEFAULT_CFG" ]; then
    echo "  FEHLER: kein ngrok-Authtoken konfiguriert (ngrok config add-authtoken <token>)"
    exit 1
  fi
fi

# --balance: kein Server-Start, stattdessen nur das Balance-Report-Tool laufen
# lassen (frontend/scripts/balance-report.mjs, siehe
# docs/plans/2026-08-13-open-balance-report-tool.md). Läuft im Live-Modus
# gegen einen bereits per separatem `start.sh`-Aufruf laufenden Dev-Server,
# sonst faellt das Tool selbst automatisch auf --static zurueck. Weitere
# Flags (--static, --out=...) werden 1:1 durchgereicht.
if [ "$1" = "--balance" ]; then
  shift
  cd "$REPO/frontend"
  npm run balance:report -- "$@"
  exit $?
fi

# Java 21 (falls JAVA_HOME nicht gesetzt oder falsch, per which java ableiten)
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  JAVA_BIN="$(command -v java)"
  if [ -n "$JAVA_BIN" ]; then
    export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

LOG_DIR="$REPO/.logs"
mkdir -p "$LOG_DIR"

echo ""
echo "  Cookie Game — Dev Start"
echo "  ========================"
echo ""

# Alte Prozesse stoppen
printf "  [1/3] Stoppe alte Prozesse ... "
fuser -k 9876/tcp 2>/dev/null; fuser -k 5173/tcp 2>/dev/null
sleep 1
echo "OK"

# Backend starten
printf "  [2/3] Backend starten      ... "
cd "$REPO/backend/cookie-server-spring-boot"
chmod +x ./mvnw 2>/dev/null
bash ./mvnw spring-boot:run > "$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

# Warte bis Backend antwortet (max 90s -- mvnw baut bei Code-Aenderungen erst
# neu (Maven-Kompilierung), bevor der Spring-Boot-Start selbst ueberhaupt
# losgeht; 30s reichte dafuer bei einem kalten Build nicht immer aus.
# /api/v1/config statt /actuator/health -- Actuator ist kein Dependency hier,
# der alte Pfad haette nie einen echten 200er geliefert.
for i in $(seq 1 90); do
  if curl -sf http://localhost:9876/api/v1/config > /dev/null 2>&1; then
    echo "OK  (PID $BACKEND_PID)"
    break
  fi
  if ! kill -0 $BACKEND_PID 2>/dev/null; then
    echo "FEHLER"
    echo ""
    echo "  Backend-Log: $LOG_DIR/backend.log"
    exit 1
  fi
  sleep 1
  if [ $i -eq 90 ]; then
    echo "TIMEOUT"
    echo ""
    echo "  Backend-Log: $LOG_DIR/backend.log"
    exit 1
  fi
done

# ngrok-Tunnel fuers Backend -- MUSS vor dem Frontend-Start stehen, damit Vite die
# oeffentliche Backend-URL per VITE_API_BASE_URL/VITE_WS_URL beim Start mitbekommt
# (Vite liest import.meta.env nur beim Prozessstart neu ein).
if [ "$NGROK_MODE" = "1" ]; then
  printf "  [ngrok] Backend-Tunnel      ... "
  printf 'version: "3"\nagent:\n  web_addr: 127.0.0.1:4040\n' > "$LOG_DIR/ngrok-web-4040.yml"
  # 127.0.0.1 statt "localhost" -- manche Systeme loesen localhost zuerst auf ::1 (IPv6)
  # auf, Backend/Vite binden aber nur IPv4 -> ngrok bekommt sonst ERR_NGROK_8012.
  ngrok http 127.0.0.1:9876 --config "$NGROK_DEFAULT_CFG" --config "$LOG_DIR/ngrok-web-4040.yml" --log stdout > "$LOG_DIR/ngrok-backend.log" 2>&1 &
  NGROK_BACKEND_PID=$!
  BACKEND_NGROK_URL=""
  for i in $(seq 1 20); do
    BACKEND_NGROK_URL="$(curl -s http://127.0.0.1:4040/api/tunnels 2>/dev/null | jq -r '.tunnels[] | select(.proto=="https") | .public_url' 2>/dev/null | head -n1)"
    [ -n "$BACKEND_NGROK_URL" ] && break
    sleep 1
  done
  if [ -z "$BACKEND_NGROK_URL" ]; then
    echo "FEHLER"
    echo "  ngrok-Log: $LOG_DIR/ngrok-backend.log"
    kill $BACKEND_PID $NGROK_BACKEND_PID 2>/dev/null
    exit 1
  fi
  echo "OK  ($BACKEND_NGROK_URL)"
  export VITE_API_BASE_URL="$BACKEND_NGROK_URL"
  export VITE_WS_URL="wss://${BACKEND_NGROK_URL#https://}/ws-market"
fi

# Frontend starten
printf "  [3/3] Frontend starten     ... "
cd "$REPO/frontend"
if [ ! -d node_modules ]; then
  npm install > "$LOG_DIR/npm-install.log" 2>&1
fi
npm run dev > "$LOG_DIR/frontend.log" 2>&1 &
FRONTEND_PID=$!

# Warte bis Frontend antwortet (max 20s)
for i in $(seq 1 20); do
  if curl -s http://localhost:5173 > /dev/null 2>&1; then
    echo "OK  (PID $FRONTEND_PID)"
    break
  fi
  if ! kill -0 $FRONTEND_PID 2>/dev/null; then
    echo "FEHLER"
    echo ""
    echo "  Frontend-Log: $LOG_DIR/frontend.log"
    exit 1
  fi
  sleep 1
  if [ $i -eq 20 ]; then
    echo "TIMEOUT"
    echo ""
    echo "  Frontend-Log: $LOG_DIR/frontend.log"
    exit 1
  fi
done

# ngrok-Tunnel fuers Frontend -- erst NACH Frontend-Start, Vite laeuft ja schon mit
# den richtigen Backend-Env-Vars.
FRONTEND_NGROK_URL=""
if [ "$NGROK_MODE" = "1" ]; then
  printf "  [ngrok] Frontend-Tunnel    ... "
  printf 'version: "3"\nagent:\n  web_addr: 127.0.0.1:4041\n' > "$LOG_DIR/ngrok-web-4041.yml"
  ngrok http 127.0.0.1:5173 --config "$NGROK_DEFAULT_CFG" --config "$LOG_DIR/ngrok-web-4041.yml" --log stdout > "$LOG_DIR/ngrok-frontend.log" 2>&1 &
  NGROK_FRONTEND_PID=$!
  for i in $(seq 1 20); do
    FRONTEND_NGROK_URL="$(curl -s http://127.0.0.1:4041/api/tunnels 2>/dev/null | jq -r '.tunnels[] | select(.proto=="https") | .public_url' 2>/dev/null | head -n1)"
    [ -n "$FRONTEND_NGROK_URL" ] && break
    sleep 1
  done
  if [ -z "$FRONTEND_NGROK_URL" ]; then
    echo "FEHLER"
    echo "  ngrok-Log: $LOG_DIR/ngrok-frontend.log"
    kill $BACKEND_PID $FRONTEND_PID $NGROK_BACKEND_PID $NGROK_FRONTEND_PID 2>/dev/null
    exit 1
  fi
  echo "OK  ($FRONTEND_NGROK_URL)"
fi

echo ""
echo "  ========================"
echo "  Backend:  http://localhost:9876"
echo "  Frontend: http://localhost:5173"
if [ "$NGROK_MODE" = "1" ]; then
  echo "  ------------------------"
  echo "  Live-Link (an Tester):  $FRONTEND_NGROK_URL"
fi
echo "  Logs:     $LOG_DIR/"
echo "  ========================"
echo ""
echo "  Ctrl+C zum Stoppen"
echo ""

trap "kill $BACKEND_PID $FRONTEND_PID $NGROK_BACKEND_PID $NGROK_FRONTEND_PID 2>/dev/null; fuser -k 9876/tcp 5173/tcp 2>/dev/null; exit 0" INT TERM
wait $BACKEND_PID $FRONTEND_PID
