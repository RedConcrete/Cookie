@echo off
echo Starting Cookie Game (Docker)...
cd /d %~dp0
docker compose up --build
