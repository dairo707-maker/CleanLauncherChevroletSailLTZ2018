@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem instalar.bat - instala Clean Launcher y aplica permisos y HOME
rem Uso: instalar.bat [ruta_del_apk] [IP:PUERTO]
rem   Sin ruta de APK busca: build\CleanLauncher.apk del repo,
rem   luego el CleanLauncher*.apk mas nuevo de %USERPROFILE%\Downloads.
rem   La IP del radio se toma de la variable RADIO o, si se pasa, del 2do argumento.
rem Requiere haber ejecutado antes tools\windows\respaldo.bat.
rem Cada cambio queda en C:\RadioBackup\cambios_aplicados.txt
rem ============================================================

set "APK_ARG=%~1"
if not "%~2"=="" set "RADIO=%~2"
if not defined RADIO set "RADIO=192.168.1.7:5555"
echo %RADIO% | findstr ":" >nul || set "RADIO=%RADIO%:5555"

where adb >nul 2>&1
if errorlevel 1 (
  echo ERROR: no se encontro adb en el PATH.
  echo Ver docs\TUTORIAL.md, seccion 3.
  exit /b 1
)

set "HAYBK="
for /f "delims=" %%d in ('dir /b /ad "C:\RadioBackup" 2^>nul') do set "HAYBK=%%d"
if not defined HAYBK (
  echo ERROR: no hay respaldo en C:\RadioBackup.
  echo Ejecuta primero tools\windows\respaldo.bat
  exit /b 1
)

for %%R in ("%~dp0..\..") do set "REPO=%%~fR"
set "APK="
if not "%APK_ARG%"=="" set "APK=%APK_ARG%"
if not defined APK if exist "%REPO%\build\CleanLauncher.apk" set "APK=%REPO%\build\CleanLauncher.apk"
if not defined APK for /f "delims=" %%a in ('dir /b /a-d /o-d "%USERPROFILE%\Downloads\CleanLauncher*.apk" 2^>nul') do if not defined APK set "APK=%USERPROFILE%\Downloads\%%a"
if not defined APK (
  echo ERROR: no encuentro el APK.
  echo Pasa la ruta: tools\windows\instalar.bat "C:\ruta\CleanLauncher.apk"
  exit /b 1
)
if not exist "%APK%" (
  echo ERROR: no existe el archivo: %APK%
  exit /b 1
)
echo APK a instalar: %APK%

set "ESTADO=ninguno"
for /f %%s in ('adb -s %RADIO% get-state 2^>nul') do set "ESTADO=%%s"
if not "%ESTADO%"=="device" (
  echo ERROR: el radio %RADIO% no esta conectado ^(estado: %ESTADO%^).
  echo Ejecuta primero tools\windows\conectar.bat
  exit /b 1
)

set "LOG=C:\RadioBackup\cambios_aplicados.txt"
set "SALIDA=%TEMP%\instalar_salida.txt"

echo.
echo [1/5] Instalando APK (adb install -r)...
adb -s %RADIO% install -r "%APK%" > "%SALIDA%" 2>&1
type "%SALIDA%"
findstr /c:"Success" "%SALIDA%" >nul
if errorlevel 1 goto instalacion_fallo
call :log "APK instalado con adb install -r: %APK%"

echo.
echo [2/5] Permiso de ubicacion (ACCESS_FINE_LOCATION)...
adb -s %RADIO% shell pm grant com.simple.carlauncher android.permission.ACCESS_FINE_LOCATION
call :log "pm grant ACCESS_FINE_LOCATION ejecutado (revisar salida)"

echo.
echo [3/5] Permiso de superponer ventanas (SYSTEM_ALERT_WINDOW)...
adb -s %RADIO% shell appops set com.simple.carlauncher SYSTEM_ALERT_WINDOW allow
call :log "appops SYSTEM_ALERT_WINDOW allow ejecutado (revisar salida)"

echo.
echo [4/5] Launcher por defecto (HOME)...
adb -s %RADIO% shell cmd package set-home-activity com.simple.carlauncher/.MainActivity > "%SALIDA%" 2>&1
type "%SALIDA%"
findstr /i /c:"Success" "%SALIDA%" >nul
if errorlevel 1 goto home_manual
call :log "HOME fijado a com.simple.carlauncher/.MainActivity"
goto whitelist

:home_manual
call :log "AVISO: set-home-activity no confirmado. Fijar HOME a mano."
echo AVISO: no se pudo fijar HOME por comando.
echo En el radio: pulsa Home, elige "Inicio" y luego "Siempre".

:whitelist
echo.
echo [5/5] Excluir del ahorro de energia (deviceidle)...
adb -s %RADIO% shell dumpsys deviceidle whitelist +com.simple.carlauncher
call :log "deviceidle whitelist +com.simple.carlauncher ejecutado (revisar salida)"

echo.
echo LISTO. Cambios registrados en C:\RadioBackup\cambios_aplicados.txt
echo Siguiente paso: docs\AGENTE_LOCAL.md, tarea T6 (checklist de pruebas).
exit /b 0

:instalacion_fallo
call :log "FALLO la instalacion de %APK%"
echo ERROR: la instalacion fallo. Revisa el mensaje de arriba.
exit /b 1

:log
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH:mm:ss"') do set "TS=%%t"
echo [%TS%] %~1>> "%LOG%"
echo [%TS%] %~1
goto :eof
