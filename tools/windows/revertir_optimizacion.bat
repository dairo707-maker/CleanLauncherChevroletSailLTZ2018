@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem revertir_optimizacion.bat - deshace optimizar.bat
rem Uso: revertir_optimizacion.bat [IP:PUERTO]
rem   Habilita de nuevo los paquetes de C:\RadioBackup\optimizacion_paquetes.txt
rem   y restaura los ajustes de C:\RadioBackup\optimizacion_settings.txt
rem   (valor "null" = borrar el ajuste).
rem Cada cambio queda en C:\RadioBackup\cambios_aplicados.txt
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

set "HAYBK="
for /f "delims=" %%d in ('dir /b /ad "C:\RadioBackup" 2^>nul') do set "HAYBK=%%d"
if not defined HAYBK (
  echo ERROR: no hay respaldo en C:\RadioBackup.
  echo Ejecuta primero tools\windows\respaldo.bat
  exit /b 1
)

set "LOG=C:\RadioBackup\cambios_aplicados.txt"
set "PAQUETES_FILE=C:\RadioBackup\optimizacion_paquetes.txt"
set "SETTINGS_FILE=C:\RadioBackup\optimizacion_settings.txt"
set "SALIDA=%TEMP%\revertir_optimizacion_salida.txt"

if not exist "%SETTINGS_FILE%" (
  echo ERROR: no hay una optimizacion registrada ^(falta %SETTINGS_FILE%^).
  echo Nada que revertir.
  exit /b 1
)

set "ESTADO=ninguno"
for /f %%s in ('adb -s %RADIO% get-state 2^>nul') do set "ESTADO=%%s"
if not "%ESTADO%"=="device" (
  echo ERROR: el radio %RADIO% no esta conectado ^(estado: %ESTADO%^).
  echo Ejecuta primero tools\windows\conectar.bat
  exit /b 1
)

echo.
echo PLAN DE REVERSION para %RADIO%:
echo   1. Habilitar de nuevo los paquetes de:
if exist "%PAQUETES_FILE%" (
  for /f "usebackq delims=" %%p in ("%PAQUETES_FILE%") do echo      %%p
) else (
  echo      ^(ninguno registrado^)
)
echo   2. Restaurar los ajustes de:
for /f "usebackq tokens=1,* delims==" %%a in ("%SETTINGS_FILE%") do echo      %%a = %%b
echo.
choice /c SN /m "Continuar con la reversion"
if errorlevel 2 (
  echo Cancelado. No se hizo ningun cambio.
  exit /b 0
)

call :log "INICIO reversion de optimizacion del radio %RADIO%"

echo.
echo [1/2] Habilitando paquetes...
if exist "%PAQUETES_FILE%" (
  for /f "usebackq delims=" %%p in ("%PAQUETES_FILE%") do call :habilitar %%p
)

echo.
echo [2/2] Restaurando ajustes globales...
for /f "usebackq tokens=1,* delims==" %%a in ("%SETTINGS_FILE%") do call :restaurar "%%a" "%%b"

if exist "%PAQUETES_FILE%" move /y "%PAQUETES_FILE%" "C:\RadioBackup\optimizacion_paquetes.revertido.txt" >nul
move /y "%SETTINGS_FILE%" "C:\RadioBackup\optimizacion_settings.revertido.txt" >nul

echo.
echo LISTO. Revertida la optimizacion. Revisa C:\RadioBackup\cambios_aplicados.txt
echo Reinicia el radio ^(apagar 30 s y encender^) para aplicar los cambios.
exit /b 0

:habilitar
set "PKG=%~1"
adb -s %RADIO% shell pm enable %PKG% > "%SALIDA%" 2>&1
type "%SALIDA%"
findstr /i /c:"enabled" "%SALIDA%" >nul
if errorlevel 1 (
  echo   AVISO: no se confirmo pm enable de %PKG%.
  call :log "AVISO: no se confirmo pm enable de %PKG%"
  goto :eof
)
call :log "pm enable %PKG% (confirmado)"
goto :eof

:restaurar
set "KEY=%~1"
set "VAL=%~2"
if "%VAL%"=="null" (
  adb -s %RADIO% shell settings delete global %KEY%
  call :log "settings delete global %KEY% (valor previo: null)"
) else (
  adb -s %RADIO% shell settings put global %KEY% %VAL%
  call :log "settings put global %KEY% %VAL%"
)
goto :eof

:log
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH:mm:ss"') do set "TS=%%t"
echo [%TS%] %~1>> "%LOG%"
echo [%TS%] %~1
goto :eof
