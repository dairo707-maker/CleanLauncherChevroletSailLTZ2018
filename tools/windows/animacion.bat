@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem animacion.bat - animacion de arranque del Sail (T17)
rem Uso: animacion.bat estado | aplicar | revertir [IP:PUERTO]
rem El primer logo (Chevrolet) lo pone el cargador de arranque y NO se toca.
rem Esto cambia solo /system/media/bootanimation.zip (la parte "Android").
rem Copia del original: C:\RadioBackup\bootanimation.zip.orig
rem ============================================================

set "MODO=%~1"
if /i not "%MODO%"=="estado" if /i not "%MODO%"=="aplicar" if /i not "%MODO%"=="revertir" (
  echo Uso: animacion.bat estado ^| aplicar ^| revertir [IP:PUERTO]
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
set "ORIG=%BK%\bootanimation.zip.orig"
set "LOG=%BK%\cambios_aplicados.txt"
set "REMOTO=/system/media/bootanimation.zip"
set "REPO=%~dp0..\.."
set "NUEVO=%REPO%\build\bootanimation.zip"

echo [1/4] Activando root en ADB...
adb -s %RADIO% root >nul 2>&1
timeout /t 4 >nul
adb connect %RADIO% >nul 2>&1
timeout /t 2 >nul
adb -s %RADIO% shell id | findstr /c:"uid=0" >nul
if errorlevel 1 (
  echo ERROR: no se obtuvo root. No se cambio nada.
  exit /b 1
)

echo Animaciones presentes en el radio:
adb -s %RADIO% shell "ls -l /system/media/*.zip /oem/media/*.zip /vendor/media/*.zip 2>/dev/null"
adb -s %RADIO% shell "getprop | grep -i bootanim"
adb -s %RADIO% shell "df /system | tail -n 1"
if /i "%MODO%"=="estado" exit /b 0

if /i "%MODO%"=="revertir" goto revertir

if not exist "%BK%\particiones" (
  echo ERROR: falta el respaldo completo de particiones. Ejecuta primero T14 ^(particiones.bat^).
  exit /b 1
)
adb -s %RADIO% shell ls %REMOTO% >nul 2>&1
if errorlevel 1 (
  echo ERROR: este radio no usa %REMOTO%. No se cambio nada; reporta la lista de arriba.
  exit /b 1
)

echo [2/4] Generando la animacion del Sail...
py "%REPO%\tools\bootanim.py" "%NUEVO%"
if not exist "%NUEVO%" (
  echo ERROR: no se pudo generar %NUEVO% ^(falta Python o Pillow^). No se cambio nada.
  exit /b 1
)

echo [3/4] Copia del original...
if exist "%ORIG%" (
  echo Ya existe %ORIG%: se conserva ^(es la de fabrica^).
) else (
  adb -s %RADIO% pull %REMOTO% "%ORIG%"
  if not exist "%ORIG%" (
    echo ERROR: no se pudo copiar el original. No se cambio nada.
    exit /b 1
  )
)
choice /c SN /m "Instalar la animacion del Sail"
if errorlevel 2 exit /b 0
set "FUENTE=%NUEVO%"
goto escribir

:revertir
if not exist "%ORIG%" (
  echo ERROR: no existe %ORIG%. No hay nada que revertir.
  exit /b 1
)
choice /c SN /m "Restaurar la animacion de fabrica"
if errorlevel 2 exit /b 0
set "FUENTE=%ORIG%"

:escribir
echo [4/4] Escribiendo en /system...
adb -s %RADIO% shell mount -o rw,remount /system
adb -s %RADIO% push "%FUENTE%" %REMOTO%
adb -s %RADIO% shell chown root:root %REMOTO%
adb -s %RADIO% shell chmod 644 %REMOTO%
adb -s %RADIO% shell restorecon %REMOTO%
adb -s %RADIO% shell sync
adb -s %RADIO% shell mount -o ro,remount /system
call :log "bootanimation %MODO%: %FUENTE% -> %REMOTO%"
adb -s %RADIO% shell ls -l %REMOTO%
echo.
echo Listo. Se vera en el proximo arranque completo (no en el despertar rapido de QuickBoot).
choice /c SN /m "Reiniciar el radio ahora para verla"
if errorlevel 2 exit /b 0
adb -s %RADIO% reboot
exit /b 0

:log
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH:mm:ss"') do set "TS=%%t"
echo [%TS%] %~1>> "%LOG%"
echo [%TS%] %~1
goto :eof
