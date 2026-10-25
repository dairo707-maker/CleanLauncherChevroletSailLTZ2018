@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem optimizar.bat - optimizacion opcional del radio (libera RAM y reduce animaciones)
rem Uso: optimizar.bat [IP:PUERTO]
rem   Deshabilita paquetes de terceros no usados (solo si estan instalados),
rem   baja las animaciones a 0.5, deja el Wi-Fi activo y limpia caches.
rem   Guarda los valores previos para poder revertir con revertir_optimizacion.bat.
rem Requiere haber ejecutado antes tools\windows\respaldo.bat.
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
set "SALIDA=%TEMP%\optimizar_salida.txt"

if exist "%SETTINGS_FILE%" (
  echo ERROR: ya hay una optimizacion aplicada ^(existe %SETTINGS_FILE%^).
  echo Ejecuta primero tools\windows\revertir_optimizacion.bat
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
echo PLAN DE OPTIMIZACION para %RADIO%:
echo   1. Deshabilitar paquetes no usados ^(solo los instalados^):
echo      com.directv.dtvlatam, com.apple.android.music, tunein.player,
echo      com.google.android.apps.photos, com.tima.carnet.vt
echo   2. Animaciones de ventanas, transicion y duracion a 0.5.
echo   3. Wi-Fi: wifi_sleep_policy 2 ^(el Wi-Fi no se apaga al dormir^).
echo   4. Limpiar caches de apps ^(pm trim-caches 999G^).
echo   5. Cerrar procesos en segundo plano ^(am kill-all^).
echo.
echo Nota: deshabilitar NO libera espacio en /system ^(93%% lleno^). Solo evita que los paquetes arranquen.
echo Los valores previos se guardan en C:\RadioBackup\optimizacion_*.txt para revertir.
echo.
choice /c SN /m "Continuar con la optimizacion"
if errorlevel 2 (
  echo Cancelado. No se hizo ningun cambio.
  exit /b 0
)

if exist "%PAQUETES_FILE%" del "%PAQUETES_FILE%"
call :log "INICIO optimizacion del radio %RADIO%"

echo.
echo [1/5] Deshabilitando paquetes no usados...
call :deshabilitar com.directv.dtvlatam
call :deshabilitar com.apple.android.music
call :deshabilitar tunein.player
call :deshabilitar com.google.android.apps.photos
call :deshabilitar com.tima.carnet.vt

echo.
echo [2/5] Reduciendo animaciones a 0.5 ^(guardando valores previos^)...
call :ajustar window_animation_scale 0.5
call :ajustar transition_animation_scale 0.5
call :ajustar animator_duration_scale 0.5

echo.
echo [3/5] Wi-Fi activo durante el sueno ^(wifi_sleep_policy 2^)...
call :ajustar wifi_sleep_policy 2

echo.
echo [4/5] Limpiando caches de apps...
adb -s %RADIO% shell pm trim-caches 999G
call :log "pm trim-caches 999G ejecutado (revisar salida)"

echo.
echo [5/5] Cerrando procesos en segundo plano...
adb -s %RADIO% shell am kill-all
call :log "am kill-all ejecutado (revisar salida)"

echo.
echo LISTO. Optimizacion aplicada. Cambios registrados en C:\RadioBackup\cambios_aplicados.txt
echo Paquetes deshabilitados: C:\RadioBackup\optimizacion_paquetes.txt
echo Valores previos:         C:\RadioBackup\optimizacion_settings.txt
echo.
echo IMPORTANTE: reinicia el radio ^(apagar 30 s y encender^) para aplicar los cambios.
echo Para deshacer: tools\windows\revertir_optimizacion.bat
exit /b 0

:deshabilitar
set "PKG=%~1"
adb -s %RADIO% shell pm list packages %PKG% | findstr /x /c:"package:%PKG%" >nul
if errorlevel 1 (
  echo   %PKG%: no instalado, se omite.
  call :log "omitido %PKG% (no instalado)"
  goto :eof
)
adb -s %RADIO% shell pm disable-user --user 0 %PKG% > "%SALIDA%" 2>&1
type "%SALIDA%"
findstr /i /c:"disabled" "%SALIDA%" >nul
if errorlevel 1 (
  echo   AVISO: no se confirmo la desactivacion de %PKG%.
  call :log "AVISO: no se confirmo pm disable-user de %PKG%"
  goto :eof
)
>> "%PAQUETES_FILE%" echo %PKG%
call :log "pm disable-user --user 0 %PKG% (confirmado)"
goto :eof

:ajustar
set "KEY=%~1"
set "NUEVO=%~2"
set "VAL="
for /f "delims=" %%v in ('adb -s %RADIO% shell settings get global %KEY% 2^>nul') do set "VAL=%%v"
if not defined VAL set "VAL=null"
>> "%SETTINGS_FILE%" echo %KEY%=!VAL!
adb -s %RADIO% shell settings put global %KEY% %NUEVO%
call :log "settings global %KEY%: !VAL! -> %NUEVO%"
goto :eof

:log
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH:mm:ss"') do set "TS=%%t"
echo [%TS%] %~1>> "%LOG%"
echo [%TS%] %~1
goto :eof
