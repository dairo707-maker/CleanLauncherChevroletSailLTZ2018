# Guía para el agente local (Antigravity / Gemini Flash)

> **Nota:** el agente local puede ser ahora **Google Argon 4 (u otro agente local)**. Las reglas de esta guía aplican igual a cualquiera de ellos.

Esta guía es para el agente que ejecuta los scripts de `tools\windows\` en la PC del dueño.
El dueño es quien decide. Si algo no está claro, **pregunta al humano antes de actuar**.

## 1. Tu rol

Solo **ejecutas, verificas y reportas**.

Prohibido, sin excepción:

- Modificar código Java (`src/`), `AndroidManifest.xml`, `build.sh` o cualquier archivo que no esté en esta guía.
- Tocar "Parameter Settings" o el MCU del radio. Solo el humano puede hacerlo.
- Hacer un "factory reset" o restaurar de fábrica.
- Ejecutar comandos `adb` que no aparezcan en esta guía. Si crees que necesitas uno, pregunta.
- Subir (`git push`) archivos distintos a los indicados en cada tarea.
- Subir `tools\sail_source.png`.
- Usar `--force` en git, reescribir historial o cambiar de rama.

Si una tarea falla, **no improvises**. Anota el error, ejecuta T7 si hay que revertir, y reporta.

## 2. Requisitos previos (verifica antes de T1)

Ejecuta en `cmd`, dentro de la carpeta raíz del repo (la que contiene `build.sh`):

1. `adb version` imprime una línea que empieza con `Android Debug Bridge version`.
2. `where adb` imprime una ruta que termina en `adb.exe`.
3. `py --version` imprime `Python 3.`.
4. `git status` y `git branch --show-current` imprimen `claude/amazing-lamport-auessa`.
5. Confirma con el humano: radio encendido, Wi-Fi del radio activo, IP `192.168.1.7` reservada en el router.

Si alguno falla, reporta cuál y detente.

Formato del reporte después de cada tarea:

```
TAREA: T<n> <nombre>
RESULTADO: OK | FALLO | PENDIENTE_HUMANO
CODIGO DE SALIDA: <número>
ULTIMAS LINEAS: <las últimas 10 líneas de la salida>
```

## 3. Tareas

Todas las tareas se hacen desde la carpeta raíz del repo, en `cmd`.

### T1. Conectar

- Comando: `tools\windows\conectar.bat`
- Éxito: aparece la línea `CONECTADO: 192.168.1.7:5555 esta en estado device.` y el código de salida es `0`.
- Si el código es `2` (la salida dice `ESTADO: unauthorized`): **pide al humano** aceptar el mensaje en la pantalla del radio con "Permitir siempre". Luego repite T1 una vez.
- Si el código es `1` (la salida dice `FALLO:`): **detente**. Pide al humano el Plan B (instalar por USB, ver TUTORIAL.md sección 9). No sigas con las demás tareas.
- Reporta: estado final (`device`, `unauthorized` o fallo) y código de salida.

### T2. Respaldo

- Antes del comando, **pide al humano** que haga "Export the config" en "Parameter Settings" del radio, guardando en USB. Tú no tocas el radio.
- Comando: `tools\windows\respaldo.bat`
- Éxito: la salida termina con `LISTO. Respaldo guardado en:` seguido de una línea `C:\RadioBackup\AAAA-MM-DD_HHMM`.
- Verifica con `dir C:\RadioBackup\<carpeta>`: deben existir `README_RESPALDO.txt`, `paquetes_todos.txt`, `launcher_por_defecto.txt` y la carpeta `apk`.
- Reporta: la ruta final y la línea de `launcher_por_defecto.txt` que contiene `/` (el componente HOME original).

### T3. Diagnóstico y subida

1. Comando: `git pull`
2. Comando: `tools\windows\diagnostico.bat`
   - El script muestra una cuenta de 60 segundos. **El humano** hace las pruebas (cambiar a Radio, cambiar a Bluetooth música, poner reversa con el auto detenido, encender y apagar luces). Tú no tocas el radio.
   - Éxito: la salida contiene `Diagnostico terminado en:`.
   - Verifica que existan y tengan contenido: `diagnostico\diagnostico_radio.txt`, `diagnostico\broadcasts.txt`, `diagnostico\logcat_eventos.txt`.
3. Subida (solo si T2 y T3 terminaron bien):
   - `git add diagnostico backup\xyauto`
   - `git commit -m "Diagnóstico del radio"`
   - `git push origin claude/amazing-lamport-auessa`
   - Éxito: el último mensaje de `git push` menciona `claude/amazing-lamport-auessa`.
4. Reporta: rutas creadas y el hash del commit. Si `git commit` dice "nothing to commit", reporta eso y no lo trates como error.

### T4. Imagen del carro

1. **Pide al humano** descargar la imagen desde `https://www.pngegg.com/es/png-wylrc` y guardarla como `tools\sail_source.png`. Si el archivo no existe, detente y pregunta.
2. Comando: `py tools\car_image.py tools\sail_source.png`
   - Éxito: aparece `Listo:` seguido de las dos rutas. Deben existir `res\drawable-nodpi\sail.png` y `res\drawable-nodpi\sail_lights.png`.
   - Si aparece `La imagen no tiene fondo transparente`: detente y pide al humano otra imagen con fondo transparente.
   - Si aparece `No module named PIL`: ejecuta `py -m pip install pillow` y repite el comando.
