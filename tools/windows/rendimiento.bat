@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem rendimiento.bat - rendimiento del radio: precompilacion, zram, GPS asistido, WebView
rem Uso: rendimiento.bat aplicar  [IP:PUERTO]
rem      rendimiento.bat revertir [IP:PUERTO]
rem      rendimiento.bat estado   [IP:PUERTO]
rem Guarda cada valor previo en C:\RadioBackup\rendimiento_previo.txt para revertir exacto.
rem La parte de memoria escribe /system/etc/init/cleanlauncher_mem.rc: exige respaldo
rem C:\RadioBackup\particiones\* (T14). No cambia el minfree del LMK.
rem ============================================================

set "MODO=%~1"
if /i not "%MODO%"=="aplicar" if /i not "%MODO%"=="revertir" if /i not "%MODO%"=="estado" (
  echo Uso: rendimiento.bat aplicar ^| revertir ^| estado [IP:PUERTO]
  exit /b 1
)
if not "%~2"=="" set "RADIO=%~2"
if not defined RADIO set "RADIO=192.168.1.7:5555"
echo %RADIO% | findstr ":" >nul || set "RADIO=%RADIO%:5555"

where adb >nul 2>&1
if errorlevel 1 (
  echo ERROR: no se encontro adb en el PATH.
  exit /b 1
)

set "BK=C:\RadioBackup"
set "PREVIO=%BK%\rendimiento_previo.txt"
set "LOG=%BK%\cambios_aplicados.txt"
set "RCREMOTO=/system/etc/init/cleanlauncher_mem.rc"
set "RCLOCAL=%BK%\cleanlauncher_mem.rc"
set "TMP1=%TEMP%\rendimiento_salida.txt"
set "PAQUETES=com.simple.carlauncher com.waze com.google.android.apps.maps com.google.android.apps.youtube.music com.spotify.music com.android.fmradio com.autochips.bluetooth"
if not exist "%BK%" (
  echo ERROR: no existe %BK%. Ejecuta primero respaldo.bat.
  exit /b 1
)

echo [1/6] Activando root en ADB...
adb -s %RADIO% root >nul 2>&1
timeout /t 4 >nul
adb connect %RADIO% >nul 2>&1
timeout /t 2 >nul
adb -s %RADIO% shell id | findstr /c:"uid=0" >nul
if errorlevel 1 (
  if /i "%MODO%"=="estado" (
    echo AVISO: sin root; algunos datos pueden no verse.
  ) else (
    echo ERROR: no se obtuvo root. No se cambio nada.
    exit /b 1
  )
)

if /i "%MODO%"=="estado" goto estado
if /i "%MODO%"=="revertir" goto revertir

rem ---------------------------- APLICAR ----------------------------
if exist "%PREVIO%" (
  echo ERROR: ya hay cambios aplicados ^(existe %PREVIO%^).
  echo Ejecuta primero: rendimiento.bat revertir
  exit /b 1
)

rem Estado de zram (solo lectura) para decidir si hay que escribir /system
set "ZEXISTE="
for /f %%z in ('adb -s %RADIO% shell "[ -e /sys/block/zram0/disksize ] && echo si"') do set "ZEXISTE=%%z"
set "ZACTIVO="
adb -s %RADIO% shell cat /proc/swaps | findstr /i "zram" >nul && set "ZACTIVO=si"
set "HAYPART="
for /f "delims=" %%d in ('dir /b /ad "%BK%\particiones" 2^>nul') do set "HAYPART=%%d"
if not "%ZEXISTE%"=="si" echo AVISO: el kernel no tiene /sys/block/zram0; se omite la memoria.
if "%ZEXISTE%"=="si" if not defined ZACTIVO if not defined HAYPART (
  echo ERROR: aplicar escribe en /system y no hay respaldo de particiones ^(%BK%\particiones\*^).
  echo Haz primero el respaldo T14. No se cambio nada.
  exit /b 1
)

