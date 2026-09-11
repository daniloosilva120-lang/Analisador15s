@echo off
setlocal EnableExtensions EnableDelayedExpansion

title Chrome Especial - Analisador15s

echo ==========================================
echo   INICIANDO CHROME PARA O ANALISADOR 15s
echo ==========================================
echo.

REM ============================================================
REM CONFIGURACOES PORTATEIS
REM ============================================================

set "PORTA=9222"
set "URL=https://qxbroker.com/pt/demo-trade"

REM Perfil fica dentro do usuario atual.
REM Exemplo:
REM C:\Users\Engenharia\AppData\Local\Analisador15s\ChromeDebug
REM C:\Users\Pichau\AppData\Local\Analisador15s\ChromeDebug

set "PERFIL=%LOCALAPPDATA%\Analisador15s\ChromeDebug"

REM ============================================================
REM PROCURA O GOOGLE CHROME
REM ============================================================

set "CHROME="

if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" (
    set "CHROME=%ProgramFiles%\Google\Chrome\Application\chrome.exe"
)

if not defined CHROME (
    if exist "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" (
        set "CHROME=%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"
    )
)

if not defined CHROME (
    if exist "%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe" (
        set "CHROME=%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe"
    )
)

REM Tenta encontrar pelo registro do Windows
if not defined CHROME (
    for /f "tokens=2,*" %%A in ('reg query "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\chrome.exe" /ve 2^>nul ^| find "REG_SZ"') do (
        set "CHROME=%%B"
    )
)

if not defined CHROME (
    for /f "tokens=2,*" %%A in ('reg query "HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\chrome.exe" /ve 2^>nul ^| find "REG_SZ"') do (
        set "CHROME=%%B"
    )
)

REM ============================================================
REM VERIFICA SE ENCONTROU
REM ============================================================

if not defined CHROME (
    echo ERRO: Google Chrome nao foi encontrado.
    echo.
    echo Instale o Google Chrome e tente novamente.
    echo.
    pause
    exit /b 1
)

if not exist "%CHROME%" (
    echo ERRO: caminho do Chrome invalido:
    echo %CHROME%
    echo.
    pause
    exit /b 1
)

echo Chrome encontrado:
echo %CHROME%
echo.

echo Usuario atual:
echo %USERNAME%
echo.

echo Perfil especial:
echo %PERFIL%
echo.

REM ============================================================
REM CRIA A PASTA DO PERFIL
REM ============================================================

if not exist "%PERFIL%" (
    mkdir "%PERFIL%" >nul 2>&1
)

REM ============================================================
REM VERIFICA SE A PORTA 9222 JA ESTA FUNCIONANDO
REM ============================================================

curl -s --max-time 2 "http://127.0.0.1:%PORTA%/json/version" >nul 2>&1

if !errorlevel! EQU 0 (
    echo Chrome especial ja esta funcionando na porta %PORTA%.
    echo.

    start "" "%CHROME%" ^
        --remote-debugging-port=%PORTA% ^
        --user-data-dir="%PERFIL%" ^
        --new-window ^
        "%URL%"

    goto :SUCESSO
)

REM ============================================================
REM INICIA O CHROME ESPECIAL
REM ============================================================

echo Iniciando Chrome especial...
echo.

start "" "%CHROME%" ^
    --remote-debugging-port=%PORTA% ^
    --user-data-dir="%PERFIL%" ^
    --no-first-run ^
    --no-default-browser-check ^
    --new-window ^
    "%URL%"

REM ============================================================
REM AGUARDA A PORTA FICAR DISPONIVEL
REM ============================================================

echo Aguardando porta %PORTA%...

set /a TENTATIVA=0

:AGUARDAR

set /a TENTATIVA+=1

curl -s --max-time 2 "http://127.0.0.1:%PORTA%/json/version" >nul 2>&1

if !errorlevel! EQU 0 (
    goto :SUCESSO
)

if !TENTATIVA! GEQ 15 (
    goto :ERRO_PORTA
)

ping 127.0.0.1 -n 2 >nul

goto :AGUARDAR


:SUCESSO

echo.
echo ==========================================
echo   CHROME ESPECIAL PRONTO
echo ==========================================
echo.
echo Porta: %PORTA%
echo Perfil: %PERFIL%
echo Usuario: %USERNAME%
echo.
echo O analisador ja pode ser iniciado.
echo.

curl -s "http://127.0.0.1:%PORTA%/json/version"

echo.
echo.

exit /b 0


:ERRO_PORTA

echo.
echo ==========================================
echo   ERRO AO INICIAR
echo ==========================================
echo.
echo O Chrome abriu, mas a porta %PORTA%
echo nao respondeu.
echo.
echo Chrome:
echo %CHROME%
echo.
echo Perfil:
echo %PERFIL%
echo.
pause

exit /b 1
