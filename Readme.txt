# App Móvil - Monitor de Cadena de Frío (Android / Kotlin)

Aplicación nativa en Kotlin que autentica usuarios mediante una API REST (FastAPI + MySQL) y recibe telemetría y alertas en tiempo real mediante MQTT.

## Qué cambiar en el código al pasar a Hardware Físico y Nube

Todos los parámetros de red se encuentran en el archivo `app/src/main/java/com/example/monitordefrio/MainActivity.kt`.

### 1. URL del Backend (`baseUrl` en Retrofit)
Actualmente está configurada en `http://127.0.0.1:8000/` para funcionar mediante el túnel USB (`adb reverse`) en el emulador. Al instalar la app en un **celular físico**, cambia esta línea según dónde esté corriendo tu servidor Python:

* **Si pruebas en tu misma red Wi-Fi local (tu PC como servidor):** Usa la IPv4 local de tu computador (ej. `http://192.168.1.85:8000/`).
* **Si el Backend ya está desplegado en la nube (AWS / VPS):** Usa la IP pública o dominio de tu servidor:
```kotlin
private val apiService: ApiService by lazy {
    Retrofit.Builder()
        .baseUrl("http://IP_PUBLICA_O_DOMINIO_DEL_SERVIDOR:8000/") // Ej: "[http://3.142.](http://3.142.)XX.XX:8000/"
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}
```
*(Nota: Si usas `http://` sin certificado SSL en producción, mantén `android:usesCleartextTraffic="true"` dentro de `AndroidManifest.xml`).*

### 2. Servidor MQTT (`conectarMqtt` en `MainActivity.kt`)
* **Si mantienes el broker público:** No cambies nada (`broker.hivemq.com`, puerto `1883`).
* **Si montas un broker privado en la nube (Mosquitto en EC2):** Actualiza el host y el puerto dentro de `conectarMqtt()`:
```kotlin
mqttClient = MqttClient.builder()
    .useMqttVersion3()
    .identifier("AndroidApp_" + UUID.randomUUID().toString().substring(0, 8))
    .serverHost("IP_O_DOMINIO_BROKER_MQTT") // Cambiar si usas broker propio
    .serverPort(1883)                       // Cambiar a 8883 si habilitas SSL/TLS
    .buildAsync()
```