echo.
echo PLAN para %RADIO%:
echo   1. Precompilar con -m speed ^(solo apps instaladas^): %PAQUETES%
echo   2. Memoria: zram 512 MB + swappiness 100 ^(solo si zram no esta activo^)
if defined ZACTIVO echo      zram YA esta activo: se omite.
echo   3. GPS asistido: assisted_gps_enabled 1, location_mode 3, wifi_scan_always_enabled 1
echo   4. WebView: com.google.android.webview si esta instalado y habilitado
echo Valores previos en %PREVIO%
echo.
choice /c SN /m "Continuar"
if errorlevel 2 (
  echo Cancelado. No se cambio nada.
  exit /b 0
)

call :log "INICIO rendimiento aplicar en %RADIO%"

echo.
echo [2/6] Precompilando apps...
set "FILTRO="
for /f "delims=" %%v in ('adb -s %RADIO% shell getprop pm.dexopt.install 2^>nul') do set "FILTRO=%%v"
if not defined FILTRO set "FILTRO=quicken"
>> "%PREVIO%" echo P^|dexopt^|!FILTRO!
call :log "filtro dexopt por defecto del dispositivo: !FILTRO!"
for %%p in (%PAQUETES%) do call :compilar %%p

echo.
echo [3/6] Memoria ^(zram^)...
if not "%ZEXISTE%"=="si" (
  echo Sin zram en el kernel: omitido.
  call :log "zram omitido (no existe /sys/block/zram0)"
  goto gps_previo
)
if defined ZACTIVO (
  echo zram ya esta activo: solo se informa, no se toca.
  call :log "zram ya activo, omitido"
  goto gps_previo
)
set "SWP=60"
for /f %%v in ('adb -s %RADIO% shell cat /proc/sys/vm/swappiness 2^>nul') do set "SWP=%%v"
>> "%PREVIO%" echo M^|swappiness^|!SWP!
set "ALGO=lzo"
adb -s %RADIO% shell cat /sys/block/zram0/comp_algorithm 2>nul | findstr /c:"lz4" >nul && set "ALGO=lz4"
set "TIENEALGO="
adb -s %RADIO% shell cat /sys/block/zram0/comp_algorithm 2>nul | findstr /r "[a-z]" >nul && set "TIENEALGO=si"
echo Algoritmo: !ALGO!
rem Se arma el .rc con LF (init no tolera CR)
> "%RCLOCAL%" echo # CleanLauncher: zram 512 MB al terminar el arranque
>> "%RCLOCAL%" echo on property:sys.boot_completed=1
if defined TIENEALGO (>> "%RCLOCAL%" echo     write /sys/block/zram0/comp_algorithm !ALGO!)
>> "%RCLOCAL%" echo     write /sys/block/zram0/disksize 536870912
>> "%RCLOCAL%" echo     exec - root root -- /system/bin/mkswap /dev/block/zram0
>> "%RCLOCAL%" echo     exec - root root -- /system/bin/swapon /dev/block/zram0
>> "%RCLOCAL%" echo     write /proc/sys/vm/swappiness 100
powershell -NoProfile -Command "$t=[IO.File]::ReadAllText('%RCLOCAL%').Replace(([string][char]13),''); [IO.File]::WriteAllText('%RCLOCAL%',$t,(New-Object Text.UTF8Encoding $false))"
type "%RCLOCAL%"
echo.
adb -s %RADIO% shell mount -o rw,remount /system
adb -s %RADIO% push "%RCLOCAL%" %RCREMOTO%
adb -s %RADIO% shell chown root:root %RCREMOTO%
adb -s %RADIO% shell chmod 644 %RCREMOTO%
adb -s %RADIO% shell restorecon %RCREMOTO%
adb -s %RADIO% shell sync
adb -s %RADIO% shell mount -o ro,remount /system
>> "%PREVIO%" echo R^|rc^|%RCREMOTO%
call :log "creado %RCREMOTO% (zram 512MB !ALGO!, swappiness 100; previo !SWP!)"
echo Aplicando ahora sin reiniciar...
if defined TIENEALGO adb -s %RADIO% shell "echo !ALGO! > /sys/block/zram0/comp_algorithm"
adb -s %RADIO% shell "echo 536870912 > /sys/block/zram0/disksize"
adb -s %RADIO% shell /system/bin/mkswap /dev/block/zram0
adb -s %RADIO% shell /system/bin/swapon /dev/block/zram0
adb -s %RADIO% shell "echo 100 > /proc/sys/vm/swappiness"
>> "%PREVIO%" echo Z^|zram^|inmediato
call :log "zram activado de inmediato"

