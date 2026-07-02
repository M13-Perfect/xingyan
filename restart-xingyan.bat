@echo off
setlocal

set "ROOT=%~dp0"
set "HOST=%~1"
if "%HOST%"=="" set "HOST=localhost"

set "CASDOOR_URL=http://%HOST%:8000"
set "FRONTEND_URL=http://%HOST%:5173"

echo Host: %HOST%
echo   Default is localhost: immune to LAN IP changes, and the staff-create /
echo   change-password screens - window.crypto.subtle - work too.
echo   Phone/LAN testing: rerun as  restart-xingyan.bat YOUR_LAN_IP
echo   A LAN-IP regex Redirect URI is registered in Casdoor already; if login
echo   still complains about Redirect URI, add http://YOUR_LAN_IP:5173/auth/callback
echo   to the xyyx app in the Casdoor console at http://localhost:8000

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

echo [3/4] Starting backend on http://%HOST%:8080 ...
start "xyyx-backend" /D "%ROOT%xyyx" cmd /k "set SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=%CASDOOR_URL%&& set SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=%CASDOOR_URL%/.well-known/jwks&& mvn spring-boot:run"

echo [4/4] Starting frontend at %FRONTEND_URL% ...
start "xyyx-frontend" /D "%ROOT%xyyx\xyyx-frontend" cmd /k "set VITE_CASDOOR_BASE_URL=%CASDOOR_URL%&& set VITE_CASDOOR_REDIRECT_URI=%FRONTEND_URL%/auth/callback&& set VITE_CASDOOR_CLIENT_ID=xyyx-web&& npm run dev -- --host 0.0.0.0 --strictPort"

echo.
echo Open: %FRONTEND_URL%/
echo Use that exact address. localhost and 127.0.0.1 are different origins to
echo the browser and to Casdoor - stick to the one printed above.
exit /b 0

:kill_port
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":%~1 .*LISTENING"') do (
  echo Stopping PID %%P on port %~1
  taskkill /F /PID %%P >nul 2>nul
)
exit /b 0
