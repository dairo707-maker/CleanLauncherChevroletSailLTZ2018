@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem experimento.bat - pruebas de control del radio: FM, Bluetooth y ecualizador
rem Uso: experimento.bat [IP:PUERTO]
rem Requiere al humano en el carro. Pide confirmacion antes de cada prueba.
rem Escribe en diagnostico\experimento.txt y diagnostico\apk\*.apk
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
set "APKDIR=%OUT%\apk"
if not exist "%APKDIR%" mkdir "%APKDIR%"
set "F=%OUT%\experimento.txt"

echo ===== EXPERIMENTO DEL RADIO %RADIO% =====> "%F%"
echo Fecha: %DATE% %TIME% >> "%F%"
echo.>> "%F%"

echo.
echo Este script prueba comandos en el radio. Necesitas al humano en el carro.
echo Cada paso se anuncia antes de ejecutarse y se registra en diagnostico\experimento.txt.

rem ---------------- PASO 0: APKs para analisis de strings
call :begin 0 "Descargar APKs de fabrica para analisis offline de strings" ""
call :pull com.android.fmradio
call :pull com.acloud.stub.localradio
call :pull com.autochips.bluetooth
call :pull com.xyauto.Settings
timeout /t 3 /nobreak >nul
call :record

rem ---------------- PASO 1: preparar la radio
call :begin 1 "Abre la app de radio de fabrica en 96.1 u otra emisora" ""
echo Abre la app de radio de fabrica en 96.1 u otra emisora.
echo Cuando la radio este sonando, pulsa una tecla aqui.
pause
timeout /t 3 /nobreak >nul
call :record

rem ---------------- PASO 2: intentos de sintonia por broadcast
call :begin 2a "Sintonizar 94.5 con xy.setfm.freq (extra freq int)" "am broadcast -a xy.setfm.freq -p com.android.fmradio --ei freq 9450"
adb -s %RADIO% shell am broadcast -a xy.setfm.freq -p com.android.fmradio --ei freq 9450 >> "%F%" 2>&1
timeout /t 3 /nobreak >nul
call :record
call :ask 2a "¿Cambió la emisora?"

call :begin 2b "Sintonizar 94.5 con xy.setfm.freq (extra frequency int)" "am broadcast -a xy.setfm.freq -p com.android.fmradio --ei frequency 9450"
adb -s %RADIO% shell am broadcast -a xy.setfm.freq -p com.android.fmradio --ei frequency 9450 >> "%F%" 2>&1
timeout /t 3 /nobreak >nul
call :record
call :ask 2b "¿Cambió la emisora?"

call :begin 2c "Sintonizar 94.5 con xy.setfm.freq (extra freq string)" "am broadcast -a xy.setfm.freq -p com.android.fmradio --es freq 94.5"
adb -s %RADIO% shell am broadcast -a xy.setfm.freq -p com.android.fmradio --es freq 94.5 >> "%F%" 2>&1
timeout /t 3 /nobreak >nul
call :record
call :ask 2c "¿Cambió la emisora?"

call :begin 2d "Sintonizar 94.5 con xy.setfm.freq (extra freq float)" "am broadcast -a xy.setfm.freq -p com.android.fmradio --ef freq 94.5"
adb -s %RADIO% shell am broadcast -a xy.setfm.freq -p com.android.fmradio --ef freq 94.5 >> "%F%" 2>&1
timeout /t 3 /nobreak >nul
call :record
call :ask 2d "¿Cambió la emisora?"

call :begin 2e "Sintonizar 103.3 con xy.open.freq (extra freq int)" "am broadcast -a xy.open.freq -p com.android.fmradio --ei freq 10330"
adb -s %RADIO% shell am broadcast -a xy.open.freq -p com.android.fmradio --ei freq 10330 >> "%F%" 2>&1
timeout /t 3 /nobreak >nul
call :record
call :ask 2e "¿Cambió la emisora?"

call :begin 2f "Sintonizar 91.3 con FM_SET_CURRENT (extra frequency int)" "am broadcast -a com.yunos.intent.FM_SET_CURRENT -p com.android.fmradio --ei frequency 9130"
adb -s %RADIO% shell am broadcast -a com.yunos.intent.FM_SET_CURRENT -p com.android.fmradio --ei frequency 9130 >> "%F%" 2>&1
timeout /t 3 /nobreak >nul
call :record
call :ask 2f "¿Cambió la emisora?"

call :begin 2g "Siguiente emisora (MEDIA_NEXT, keyevent 87). La radio debe estar sonando" "input keyevent 87"
adb -s %RADIO% shell input keyevent 87 >> "%F%" 2>&1
timeout /t 3 /nobreak >nul
call :record
call :ask 2g "¿Cambió la emisora?"

call :begin 2h "Emisora anterior (MEDIA_PREVIOUS, keyevent 88). La radio debe estar sonando" "input keyevent 88"
adb -s %RADIO% shell input keyevent 88 >> "%F%" 2>&1
timeout /t 3 /nobreak >nul
call :record
call :ask 2h "¿Cambió la emisora?"

rem ---------------- PASO 3: Bluetooth (opcional)
call :begin 3 "Bluetooth: marcar un numero de prueba desde el radio" ""
echo Requisito: el telefono debe estar conectado por Bluetooth al radio.
echo Usa un numero de prueba que TU elijas. No uses tu buzon de voz.
choice /c SN /m "¿Hacer la prueba Bluetooth ahora?"
if errorlevel 2 (
  echo Seccion Bluetooth omitida por el humano. >> "%F%"
  goto :bt_fin
)
set "NUM="
set /p "NUM=Escribe el numero a marcar (solo digitos, + opcional): "
echo !NUM! | findstr /x /r "[+]*[0-9][0-9]*" >nul
if errorlevel 1 (
  echo Numero no valido. Seccion Bluetooth omitida.
  echo Numero no valido. Seccion Bluetooth omitida. >> "%F%"
  goto :bt_fin
)
echo Numero elegido: !NUM! >> "%F%"

