@echo off
chcp 65001 >nul
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
 echo Java nao encontrado. Instale o JDK 26.
 pause
 exit /b 1
)
if not exist out mkdir out
echo Compilando...
javac -encoding UTF-8 -d out src\main\java\com\d4niboy\analisador\*.java
if errorlevel 1 (
 echo ERRO NA COMPILACAO.
 pause
 exit /b 1
)
echo Iniciando...
java -cp out com.d4niboy.analisador.Main
pause
