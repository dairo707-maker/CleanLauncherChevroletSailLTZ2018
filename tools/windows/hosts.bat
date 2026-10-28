@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem hosts.bat - bloqueo de anuncios y rastreadores via /system/etc/hosts
rem Uso: hosts.bat aplicar  [IP:PUERTO]
rem      hosts.bat revertir [IP:PUERTO]
rem      hosts.bat estado   [IP:PUERTO]
rem Lista: tools\hosts\hosts_anuncios.txt. Guarda el original en C:\RadioBackup\hosts.orig
rem Escribe en /system: aplicar exige respaldo C:\RadioBackup\particiones\* (T14).
rem ============================================================

set "MODO=%~1"
if /i not "%MODO%"=="aplicar" if /i not "%MODO%"=="revertir" if /i not "%MODO%"=="estado" (
  echo Uso: hosts.bat aplicar ^| revertir ^| estado [IP:PUERTO]
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
set "ORIG=%BK%\hosts.orig"
set "NUEVO=%BK%\hosts.nuevo"
set "LISTA=%~dp0..\hosts\hosts_anuncios.txt"
set "LOG=%BK%\cambios_aplicados.txt"
set "REMOTO=/system/etc/hosts"
if not exist "%BK%" (
  echo ERROR: no existe %BK%. Ejecuta primero respaldo.bat.
  exit /b 1
)

echo [1/5] Activando root en ADB...
adb -s %RADIO% root >nul 2>&1
timeout /t 4 >nul
adb connect %RADIO% >nul 2>&1
timeout /t 2 >nul
adb -s %RADIO% shell id | findstr /c:"uid=0" >nul
if errorlevel 1 (
  echo ERROR: no se obtuvo root. No se cambio nada.
  exit /b 1
)

if /i "%MODO%"=="estado" goto estado
if /i "%MODO%"=="revertir" goto revertir

set "HAYPART="
for /f "delims=" %%d in ('dir /b /ad "%BK%\particiones" 2^>nul') do set "HAYPART=%%d"
if not defined HAYPART (
  echo ERROR: aplicar escribe en /system y no hay respaldo de particiones ^(%BK%\particiones\*^).
  echo Haz primero el respaldo T14. No se cambio nada.
  exit /b 1
)
if not exist "%LISTA%" (
  echo ERROR: no se encontro %LISTA%
  exit /b 1
)

echo [2/5] Copia del original...
if exist "%ORIG%" (
  echo Ya existe %ORIG%: se conserva ^(es el original^).
) else (
  adb -s %RADIO% pull %REMOTO% "%ORIG%"
  if not exist "%ORIG%" (
    echo ERROR: no se pudo copiar %REMOTO%. No se cambio nada.
    exit /b 1
  )
)

echo [3/5] Preparando el hosts nuevo ^(finales de linea LF^)...
powershell -NoProfile -Command "$t=[IO.File]::ReadAllText('%LISTA%').Replace(([string][char]13),''); [IO.File]::WriteAllText('%NUEVO%',$t,(New-Object Text.UTF8Encoding $false))"
if errorlevel 1 (
  echo ERROR: no se pudo preparar el archivo. No se cambio nada.
  exit /b 1
)
for /f %%n in ('findstr /b /c:"0.0.0.0" "%NUEVO%" ^| find /c /v ""') do echo Dominios bloqueados: %%n

echo.
choice /c SN /m "Escribir el hosts nuevo en el radio"
if errorlevel 2 (
  echo Cancelado. No se cambio nada.
  exit /b 0
)
set "FUENTE=%NUEVO%"
goto escribir

:revertir
if not exist "%ORIG%" (
  echo ERROR: no existe %ORIG%. No hay nada que revertir.
  exit /b 1
)
choice /c SN /m "Restaurar el hosts original"
if errorlevel 2 exit /b 0
set "FUENTE=%ORIG%"

:escribir
echo [4/5] Escribiendo en /system...
adb -s %RADIO% shell mount -o rw,remount /system
adb -s %RADIO% push "%FUENTE%" %REMOTO%
adb -s %RADIO% shell chown root:root %REMOTO%
adb -s %RADIO% shell chmod 644 %REMOTO%
adb -s %RADIO% shell restorecon %REMOTO%
adb -s %RADIO% shell sync
adb -s %RADIO% shell mount -o ro,remount /system
call :log "hosts %MODO%: %FUENTE% -> %REMOTO%"

echo [5/5] Verificando...
adb -s %RADIO% shell ls -lZ %REMOTO%
adb -s %RADIO% shell grep -c "^0.0.0.0" %REMOTO%
echo.
echo Listo. Prueba Waze, Maps, YouTube Music y Play Store ^(ver T16^).
exit /b 0

:estado
echo.
adb -s %RADIO% shell ls -lZ %REMOTO%
echo Lineas bloqueadas ^(0.0.0.0^):
adb -s %RADIO% shell grep -c "^0.0.0.0" %REMOTO%
echo Primeras lineas:
adb -s %RADIO% shell head -n 8 %REMOTO%
if exist "%ORIG%" (echo Original guardado: %ORIG%) else (echo Sin copia del original ^(no se ha aplicado^).)
exit /b 0

:log
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH:mm:ss"') do set "TS=%%t"
echo [%TS%] %~1>> "%LOG%"
echo [%TS%] %~1
goto :eof