call :begin 3a "Marcar con broadcast xy.bluetoothcall.out (extra number)" "am broadcast -a xy.bluetoothcall.out --es number !NUM!"
choice /c SN /m "¿Confirmas marcar !NUM! ahora?"
if errorlevel 2 goto :bt_3b
adb -s %RADIO% shell am broadcast -a xy.bluetoothcall.out --es number !NUM! >> "%F%" 2>&1
timeout /t 3 /nobreak >nul
call :record
call :ask 3a "¿Marcó?"
echo Cuelga la llamada desde el telefono y luego pulsa una tecla.
pause
:bt_3b

call :begin 3b "Marcar con broadcast xy.bluetoothcall.out (extra phonenumber)" "am broadcast -a xy.bluetoothcall.out --es phonenumber !NUM!"
choice /c SN /m "¿Confirmas marcar !NUM! ahora?"
if errorlevel 2 goto :bt_3c
adb -s %RADIO% shell am broadcast -a xy.bluetoothcall.out --es phonenumber !NUM! >> "%F%" 2>&1
timeout /t 3 /nobreak >nul
call :record
call :ask 3b "¿Marcó?"
echo Cuelga la llamada desde el telefono y luego pulsa una tecla.
pause
:bt_3c

call :begin 3c "Llamar con android.intent.action.CALL (tel:)" "am start -a android.intent.action.CALL -d tel:!NUM!"
choice /c SN /m "¿Confirmas llamar a !NUM! ahora?"
if errorlevel 2 goto :bt_3d
adb -s %RADIO% shell am start -a android.intent.action.CALL -d tel:!NUM! >> "%F%" 2>&1
timeout /t 3 /nobreak >nul
call :record
call :ask 3c "¿Marcó?"
echo Cuelga la llamada desde el telefono y luego pulsa una tecla.
pause
:bt_3d

call :begin 3d "Abrir marcador con android.intent.action.DIAL (tel:)" "am start -a android.intent.action.DIAL -d tel:!NUM!"
choice /c SN /m "¿Confirmas abrir el marcador con !NUM! ahora?"
if errorlevel 2 goto :bt_fin
adb -s %RADIO% shell am start -a android.intent.action.DIAL -d tel:!NUM! >> "%F%" 2>&1
timeout /t 3 /nobreak >nul
call :record
call :ask 3d "¿Marcó?"
echo Si el marcador quedo abierto, marca desde el telefono o cierralo, y luego pulsa una tecla.
pause
:bt_fin

rem ---------------- PASO 4: ecualizador y efectos de audio
call :begin 4 "Ecualizador: buscar efectos en audio_flinger y en audio" ""
echo ##### dumpsys media.audio_flinger (Effect, Equalizer) >> "%F%"
adb -s %RADIO% shell dumpsys media.audio_flinger | findstr /i "Effect Equalizer" >> "%F%"
echo ##### dumpsys audio (effect, eq) >> "%F%"
adb -s %RADIO% shell dumpsys audio | findstr /i "effect eq" >> "%F%"
timeout /t 3 /nobreak >nul
call :record

rem ---------------- PASO 5: fin
echo.
echo ============================================================
echo Experimento terminado.
echo Resultados: %F%
echo APKs:       %APKDIR%
echo ============================================================
echo.
echo PARA SUBIR AL REPO, desde la raiz del repo ejecuta:
echo   git add diagnostico\experimento.txt diagnostico\apk
echo   git commit -m "Experimento de radio, Bluetooth y ecualizador"
echo   git push
exit /b 0

rem ============================================================
rem Subrutinas
rem ============================================================

:begin
rem %1 = paso, %2 = descripcion, %3 = comando (opcional)
echo.
echo [PASO %~1] %~2
if not "%~3"=="" echo     comando: %~3
echo.>> "%F%"
echo ===== PASO %~1: %~2 >> "%F%"
if not "%~3"=="" echo comando: %~3 >> "%F%"
adb -s %RADIO% logcat -c
goto :eof

:pull
rem %1 = paquete. Obtiene la ruta del APK y lo copia a diagnostico\apk
set "PKG=%~1"
set "APKPATH="
echo.
echo [APK] %PKG%
for /f "tokens=1* delims=:" %%a in ('adb -s %RADIO% shell pm path %PKG% 2^>nul') do (
  if /i "%%a"=="package" if not defined APKPATH set "APKPATH=%%b"
)
if not defined APKPATH (
  echo   no encontrado: %PKG%
  echo APK no encontrado: %PKG% >> "%F%"
  goto :eof
)
echo   ruta: !APKPATH!
echo APK !APKPATH! -^> apk\%PKG%.apk >> "%F%"
adb -s %RADIO% pull "!APKPATH!" "%APKDIR%\%PKG%.apk"
goto :eof

:record
rem Anota fmradio_text y las ultimas 40 lineas de logcat
echo --- settings get system fmradio_text >> "%F%"
adb -s %RADIO% shell settings get system fmradio_text >> "%F%" 2>&1
echo --- logcat -d -t 40 >> "%F%"
adb -s %RADIO% logcat -d -t 40 >> "%F%" 2>&1
echo. >> "%F%"
goto :eof

:ask
rem %1 = paso, %2 = pregunta. Registra S o N.
choice /c SN /m "%~2"
if errorlevel 2 (set "ANS=N") else (set "ANS=S")
echo Paso %~1 - %~2 Respuesta: !ANS! >> "%F%"
goto :eof
