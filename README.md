# GPO Fishing Macro Android — V5 experimental

Esta versión corrige el inicio del servicio MediaProjection para Android 10+ y hace más conservador el intento de control: exige detección de verde/blanco y usa una zona muerta antes de enviar pulsaciones cortas.

## Qué hace
- Captura pantalla después de la autorización del sistema.
- Analiza candidatos de color en una región fija aproximada.
- Si el indicador blanco se detecta debajo de la zona verde, envía una pulsación breve de hasta 140 ms para intentar subirlo; si no, deja de enviar pulsaciones.
- Control experimental con límite temporal de 30 segundos y botón de parada.

## Limitaciones importantes
No es un APK compilado ni validado. La detección de color puede confundir texto, efectos y bordes con el indicador; las coordenadas dependen de resolución, orientación y escala de interfaz. La lógica es una hipótesis basada en la captura compartida, no una comprensión del juego. No garantiza pesca AFK y puede tocar controles inesperados. La parada evita nuevos gestos, pero no necesariamente interrumpe un gesto ya enviado.

## Compilación
Abre el proyecto en Android Studio, sincroniza Gradle y elige Build > Build Bundle(s) / APK(s) > Build APK(s). No se ha probado en un dispositivo físico.
