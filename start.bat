@echo off
echo Starting Cookie Game...

echo [1/2] Starting Backend (Spring Boot on port 9876)...
start "Cookie Backend" cmd /k "cd /d %~dp0Server\cookie-server-spring-boot && mvnw.cmd spring-boot:run"

timeout /t 3 /nobreak >nul

echo [2/2] Starting Frontend (Vite on port 5173)...
start "Cookie Frontend" cmd /k "cd /d %~dp0frontend && npm install && npm run dev"

echo.
echo Backend:  http://localhost:9876
echo Frontend: http://localhost:5173
echo.
echo Beide Fenster offen lassen. Im Browser http://localhost:5173 aufrufen.
pause
