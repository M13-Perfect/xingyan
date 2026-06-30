@echo off
setlocal

set "ROOT=%~dp0"
set "LAN_IP=%~1"
if "%LAN_IP%"=="" set "LAN_IP=192.168.1.29"

set "CASDOOR_URL=http://%LAN_IP%:8000"
set "FRONTEND_URL=http://%LAN_IP%:5173"

echo [1/4] Starting Casdoor at %CASDOOR_URL% ...
pushd "%ROOT%deploy\casdoor" || exit /b 1
docker compose up -d
if errorlevel 1 (
  echo Casdoor failed to start. Open Docker Desktop first, then run this file again.
  popd
  exit /b 1
)
popd

echo [2/4] Stopping old backend/frontend processes ...
call :kill_port 8080
call :kill_port 5173
timeout /t 2 /nobreak >nul

echo [3/4] Starting backend on http://%LAN_IP%:8080 ...
start "xyyx-backend" /D "%ROOT%xyyx" cmd /k "set SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=%CASDOOR_URL%&& set SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=%CASDOOR_URL%/.well-known/jwks&& mvn spring-boot:run"

echo [4/4] Starting frontend at %FRONTEND_URL% ...
start "xyyx-frontend" /D "%ROOT%xyyx\xyyx-frontend" cmd /k "set VITE_CASDOOR_BASE_URL=%CASDOOR_URL%&& set VITE_CASDOOR_REDIRECT_URI=%FRONTEND_URL%/auth/callback&& set VITE_CASDOOR_CLIENT_ID=xyyx-web&& npm run dev -- --host 0.0.0.0 --strictPort"

echo.
echo Open: %FRONTEND_URL%/
echo If your LAN IP changed, run: restart-xingyan.bat YOUR_LAN_IP
exit /b 0

:kill_port
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":%~1 .*LISTENING"') do (
  echo Stopping PID %%P on port %~1
  taskkill /F /PID %%P >nul 2>nul
)
exit /b 0
