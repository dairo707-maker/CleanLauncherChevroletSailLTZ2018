@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem conectar.bat - conecta ADB por Wi-Fi con el radio (SOLO LECTURA)
rem Uso: conectar.bat [IP:PUERTO]     ej: conectar.bat 192.168.1.7:5555
rem Tambien se puede definir la variable de entorno RADIO.
rem Codigos de salida: 0 = conectado, 1 = fallo, 2 = no autorizado
rem ============================================================

if not "%~1"=="" set "RADIO=%~1"
if not defined RADIO set "RADIO=192.168.1.7:5555"
echo %RADIO% | findstr ":" >nul || set "RADIO=%RADIO%:5555"

where adb >nul 2>&1
if errorlevel 1 (
  echo ERROR: no se encontro adb en el PATH.
  echo Instala platform-tools en C:\platform-tools y agregalo al PATH.
  echo Guia: docs\TUTORIAL.md, seccion 3.
  exit /b 1
)

set "INTENTO=0"
set "ESTADO=ninguno"

:intentar
set /a INTENTO+=1
echo.
echo Intento %INTENTO% de 3: adb connect %RADIO%
adb connect %RADIO%
set "ESTADO=ninguno"
for /f "tokens=1,2" %%a in ('adb devices ^| findstr /b /c:"%RADIO%"') do set "ESTADO=%%b"
echo Estado: %ESTADO%

if /i "%ESTADO%"=="device" goto ok
if %INTENTO% LSS 3 (
  timeout /t 3 /nobreak >nul
  goto intentar
)
if /i "%ESTADO%"=="unauthorized" goto noauth
goto fallo

:ok
echo.
echo CONECTADO: %RADIO% esta en estado device.
echo Siguiente paso: tools\windows\respaldo.bat
exit /b 0

:noauth
echo.
echo ESTADO: unauthorized. El radio pide autorizar esta PC.
echo Paso 1: mira la pantalla del radio y acepta el mensaje de depuracion USB.
echo Paso 2: marca la casilla "Permitir siempre" antes de aceptar.
echo Paso 3: vuelve a ejecutar este archivo.
exit /b 2

:fallo
echo.
echo FALLO: no se pudo conectar a %RADIO% (estado: %ESTADO%).
echo Revisa: radio encendido, Wi-Fi del radio activo, misma red del router,
echo IP 192.168.1.7 reservada en el router y depuracion ADB por red activada.
echo.
echo PLAN B: copia el APK al USB, abre el archivo desde el administrador de
echo archivos del radio y permite instalar fuentes desconocidas. Luego usa la
echo pantalla "Diagnostico del radio" de la app, si existe en la version instalada.
exit /b 1
