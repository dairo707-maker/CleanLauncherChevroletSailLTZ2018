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
  Sin embargo, los campos `fatigue`, `speedAlert` y `speedLimit` nunca se instancian en `buildProfile()` ni en `buildPermissions()`, por lo que son `null`. Al intentar evaluar `.isChecked()`, se produce un `NullPointerException` antes de que se ejecute `.apply()` en las preferencias compartidas (`SharedPreferences`), abortando el guardado de todas las configuraciones.

