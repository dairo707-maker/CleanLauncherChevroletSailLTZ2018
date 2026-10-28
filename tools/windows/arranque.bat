@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem arranque.bat - diagnostico del arranque lento (quickboot)
rem SOLO LECTURA en el radio. Pide al humano apagar/encender el ACC.
rem Uso: arranque.bat [IP:PUERTO]
rem Escribe en la raiz del repo: diagnostico\arranque.txt,
rem diagnostico\arranque_logcat.txt, diagnostico\arranque_dmesg.txt,
rem diagnostico\apk\com.autochips.quickbootmanager.apk, diagnostico\qb\
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
if not exist "%OUT%\apk" mkdir "%OUT%\apk"
if not exist "%OUT%\qb" mkdir "%OUT%\qb"
set "F=%OUT%\arranque.txt"

echo ===== DIAGNOSTICO DE ARRANQUE DEL RADIO %RADIO% =====> "%F%"
echo.>> "%F%"

echo [1/5] getprop, settings, power, deviceidle y servicios...
echo ##### getprop ^(qb^|quick^|boot^|sleep^|acc^)>> "%F%"
adb -s %RADIO% shell getprop | findstr /i /r "qb quick boot sleep acc" >> "%F%"
echo ##### settings list system ^(sleep^|qb^|acc^|standby^|off^)>> "%F%"
adb -s %RADIO% shell settings list system | findstr /i /r "sleep qb acc standby off" >> "%F%"
echo ##### settings list global ^(sleep^|qb^|acc^|standby^|off^)>> "%F%"
adb -s %RADIO% shell settings list global | findstr /i /r "sleep qb acc standby off" >> "%F%"
echo ##### dumpsys power ^(120 lineas^)>> "%F%"
adb -s %RADIO% shell dumpsys power | powershell -NoProfile -Command "$input | Select-Object -First 120" >> "%F%"
echo ##### dumpsys deviceidle whitelist>> "%F%"
adb -s %RADIO% shell dumpsys deviceidle whitelist >> "%F%"
echo ##### dumpsys activity services com.simple.carlauncher>> "%F%"
adb -s %RADIO% shell dumpsys activity services com.simple.carlauncher >> "%F%"

echo [2/5] APK de quickbootmanager y archivos qb...
echo ##### pm path com.autochips.quickbootmanager>> "%F%"
set "APKPATH="
for /f "tokens=2 delims=:" %%p in ('adb -s %RADIO% shell pm path com.autochips.quickbootmanager 2^>nul') do if not defined APKPATH set "APKPATH=%%p"
echo !APKPATH!>> "%F%"
if defined APKPATH (
  adb -s %RADIO% pull !APKPATH! "%OUT%\apk\com.autochips.quickbootmanager.apk"
) else (
  echo AVISO: no se encontro el paquete com.autochips.quickbootmanager.
)
echo ##### ls -l /system/etc /vendor/etc ^(qb^|quick^|boot^)>> "%F%"
adb -s %RADIO% shell "ls -l /system/etc /vendor/etc 2>&1" | findstr /i /r "qb quick boot" >> "%F%"
for %%D in (/system/etc /vendor/etc) do (
  for /f "tokens=*" %%F in ('adb -s %RADIO% shell ls %%D 2^>nul') do (
    echo %%F | findstr /i /c:"qb" /c:"quickboot" >nul && adb -s %RADIO% pull %%D/%%F "%OUT%\qb\%%F"
  )
)

echo [3/5] reinicio por ACC: ahora el humano apaga y enciende el contacto...
echo.
echo ============================================================
echo PASO MANUAL 1: apaga el contacto ^(ACC OFF^).
echo Cuando este apagado, presiona una tecla.
echo ============================================================
pause
echo Esperando 20 segundos con el contacto apagado...
timeout /t 20 /nobreak >nul
echo ============================================================
echo PASO MANUAL 2: enciende el contacto ^(ACC ON^).
echo Cuando veas el launcher en pantalla, presiona una tecla.
echo El ADB por Wi-Fi se va a cortar; el script reconecta solo.
echo ============================================================
pause

echo Reconectando con %RADIO% ^(hasta 3 minutos^)...
set /a INTENTOS=0
goto :esperar

:esperar
set /a INTENTOS+=1
adb connect %RADIO% >nul 2>&1
set "ESTADO=ninguno"
for /f %%s in ('adb -s %RADIO% get-state 2^>nul') do set "ESTADO=%%s"
if "%ESTADO%"=="device" goto :conectado
if %INTENTOS% GEQ 36 goto :sinradio
timeout /t 5 /nobreak >nul
goto :esperar

:sinradio
echo ERROR: el radio no volvio a conectarse en 3 minutos.
echo ##### RECONEXION FALLIDA tras ciclo ACC>> "%F%"
echo Revisa la Wi-Fi del radio y vuelve a ejecutar el script.
exit /b 1

:conectado
echo Radio conectado. Capturando datos...
echo ##### adb shell uptime>> "%F%"
set "UPTIME="
for /f "tokens=*" %%u in ('adb -s %RADIO% shell uptime 2^>nul') do set "UPTIME=%%u"
echo !UPTIME!>> "%F%"
echo !UPTIME! | findstr /i /c:"up 0:" /c:"up 0 min" >nul && echo AVISO: uptime corto: probable REINICIO COMPLETO ^(no fue resume rapido^).
echo ##### logcat filtrado ^(ultimas 3000 lineas^)>> "%F%"
adb -s %RADIO% logcat -d -b all -t 3000 | findstr /i /c:"QuickBoot" /c:"QB_" /c:"qb_" /c:"PREQB" /c:"boot_completed" /c:"BOOT_COMPLETED" /c:"ActivityManager:" /c:"Start proc com.simple.carlauncher" /c:"acc.on" /c:"acc.off" > "%OUT%\arranque_logcat.txt"
echo ##### dmesg ^(ultimas 200 lineas, si se permite^)>> "%OUT%\arranque_dmesg.txt"
adb -s %RADIO% shell dmesg 2^>^&1 | powershell -NoProfile -Command "$input | Select-Object -Last 200" >> "%OUT%\arranque_dmesg.txt"
echo Captura terminada.

echo.
echo ============================================================
echo PREGUNTA PARA EL HUMANO:
choice /c SN /n /m "Se vio el logo de Chevrolet? [S,N]"
set "LOGO=S"
if errorlevel 2 set "LOGO=N"
echo ##### logo de Chevrolet visto: %LOGO%>> "%F%"
echo Respuesta registrada: %LOGO%
echo ============================================================

echo.
echo Arranque terminado en: %OUT%
echo.
echo PARA SUBIR AL REPO, desde la raiz del repo ejecuta:
echo   git add diagnostico\arranque*.txt diagnostico\apk\com.autochips.quickbootmanager.apk diagnostico\qb
echo   git commit -m "Diagnostico de arranque del radio"
echo   git push
exit /b 0
