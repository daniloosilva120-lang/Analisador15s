@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title Analisador15s - Salvar no GitHub

cd /d "%~dp0"

echo ============================================
echo      ANALISADOR15s - SALVAR NO GITHUB
echo ============================================
echo.

REM ============================================================
REM 1. LOCALIZAR GIT
REM ============================================================
set "GIT_EXE="

for /f "delims=" %%I in ('where git.exe 2^>nul') do (
    if not defined GIT_EXE set "GIT_EXE=%%I"
)

if not defined GIT_EXE if exist "%ProgramFiles%\Git\cmd\git.exe" set "GIT_EXE=%ProgramFiles%\Git\cmd\git.exe"
if not defined GIT_EXE if exist "%ProgramFiles(x86)%\Git\cmd\git.exe" set "GIT_EXE=%ProgramFiles(x86)%\Git\cmd\git.exe"
if not defined GIT_EXE if exist "%LOCALAPPDATA%\Programs\Git\cmd\git.exe" set "GIT_EXE=%LOCALAPPDATA%\Programs\Git\cmd\git.exe"

if not defined GIT_EXE (
    echo ERRO: Git nao foi encontrado neste computador.
    goto :erro
)

echo Git encontrado:
echo %GIT_EXE%
echo.

REM ============================================================
REM 2. VALIDAR REPOSITORIO
REM ============================================================
if not exist ".git" (
    echo ERRO: a pasta .git nao foi encontrada.
    echo.
    echo Este BAT precisa ficar na pasta principal do projeto,
    echo junto com pom.xml, src e .git.
    goto :erro
)

if not exist "pom.xml" (
    echo ERRO: pom.xml nao foi encontrado.
    echo Coloque este BAT na pasta principal do projeto.
    goto :erro
)

REM ============================================================
REM 3. GARANTIR .GITIGNORE
REM ============================================================
if not exist ".gitignore" type nul > ".gitignore"

call :ignorar "target/"
call :ignorar "out/"
call :ignorar ".maven-repository/"
call :ignorar ".idea/"
call :ignorar "*.iml"

REM ============================================================
REM 4. MOSTRAR REPOSITORIO E BRANCH
REM ============================================================
echo Repositorio remoto:
"%GIT_EXE%" remote -v
echo.

set "BRANCH="
for /f "delims=" %%B in ('"%GIT_EXE%" branch --show-current 2^>nul') do set "BRANCH=%%B"
if not defined BRANCH set "BRANCH=master"

echo Branch atual: %BRANCH%
echo.

REM ============================================================
REM 5. SALVAR ALTERACOES LOCAIS
REM ============================================================
echo Adicionando alteracoes...
"%GIT_EXE%" add .
if errorlevel 1 goto :erro

"%GIT_EXE%" diff --cached --quiet
if not errorlevel 1 (
    echo Nenhuma alteracao nova para criar commit.
) else (
    echo Criando commit...
    "%GIT_EXE%" commit -m "Atualizacao do projeto"
    if errorlevel 1 goto :erro
)

REM ============================================================
REM 6. SINCRONIZAR COM O GITHUB
REM Primeiro traz eventuais commits feitos em outro computador,
REM depois envia a versao atual.
REM ============================================================
echo.
echo Sincronizando com origin/%BRANCH%...
"%GIT_EXE%" pull --rebase origin "%BRANCH%"
if errorlevel 1 (
    echo.
    echo ERRO durante o git pull --rebase.
    echo Seus arquivos locais NAO foram apagados.
    echo Envie uma foto desta tela para eu corrigir.
    goto :erro
)

echo.
echo Enviando projeto para o GitHub...
"%GIT_EXE%" push -u origin "%BRANCH%"
if errorlevel 1 (
    echo.
    echo ERRO durante o git push.
    echo Pode ser necessario fazer login no GitHub.
    goto :erro
)

echo.
echo ============================================
echo PROJETO SALVO NO GITHUB COM SUCESSO
echo ============================================
echo.
echo Pressione qualquer tecla para sair...
pause >nul
exit /b 0

:ignorar
findstr /x /l /c:"%~1" ".gitignore" >nul 2>&1
if errorlevel 1 echo %~1>>".gitignore"
exit /b 0

:erro
echo.
echo ============================================
echo NAO FOI POSSIVEL SALVAR NO GITHUB
echo ============================================
echo.
echo Pressione qualquer tecla para sair...
pause >nul
exit /b 1
