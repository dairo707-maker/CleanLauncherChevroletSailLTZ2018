# Tutorial: Clean Launcher en el Chevrolet Sail LTZ 2018

Guía para el dueño del auto. No necesitas saber programar. Sigue los pasos en orden.

## 1. Qué hace este proyecto

Clean Launcher es una pantalla de inicio limpia y rápida para el radio del auto (XY AUTO 9216BJ, Android). Reemplaza el menú de fábrica por una interfaz más simple.

Los scripts de `tools\windows\` te permiten, desde tu PC con Windows:

- Conectarte al radio por Wi-Fi.
- Hacer una copia de seguridad del radio (antes de tocar nada).
- Revisar el estado del radio (diagnóstico).
- Instalar la app.
- Deshacer todo si algo sale mal.

Todo se hace por Wi-Fi con una herramienta llamada ADB. Los scripts solo leen del radio, excepto `instalar.bat` y `restaurar.bat`.

> **Advertencia:** el launcher nació para un radio NWD. En el XY AUTO del Sail, los botones de radio FM y Bluetooth abren las apps de fábrica hasta que el diagnóstico nos diga cómo controlarlas directamente. Por eso el orden es: respaldo, diagnóstico, instalación, pruebas, y siempre con posibilidad de revertir.

## 2. Lo que necesitas

- Una PC con Windows 10 u 11.
- El radio encendido y conectado a la misma red Wi-Fi que la PC (el router de tu casa).
- Un USB vacío (para el respaldo de configuración y el Plan B).
- Opcional: Google Antigravity, si quieres que un agente ejecute los pasos técnicos.

## 3. Paso 1: instalar platform-tools (ADB)

1. Descarga el archivo: https://dl.google.com/android/repository/platform-tools-latest-windows.zip
2. Descomprímelo en `C:\`. Al terminar debe existir el archivo `C:\platform-tools\adb.exe`.
3. Agrega la carpeta al PATH. Elige una opción:

   **Opción A (PowerShell, como usuario normal):**

   ```
   [Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path","User") + ";C:\platform-tools", "User")
   ```

   **Opción B (con mouse):** Inicio → escribe "variables de entorno" → "Editar las variables de entorno de la cuenta" → selecciona `Path` → Editar → Nuevo → escribe `C:\platform-tools` → Aceptar.

4. Cierra todas las ventanas de cmd y PowerShell, y abre una nueva.
5. Verifica: escribe `adb version` en cmd. Debe aparecer `Android Debug Bridge version`.

Si la opción A se ejecuta dos veces, la ruta queda repetida. No es un problema, pero puedes quitarla desde la opción B.

## 4. Paso 2: IP fija del radio en el router

El radio debe tener siempre la misma dirección, `192.168.1.7`. Si no, ADB no lo encuentra.

1. Entra al router (la dirección está en la etiqueta del router; a menudo `192.168.1.1`).
2. Busca la sección "Reserva DHCP", "DHCP reservation" o "Asignar IP por dirección MAC".
3. Asocia la dirección MAC del radio a `192.168.1.7`. La MAC aparece en los ajustes de Wi-Fi del radio.
4. Guarda y reinicia el Wi-Fi del radio (apágalo y préndelo).

Si no sabes hacerlo, pide ayuda a quien administra la red. Y verifica que la depuración ADB por red (puerto 5555) esté activada en el radio. Si no aparece en los ajustes, pide al técnico que la active.

## 5. Paso 3: copia de configuración en USB (obligatoria)

Antes de instalar nada, guarda la configuración de fábrica del radio:

1. En el radio, entra a **Parameter Settings**.
2. Elige **Export the config** y guarda la copia en el USB.
3. No cambies nada más en Parameter Settings. Es la configuración de fábrica del radio.

Guarda el USB en un lugar seguro.

## 6. Paso 4: ejecutar los scripts en orden

### Cómo abrir la terminal en la carpeta del proyecto

1. Abre la carpeta del proyecto en el Explorador de Windows.
2. Haz clic en la barra de direcciones, escribe `cmd` y presiona Enter.
3. Se abre una ventana negra. Escribe los comandos de abajo y presiona Enter.

Los scripts se ejecutan desde esa ventana. No los abras con doble clic, porque la ventana se cierra al terminar y no podrás leer los mensajes.

### Orden de ejecución

**A. Conectar.** Escribe:

```
tools\windows\conectar.bat
```

- Éxito: aparece `CONECTADO: 192.168.1.7:5555 esta en estado device.`
- Si dice `ESTADO: unauthorized`: mira la pantalla del radio, acepta el mensaje de depuración USB y marca **"Permitir siempre"**. Luego vuelve a escribir el comando.
- Si dice `FALLO`: ve a la sección 9 (Plan B).

**B. Respaldo.** Escribe:

```
tools\windows\respaldo.bat
```

- Crea una carpeta en `C:\RadioBackup\` con la fecha y hora. Al final muestra la ruta.
- Tarda varios minutos. No apagues el radio ni la PC.
- Copia esa carpeta también a un USB.

**C. Diagnóstico (opcional, recomendado).** Escribe:

```
tools\windows\diagnostico.bat
```

- Cuando aparezca la cuenta de 60 segundos, haz durante ese tiempo: cambia a Radio, cambia a Bluetooth música, pon reversa con el auto detenido, enciende y apaga las luces.
- Al final se crea la carpeta `diagnostico` en el proyecto. Puedes enviarla al equipo técnico.

**D. Instalar la app.** Escribe:

```
tools\windows\instalar.bat
```

- Solo funciona si el paso B se hizo antes. Si no, el script te lo dice.
- Éxito: aparece `LISTO`. Los cambios quedan anotados en `C:\RadioBackup\cambios_aplicados.txt`.
- Si aparece `AVISO: no se pudo fijar HOME`: en el radio pulsa el botón **Home**, elige **"Inicio"** y luego **"Siempre"**.

Después de instalar, prueba: apaga y enciende el radio, apaga y enciende el auto, cambia de fuente, usa los botones del volante.

## 7. Paso 5: usar Google Antigravity (opcional)

Si quieres que un agente haga los pasos técnicos:

1. Abre la carpeta del proyecto en Antigravity.
2. Pide al agente: "Lee `docs/AGENTE_LOCAL.md` y ejecuta las tareas T1 a T6 en orden. Pide confirmación al humano antes de cada paso que lo requiera."
3. El agente no debe tocar "Parameter Settings" ni hacer restauraciones de fábrica. La guía lo prohíbe.
4. Revisa los reportes del agente antes de aceptar que todo está bien.

Antes de que el agente haga `git push`, revisa qué archivos va a subir.

## 8. Cómo revertir todo

Si algo no funciona, o quieres volver al estado original:

1. En la terminal del proyecto escribe:

   ```
   tools\windows\restaurar.bat
   ```

2. El script pide confirmación. Escribe `S` y Enter para continuar, o `N` para cancelar.
3. Restaura el launcher de fábrica, desinstala Clean Launcher y reactiva los paquetes que estaban activos.
4. Al terminar, el radio debe mostrar su pantalla de fábrica.

Si el radio no arranca o no responde después de revertir, no intentes más cambios. Lleva el radio con un técnico y entrégale la carpeta de `C:\RadioBackup\`.

## 9. Plan B: instalar por USB (sin PC)

Úsalo si `conectar.bat` falla o el radio no responde por Wi-Fi.

1. En la PC, copia el archivo `build\CleanLauncher.apk` al USB.
2. Conecta el USB al radio.
3. Abre el administrador de archivos del radio y busca el archivo `CleanLauncher.apk`.
4. Tócalo para instalar. Si pide permiso para "fuentes desconocidas" o "instalar apps desconocidas", permítelo.
5. Abre la app, pulsa **Home** y elige **"Inicio"** → **"Siempre"**.

Luego, en Clean Launcher: toca la imagen del carro (abre **Configuración**) → **Herramientas del carro** → **Diagnóstico del radio** → generar informe. El archivo `diagnostico_radio.txt` queda en el USB (o en la ruta que muestre la pantalla). Súbelo a la carpeta `diagnostico/` del repositorio o pégalo en el chat.

## 10. Anexo A: OBD2 (lectura del motor, opcional)

Materiales:

- Adaptador **ELM327 Bluetooth Classic v1.5**, con chip PIC18F25K80. Evita los modelos que solo dicen "BLE" (Bluetooth Low Energy): el launcher usa Bluetooth clásico.

Pasos:

1. Conecta el adaptador en el puerto OBD del auto. Está bajo el tablero, a la izquierda de la columna de dirección.
2. Enciende el contacto (no hace falta arrancar el motor).
3. En el radio: Ajustes → Bluetooth → busca el adaptador y emparéjalo. El PIN es **1234** o **0000**.
4. En Clean Launcher: **Configuración → Herramientas del carro → Escáner OBD2 (ELM327)** → elegir dispositivo emparejado.
5. Sin adaptador puedes probar la pantalla con el interruptor **Simulación**.
6. Con el adaptador conectado, la pantalla principal avisa de check engine, temperatura del motor sobre 105 °C y batería baja; el consumo L/100 km es una estimación.

Consejo: algunos adaptadores consumen batería si quedan conectados con el auto apagado. Desconéctalos cuando no los uses.

## 11. Anexo B: reportes en Power BI (para análisis de costos)

Las tanqueadas se registran en **Configuración → Herramientas del carro → Mantenimiento, combustible y costos → Combustible y costos**. Registra siempre el kilometraje y marca "tanque lleno" cuando lo llenes: el rendimiento se calcula entre llenados completos.

Archivos esperados, en el almacenamiento del radio:

- `Android/data/com.simple.carlauncher/files/CleanLauncher/tanqueadas.csv`
- `Android/data/com.simple.carlauncher/files/CleanLauncher/mantenimiento.csv`

Pasos:

1. En la app, pulsa **"Exportar a USB"**. Copia los dos archivos a la PC.
2. Abre Power BI Desktop → **Obtener datos** → **Texto/CSV** → selecciona `tanqueadas.csv` → **Cargar**. Repite con `mantenimiento.csv`.
3. Columnas de `tanqueadas.csv`: `fecha, km, galones, valor_cop, lleno, km_por_galon, costo_km` (fechas `AAAA-MM-DD`, decimales con punto; si Power BI usa configuración regional de Colombia, en **Transformar datos** cambia el tipo con "Usar configuración regional → Inglés (Estados Unidos)"). Medidas sugeridas (DAX):

   ```
   Gasto COP = SUM(tanqueadas[valor_cop])
   Km recorridos = MAX(tanqueadas[km]) - MIN(tanqueadas[km])
   Costo por km = DIVIDE([Gasto COP], [Km recorridos])
   Km por galón = AVERAGEX(FILTER(tanqueadas, tanqueadas[km_por_galon] > 0), tanqueadas[km_por_galon])
   ```

4. `DIVIDE` evita errores cuando no hay datos. Si el número sale vacío, revisa que los nombres de columna coincidan.

## 12. Optimización (opcional)

Solo si el radio va lento (2 GB de RAM, `/system` al 93%). No es necesaria para usar Clean Launcher.

1. Ejecuta `tools\windows\optimizar.bat`. Muestra el plan y pide confirmación (`S` o `N`).
2. Reinicia el radio (apagar 30 s y encender).
3. Para deshacer: `tools\windows\revertir_optimizacion.bat`, con confirmación, y reinicia el radio.

Qué hace: deshabilita 5 apps de terceros no usadas (solo las instaladas), baja las animaciones a 0.5, deja el Wi-Fi activo y limpia caches. Los valores previos quedan en `C:\RadioBackup\optimizacion_*.txt`. Deshabilitar no libera espacio en `/system`.