3. Subida (solo esos dos archivos):
   - `git add res\drawable-nodpi\sail.png res\drawable-nodpi\sail_lights.png`
   - `git commit -m "Imagen del carro Sail"`
   - `git push origin claude/amazing-lamport-auessa`
4. Reporta: tamaños de los PNG (`dir res\drawable-nodpi\sail*.png`) y el resultado del push.

### T5. Descargar APK e instalar

1. Comando: `git pull`
2. Obtén el ID de la última ejecución exitosa de "Build APK" en la rama:
   - `gh run list --workflow build.yml --branch claude/amazing-lamport-auessa --status success --limit 1 --json databaseId --jq ".[0].databaseId"`
   - Éxito: imprime un número. Si no imprime nada, detente y reporta. Puede que `gh` no esté autenticado.
3. Descarga el artefacto (reemplaza `<ID>` por el número del paso 2):
   - `gh run download <ID> --dir build\artifact`
4. Copia el APK a la ruta por defecto:
   - PowerShell: `Copy-Item build\artifact\CleanLauncher-*\CleanLauncher.apk build\CleanLauncher.apk -Force`
   - Verifica que exista `build\CleanLauncher.apk`.
5. Instala: `tools\windows\instalar.bat`
   - Éxito: aparecen `Success` (de `adb install`) y la línea `LISTO. Cambios registrados en C:\RadioBackup\cambios_aplicados.txt`.
   - Si aparece `AVISO: set-home-activity no confirmado`: **pide al humano** fijar HOME a mano (Home, "Inicio", "Siempre"). Eso no es un fallo de la tarea, repórtalo.
   - Si aparece `ERROR: la instalacion fallo`: detente y ve a T7 solo si el radio quedó raro. Si no, reporta.
6. Reporta: el `Success` o el error, y si HOME quedó fijado o hay que hacerlo a mano.

### T6. Checklist de pruebas

El humano hace las pruebas físicas. Tú anotas los resultados.

1. Crea `diagnostico\pruebas.md` con esta tabla y pide al humano el resultado de cada fila:

   ```
   | # | Prueba | Resultado (OK/FALLO) | Observación |
   |---|--------|----------------------|-------------|
   | 1 | HOME: al pulsar Home aparece Clean Launcher | | |
   | 2 | Reinicio del radio (apagar 30 s y encender): vuelve el launcher | | |
   | 3 | ACC off/on: al dar contacto aparece el launcher y la fuente de audio se restaura | | |
   | 4 | Botones del volante: volumen, cambio de fuente, siguiente/anterior | | |
   | 5 | Radio: sintonía y presets | | |
   | 6 | Bluetooth: conectar el teléfono y reproducir música | | |
   | 7 | Volumen: el overlay aparece al subir o bajar | | |
   | 8 | Pantalla apagada: los botones del volante siguen funcionando | | |
   ```

