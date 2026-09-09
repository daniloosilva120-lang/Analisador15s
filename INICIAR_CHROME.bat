@echo off

set "CHROME=C:\Program Files\Google\Chrome\Application\chrome.exe"

if not exist "%CHROME%" (
    set "CHROME=C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
)

if not exist "%CHROME%" (
    echo Chrome nao encontrado.
    echo Instale o Google Chrome.
    pause
    exit /b 1
)

set "PERFIL=%USERPROFILE%\ChromeAnalisador15s"

start "" "%CHROME%" ^
 --remote-debugging-port=9222 ^
 --user-data-dir="%PERFIL%" ^
 --no-first-run ^
 --no-default-browser-check

echo.
echo Chrome especial iniciado.
echo.
echo Entre manualmente na plataforma.
echo Aguarde carregar totalmente antes de iniciar o analisador.
echo.
pause