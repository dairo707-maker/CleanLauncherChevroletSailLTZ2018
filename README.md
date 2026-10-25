# Clean Launcher — Chevrolet Sail LTZ 2018

Launcher Android limpio y a medida para el radio **XY AUTO 9216BJ** (CANBUS 3.7, MCU 4.0, 1024×600)
de un Chevrolet Sail LTZ 2018 en Cartagena. Basado en el Clean Launcher original para radios NWD
(Chevrolet Epica), que sigue soportado: las funciones NWD se activan solas si el radio las tiene.

**Para instalarlo paso a paso: [`docs/TUTORIAL.md`](docs/TUTORIAL.md).**
El trabajo en el PC (ADB, respaldo, diagnóstico, instalación) lo puede ejecutar un agente local
siguiendo [`docs/AGENTE_LOCAL.md`](docs/AGENTE_LOCAL.md) con los scripts de [`tools/windows/`](tools/windows/).

## Novedades para el Sail

- **Respaldo y reversión completos** antes de tocar el radio (`respaldo.bat` / `restaurar.bat`).
- **Mantenimiento por km o tiempo** (aceite, llantas, alineación, frenos, filtros, bujías, refrigerante,
  correa, suspensión/rodamientos) con notificación, voz y alertas sobre la imagen del carro.
- **Combustible y costos**: tanqueadas, km/gal, costo/km y gasto mensual; CSV listos para Power BI.
- **Pico y placa de Cartagena** con rotación editable y festivos de Colombia.
- **Volumen según la velocidad** (GPS).
- **Escáner OBD2** (ELM327 Bluetooth) con modo simulación: check engine, temperatura, batería, consumo estimado.
- **Diagnóstico del radio** dentro de la app (plan B si no hay ADB por Wi-Fi).
- Imagen del Sail con luces encendidas/apagadas (`tools/car_image.py`).

## Funciones heredadas (radio NWD)

- **Pantalla principal** con hora, clima (Open-Meteo, por GPS o IP) y reproducción actual.
- **Radio FM**: dial, presets y sintonía hablando por Binder con `com.nwd.radio.service`.
- **Bluetooth**: teléfono (marcador, contactos) y música vía `com.bt.bc03`.
- **Overlay de volumen** propio (reemplaza el FloatBar de fábrica).
- **Modo pantalla apagada** que sigue respondiendo a los botones del volante.
- **Control de aceite / odómetro** con recordatorios de mantenimiento.
- **GreeterService**: saludo por voz al encender (voz Salomé, es-CO), avisos de cinturón,
  clima, mantenimiento los lunes y *pico y placa* de Quito. Restaura la fuente de audio
  (radio o BT) al dar contacto.
- **Ecualizador** con balance/fader y ajuste automático por fuente.
- **Dispositivos Bluetooth**: lista de emparejados y conexión.
- **Configuración inicial** para instalaciones compartidas: nombre, foto del carro, voz y permisos.
  En radios que no son NWD se ocultan las funciones que dependen de los servicios de fábrica.
- Cajón de aplicaciones.

## Compilar

No usa Gradle: [`build.sh`](build.sh) llama directamente a `aapt2`, `javac`, `d8`, `zipalign` y
`apksigner`. Funciona en Linux, macOS y Windows (Git Bash).

### Requisitos

| Herramienta | Versión | Notas |
|---|---|---|
| JDK | 17 o superior | Sirve el JBR que trae Android Studio |
| Android SDK Platform | `android-33` | Solo para compilar. El APK corre en Android 7.1+ (minSdk 25) |
| Android SDK Build-Tools | 36.0.0 (probado) | El script usa la más nueva instalada |
| Platform-Tools (`adb`) | cualquiera | Solo para `install` |

### Desde una instalación limpia

1. Instala un JDK 17+ y las [herramientas de línea de comandos del Android SDK](https://developer.android.com/studio#command-tools)
   (o Android Studio, que trae ambos).
2. Instala los paquetes del SDK:
   ```sh
   sdkmanager "platforms;android-33" "build-tools;36.0.0" "platform-tools"
   ```
3. Indica dónde está el SDK si no está en la ruta por defecto de Android Studio:
   ```sh
   export ANDROID_HOME=$HOME/Android/Sdk
   ```
4. Compila:
   ```sh
   sh build.sh                            # genera build/CleanLauncher.apk
   sh build.sh install                    # además lo instala con adb
   RADIO=192.168.1.15:5555 sh build.sh install   # radio por ADB Wi-Fi
   ```

Si falta algo, el script se detiene y dice qué instalar. Detecta el SDK con `ANDROID_HOME` o
`ANDROID_SDK_ROOT` (o las rutas por defecto de Android Studio) y el JDK con `JAVA_HOME`, `javac`
en el `PATH` o el JBR de Android Studio. Variables opcionales: `ANDROID_PLATFORM`, `BUILD_TOOLS`,
`KEYSTORE`, `KEYSTORE_PASS`, `KEY_ALIAS`.
`VARIANT=share sh build.sh` genera `CleanLauncher-compartir.apk`, una versión para regalar sin las fotos
del carro ni las voces grabadas: usa la foto del usuario y el texto a voz del equipo. Si no existe `~/.android/debug.keystore`, el script lo crea.

### Integración continua

Cada push a `main` y cada pull request se compila en GitHub Actions
([`.github/workflows/build.yml`](.github/workflows/build.yml)). El APK queda como artefacto
descargable en la pestaña **Actions** del repo. Está firmado con una clave de debug.

## Integración con el radio NWD

| Qué | Cómo |
|---|---|
| Cambio de fuente de audio | broadcast `com.nwd.action.ACTION_REQUEST_CHANGE_SOURCE` (radio=8, BT música=15, USB=2) |
| Radio | Binder crudo a `com.nwd.radio.service` (`RadioClient`) |
| Bluetooth | Binder crudo a `com.bt.bc03` (`BtClient`) |
| Voz sobre la radio | launcher añadido a `/nwdappconfig/app/NaviSound.ini` |
| No ser cerrado en segundo plano | añadido a `BackgroundManager.ini` y `OneClearConfig.xml` |

## Cambios hechos al radio (todos reversibles)

- Deshabilitado bloatware de fábrica (apps de prueba, DiDi, voz, anuncios CaradService, carstore, Aptoide).
- Deshabilitados componentes del launcher de fábrica: FloatBar, WeatherService, SmallPackageUpdater, AMap APSService.
  **No** se deshabilita `com.android.launcher` completo (aloja el aprendizaje de botones del volante y el apagado de pantalla).
- Animaciones a 0.5x.
- Archivos de configuración modificados: los originales están en [`backup/`](backup/).

Para revertir: copiar los `.bak` de `backup/` a `/nwdappconfig/app/` y volver a habilitar los paquetes con
`adb shell pm enable <paquete>`.
