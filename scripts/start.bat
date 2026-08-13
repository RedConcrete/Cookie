@echo off

if "%1"=="--balance" (
  echo Running Balance Report...
  cd /d %~dp0..\frontend
  npm run balance:report -- %2 %3 %4
  exit /b %errorlevel%
)

echo Starting Cookie Game...

echo [1/2] Starting Backend (Spring Boot on port 9876)...
start "Cookie Backend" cmd /k "cd /d %~dp0..\backend\cookie-server-spring-boot && mvnw.cmd spring-boot:run"

timeout /t 3 /nobreak >nul

echo [2/2] Starting Frontend (Vite on port 5173)...
start "Cookie Frontend" cmd /k "cd /d %~dp0..\frontend && npm install && npm run dev"

echo.
echo Backend:  http://localhost:9876
echo Frontend: http://localhost:5173
echo.
echo Beide Fenster offen lassen. Im Browser http://localhost:5173 aufrufen.
pause
