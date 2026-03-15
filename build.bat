@echo off
setlocal
cd /d %~dp0

:menu
cls
echo.
echo  ==========================================
echo   Cookie Game — Build ^& Start
echo  ==========================================
echo.
echo   1  Dev starten    (Backend + Frontend)
echo   2  Windows bauen  (.exe Installer)
echo   3  Linux bauen    (AppImage)
echo   4  Beides bauen   (Windows + Linux)
echo   5  Docker starten
echo   6  Beenden
echo.
set /p choice="  Auswahl: "

if "%choice%"=="1" goto dev
if "%choice%"=="2" goto build_win
if "%choice%"=="3" goto build_linux
if "%choice%"=="4" goto build_all
if "%choice%"=="5" goto docker
if "%choice%"=="6" exit /b
goto menu

:: ------------------------------------------
:dev
cls
echo  [Dev] Starte Backend und Frontend...
echo.
start "Cookie Backend" cmd /k "cd /d %~dp0backend\cookie-server-spring-boot && mvnw.cmd spring-boot:run"
timeout /t 5 /nobreak >nul
start "Cookie Frontend" cmd /k "cd /d %~dp0frontend && npm run dev"
echo.
echo  Backend:  http://localhost:9876
echo  Frontend: http://localhost:5173
echo.
pause
goto menu

:: ------------------------------------------
:build_win
cls
echo  [Build] Baue Backend JAR...
call :build_backend
if errorlevel 1 goto build_error
echo.
echo  [Build] Baue Windows Installer...
cd /d %~dp0frontend
call npm run build:win
if errorlevel 1 goto build_error
echo.
echo  Fertig! Ausgabe: frontend\release\
echo.
pause
goto menu

:: ------------------------------------------
:build_linux
cls
echo  [Build] Baue Backend JAR...
call :build_backend
if errorlevel 1 goto build_error
echo.
echo  [Build] Baue Linux AppImage...
cd /d %~dp0frontend
call npm run build:linux
if errorlevel 1 goto build_error
echo.
echo  Fertig! Ausgabe: frontend\release\
echo.
pause
goto menu

:: ------------------------------------------
:build_all
cls
echo  [Build] Baue Backend JAR...
call :build_backend
if errorlevel 1 goto build_error
echo.
echo  [Build] Baue Windows + Linux...
cd /d %~dp0frontend
call npm run build:all
if errorlevel 1 goto build_error
echo.
echo  Fertig! Ausgabe: frontend\release\
echo.
pause
goto menu

:: ------------------------------------------
:docker
cls
echo  [Docker] Starte alle Services...
echo.
cd /d %~dp0
docker compose up --build
pause
goto menu

:: ------------------------------------------
:build_backend
cd /d %~dp0backend\cookie-server-spring-boot
echo  Maven baut JAR (dauert beim ersten Mal 1-2 Minuten)...
call mvnw.cmd package -DskipTests
exit /b %errorlevel%

:: ------------------------------------------
:build_error
echo.
echo  FEHLER beim Bauen. Bitte Log pruefen.
echo.
pause
goto menu
