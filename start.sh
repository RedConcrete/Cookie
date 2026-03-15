#!/usr/bin/env bash
REPO="$(cd "$(dirname "$0")" && pwd)"

echo "[0/2] Stoppe alte Prozesse auf Port 9876 und 5173..."
fuser -k 9876/tcp 2>/dev/null
fuser -k 5173/tcp 2>/dev/null
sleep 1

echo "[1/2] Backend starten..."
cd "$REPO/backend/cookie-server-spring-boot"
./mvnw spring-boot:run &
BACKEND_PID=$!

sleep 5

echo "[2/2] Frontend starten..."
cd "$REPO/frontend"
[ ! -d node_modules ] && npm install
npm run dev &
FRONTEND_PID=$!

echo ""
echo "Backend:  http://localhost:9876"
echo "Frontend: http://localhost:5173"
echo ""
echo "Ctrl+C zum Stoppen"

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; fuser -k 9876/tcp 5173/tcp 2>/dev/null; exit 0" INT TERM
wait $BACKEND_PID $FRONTEND_PID
