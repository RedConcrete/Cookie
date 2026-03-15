#!/usr/bin/env bash
REPO="$(cd "$(dirname "$0")" && pwd)"

export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"

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
./mvnw spring-boot:run > "$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

# Warte bis Backend antwortet (max 30s)
for i in $(seq 1 30); do
  if curl -s http://localhost:9876/actuator/health > /dev/null 2>&1; then
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
  if [ $i -eq 30 ]; then
    echo "TIMEOUT"
    echo ""
    echo "  Backend-Log: $LOG_DIR/backend.log"
    exit 1
  fi
done

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

echo ""
echo "  ========================"
echo "  Backend:  http://localhost:9876"
echo "  Frontend: http://localhost:5173"
echo "  Logs:     $LOG_DIR/"
echo "  ========================"
echo ""
echo "  Ctrl+C zum Stoppen"
echo ""

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; fuser -k 9876/tcp 5173/tcp 2>/dev/null; exit 0" INT TERM
wait $BACKEND_PID $FRONTEND_PID
