@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem respaldo.bat - copia de seguridad del radio (SOLO LECTURA en el radio)
rem Uso: respaldo.bat [IP:PUERTO]
rem Crea C:\RadioBackup\AAAA-MM-DD_HHMM con textos, APKs de fabrica y SHA-256.
rem ============================================================

if not "%~1"=="" set "RADIO=%~1"
if not defined RADIO set "RADIO=192.168.1.7:5555"
echo %RADIO% | findstr ":" >nul || set "RADIO=%RADIO%:5555"

where adb >nul 2>&1
if errorlevel 1 (
  echo ERROR: no se encontro adb en el PATH.
  echo Ver docs\TUTORIAL.md, seccion 3.
  exit /b 1
)

set "ESTADO=ninguno"
for /f %%s in ('adb -s %RADIO% get-state 2^>nul') do set "ESTADO=%%s"
if not "%ESTADO%"=="device" (
  echo ERROR: el radio %RADIO% no esta conectado ^(estado: %ESTADO%^).
  echo Ejecuta primero tools\windows\conectar.bat
  exit /b 1
)

for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HHmm"') do set "STAMP=%%t"
if not defined STAMP (
  echo ERROR: no se pudo generar la fecha con PowerShell.
  exit /b 1
)

set "DEST=C:\RadioBackup\%STAMP%"
if exist "%DEST%" (
  echo ERROR: la carpeta %DEST% ya existe. Espera un minuto y vuelve a ejecutar.
  exit /b 1
)
mkdir "%DEST%\apk"
if errorlevel 1 (
  echo ERROR: no se pudo crear %DEST%
  exit /b 1
)

echo.
echo [1/5] Propiedades del sistema...
adb -s %RADIO% shell getprop > "%DEST%\getprop.txt"

echo [2/5] Listas de paquetes y launcher...
adb -s %RADIO% shell pm list packages -f > "%DEST%\paquetes_todos.txt"
adb -s %RADIO% shell pm list packages -d > "%DEST%\paquetes_deshabilitados.txt"
adb -s %RADIO% shell pm list packages -e > "%DEST%\paquetes_habilitados.txt"
adb -s %RADIO% shell pm list packages -3 > "%DEST%\paquetes_terceros.txt"
adb -s %RADIO% shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "%DEST%\launcher_por_defecto.txt"

echo [3/5] Ajustes del sistema...
adb -s %RADIO% shell settings list global > "%DEST%\settings_global.txt"
adb -s %RADIO% shell settings list system > "%DEST%\settings_system.txt"
adb -s %RADIO% shell settings list secure > "%DEST%\settings_secure.txt"

echo [4/5] Copiando APKs de fabrica relacionados (solo lectura)...
for /f "usebackq tokens=1* delims==" %%a in ("%DEST%\paquetes_todos.txt") do call :pull "%%a" "%%b"

echo [5/5] Calculando SHA-256 y escribiendo README_RESPALDO.txt...
set "README=%DEST%\README_RESPALDO.txt"
echo Respaldo del radio %RADIO%> "%README%"
echo Fecha: %STAMP%>> "%README%"
echo Carpeta: %DEST%>> "%README%"
echo.>> "%README%"
echo SHA-256 de cada archivo:>> "%README%"
for %%f in (%DEST%\*.txt) do if /i not "%%~nxf"=="README_RESPALDO.txt" call :hash "%%~ff"
for %%f in (%DEST%\apk\*.apk) do call :hash "%%~ff"

for %%R in ("%~dp0..\..") do set "REPO=%%~fR"
set "REPO_BK=%REPO%\backup\xyauto"
if not exist "%REPO_BK%" mkdir "%REPO_BK%"
copy /y "%DEST%\*.txt" "%REPO_BK%" >nul

echo.
echo LISTO. Respaldo guardado en:
echo %DEST%
echo Copia de los textos dentro del repo: %REPO_BK%
echo.
echo Siguiente paso: pide al humano guardar "Export the config" en USB si aun no lo hizo.
echo Luego ejecuta tools\windows\diagnostico.bat o tools\windows\instalar.bat
exit /b 0

:pull
set "RUTA=%~1"
set "RUTA=!RUTA:package:=!"
set "PKG=%~2"
echo !PKG! | findstr /i /r "launcher radio bt bluetooth car xyauto autochips atc mediatek mtk can mcu camera setting music video media avm" >nul
if errorlevel 1 goto :eof
echo   APK: !PKG!
adb -s %RADIO% pull "!RUTA!" "%DEST%\apk\!PKG!.apk" >nul
if errorlevel 1 echo   AVISO: no se pudo copiar !PKG!
goto :eof

:hash
set "HASHVAL="
for /f "skip=1 tokens=*" %%h in ('certutil -hashfile "%~1" SHA256') do if not defined HASHVAL set "HASHVAL=%%h"
echo %~nx1: !HASHVAL!>> "%README%"
goto :eof