:gps_previo
echo.
echo [4/6] GPS asistido ^(guardando valores previos^)...
call :ajustar global assisted_gps_enabled 1
call :ajustar secure location_mode 3
call :ajustar global wifi_scan_always_enabled 1

echo.
echo [5/6] WebView...
set "PROV="
adb -s %RADIO% shell dumpsys webviewupdate > "%TMP1%" 2>&1
for /f "usebackq delims=" %%v in (`powershell -NoProfile -Command "if ((Get-Content '%TMP1%' -Raw) -match 'Current WebView package \(name, version\): \(([^,\)]+)') { $Matches[1] }"`) do set "PROV=%%v"
if not defined PROV set "PROV=null"
echo Proveedor actual: !PROV!
set "WVOK="
adb -s %RADIO% shell pm list packages -e com.google.android.webview | findstr /x /c:"package:com.google.android.webview" >nul && set "WVOK=si"
if not defined WVOK (
  echo com.google.android.webview no esta instalado o esta deshabilitado: se omite.
  call :log "webview omitido (no instalado o deshabilitado)"
) else (
  >> "%PREVIO%" echo W^|webview^|!PROV!
  adb -s %RADIO% shell cmd webviewupdate set-webview-implementation com.google.android.webview
  call :log "webview: !PROV! -> com.google.android.webview"
)
adb -s %RADIO% shell dumpsys webviewupdate > "%TMP1%"
powershell -NoProfile -Command "Get-Content '%TMP1%' -TotalCount 12"
echo.
echo ACCION HUMANA: abre Play Store y actualiza "Android System WebView".

echo.
echo [6/6] Verificando...
call :memoria
echo.
echo LISTO. Reinicia el radio para comprobar que el zram arranca solo.
echo Para deshacer: rendimiento.bat revertir
exit /b 0

rem ---------------------------- REVERTIR ----------------------------
:revertir
if not exist "%PREVIO%" (
  echo ERROR: no existe %PREVIO%. No hay nada que revertir.
  exit /b 1
)
echo Se restauraran los valores guardados en %PREVIO%:
type "%PREVIO%"
echo.
choice /c SN /m "Revertir"
if errorlevel 2 exit /b 0
call :log "INICIO rendimiento revertir en %RADIO%"
set "DEX=quicken"
for /f "tokens=1,2,3,4 delims=|" %%a in ('findstr /b "P|" "%PREVIO%"') do set "DEX=%%c"
echo [2/6] Restaurando compilacion ^(-m !DEX!^)...
for /f "tokens=1,2 delims=|" %%a in ('findstr /b "C|" "%PREVIO%"') do (
  adb -s %RADIO% shell cmd package compile -m !DEX! -f %%b
  call :log "compile -m !DEX! -f %%b"
)
echo [3/6] Memoria...
set "HAYRC="
for /f "tokens=1,2,3 delims=|" %%a in ('findstr /b "R|" "%PREVIO%"') do set "HAYRC=%%c"
if defined HAYRC (
  adb -s %RADIO% shell mount -o rw,remount /system
  adb -s %RADIO% shell rm -f !HAYRC!
  adb -s %RADIO% shell sync
  adb -s %RADIO% shell mount -o ro,remount /system
  call :log "borrado !HAYRC!"
)
findstr /b "Z|" "%PREVIO%" >nul
if not errorlevel 1 (
  adb -s %RADIO% shell /system/bin/swapoff /dev/block/zram0
  adb -s %RADIO% shell "echo 1 > /sys/block/zram0/reset"
  call :log "zram desactivado ahora (swapoff + reset)"
)
for /f "tokens=1,2,3 delims=|" %%a in ('findstr /b "M|" "%PREVIO%"') do (
  adb -s %RADIO% shell "echo %%c > /proc/sys/vm/%%b"
  call :log "vm %%b restaurado a %%c"
)
echo [4/6] Ajustes ^(GPS^)...
for /f "tokens=1,2,3,4 delims=|" %%a in ('findstr /b "S|" "%PREVIO%"') do (
  if "%%d"=="null" (
    adb -s %RADIO% shell settings delete %%b %%c
  ) else (
    adb -s %RADIO% shell settings put %%b %%c %%d
  )
  call :log "settings %%b %%c restaurado a %%d"
)
echo [5/6] WebView...
for /f "tokens=1,2,3 delims=|" %%a in ('findstr /b "W|" "%PREVIO%"') do (
  if /i not "%%c"=="null" (
    adb -s %RADIO% shell cmd webviewupdate set-webview-implementation %%c
    call :log "webview restaurado a %%c"
  )
)
move /y "%PREVIO%" "%PREVIO%.revertido" >nul
echo [6/6] Listo. Valores previos archivados en %PREVIO%.revertido
exit /b 0

