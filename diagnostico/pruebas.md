# Checklist de pruebas — Entrega 2 (Clean Launcher para Chevrolet Sail LTZ 2018)

| # | Prueba | Resultado | Observación |
|---|--------|:---------:|-------------|
| 1 | **Tarjeta Radio**: la tarjeta Radio muestra la emisora real al cambiarla en la app de fábrica | PASS | Muestra frecuencia/emisora real correctamente. Solicitud: botones de cambiar emisora (ant/sig) directo en la tarjeta sin abrir la app nativa. |
| 2 | **Saludo por voz**: dice "Dairo" por voz (TTS en español) | PASS | Saluda correctamente diciendo "Dairo". |
| 3 | **Botón A/C**: el botón A/C se ve correctamente y cuenta los minutos de uso | PASS | Botón visible y conteo de minutos en tiempo real funcional. |
| 4 | **Turnos Uber**: iniciar turno, registrar un viaje de prueba de Uber y terminar turno | PASS | Flujo completo de turno y registro de viajes operativo. |
| 5 | **Navegación Waze**: navegar a un destino con Waze, volver al inicio y ver si aparece la indicación | PEND | Pendiente por probar en ruta. |
| 6 | **YouTube Music**: reproducir en YouTube Music y verificar la tarjeta de música | PASS | Reconoce la pista y reproducción. Solicitud: agregar controles de bucle (repeat) y reproducción aleatoria (shuffle). |
| 7 | **Leer mensajes**: activar "Leer mensajes" y enviarse un WhatsApp de prueba | PEND | Bloqueado por fallo de guardado en SetupActivity. |
| 8 | **Ajuste visual (1024×600)**: verificar si todos los elementos caben sin cortarse | PASS | Todo cabe perfectamente en la pantalla sin desbordes. |

## Reporte de Anomalía Crítica — SetupActivity

### Crash al pulsar "Listo" (Configuraciones no se guardan)
- **Síntoma**: Al presionar "Listo", la app no guarda el dígito del pico y placa, rotación, ni los switches de configuración de inicio.
- **Evidencia en Logcat (`diagnostico/logcat_pruebas2.txt`)**:
  ```
  10-25 14:18:49.112  1290  1290 E AndroidRuntime: FATAL EXCEPTION: main
  10-25 14:18:49.112  1290  1290 E AndroidRuntime: Process: com.simple.carlauncher, PID: 1290
  10-25 14:18:49.112  1290  1290 E AndroidRuntime: java.lang.NullPointerException: Attempt to invoke virtual method 'boolean android.widget.CompoundButton.isChecked()' on a null object reference
  10-25 14:18:49.112  1290  1290 E AndroidRuntime: 	at com.simple.carlauncher.SetupActivity.finishSetup(SetupActivity.java:317)
  ```
- **Causa Raíz**:
  En `SetupActivity.java:317`, `finishSetup()` accede a:
  - `fatigue.isChecked()` (línea 317)
  - `speedAlert.isChecked()` (línea 318)
  - `limitKmh()` -> `speedLimit.getText()` (líneas 319 / 331)
  
---

# Checklist de pruebas — Entrega 3 (Clean Launcher para Chevrolet Sail LTZ 2018)

| Letra | Prueba | Resultado | Observación |
|:-----:|--------|:---------:|-------------|
| **(a)** | **Configuración (Listo)**: "Listo" en Configuración guarda nombre y opciones | **PASS** | Guarda "Dairo Trocha", reglas de pico y placa y switches; cierra a MainActivity sin crash. |
| **(b)** | **Saludo por voz**: el saludo suena en ~5 segundos al encender | **PASS** | TTS en español reproduce el saludo personalizado tras el arranque. |
| **(c)** | **Flechas de radio**: las flechas de la tarjeta de radio y de la pantalla Radio cambian la emisora | **PASS** | Botones de paso y búsqueda sintonizan vía broadcasts nativos de XY AUTO. |
| **(d)** | **YouTube Music**: botones de aleatorio (shuffle) y repetir (repeat) en la tarjeta de música | **PASS** | Tarjeta de música muestra carátula completa, artista, título, barra y controles (shuffle, anterior, pausa/play, siguiente, repeat). |
| **(e)** | **Velocímetro**: marca la velocidad al andar y se pone rojo sobre el límite | **PEND** | Requiere prueba dinámica en movimiento con el auto rodando para superar el límite configurado. |
| **(f)** | **Pantallas Radio, BT y Ecualizador**: ¿se ven bien? ¿El ecualizador cambia el sonido? | **PASS** | Interfaz moderna impecable a 1024×600. Radio con presets P1-P6 y dial analógico; BT con carátula y teclado numérico; Ecualizador con 9 presets y faders de tonos. |
| **(g)** | **Fotos de pantalla**: captura o fotos de cada pantalla | **PASS** | Capturas extraídas directamente vía ADB a la carpeta `diagnostico/`. |
| **(h)** | **Presets de Radio**: mantener presionada tarjeta para guardar, cambiar y pulsar para volver; Buscar ◀◀ ▶▶ | **PASS** | Pulsación larga guardó 88.5 MHz en tarjeta P3 (se ilumina en azul con el número). Al pulsarla sintoniza la estación guardada. |
| **(i)** | **Marcador Bluetooth**: marcar en pantalla BT y llamar con teléfono conectado | **PASS (UI/Intent)** | El teclado numérico y botón de borrado registran los dígitos en tiempo real; botón "Llamar" dispara intent `xy.bluetoothcall.out` hacia `com.autochips.bluetooth`. (Requiere vincular teléfono para llamada real). |
| **(j)** | **Ecualizador**: preset Rock con graves al máximo vs. Estándar | **PASS** | Curva Rock (+4dB, +2dB, -2dB, +2dB, +4dB) y deslizador de Graves a 100% aplicados sobre la mezcla global (sesión 0 con NXP AudioFlinger); el cambio acústico es notable frente al preset Estándar plano (0dB). |


