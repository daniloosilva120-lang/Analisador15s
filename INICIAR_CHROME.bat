@echo off
set "CHROME=C:\Program Files\Google\Chrome\Application\chrome.exe"
if not exist "%CHROME%" set "CHROME=C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
if not exist "%CHROME%" (
 echo Chrome nao encontrado. Instale o Google Chrome.
 pause
 exit /b 1
)
start "" "%CHROME%" --remote-debugging-port=9222 --user-data-dir="%USERPROFILE%\ChromeAnalisador15s"
echo Chrome especial iniciado.
echo Entre manualmente na plataforma e abra GBP/USD OTC.
pause
