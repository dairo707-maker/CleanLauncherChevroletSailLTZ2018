@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem qb_lista.bat - agrega Clean Launcher a la lista blanca de QuickBoot
rem (/system/etc/qb_list.xml) para que el radio no lo cierre al apagar la llave.
rem Uso: qb_lista.bat aplicar  [IP:PUERTO]
rem      qb_lista.bat revertir [IP:PUERTO]
rem Requiere root por ADB (ver T12). Guarda el original en C:\RadioBackup\qb_list.xml.orig
rem ============================================================

set "MODO=%~1"
if /i not "%MODO%"=="aplicar" if /i not "%MODO%"=="revertir" (
  echo Uso: qb_lista.bat aplicar ^| revertir [IP:PUERTO]
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
set "ORIG=%BK%\qb_list.xml.orig"
set "NUEVO=%BK%\qb_list.xml.nuevo"
set "LOG=%BK%\cambios_aplicados.txt"
set "REMOTO=/system/etc/qb_list.xml"
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

if /i "%MODO%"=="revertir" goto revertir

echo [2/5] Copia del original...
if exist "%ORIG%" (
  echo Ya existe %ORIG%: se conserva ^(es la copia de fabrica^).
) else (
  adb -s %RADIO% pull %REMOTO% "%ORIG%"
  if not exist "%ORIG%" (
    echo ERROR: no se pudo copiar %REMOTO%. No se cambio nada.
    exit /b 1
  )
)
adb -s %RADIO% shell grep -c com.simple.carlauncher %REMOTO% | findstr /r "^[1-9]" >nul
if not errorlevel 1 (
  echo El launcher ya esta en la lista. Nada que hacer.
  exit /b 0
)

echo [3/5] Preparando la lista nueva ^(copia la linea del launcher de fabrica^)...
powershell -NoProfile -Command ^
  "$t = [IO.File]::ReadAllText('%ORIG%');" ^
  "$lines = $t -split \"`r?`n\";" ^
  "$i = -1; foreach ($p in @('com.xygala.launcher\b(?!all)', 'com.android.launcher3\b', 'com.android.launcher\b')) { for ($k = 0; $k -lt $lines.Count; $k++) { if ($lines[$k] -match $p) { $i = $k; break } }; if ($i -ge 0) { break } };" ^
  "if ($i -lt 0) { Write-Host 'ERROR: no se encontro un launcher de fabrica en la lista'; exit 2 };" ^
  "$new = $lines[$i] -replace 'com\.xygala\.launcher|com\.android\.launcher3|com\.android\.launcher', 'com.simple.carlauncher';" ^
  "$out = New-Object System.Collections.Generic.List[string]; $out.AddRange([string[]]$lines); $out.Insert($i + 1, $new);" ^
  "$res = [string]::Join(\"`n\", $out); [xml]$check = $res;" ^
  "[IO.File]::WriteAllText('%NUEVO%', $res, (New-Object Text.UTF8Encoding $false));" ^
  "Write-Host ('Linea agregada: ' + $new.Trim())"
if errorlevel 1 (
  echo ERROR: no se pudo preparar la lista ^(XML invalido o formato inesperado^). No se cambio nada.
  exit /b 1
)

echo.
choice /c SN /m "Escribir la lista nueva en el radio"
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
choice /c SN /m "Restaurar la lista de fabrica"
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
call :log "qb_list.xml %MODO%: %FUENTE% -> %REMOTO%"

echo [5/5] Verificando...
adb -s %RADIO% shell ls -lZ %REMOTO%
adb -s %RADIO% shell grep -c com.simple.carlauncher %REMOTO%
echo.
echo Listo. Reinicia el radio para que QuickBootManager lea la lista ^(apagar y prender no basta^).
choice /c SN /m "Reiniciar el radio ahora"
if errorlevel 2 exit /b 0
adb -s %RADIO% reboot
exit /b 0

:log
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH:mm:ss"') do set "TS=%%t"
echo [%TS%] %~1>> "%LOG%"
echo [%TS%] %~1
goto :eof