rem ---------------------------- ESTADO ----------------------------
:estado
echo.
echo === Memoria ===
call :memoria
echo.
echo === Precompilacion ===
adb -s %RADIO% shell getprop pm.dexopt.install
for %%p in (%PAQUETES%) do (
  adb -s %RADIO% shell pm list packages %%p | findstr /x /c:"package:%%p" >nul
  if not errorlevel 1 (
    echo %%p:
    adb -s %RADIO% shell dumpsys package %%p | findstr /i /c:"[%%p]" /c:"status="
  )
)
echo.
echo === GPS ===
adb -s %RADIO% shell settings get global assisted_gps_enabled
adb -s %RADIO% shell settings get secure location_mode
adb -s %RADIO% shell settings get global wifi_scan_always_enabled
echo.
echo === WebView ===
adb -s %RADIO% shell dumpsys webviewupdate > "%TMP1%" 2>&1
powershell -NoProfile -Command "Get-Content '%TMP1%' -TotalCount 12"
echo.
if exist "%PREVIO%" (
  echo Cambios aplicados por este script:
  type "%PREVIO%"
) else (
  echo No hay cambios de rendimiento aplicados.
)
exit /b 0

:memoria
echo --- /proc/swaps
adb -s %RADIO% shell cat /proc/swaps
echo --- zram0 disksize / comp_algorithm
adb -s %RADIO% shell "cat /sys/block/zram0/disksize /sys/block/zram0/comp_algorithm 2>/dev/null"
echo --- LMK minfree / adj
adb -s %RADIO% shell "cat /sys/module/lowmemorykiller/parameters/minfree /sys/module/lowmemorykiller/parameters/adj 2>/dev/null"
echo --- swappiness
adb -s %RADIO% shell cat /proc/sys/vm/swappiness
echo --- meminfo
adb -s %RADIO% shell "grep -E 'MemTotal|MemFree|MemAvailable|SwapTotal|SwapFree' /proc/meminfo"
goto :eof

:compilar
set "PKG=%~1"
adb -s %RADIO% shell pm list packages %PKG% | findstr /x /c:"package:%PKG%" >nul
if errorlevel 1 (
  echo   %PKG%: no instalado, se omite.
  call :log "omitido %PKG% (no instalado)"
  goto :eof
)
>> "%PREVIO%" echo C^|%PKG%
echo   %PKG%: compile -m speed...
adb -s %RADIO% shell cmd package compile -m speed -f %PKG%
call :log "compile -m speed -f %PKG%"
goto :eof

:ajustar
set "NS=%~1"
set "KEY=%~2"
set "NUEVO=%~3"
set "VAL="
for /f "delims=" %%v in ('adb -s %RADIO% shell settings get %NS% %KEY% 2^>nul') do set "VAL=%%v"
if not defined VAL set "VAL=null"
>> "%PREVIO%" echo S^|%NS%^|%KEY%^|!VAL!
adb -s %RADIO% shell settings put %NS% %KEY% %NUEVO%
call :log "settings %NS% %KEY%: !VAL! -> %NUEVO%"
goto :eof

:log
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH:mm:ss"') do set "TS=%%t"
echo [%TS%] %~1>> "%LOG%"
echo [%TS%] %~1
goto :eof
