@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem launcher_sistema.bat - instala Clean Launcher como app privilegiada del sistema
rem (/system/priv-app/CleanLauncher) para que Android nunca la cierre.
rem Uso: launcher_sistema.bat aplicar  [IP:PUERTO]
rem      launcher_sistema.bat revertir [IP:PUERTO]
rem      launcher_sistema.bat estado   [IP:PUERTO]
rem Requiere root por ADB (T12) y el respaldo de particiones (T14).
rem Tras reiniciar, Android trata la copia de /data como actualizacion de la app
rem del sistema: "adb install -r" sigue funcionando porque la firma es la misma.
rem ============================================================

set "MODO=%~1"
if /i not "%MODO%"=="aplicar" if /i not "%MODO%"=="revertir" if /i not "%MODO%"=="estado" (
  echo Uso: launcher_sistema.bat aplicar ^| revertir ^| estado [IP:PUERTO]
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
set "LOG=%BK%\cambios_aplicados.txt"
set "PKG=com.simple.carlauncher"
set "DIRSYS=/system/priv-app/CleanLauncher"
set "APKSYS=%DIRSYS%/CleanLauncher.apk"
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
  echo ERROR: no se obtuvo root. No se cambio nada.
  exit /b 1
)

if /i "%MODO%"=="revertir" goto revertir
if /i "%MODO%"=="estado" goto estado

rem ---------------- aplicar ----------------
echo [2/6] Verificando el respaldo de particiones ^(T14^)...
dir /s /b "%BK%\particiones\*.img" >nul 2>&1
if errorlevel 1 (
  echo ERROR: no hay respaldo de particiones en %BK%\particiones. Ejecuta primero T14 ^(particiones.bat respaldar^).
  exit /b 1
)

echo [3/6] Buscando el APK instalado...
set "APKDATA="
for /f "tokens=1* delims=:" %%a in ('adb -s %RADIO% shell pm path %PKG%') do if not defined APKDATA set "APKDATA=%%b"
if not defined APKDATA (
  echo ERROR: %PKG% no esta instalado. Instalalo primero con instalar.bat ^(T5^).
  exit /b 1
)
echo APK actual: %APKDATA%
echo %APKDATA%| findstr /b /c:"/system/" >nul
if not errorlevel 1 (
  echo El launcher ya es app del sistema. Nada que hacer.
  exit /b 0
)
set "APKSZ=0"
for /f %%s in ('adb -s %RADIO% shell stat -c %%s %APKDATA%') do set "APKSZ=%%s"
if "%APKSZ%"=="0" (
  echo ERROR: no se pudo leer el tamano de %APKDATA%.
  exit /b 1
)
set "LIBRE=0"
for /f "skip=1 tokens=4" %%f in ('adb -s %RADIO% shell df -k /system') do set "LIBRE=%%f"
set /a NEED=(2*APKSZ)/1024+1+10240
echo APK: %APKSZ% bytes. Libre en /system: %LIBRE% KB. Necesario: %NEED% KB.
if %LIBRE% LSS %NEED% (
  echo ERROR: no hay espacio suficiente en /system. No se cambio nada.
  exit /b 1
)

set "ENF="
for /f %%e in ('adb -s %RADIO% shell getprop ro.control_privapp_permissions') do set "ENF=%%e"
if /i "%ENF%"=="enforce" (
  echo ADVERTENCIA: ro.control_privapp_permissions=enforce. Si el launcher pide permisos privilegiados
  echo que no estan en /system/etc/permissions/privapp-permissions-*.xml, el radio puede NO arrancar.
  choice /c SN /m "Continuar de todos modos"
  if errorlevel 2 (
    echo Cancelado. No se cambio nada.
    exit /b 0
  )
)

echo.
echo Se copiara %APKDATA% a %APKSYS% ^(permisos 755/644, root:root^).
choice /c SN /m "Escribir en /system"
if errorlevel 2 (
  echo Cancelado. No se cambio nada.
  exit /b 0
)

echo [4/6] Escribiendo en /system...
adb -s %RADIO% shell mount -o rw,remount /system
adb -s %RADIO% shell mkdir -p %DIRSYS%
adb -s %RADIO% shell cp %APKDATA% %APKSYS%
adb -s %RADIO% shell chmod 755 %DIRSYS%
adb -s %RADIO% shell chmod 644 %APKSYS%
adb -s %RADIO% shell chown root:root %DIRSYS% %APKSYS%
adb -s %RADIO% shell restorecon -R %DIRSYS%
adb -s %RADIO% shell sync
adb -s %RADIO% shell cmp %APKDATA% %APKSYS%
set "CMPERR=!errorlevel!"
adb -s %RADIO% shell mount -o ro,remount /system
if not "%CMPERR%"=="0" (
  echo ERROR: la copia no coincide con el original. Revierte con: launcher_sistema.bat revertir
  call :log "launcher_sistema aplicar: copia INCORRECTA en %APKSYS%"
  exit /b 1
)
call :log "launcher_sistema aplicar: %APKDATA% -> %APKSYS%"

echo [5/6] Verificando...
adb -s %RADIO% shell ls -lZ %DIRSYS%
echo.
echo [6/6] Listo. Tras reiniciar, Android tomara la copia de /data como actualizacion de la app del sistema.
echo Las actualizaciones siguen con "adb install -r" ^(misma firma^).
choice /c SN /m "Reiniciar el radio ahora"
if errorlevel 2 exit /b 0
adb -s %RADIO% reboot
exit /b 0

:revertir
echo [2/4] Quitando la app del sistema...
choice /c SN /m "Borrar %DIRSYS%"
if errorlevel 2 (
  echo Cancelado. No se cambio nada.
  exit /b 0
)
echo [3/4] Escribiendo en /system...
adb -s %RADIO% shell mount -o rw,remount /system
adb -s %RADIO% shell rm -rf %DIRSYS%
adb -s %RADIO% shell sync
adb -s %RADIO% shell mount -o ro,remount /system
call :log "launcher_sistema revertir: borrado %DIRSYS%"
echo [4/4] Verificando...
adb -s %RADIO% shell ls -d %DIRSYS%
echo ^(Debe decir "No such file or directory".^) La copia instalada en /data sigue como app normal.
choice /c SN /m "Reiniciar el radio ahora"
if errorlevel 2 exit /b 0
adb -s %RADIO% reboot
exit /b 0

:estado
echo [2/2] Estado de %PKG%...
adb -s %RADIO% shell dumpsys package %PKG% | findstr /i "codePath flags pkgFlags privateFlags"
adb -s %RADIO% shell ls -ld %DIRSYS%
exit /b 0

:log
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH:mm:ss"') do set "TS=%%t"
echo [%TS%] %~1>> "%LOG%"
echo [%TS%] %~1
goto :eof
