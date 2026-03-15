#!/usr/bin/env bash
set -e
REPO="$(cd "$(dirname "$0")" && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

build_backend() {
  echo -e "${CYAN}[Build] Baue Backend JAR...${NC}"
  cd "$REPO/backend/cookie-server-spring-boot"
  ./mvnw package -DskipTests
  echo -e "${GREEN}[Build] JAR fertig.${NC}"
}

menu() {
  while true; do
    echo ""
    echo " =========================================="
    echo "  Cookie Game — Build & Start"
    echo " =========================================="
    echo ""
    echo "  1  Dev starten    (Backend + Frontend)"
    echo "  2  Linux bauen    (AppImage)"
    echo "  3  Windows bauen  (.exe Installer, benötigt Wine)"
    echo "  4  Beides bauen"
    echo "  5  Docker starten"
    echo "  6  Beenden"
    echo ""
    read -rp "  Auswahl: " choice

    case "$choice" in
      1)
        echo -e "${CYAN}[Dev] Stoppe alte Prozesse...${NC}"
        fuser -k 9876/tcp 2>/dev/null || true
        fuser -k 5173/tcp 2>/dev/null || true
        sleep 1
        echo -e "${CYAN}[Dev] Starte Backend...${NC}"
        cd "$REPO/backend/cookie-server-spring-boot"
        ./mvnw spring-boot:run &
        BACKEND_PID=$!
        sleep 5
        echo -e "${CYAN}[Dev] Starte Frontend...${NC}"
        cd "$REPO/frontend"
        [ ! -d node_modules ] && npm install
        npm run dev &
        FRONTEND_PID=$!
        echo ""
        echo -e "${GREEN}Backend:  http://localhost:9876${NC}"
        echo -e "${GREEN}Frontend: http://localhost:5173${NC}"
        echo ""
        echo "  Ctrl+C zum Stoppen"
        wait $BACKEND_PID $FRONTEND_PID
        ;;
      2)
        build_backend
        echo -e "${CYAN}[Build] Baue Linux AppImage...${NC}"
        cd "$REPO/frontend"
        npm run build:linux
        echo -e "${GREEN}Fertig! Ausgabe: frontend/release/${NC}"
        ;;
      3)
        build_backend
        echo -e "${CYAN}[Build] Baue Windows Installer (benötigt Wine)...${NC}"
        cd "$REPO/frontend"
        npm run build:win
        echo -e "${GREEN}Fertig! Ausgabe: frontend/release/${NC}"
        ;;
      4)
        build_backend
        echo -e "${CYAN}[Build] Baue Windows + Linux...${NC}"
        cd "$REPO/frontend"
        npm run build:all
        echo -e "${GREEN}Fertig! Ausgabe: frontend/release/${NC}"
        ;;
      5)
        echo -e "${CYAN}[Docker] Starte alle Services...${NC}"
        cd "$REPO"
        docker compose up --build
        ;;
      6)
        exit 0
        ;;
      *)
        echo -e "${RED}Ungültige Auswahl.${NC}"
        ;;
    esac
  done
}

menu
