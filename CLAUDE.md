# Clean Launcher — Chevrolet Sail LTZ 2018

Launcher Android a medida para el radio del carro del dueño (Dairo, Cartagena). El dueño es economista y no programa: respóndele en español, claro y sin jerga. Los datos de la app (CSV) los analiza en Power BI.

## Hardware (confirmado con diagnóstico, no con la etiqueta del vendedor)

- Radio **XY AUTO 9216BJ**, chip **MediaTek AC8227L** (Cortex-A7 ×4, 32 bits), **Android 8.1 (SDK 27)**; el menú dice "10.1", es falso. 2 GB RAM, pantalla 1024×600 horizontal.
- ADB por Wi-Fi: `192.168.1.7:5555`. **Root por ADB** disponible (`adb root` → uid=0; `/system/xbin/su`; SELinux permissive; `ro.treble.enabled=true`).
- **Sin caja CAN**: el carro no le manda nada al radio (ni puertas, ni A/C, ni combustible). El volante no tiene botones. Panel de A/C mecánico.
- Hace **QuickBoot**: apagar la llave suspende y mata las apps que no están en `/system/etc/qb_list.xml`.
- `/system` ~93 % lleno (~114 MB libres).

## APIs de fábrica (halladas desensamblando los APK en `diagnostico/apk/`)

- Luces: broadcast `xy.xygala.lamplet`. ACC: `xy.android.acc.on` / `xy.android.acc.off`. QuickBoot: `autochips.intent.action.QB_POWERON`.
- Radio FM (`com.android.fmradio`): frecuencia actual en `Settings.System "fmradio_text"` ("FM   88.50"). Órdenes `xy.android.nextmedia|previousmedia|seek_next|seek_prev` solo funcionan si `FmMainActivity` está viva; `xy.setfm.freq` (extra `fm`="945") siempre trae la app al frente. **Decisión del dueño: el botón Radio abre la app nativa**; no reintentar una radio propia.
- Llamar: `xy.bluetoothcall.out` con extra `XY_BLUETOOTHCALL_OUT` = número, paquete `com.autochips.bluetooth`.
- Ecualizador: los efectos estándar de Android en sesión 0 sí suenan (XyEq).

## Código

- Java puro, sin Gradle ni AndroidX, Java 8, UI hecha en código con `Ui.java`. Paquete `com.simple.carlauncher`, fuentes en `src/com/simple/carlauncher/`.
- Estilo: textos de usuario en español, comentarios en inglés, misma densidad que el código vecino.
- Plataforma: `XyAuto.java` (todo lo específico del radio), `Profile.isXyAuto()`. El código NWD (radio del autor original) se mantiene sin romper.
- Piezas principales: `MainActivity` (inicio: reloj, mini-mapa OSM `MiniMapView`, música, carro con `CarMotionView`, chips de velocidad/combustible/turno/A/C), `GreeterService` (voz, pico y placa), `CarEvents` (despierta servicios tras QuickBoot), `DriverAssist` (GPS: velocímetro, fatiga, velocidad), `Odometer`, `Maintenance`, `FuelLog`/`FuelTank` (tanque 42 L), `ShiftLog` (Uber/DiDi/InDrive), `NavInfo` (Waze), `MessageReader`, `Tts` (voz, frases prerenderizadas a WAV), `PicoPlaca` (franjas + temporada 24 h), `ObdClient` (ELM327, aún sin adaptador).
- `build.sh` compila sin Gradle (aapt2/javac/d8/apksigner). `targetSdk` 25 a propósito (el radio entrega sus broadcasts y deja arrancar servicios).
- CI: `.github/workflows/build.yml` compila en cada push; el APK queda como artefacto en Actions. Firma fija con los secretos `SIGNING_KEYSTORE_B64` / `SIGNING_KEYSTORE_PASS`; la llave vive en `C:\RadioBackup\cleanlauncher.keystore` y **nunca** se sube al repo.
- Sin SDK de Android local: verificar compilando en CI (o instalando el SDK). No declarar algo "probado" si solo compiló.

## Trabajo en el radio

- Scripts en `tools/windows/*.bat`; guion paso a paso en `docs/AGENTE_LOCAL.md` (tareas T1–T18). Respaldo en `C:\RadioBackup` (incluye `cambios_aplicados.txt`).
- Todo cambio al radio: copia del original antes, confirmación del dueño, reversión documentada, registro en el log.
- Nunca: tocar el menú "Parameter Settings"/MCU, kernel, logo de arranque del bootloader, escribir en `/dev/block`, instalar ROMs/GSI, ni restablecer de fábrica.
- Antes de cualquier escritura en `/system` debe existir el respaldo de particiones (T14).
- Desinstalar la app borra sus datos (km, tanqueadas, turnos): exportar a USB antes.

## Estado al traspaso (entrega 7)

Pendiente de ejecutar en el radio, en este orden: T5 (instalar build con firma fija) → T14 → T13 → T15 → T16 → T17 → T18, reiniciando y verificando entre pasos (arranque, cámara de reversa, radio, Bluetooth, volumen, Waze, YouTube Music). Pendiente de probar en ruta: mini-mapa siguiendo el carro, velocímetro tras apagar/prender, carro animado. Resultados de pruebas en `diagnostico/pruebas.md`.

## Rama

Trabajar en `claude/amazing-lamport-auessa` salvo que el dueño diga otra cosa. Commits en español, descriptivos.
