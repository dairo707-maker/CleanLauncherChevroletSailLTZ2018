# Checklist de pruebas en el radio (Chevrolet Sail LTZ 2018 - XY AUTO 9216BJ)

| # | Prueba | Resultado | Observaciones |
|---|--------|-----------|---------------|
| 1 | **HOME**: al pulsar Home aparece Clean Launcher | **OK** | Vuelve siempre a Clean Launcher como launcher principal. |
| 2 | **Luces del carro (a)**: prender y apagar las luces 2 veces | **OK** | La imagen del Sail en pantalla cambia perfectamente al encender y apagar las luces reales del carro. |
| 3 | **Saludo / ACC (b)**: apagar y encender el carro/contacto | **OK (Ajuste nombre)** | Saluda por voz al encender. Dijo "Jhon" por defecto en el código base; debe ser "Dairo Trocha" (el usuario lo ajustó en configuración). |
| 4 | **Radio (c)**: tocar el botón Radio | **OK (Mejora)** | Abre la app nativa de radio. En el widget se muestra `--.-` en lugar de la previsualización de la frecuencia sintonizada. |
| 5 | **Bluetooth / Música (c)**: tocar Bluetooth | **OK (Mejora)** | Abre la app nativa. El usuario usa frecuentemente YouTube Music / YouTube y solicita visualización armónica / moderna de medios. |
| 6 | **Ajustes (c)**: tocar el botón de Ajustes | **OK** | Abre los ajustes nativos de fábrica de XY AUTO (`com.xyauto.Settings`). |
| 7 | **Botón A/C (d)**: tocar el botón A/C en el launcher | **FALLO (Layout)** | Casi no se ve el botón; queda por debajo de "aceite configurar" y no es visible en pantalla. Requiere reajuste de layout para 1024x600. |
| 8 | **Volumen**: botones del volante y volumen | **OK** | Suben y bajan el volumen sin problemas. |
| 9 | **Estabilidad / Ciclo de vida**: registro de eventos | **CRASH EN LOGS** | `MainActivity.onPause` lanzó `IllegalArgumentException: Receiver not registered` en línea 678 al desregistrar `illReceiver`. |