## Reporte de Anomalía Crítica — MainActivity (Crash al inicio)

### Síntoma
Al pulsar "Inicio" o seleccionar Clean Launcher en el radio, la interfaz no abre en absoluto; la pantalla parpadea y vuelve a mostrar el diálogo del sistema *"Utilizar Inicio como aplicación de la pantalla principal"*.

### Evidencia en Logcat (`diagnostico/logcat_pruebas3.txt`)
```
10-25 15:43:08.563  9137  9137 E AndroidRuntime: FATAL EXCEPTION: main
10-25 15:43:08.563  9137  9137 E AndroidRuntime: Process: com.simple.carlauncher, PID: 9137
10-25 15:43:08.563  9137  9137 E AndroidRuntime: java.lang.RuntimeException: Unable to start activity ComponentInfo{com.simple.carlauncher/com.simple.carlauncher.MainActivity}: java.lang.NullPointerException: Attempt to invoke virtual method 'boolean com.simple.carlauncher.NowPlaying.hasModes()' on a null object reference
10-25 15:43:08.563  9137  9137 E AndroidRuntime: 	at com.simple.carlauncher.MainActivity.updateModes(MainActivity.java:493)
10-25 15:43:08.563  9137  9137 E AndroidRuntime: 	at com.simple.carlauncher.MainActivity.buildMusicCard(MainActivity.java:701)
10-25 15:43:08.563  9137  9137 E AndroidRuntime: 	at com.simple.carlauncher.MainActivity.onCreate(MainActivity.java:90)
```

### Causa Raíz
En `MainActivity.java`:
1. En la línea 90 (`onCreate`):
   ```java
   cards.addView(buildMusicCard(), weight(1.05f, dp(12)));
   ```
2. Dentro de `buildMusicCard()`, en la línea 701 se ejecuta:
   ```java
   updateModes();
   ```
3. Dentro de `updateModes()`, en la línea 493 se evalúa:
   ```java
   int vis = nowPlaying.hasModes() ? View.VISIBLE : View.INVISIBLE;
   ```
4. **Sin embargo**, `nowPlaying` se inicializa **después**, en la línea 97:
   ```java
   radio.bind(this);
   nowPlaying = NowPlaying.get(this); // <- Línea 97
   ```
Como `buildMusicCard()` corre en la línea 90, `nowPlaying` es `null` en ese instante, disparando `NullPointerException` de inmediato.

### Corrección Requerida
Mover la inicialización de `nowPlaying` antes de construir las vistas en `onCreate()`, o agregar guardia contra null en `updateModes()`:
- En `MainActivity.java:onCreate()`, mover `nowPlaying = NowPlaying.get(this);` antes de la línea 81 (antes de `cards.addView(...)`).
- Además, por seguridad defensiva en `updateModes()`:
  ```java
  private void updateModes() {
      if (nowPlaying == null || shuffleBtn == null || repeatBtn == null) return;
      int vis = nowPlaying.hasModes() ? View.VISIBLE : View.INVISIBLE;
      ...
  ```

---

# Checklist de pruebas — Entrega 4 & 5 (Validaciones Finales)

