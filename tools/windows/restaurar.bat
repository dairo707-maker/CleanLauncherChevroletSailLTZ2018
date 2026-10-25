@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem restaurar.bat - revierte los cambios usando un respaldo de respaldo.bat
rem Uso: restaurar.bat [carpeta_del_respaldo] [IP:PUERTO]
rem   Sin carpeta usa la mas reciente de C:\RadioBackup.
rem Pide confirmacion (S/N) antes de cambiar nada.
rem Cada cambio queda en C:\RadioBackup\cambios_aplicados.txt
rem ============================================================

set "BK_ARG=%~1"
if not "%~2"=="" set "RADIO=%~2"
if not defined RADIO set "RADIO=192.168.1.7:5555"
echo %RADIO% | findstr ":" >nul || set "RADIO=%RADIO%:5555"

where adb >nul 2>&1
if errorlevel 1 (
  echo ERROR: no se encontro adb en el PATH.
  echo Ver docs\TUTORIAL.md, seccion 3.
  exit /b 1
)

set "BK="
if not "%BK_ARG%"=="" set "BK=%BK_ARG%"
if not defined BK for /f "delims=" %%d in ('dir /b /ad /o-n "C:\RadioBackup" 2^>nul') do if not defined BK set "BK=C:\RadioBackup\%%d"
if not defined BK (
  echo ERROR: no hay respaldos en C:\RadioBackup.
  echo Ejecuta primero tools\windows\respaldo.bat o pasa la carpeta como argumento.
  exit /b 1
)
if not exist "%BK%\launcher_por_defecto.txt" (
  echo ERROR: %BK% no es un respaldo valido. Falta launcher_por_defecto.txt
  exit /b 1
)

set "ESTADO=ninguno"
for /f %%s in ('adb -s %RADIO% get-state 2^>nul') do set "ESTADO=%%s"
if not "%ESTADO%"=="device" (
  echo ERROR: el radio %RADIO% no esta conectado ^(estado: %ESTADO%^).
  echo Ejecuta primero tools\windows\conectar.bat
  exit /b 1
)

echo Respaldo a usar: %BK%
echo Esto hara lo siguiente:
echo   - Devolver el launcher por defecto (HOME) al original del respaldo.
echo   - Desinstalar com.simple.carlauncher.
echo   - Volver a habilitar los paquetes que estaban habilitados.
echo   - Reinstalar los APK de fabrica que falten.
choice /c SN /m "Continuar con la restauracion"
if errorlevel 2 (
  echo Cancelado. No se hizo ningun cambio.
  exit /b 0
)

set "LOG=C:\RadioBackup\cambios_aplicados.txt"
set "SALIDA=%TEMP%\restaurar_salida.txt"

echo.
echo [1/4] Restaurando launcher por defecto (HOME)...
set "HOME_ORIG="
for /f "delims=" %%l in ('findstr /c:"/" "%BK%\launcher_por_defecto.txt"') do if not defined HOME_ORIG set "HOME_ORIG=%%l"
if not defined HOME_ORIG goto sin_home
echo !HOME_ORIG! | findstr /i /c:"com.simple.carlauncher" >nul && goto sin_home
adb -s %RADIO% shell cmd package set-home-activity !HOME_ORIG!
call :log "HOME restaurado a !HOME_ORIG! (revisar salida)"
goto desinstalar

:sin_home
echo AVISO: no hay un HOME original valido en el respaldo. Se omite este paso.
echo En el radio: pulsa Home y elige el launcher de fabrica.
call :log "AVISO: sin HOME original valido en el respaldo"

:desinstalar
echo.
echo [2/4] Desinstalando com.simple.carlauncher...
adb -s %RADIO% uninstall com.simple.carlauncher
call :log "uninstall com.simple.carlauncher ejecutado (revisar salida)"

echo.
echo [3/4] Habilitando paquetes que estaban habilitados...
adb -s %RADIO% shell pm list packages -d > "%TEMP%\restaurar_deshabilitados.txt"
for /f "tokens=2 delims=:" %%p in ('findstr /b /c:"package:" "%BK%\paquetes_habilitados.txt"') do call :reactivar "%%p"

echo.
echo [4/4] Reinstalando APK de fabrica que falten...
adb -s %RADIO% shell pm list packages > "%TEMP%\restaurar_instalados.txt"
if exist "%BK%\apk\*.apk" (
  pushd "%BK%\apk"
  for %%f in (*.apk) do call :reinstalar "%%~ff"
  popd
) else (
  echo No hay carpeta apk en el respaldo. Se omite este paso.
)

call :log "restauracion desde %BK% terminada"
echo.
echo LISTO. Revisa C:\RadioBackup\cambios_aplicados.txt
echo Verifica que el radio arranque y muestre el launcher de fabrica.
exit /b 0

:reactivar
set "PKG=%~1"
findstr /x /c:"package:!PKG!" "%TEMP%\restaurar_deshabilitados.txt" >nul
if errorlevel 1 goto :eof
adb -s %RADIO% shell pm enable !PKG!
call :log "pm enable !PKG! (revisar salida)"
goto :eof

:reinstalar
set "PKG=%~n1"
findstr /x /c:"package:!PKG!" "%TEMP%\restaurar_instalados.txt" >nul
if not errorlevel 1 goto :eof
echo Reinstalando !PKG! desde %~1
adb -s %RADIO% install -r "%~1" > "%SALIDA%" 2>&1
type "%SALIDA%"
call :log "install -r de !PKG! (revisar salida)"
goto :eof

:log
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH:mm:ss"') do set "TS=%%t"
echo [%TS%] %~1>> "%LOG%"
echo [%TS%] %~1
goto :eof