2. Verificación de solo lectura para la fila 1: `adb -s 192.168.1.7:5555 shell dumpsys activity activities` y busca la línea que contiene `mResumedActivity`. Debe mencionar `com.simple.carlauncher`.
3. Sube los resultados (solo `diagnostico\pruebas.md`):
   - `git add diagnostico\pruebas.md`
   - `git commit -m "Pruebas en el radio"`
   - `git push origin claude/amazing-lamport-auessa`
4. Reporta: la tabla completa con resultados. Cualquier FALLO se reporta primero.

### T7. Revertir (solo si algo falla)

Úsalo solo si el humano lo pide o si una prueba crítica falla (HOME, arranque, audio).

1. Comando: `tools\windows\restaurar.bat`
2. El script pregunta `[S,N]?`. **Pide al humano** que responda. Tú no respondes por él.
3. Éxito: la salida termina con `LISTO. Revisa C:\RadioBackup\cambios_aplicados.txt`.
4. Después: `tools\windows\conectar.bat` (T1) para confirmar que el radio responde.
5. Reporta: las líneas de `cambios_aplicados.txt` generadas por la reversión. Si el radio no responde, **no** hagas nada más: pide al humano llevar el radio con un técnico.

### T8. Optimizar (opcional, solo si el humano lo pide)

Úsalo solo si el humano lo pide. No hace falta para el funcionamiento del launcher.

1. Comando: `tools\windows\optimizar.bat`
2. El script muestra el plan y pregunta `[S,N]?`. **Pide al humano** que responda. Tú no respondes por él.
3. Éxito: la salida termina con `LISTO. Optimizacion aplicada.` y luego pide reiniciar el radio.
4. Después: pide al humano reiniciar el radio (apagar 30 s y encender). Luego ejecuta T6, fila 1 y fila 2, para confirmar que el launcher sigue funcionando.
5. Para revertir: `tools\windows\revertir_optimizacion.bat` (también pide confirmación). Éxito: `LISTO. Revertida la optimizacion.`
6. Reporta: las líneas de `cambios_aplicados.txt` generadas por la optimización. Si una prueba de T6 falla después de optimizar, **no** hagas nada más: revierte con el paso 5 y avisa al humano.

### T9. Experimento (radio FM, Bluetooth, ecualizador)

Qué hace: descarga 4 APK de fábrica para análisis de strings y prueba comandos `am broadcast` / `am start` / `input keyevent` para sintonizar la radio, marcar por Bluetooth y listar efectos de audio. No cambia configuración persistente del sistema.

Requiere al humano en el carro: radio encendido con la app de radio de fábrica abierta. Para la sección Bluetooth, el teléfono debe estar conectado y el humano debe elegir el número de prueba (no su buzón de voz) y colgar desde el teléfono.

