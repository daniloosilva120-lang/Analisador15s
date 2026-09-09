@echo off
title Atualizar projeto no GitHub

cd /d "%~dp0"

echo ==========================================
echo       ATUALIZAR PROJETO NO GITHUB
echo ==========================================
echo.

echo Pasta do projeto:
cd
echo.

echo Adicionando alteracoes...
git add .

echo.
set /p MENSAGEM=Digite a descricao da atualizacao: 

if "%MENSAGEM%"=="" (
    set "MENSAGEM=Atualizacao do projeto"
)

echo.
echo Criando commit...
git commit -m "%MENSAGEM%"

echo.
echo Atualizando informacoes do GitHub...
git fetch origin

echo.
echo Enviando para o GitHub...
git push origin master

if errorlevel 1 (
    echo.
    echo ==========================================
    echo ERRO AO ATUALIZAR O GITHUB
    echo ==========================================
    echo Copie o erro acima e me envie.
    echo.
    pause
    exit /b 1
)

echo.
echo ==========================================
echo       GITHUB ATUALIZADO COM SUCESSO
echo ==========================================
echo.

git log --oneline -3

echo.
pause