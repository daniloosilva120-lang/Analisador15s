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
REM 1. LOCALIZAR O GIT
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
    echo.
    echo Instale o Git e tente novamente.
    goto :ERRO
)

echo Git encontrado:
echo %GIT_EXE%
echo.

REM ============================================================
REM 2. VALIDAR A PASTA DO PROJETO
REM ============================================================
if not exist ".git" (
    echo ERRO: a pasta .git nao foi encontrada.
    echo.
    echo Este BAT deve ficar na pasta principal do projeto
    echo que foi clonada ou configurada com o Git.
    goto :ERRO
)

if not exist "pom.xml" (
    echo ERRO: pom.xml nao foi encontrado.
    echo.
    echo Coloque este BAT na pasta principal do Analisador15s.
    goto :ERRO
)

"%GIT_EXE%" rev-parse --is-inside-work-tree >nul 2>&1
if errorlevel 1 (
    echo ERRO: esta pasta nao e um repositorio Git valido.
    goto :ERRO
)

REM ============================================================
REM 3. GARANTIR O .GITIGNORE
REM ============================================================
if not exist ".gitignore" type nul > ".gitignore"

REM Corrige uma linha antiga que pode ter ficado grudada.
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p='.gitignore'; if (Test-Path $p) { $c=Get-Content $p; $c=$c -replace '^Thumbs\.db\.maven-repository/$','Thumbs.db'; Set-Content -Encoding UTF8 $p $c }" >nul 2>&1

call :GARANTIR_IGNORE "target/"
call :GARANTIR_IGNORE "out/"
call :GARANTIR_IGNORE "/.maven-repository/"
call :GARANTIR_IGNORE ".idea/"
call :GARANTIR_IGNORE "*.iml"

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

REM Verifica se existe origin
"%GIT_EXE%" remote get-url origin >nul 2>&1
if errorlevel 1 (
    echo ERRO: o repositorio remoto "origin" nao esta configurado.
    echo.
    echo Configure o GitHub antes de usar este BAT.
    goto :ERRO
)

REM ============================================================
REM 5. ADICIONAR E SALVAR ALTERACOES LOCAIS
REM ============================================================
echo Adicionando alteracoes...
"%GIT_EXE%" add .
if errorlevel 1 goto :ERRO

"%GIT_EXE%" diff --cached --quiet
if errorlevel 1 (
    echo Criando commit...
    "%GIT_EXE%" commit -m "Atualizacao do projeto"
    if errorlevel 1 (
        echo.
        echo ERRO ao criar o commit.
        goto :ERRO
    )
) else (
    echo Nenhuma alteracao nova para criar commit.
)

REM ============================================================
REM 6. TRAZER ALTERACOES DO GITHUB
REM ============================================================
echo.
echo Sincronizando com origin/%BRANCH%...
"%GIT_EXE%" pull --rebase origin "%BRANCH%"
if errorlevel 1 (
    echo.
    echo ERRO durante o git pull --rebase.
    echo.
    echo Pode existir conflito entre alteracoes feitas
    echo neste computador e em outro computador.
    echo.
    echo Seus arquivos locais NAO foram apagados.
    goto :ERRO
)

REM ============================================================
REM 7. ENVIAR PARA O GITHUB
REM ============================================================
echo.
echo Enviando projeto para o GitHub...
"%GIT_EXE%" push -u origin "%BRANCH%"
if errorlevel 1 (
    echo.
    echo ERRO durante o git push.
    echo.
    echo Pode ser necessario fazer login no GitHub.
    goto :ERRO
)

echo.
echo ============================================
echo PROJETO SALVO NO GITHUB COM SUCESSO
echo ============================================
echo.
echo Pressione qualquer tecla para sair...
pause >nul
exit /b 0

:GARANTIR_IGNORE
findstr /x /l /c:"%~1" ".gitignore" >nul 2>&1
if errorlevel 1 echo %~1>>".gitignore"
exit /b 0

:ERRO
echo.
echo ============================================
echo NAO FOI POSSIVEL SALVAR NO GITHUB
echo ============================================
echo.
echo Pressione qualquer tecla para sair...
pause >nul
exit /b 1