1. Comando: `tools\windows\experimento.bat`
2. El script pide `[S,N]` antes de cada prueba y el número a marcar. **Pide al humano** que responda cada pregunta. Tú no respondes por él.
3. Éxito: la salida termina con `Experimento terminado.`
4. Resultados: `diagnostico\experimento.txt` y `diagnostico\apk\`.
5. Subir solo esos dos: `git add diagnostico\experimento.txt diagnostico\apk`, luego commit y push.
6. Reporta: cada respuesta del humano (cambió la emisora, marcó) y las líneas de ecualizador encontradas.

### T10. Arranque (arranque lento, quickboot)

Qué hace: diagnostica por qué el radio hace un arranque completo (logo Chevrolet, logo Android, launcher) al dar ACC en vez de reanudar rápido. Es **solo lectura** en el radio. Guarda `getprop`, `settings`, `dumpsys power`, whitelist de `deviceidle`, servicios del launcher, el APK de `com.autochips.quickbootmanager` y archivos qb de `/system/etc` y `/vendor/etc`. Después pide al humano apagar y encender el ACC, y captura logcat y dmesg.

Requiere al humano en el carro: contacto encendido y el humano disponible para las pausas y la pregunta del logo.

1. Comando: `tools\windows\arranque.bat`
2. El script pide al humano apagar el ACC (tecla), espera 20 s, y pide encenderlo (tecla) cuando aparezca el launcher. **Tú no tocas el contacto ni el radio.** El ADB por Wi-Fi se corta; el script reintenta `adb connect` hasta 3 minutos.
3. El script pregunta `[S,N]` por el logo de Chevrolet. **Pide al humano** que responda. Tú no respondes por él.
4. Éxito: la salida termina con `Arranque terminado en:`. Si aparece `RECONEXION FALLIDA` o `ERROR: el radio no volvio`, detente y reporta.
5. Verifica que existan: `diagnostico\arranque.txt` y `diagnostico\arranque_logcat.txt`. `diagnostico\arranque_dmesg.txt` puede estar vacío o contener un error de permisos; repórtalo tal cual.
6. Comandos `adb` usados por esta tarea (son todos de lectura): `get-state`, `connect`, `shell getprop`, `shell settings list system|global`, `shell dumpsys power|deviceidle whitelist|activity services com.simple.carlauncher`, `shell pm path com.autochips.quickbootmanager`, `pull` (APK y archivos qb), `shell ls`, `shell uptime`, `logcat -d -b all -t 3000`, `shell dmesg`.
7. Subida (solo si el humano lo pide): `git add diagnostico\arranque*.txt diagnostico\apk\com.autochips.quickbootmanager.apk diagnostico\qb`, luego commit y push a `claude/amazing-lamport-auessa`.
8. Reporta: la respuesta del logo (`S` o `N`), si `uptime` indica reinicio completo, y las rutas creadas.

## T11 — Llave de firma fija (una sola vez)

Objetivo: que todos los APK salgan con la misma firma y se instalen encima sin desinstalar (desinstalar borra km, tanqueadas, turnos y mantenimiento).

1. **Antes**, el humano exporta sus datos: Combustible y costos → Exportar a USB, y anota el km del tablero.
2. Generar la llave en el PC. El **humano** elige la contraseña y la escribe él mismo; el agente no la anota en archivos, commits ni chats. `keytool` viene con el JDK o con Android Studio (`C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`).
   ```
   keytool -genkeypair -keystore C:\RadioBackup\cleanlauncher.keystore -alias cleanlauncher -keyalg RSA -keysize 2048 -validity 20000 -dname "CN=Clean Launcher Sail"
   ```
3. Copiar la llave en base64 al portapapeles (PowerShell):
   ```
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\RadioBackup\cleanlauncher.keystore")) | Set-Clipboard
   ```
4. El **humano** crea dos secretos en GitHub: repositorio → Settings → Secrets and variables → Actions → New repository secret:
   - `SIGNING_KEYSTORE_B64` = lo copiado en el paso 3.
   - `SIGNING_KEYSTORE_PASS` = la contraseña del paso 2.
5. Guardar `C:\RadioBackup\cleanlauncher.keystore` en un lugar seguro (si se pierde, habrá que desinstalar una vez más). **Nunca** subirlo al repositorio.
6. Pedir al orquestador un build nuevo (o re-ejecutar el workflow). Esta primera vez sí hay que desinstalar (`adb -s 192.168.1.7:5555 uninstall com.simple.carlauncher`) e instalar con T5; desde ahí, siempre encima.
7. Reportar: "secretos creados" y el resultado de T5. No reportar la contraseña.

## T12 — ¿Hay acceso root? (solo lectura)

Para saber si se puede añadir el launcher a la lista de QuickBoot (`/system/etc/qb_list.xml`). No modificar nada.
```
adb -s 192.168.1.7:5555 shell id
adb -s 192.168.1.7:5555 root
adb -s 192.168.1.7:5555 shell id
adb -s 192.168.1.7:5555 shell ls -l /system/xbin/su /system/bin/su /sbin/su
adb -s 192.168.1.7:5555 shell getprop ro.debuggable
```
Guardar la salida en `diagnostico\root.txt`. Subir también lo que faltó de T10: `diagnostico\arranque*.txt`, `diagnostico\qb\qb_list.xml` y `diagnostico\apk\com.autochips.quickbootmanager.apk` (con `git add -f` si `.gitignore` los excluye).

## T13 — Launcher en la lista blanca de QuickBoot (requiere root, T12)

Evita que el radio cierre Clean Launcher al apagar la llave. Cambia un archivo del sistema, con copia del original y reversión.
1. `tools\windows\qb_lista.bat aplicar` — guarda `C:\RadioBackup\qb_list.xml.orig`, copia la línea del launcher de fábrica cambiando el paquete, valida el XML, pide confirmación, escribe y ofrece reiniciar.
2. Éxito: el `grep -c` final muestra `1` o más, y tras reiniciar + apagar/prender la llave, `adb shell pidof com.simple.carlauncher` muestra el **mismo** número de proceso antes y después.
3. Revertir: `tools\windows\qb_lista.bat revertir`.
4. Reportar la línea agregada, el `ls -lZ` y el resultado del punto 2. Subir `C:\RadioBackup\qb_list.xml.orig` como `diagnostico\qb\qb_list.xml` (con `git add -f`) si aún no está en el repo.

## T14 — Respaldo completo de particiones (solo lectura, requiere root, T12)

Copia todas las particiones del radio al PC para poder restaurarlo con MediaTek SP Flash Tool si algo sale muy mal. No escribe en ninguna particion; solo usa `/data/local/tmp` como paso intermedio (una imagen a la vez, se borra al terminar). Omite `userdata`, `cache`, `metadata` y particiones de 2 GiB o mas. Necesita espacio libre en `/data` y en el PC (varios GB).
1. `tools\windows\particiones.bat respaldar` — muestra la tabla, pide confirmacion, copia cada particion y las areas `mmcblk0boot0/1` (preloader).
2. Esperado: por cada particion `OK <suma>`; al final "Todas las particiones copiadas coinciden con la suma del radio" y el tamano total. Carpeta: `C:\RadioBackup\particiones\<fecha_hora>\` con `*.img`, `tabla.txt`, `sumas.txt`, `LEEME.txt`. Puede tardar varios minutos.
3. Revertir: no hay nada que revertir (solo lectura). Si se cancela a medias, borrar `/data/local/tmp/*.img` con `adb shell rm`.
4. Reportar: cantidad OK/FALLO, lista de fallas (si las hay), tamano total y contenido de `tabla.txt` y `sumas.txt`. No subir las `*.img` al repositorio; pedir al humano que copie la carpeta a un segundo disco o a la nube. **Nunca** restaurar/flashear sin guia del orquestador.

## T18 — Clean Launcher como app privilegiada del sistema (requiere root, T12 y T14)

Para que Android nunca cierre el launcher. Escribe en `/system` (quedan ~114 MB libres; el APK ocupa pocos MB). Exige haber hecho T14 y tener instalado `com.simple.carlauncher` (T5).
1. `tools\windows\launcher_sistema.bat estado` — antes y despues. Muestra `codePath`, `flags`, `pkgFlags`, `privateFlags`.
2. `tools\windows\launcher_sistema.bat aplicar` — comprueba T14, espacio (`df /system` >= 2x APK + 10 MB), pide confirmacion, copia el APK a `/system/priv-app/CleanLauncher/CleanLauncher.apk` (755/644, root:root, restorecon), verifica con `cmp`, vuelve `/system` a solo lectura y ofrece reiniciar. Si `ro.control_privapp_permissions=enforce` avisa del riesgo y pide confirmar.
3. Esperado tras reiniciar: `estado` muestra `pkgFlags` con `SYSTEM` y `privateFlags` con `PRIVILEGED`; el `codePath` puede seguir en `/data/app` (es la actualizacion); `adb install -r` sigue funcionando por la misma firma.
4. Revertir: `tools\windows\launcher_sistema.bat revertir` (borra `/system/priv-app/CleanLauncher`; la copia de `/data` queda como app normal). Si el radio no arranca, parar y avisar al orquestador (el respaldo T14 es la red de seguridad).
5. Reportar: salida de `estado` antes/despues, `ls -lZ` del directorio y si el launcher sobrevive al apagar/prender la llave (`adb shell pidof com.simple.carlauncher` igual antes y despues).