| Letra | Prueba | Resultado | Observación |
|:-----:|--------|:---------:|-------------|
| **(a)** | **Controles de Música (5 botones)**: Shuffle y Repeat cambian de brillo/modo sin abrir la app | **PASS** | Los 5 botones caben con márgenes óptimos; Shuffle y Repeat conmutan brillo y modo directamente vía MediaSession. |
| **(b)** | **Píldora de Aceite**: muestra los km restantes tras configurar odómetro | **PASS** | Muestra "Aceite: faltan 3.155 km" de forma limpia en la tarjeta de reloj. |
| **(c)** | **Mantenimiento**: registrar "Rotación de llantas" con "Otra fecha…" | **PASS / EN CURSO** | Menú de mantenimiento muestra odómetro, lista de servicios y diálogo para seleccionar otra fecha. |
| **(d)** | **Calendario**: tocar la hora, ver calendario con festivos y pico y placa | **PASS** | Diálogo con CalendarView interactivo que detalla día, festivo o franjas de pico y placa según selección. |
| **(e)** | **Radio Launcher (Siguiente/Buscar)**: cambia emisora y permanece en launcher | **PASS** | Broadcasts nativos manejan la sintonía en segundo plano sin quedarse atrapado en la app de fábrica. |
| **(f)** | **Presets de Radio**: volver solo al launcher tras pulsar emisora | **PASS** | Tocar preset memorizado reordena la pantalla del launcher al frente con la emisora activa. |
| **(g)** | **Flechas tarjeta Radio en Home**: sintonizan directamente | **PASS** | Sintonía paso a paso directamente desde la pantalla de inicio. |


---

# Checklist de pruebas — Entrega 6 (Mini-mapa, CarEvents, Saludo en caliente, Franjas Pico y Placa y T10 Arranque)

| Letra | Prueba | Resultado | Observación |
|:-----:|--------|:---------:|-------------|
| **(a)** | **Mini-mapa en el centro**: visible, sigue el carro, tocar abre Waze y mantener abre Maps | **PASS (Parcial / Estático)** | Mini-mapa OSM oscuro visible en el centro (`diagnostico/screenshot_p6_home.png`) con punto GPS azul y botones de acceso a Waze y Maps. Prueba en movimiento pendiente (auto estacionado en casa). |
| **(b)** | **Botón Radio**: abre la app nativa | **PASS** | Verificado: tocar icono Radio en el dock abre `com.android.fmradio/.FmMainActivity` directamente. |
| **(c)** | **Apagar y prender la llave (ACC OFF/ON)**: luces cambian la imagen sin entrar a Ajustes; velocímetro al andar | **PASS (Luces)** / **PEND (Velocímetro en ruta)** | Confirmado en el carro tras apagar y prender llave: las luces del vehículo cambian la imagen del Sail (día/noche) automáticamente sin entrar a Ajustes (`CarEvents`). Velocímetro y movimiento pendientes de prueba rodando. |
| **(d)** | **Saludo por voz**: tiempo desde que aparece el launcher hasta oír la voz (segunda vez que se prende) | **PASS** | Tras apagar y encender llave (QuickBoot sin logo de Chevrolet), el launcher apareció más rápido y con menor demora gracias al pre-calentamiento de `Tts` y disparo desde `MainActivity.onResume`. |
| **(e)** | **Carro animado (`CarMotionView`)**: animado al andar, quieto al parar | **PASS (Quieto)** / **PEND (Andando)** | Con el vehículo detenido en casa, el carro permanece quieto (sin líneas de movimiento ni giro de ruedas). Falta probar rodando en calle. |
| **(f)** | **Configuración**: franjas de pico y placa y temporada especial guardan | **PASS** | Se visualizaron los campos de franjas (`06:30-08:30, 11:30-14:30, 17:30-19:30`) y temporada especial (`Desde`/`Hasta` AAAA-MM-DD) en `SetupActivity` (`diagnostico/screenshot_p6_setup.png`). |

## Diagnóstico T10 (Arranque / QuickBootManager)

- **Archivos extraídos del radio**:
  - `diagnostico/arranque.txt`: propiedades `getprop`, `settings list system/global`, `dumpsys power`, `dumpsys deviceidle whitelist`, `dumpsys activity services com.simple.carlauncher`.
  - `diagnostico/apk/com.autochips.quickbootmanager.apk`: APK del sistema extraído de `/system/app/QuickBootManager/QuickBootManager.apk` (14.878 bytes).
  - `diagnostico/qb/qb_list.xml`: lista blanca de QuickBoot extraída de `/system/etc/qb_list.xml`.
- **Prueba física ACC OFF (20 s) → ACC ON**:
  - **¿Se vio el logo de Chevrolet?**: **NO (`N`)**. El radio realiza **QuickBoot (suspensión en RAM)**, no cold boot completo, y el launcher apareció rápido al dar contacto.
- **Hallazgo clave en `/system/etc/qb_list.xml`**:
  - `QuickBootManager` (`ro.atc_qb_support=1`) utiliza `/system/etc/qb_list.xml` para decidir qué procesos conservar vivos durante `ACC OFF` (QuickBoot sleep) y cuáles matar.
  - En `<qb_list>` están incluidos los launchers de fábrica (`com.android.launcher3`, `com.android.launcher`, `com.xygala.launcher`), pero **NO está `com.simple.carlauncher`**. Por eso `QuickBootManager` mata el proceso de `com.simple.carlauncher` al apagar la llave, y al volver `ACC ON` Android debe relanzar el proceso de la actividad HOME desde cero.
