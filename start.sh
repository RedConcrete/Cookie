#!/usr/bin/env bash
REPO="$(cd "$(dirname "$0")" && pwd)"

echo "[1/2] Backend starten..."
cd "$REPO/backend/cookie-server-spring-boot"
./mvnw spring-boot:run &
BACKEND_PID=$!

sleep 3

echo "[2/2] Frontend starten..."
cd "$REPO/frontend"
npm run dev &
FRONTEND_PID=$!

echo ""
echo "Backend:  http://localhost:9876"
echo "Frontend: http://localhost:5173"
echo ""
echo "Ctrl+C zum Stoppen"

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit 0" INT TERM
wait $BACKEND_PID $FRONTEND_PID
