@echo off
setlocal EnableDelayedExpansion

title Atualizar projeto no GitHub
color 0A

echo ============================================
echo        ATUALIZAR PROJETO NO GITHUB
echo ============================================
echo.

cd /d "%~dp0"

echo Pasta do projeto:
echo %cd%
echo.

echo Verificando repositorio...
git rev-parse --is-inside-work-tree >nul 2>&1

if errorlevel 1 (
    echo.
    echo ERRO: Esta pasta nao e um repositorio Git.
    echo.
    pause
    exit /b
)

echo.
echo Verificando alteracoes locais...

git add .

git diff --cached --quiet

if errorlevel 1 (
    echo.
    set /p mensagem=Digite a descricao da atualizacao: 

    if "!mensagem!"=="" set "mensagem=Atualizacao do projeto"

    echo.
    echo Salvando alteracoes locais...
    git commit -m "!mensagem!"

    if errorlevel 1 (
        echo.
        echo ERRO AO CRIAR COMMIT.
        pause
        exit /b
    )
) else (
    echo Nenhuma alteracao local para salvar.
)

echo.
echo Baixando atualizacoes do GitHub...
git pull --rebase origin master

if errorlevel 1 (
    echo.
    echo ============================================
    echo ERRO AO SINCRONIZAR COM O GITHUB
    echo ============================================
    echo.
    echo Pode existir conflito entre arquivos.
    echo Nao foi feito o push.
    echo.
    pause
    exit /b
)

echo.
echo Enviando projeto para o GitHub...
git push origin master

if errorlevel 1 (
    echo.
    echo ============================================
    echo ERRO AO ENVIAR PARA O GITHUB
    echo ============================================
    echo.
    pause
    exit /b
)

echo.
echo ============================================
echo PROJETO ATUALIZADO COM SUCESSO!
echo ============================================
echo.

pause