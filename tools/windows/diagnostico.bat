@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem diagnostico.bat - diagnostico del radio (SOLO LECTURA en el radio)
rem Uso: diagnostico.bat [IP:PUERTO]
rem Escribe en la raiz del repo: diagnostico\diagnostico_radio.txt,
rem diagnostico\paquetes\*.txt, diagnostico\broadcasts.txt, diagnostico\logcat_eventos.txt
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

for %%R in ("%~dp0..\..") do set "REPO=%%~fR"
set "OUT=%REPO%\diagnostico"
if not exist "%OUT%\paquetes" mkdir "%OUT%\paquetes"
set "F=%OUT%\diagnostico_radio.txt"

echo ===== DIAGNOSTICO DEL RADIO %RADIO% =====> "%F%"
echo.>> "%F%"

echo [1/9] getprop...
echo ##### getprop>> "%F%"
adb -s %RADIO% shell getprop >> "%F%"

echo [2/9] cpuinfo y meminfo...
echo ##### /proc/cpuinfo>> "%F%"
adb -s %RADIO% shell cat /proc/cpuinfo >> "%F%"
echo ##### /proc/meminfo>> "%F%"
adb -s %RADIO% shell cat /proc/meminfo >> "%F%"

echo [3/9] df y service list...
echo ##### df>> "%F%"
adb -s %RADIO% shell df >> "%F%"
echo ##### service list>> "%F%"
adb -s %RADIO% shell service list >> "%F%"

echo [4/9] paquetes con ruta...
echo ##### pm list packages -f>> "%F%"
adb -s %RADIO% shell pm list packages -f >> "%F%"

echo [5/9] dispositivos de video, serie y audio...
echo ##### ls -l /dev/video* /dev/ttyS* /dev/ttyMT*>> "%F%"
adb -s %RADIO% shell "ls -l /dev/video* /dev/ttyS* /dev/ttyMT* 2>&1" >> "%F%"

echo [6/9] dumpsys media.camera (200 lineas)...
echo ##### dumpsys media.camera ^(inicio^)>> "%F%"
adb -s %RADIO% shell dumpsys media.camera | powershell -NoProfile -Command "$input | Select-Object -First 200" >> "%F%"

echo [7/9] dumpsys audio (200 lineas)...
echo ##### dumpsys audio ^(inicio^)>> "%F%"
adb -s %RADIO% shell dumpsys audio | powershell -NoProfile -Command "$input | Select-Object -First 200" >> "%F%"

echo [8/9] paquetes relacionados (dumpsys package por paquete)...
for /f "tokens=2 delims=:" %%p in ('adb -s %RADIO% shell pm list packages') do call :pkg "%%p"

echo [9/9] broadcasts...
adb -s %RADIO% shell dumpsys activity broadcasts > "%OUT%\broadcasts.txt"

echo.
echo ============================================================
echo PRUEBA DE EVENTOS: 60 SEGUNDOS
echo Cuando termine la cuenta, durante 60 segundos haz lo siguiente:
echo   1. Cambia la fuente a Radio.
echo   2. Cambia la fuente a Bluetooth musica.
echo   3. Pon reversa SOLO con el auto detenido.
echo   4. Enciende y apaga las luces.
echo ============================================================
adb -s %RADIO% logcat -c
echo.
echo AHORA: haz las acciones. Espera 60 segundos...
timeout /t 60 /nobreak >nul
adb -s %RADIO% logcat -d > "%OUT%\logcat_eventos.txt"
echo Eventos guardados en diagnostico\logcat_eventos.txt

echo.
echo Diagnostico terminado en: %OUT%
echo.
echo PARA SUBIR AL REPO, desde la raiz del repo ejecuta:
echo   git add diagnostico backup\xyauto
echo   git commit -m "Diagnóstico del radio"
echo   git push
exit /b 0

:pkg
set "PKG=%~1"
echo !PKG! | findstr /i /r "launcher radio bt bluetooth car xyauto autochips atc mediatek mtk can mcu camera setting music video media avm" >nul
if errorlevel 1 goto :eof
echo   dumpsys package !PKG!
adb -s %RADIO% shell dumpsys package !PKG! > "%OUT%\paquetes\!PKG!.txt"
goto :eof
