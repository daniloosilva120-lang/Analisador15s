@echo off
title Chrome Especial - Analisador15s

set "CHROME=C:\Program Files\Google\Chrome\Application\chrome.exe"

if not exist "%CHROME%" (
    set "CHROME=C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
)

if not exist "%CHROME%" (
    echo Chrome nao encontrado.
    exit /b 1
)

echo ==========================================
echo   INICIANDO CHROME PARA O ANALISADOR 15s
echo ==========================================
echo.

start "" "%CHROME%" ^
 --remote-debugging-port=9222 ^
 --user-data-dir="C:\ChromeDebug2" ^
 --no-first-run ^
 --no-default-browser-check ^
 --new-window ^
 "https://qxbroker.com/pt/demo-trade"

echo Chrome especial iniciado na porta 9222.
echo Perfil: C:\ChromeDebug2
echo Seu Chrome normal continuara aberto.

exit /b 0