@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
rem ============================================================
rem particiones.bat - respaldo COMPLETO de solo lectura de las particiones del radio
rem (XY AUTO 9216BJ, MediaTek AC8227L). No escribe en ninguna particion ni en /dev/block.
rem Uso: particiones.bat respaldar [IP:PUERTO]
rem Requiere root por ADB (ver T12). Guarda todo en C:\RadioBackup\particiones\<fecha_hora>\
rem Omite userdata, cache, metadata y toda particion de 2 GiB o mas.
rem Usa /data/local/tmp como paso intermedio, de una en una, y borra cada archivo.
rem ============================================================

set "MODO=%~1"
if /i not "%MODO%"=="respaldar" (
  echo Uso: particiones.bat respaldar [IP:PUERTO]
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
if not exist "%BK%" (
  echo ERROR: no existe %BK%. Ejecuta primero respaldo.bat.
  exit /b 1
)

echo [1/6] Activando root en ADB...
adb -s %RADIO% root >nul 2>&1
ping -n 5 127.0.0.1 >nul
adb connect %RADIO% >nul 2>&1
ping -n 3 127.0.0.1 >nul
adb -s %RADIO% shell id | findstr /c:"uid=0" >nul
if errorlevel 1 (
  echo ERROR: no se obtuvo root. No se hizo nada.
  exit /b 1
)

for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "STAMP=%%t"
set "DEST=%BK%\particiones\%STAMP%"
mkdir "%DEST%"
if not exist "%DEST%" (
  echo ERROR: no se pudo crear %DEST%.
  exit /b 1
)
set "TABLA=%DEST%\tabla.txt"
set "SUMAS=%DEST%\sumas.txt"

echo [2/6] Guardando la tabla de particiones en %TABLA%...
>"%TABLA%" echo === ls -l /dev/block/platform/*/*/by-name/ ===
adb -s %RADIO% shell "ls -l /dev/block/platform/*/*/by-name/ 2>&1" >>"%TABLA%"
>>"%TABLA%" echo.
>>"%TABLA%" echo === ls -l /dev/block/by-name/ ^(respaldo^) ===
adb -s %RADIO% shell "ls -l /dev/block/by-name/ 2>&1" >>"%TABLA%"
>>"%TABLA%" echo.
>>"%TABLA%" echo === /proc/partitions ===
adb -s %RADIO% shell cat /proc/partitions >>"%TABLA%"
>>"%TABLA%" echo.
>>"%TABLA%" echo === /proc/mounts ===
adb -s %RADIO% shell cat /proc/mounts >>"%TABLA%"
>>"%TABLA%" echo.
>>"%TABLA%" echo === ro.product.board ===
adb -s %RADIO% shell getprop ro.product.board >>"%TABLA%"
>>"%TABLA%" echo.
>>"%TABLA%" echo === nombre / sectores de 512 B / bloque ===
adb -s %RADIO% shell "D=/dev/block/platform/*/*/by-name; [ -d /dev/block/by-name ] && D=/dev/block/by-name; for f in $D/*; do n=${f##*/}; b=`readlink -f $f`; k=${b##*/}; s=`cat /sys/class/block/$k/size 2>/dev/null`; echo $n $s $b .; done" >"%DEST%\lista.tmp"
type "%DEST%\lista.tmp" >>"%TABLA%"
for /f %%c in ('type "%DEST%\lista.tmp" ^| find /c "/dev/"') do set "NPART=%%c"
if not defined NPART set "NPART=0"
if "%NPART%"=="0" (
  echo ERROR: no se encontro ninguna particion en by-name. Revisa %TABLA%.
  exit /b 1
)
type "%TABLA%"
echo.
echo Se encontraron %NPART% particiones. Se copiaran en modo SOLO LECTURA ^(dd a /data/local/tmp, pull y borrado, una por una^).
if defined RADIO_SI (set "ERRORLEVEL=") else choice /c SN /m "Iniciar el respaldo completo"
if errorlevel 2 (
  echo Cancelado. No se cambio nada.
  exit /b 0
)

rem Algoritmo de suma: SHA-256 si el radio tiene sha256sum, si no MD5
set "ALG=SHA256"
set "CMDH=sha256sum"
adb -s %RADIO% shell "sha256sum /dev/null 2>&1" | findstr /c:"e3b0c442" >nul
if errorlevel 1 (
  set "ALG=MD5"
  set "CMDH=md5sum"
)
echo Suma de verificacion: %ALG%
>"%SUMAS%" echo # estado algoritmo=%ALG% nombre suma
set /a NFAIL=0
set /a NOK=0
set /a TOTKB=0
set "FALLAS="

echo [3/6] Copiando particiones by-name...
for /f "usebackq tokens=1-3" %%a in ("%DEST%\lista.tmp") do call :proc "%%a" "%%b" "%%c"
del "%DEST%\lista.tmp" >nul 2>&1

echo [4/6] Areas de arranque eMMC ^(preloader^)...
for %%b in (mmcblk0boot0 mmcblk0boot1) do call :boot %%b

echo [5/6] Escribiendo LEEME.txt...
>"%DEST%\LEEME.txt" echo RESPALDO DE PARTICIONES - Radio XY AUTO 9216BJ ^(MediaTek AC8227L^)
>>"%DEST%\LEEME.txt" echo Fecha: %STAMP%
>>"%DEST%\LEEME.txt" echo.
>>"%DEST%\LEEME.txt" echo Que es esto: copias exactas, de solo lectura, de las particiones del radio ^(*.img^),
>>"%DEST%\LEEME.txt" echo mas tabla.txt ^(tabla de particiones y montajes^) y sumas.txt ^(%ALG% de cada imagen^).
>>"%DEST%\LEEME.txt" echo No se copiaron userdata, cache, metadata ni particiones de 2 GiB o mas.
>>"%DEST%\LEEME.txt" echo Las areas mmcblk0boot0 y mmcblk0boot1 contienen el preloader.
>>"%DEST%\LEEME.txt" echo.
>>"%DEST%\LEEME.txt" echo Para que sirve: estas imagenes, junto con la herramienta MediaTek SP Flash Tool y un
>>"%DEST%\LEEME.txt" echo archivo scatter para MT8227L, permiten restaurar el radio si queda inutilizable.
>>"%DEST%\LEEME.txt" echo.
>>"%DEST%\LEEME.txt" echo ADVERTENCIA: restaurar SOLO con acompanamiento de alguien que sepa. Un flasheo
>>"%DEST%\LEEME.txt" echo incorrecto ^(scatter equivocado, preloader mal grabado^) puede dejar el radio sin
>>"%DEST%\LEEME.txt" echo arrancar y sin ADB. No grabes nada por tu cuenta.
>>"%DEST%\LEEME.txt" echo.
>>"%DEST%\LEEME.txt" echo Copia esta carpeta completa a un segundo disco o a la nube.
echo.

echo [6/6] Resumen
set /a TOTMB=TOTKB/1024
call :log "particiones: %NOK% copiadas OK, %NFAIL% fallidas, %TOTMB% MB en %DEST%"
echo Tamano total respaldado: %TOTMB% MB
if %NFAIL% GTR 0 (
  echo ATENCION: fallaron %NFAIL% particiones:%FALLAS%
  echo Revisa %SUMAS% y repite el respaldo.
) else (
  echo Todas las particiones copiadas coinciden con la suma del radio.
)
echo.
echo IMPORTANTE: copia la carpeta %DEST% a un segundo disco o a la nube.
echo No se escribio en ninguna particion del radio.
if %NFAIL% GTR 0 exit /b 2
exit /b 0

:boot
adb -s %RADIO% shell "[ -e /dev/block/%~1 ] && echo SI" | findstr /c:"SI" >nul
if errorlevel 1 (
  echo %~1: no existe, se omite.
  exit /b 0
)
set "BS="
for /f %%s in ('adb -s %RADIO% shell "cat /sys/class/block/%~1/size 2>/dev/null"') do set "BS=%%s"
call :proc "%~1" "!BS!" "/dev/block/%~1"
exit /b 0

:proc
set "N=%~1"
set "SEC=%~2"
set "BLK=%~3"
set "TMPR=/data/local/tmp/%N%.img"
set "SKIP="
for %%x in (userdata cache metadata) do if /i "%N%"=="%%x" set "SKIP=excluida por regla"
echo %SEC%| findstr /r "^[0-9][0-9]*$" >nul
if errorlevel 1 set "SKIP=sin tamano valido"
echo %BLK%| findstr /b /c:"/dev/" >nul
if errorlevel 1 set "SKIP=sin dispositivo de bloque"
if not defined SKIP if %SEC% GEQ 4194304 set "SKIP=2 GiB o mas"
if defined SKIP (
  echo   %N%: omitida ^(!SKIP!^)
  >>"%SUMAS%" echo OMITIDA %ALG% %N% !SKIP!
  exit /b 0
)
set /a MB=SEC/2048
set /a NEED=SEC/2+102400
set "LIBRE=0"
for /f "skip=1 tokens=4" %%f in ('adb -s %RADIO% shell df -k /data') do set "LIBRE=%%f"
if %LIBRE% LSS %NEED% (
  call :fallo "poco espacio en /data"
  exit /b 0
)
echo   %N% ^(%MB% MB^)...
adb -s %RADIO% shell "dd if=%BLK% of=%TMPR% bs=1048576 2>&1"
if errorlevel 1 (
  call :fallo "dd fallo"
  goto proc_fin
)
adb -s %RADIO% shell "[ $(( $(stat -c %%s %TMPR%) / 512 )) -eq %SEC% ] && echo TAMOK" | findstr /c:"TAMOK" >nul
if errorlevel 1 (
  call :fallo "tamano de la copia no coincide"
  goto proc_fin
)
set "HD="
for /f "tokens=1" %%h in ('adb -s %RADIO% shell %CMDH% %TMPR%') do set "HD=%%h"
if not defined HD (
  call :fallo "no se pudo calcular la suma en el radio"
  goto proc_fin
)
adb -s %RADIO% pull %TMPR% "%DEST%\%N%.img" >nul
if not exist "%DEST%\%N%.img" (
  call :fallo "adb pull fallo"
  goto proc_fin
)
set "HP="
for /f "skip=1 tokens=*" %%h in ('certutil -hashfile "%DEST%\%N%.img" %ALG%') do if not defined HP set "HP=%%h"
set "HP=!HP: =!"
if /i not "!HP!"=="!HD!" (
  call :fallo "la suma en el PC no coincide"
  goto proc_fin
)
>>"%SUMAS%" echo OK %ALG% %N% !HP!
set /a NOK+=1
set /a TOTKB+=SEC/2
echo     OK !HP!
:proc_fin
adb -s %RADIO% shell rm -f %TMPR%
exit /b 0

:fallo
echo     FALLO: %~1
>>"%SUMAS%" echo FAIL %ALG% %N% %~1
set /a NFAIL+=1
set "FALLAS=!FALLAS! %N%"
exit /b 0

:log
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH:mm:ss"') do set "TS=%%t"
echo [%TS%] %~1>> "%LOG%"
echo [%TS%] %~1
goto :eof
