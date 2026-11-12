package com.example.monitordefrio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.nio.charset.StandardCharsets
import java.util.UUID

// --- MODELOS E INTERFAZ RETROFIT PARA FASTAPI ---
data class RegisterReq(val nombre: String, val correo: String, val password: String)
data class LoginReq(val correo: String, val password: String)
data class AuthRes(val ok: Boolean, val mensaje: String, val nombre: String?)

interface ApiService {
    @POST("register")
    fun register(@Body req: RegisterReq): Call<AuthRes>

    @POST("login")
    fun login(@Body req: LoginReq): Call<AuthRes>
}

class MainActivity : AppCompatActivity() {

    // Vistas de Autenticación
    private lateinit var layoutAuth: LinearLayout
    private lateinit var tvAuthTitulo: TextView
    private lateinit var etNombre: EditText
    private lateinit var etCorreo: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnAccionAuth: Button
    private lateinit var tvCambiarModoAuth: TextView
    private var esModoRegistro = false

    // Vistas del Dashboard
    private lateinit var layoutDashboard: LinearLayout
    private lateinit var btnCerrarSesion: Button
    private lateinit var tvEstadoMqtt: TextView
    private lateinit var cardAlerta: CardView
    private lateinit var tvTituloAlerta: TextView
    private lateinit var tvDetalleAlerta: TextView
    private lateinit var tvTemperatura: TextView
    private lateinit var tvEstadoPuerta: TextView

    // Cliente MQTT y Retrofit
    private var mqttClient: Mqtt3AsyncClient? = null
    private val gson = Gson()
    private val CHANNEL_ID = "alertas_frio_channel"
    private var estadoAlertaAnterior: Boolean? = null

    // 10.0.2.2 apunta al localhost de tu PC desde el Emulador Android
    private val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("http://127.0.0.1:8000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inicializarVistas()
        configurarNotificaciones()
        configurarEventosAuth()
    }

    private fun inicializarVistas() {
        layoutAuth = findViewById(R.id.layoutAuth)
        tvAuthTitulo = findViewById(R.id.tvAuthTitulo)
        etNombre = findViewById(R.id.etNombre)
        etCorreo = findViewById(R.id.etCorreo)
        etPassword = findViewById(R.id.etPassword)
        btnAccionAuth = findViewById(R.id.btnAccionAuth)
        tvCambiarModoAuth = findViewById(R.id.tvCambiarModoAuth)

        layoutDashboard = findViewById(R.id.layoutDashboard)
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion)
        tvEstadoMqtt = findViewById(R.id.tvEstadoMqtt)
        cardAlerta = findViewById(R.id.cardAlerta)
        tvTituloAlerta = findViewById(R.id.tvTituloAlerta)
        tvDetalleAlerta = findViewById(R.id.tvDetalleAlerta)
        tvTemperatura = findViewById(R.id.tvTemperatura)
        tvEstadoPuerta = findViewById(R.id.tvEstadoPuerta)
    }

    private fun configurarEventosAuth() {
        tvCambiarModoAuth.setOnClickListener {
            esModoRegistro = !esModoRegistro
            if (esModoRegistro) {
                tvAuthTitulo.text = "Crear Cuenta"
                etNombre.visibility = View.VISIBLE
                btnAccionAuth.text = "Registrarse"
                tvCambiarModoAuth.text = "¿Ya tienes cuenta? Inicia sesión"
            } else {
                tvAuthTitulo.text = "Iniciar Sesión"
                etNombre.visibility = View.GONE
                btnAccionAuth.text = "Ingresar"
                tvCambiarModoAuth.text = "¿No tienes cuenta? Regístrate aquí"
            }
        }

        btnAccionAuth.setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val nombre = etNombre.text.toString().trim()

            if (correo.isEmpty() || password.isEmpty() || (esModoRegistro && nombre.isEmpty())) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnAccionAuth.isEnabled = false

            if (esModoRegistro) {
                apiService.register(RegisterReq(nombre, correo, password)).enqueue(object : Callback<AuthRes> {
                    override fun onResponse(call: Call<AuthRes>, response: Response<AuthRes>) {
                        btnAccionAuth.isEnabled = true
                        if (response.isSuccessful && response.body()?.ok == true) {
                            Toast.makeText(this@MainActivity, "Cuenta creada. Bienvenido $nombre", Toast.LENGTH_SHORT).show()
                            mostrarDashboard()
                        } else {
                            Toast.makeText(this@MainActivity, "Error: El correo ya está registrado", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<AuthRes>, t: Throwable) {
                        btnAccionAuth.isEnabled = true
                        Toast.makeText(this@MainActivity, "Error de conexión con servidor: ${t.message}", Toast.LENGTH_LONG).show()
                    }
                })
            } else {
                apiService.login(LoginReq(correo, password)).enqueue(object : Callback<AuthRes> {
                    override fun onResponse(call: Call<AuthRes>, response: Response<AuthRes>) {
                        btnAccionAuth.isEnabled = true
                        if (response.isSuccessful && response.body()?.ok == true) {
                            val userNombre = response.body()?.nombre ?: correo
                            Toast.makeText(this@MainActivity, "Bienvenido $userNombre", Toast.LENGTH_SHORT).show()
                            mostrarDashboard()
                        } else {
                            Toast.makeText(this@MainActivity, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<AuthRes>, t: Throwable) {
                        btnAccionAuth.isEnabled = true
                        Toast.makeText(this@MainActivity, "Error de conexión con servidor: ${t.message}", Toast.LENGTH_LONG).show()
                    }
                })
            }
        }

        btnCerrarSesion.setOnClickListener {
            desconectarMqtt()
            layoutDashboard.visibility = View.GONE
            layoutAuth.visibility = View.VISIBLE
        }
    }

    private fun mostrarDashboard() {
        layoutAuth.visibility = View.GONE
        layoutDashboard.visibility = View.VISIBLE
        conectarMqtt()
    }

    // ---------------------------------------------------------
    // CONEXIÓN MQTT CON HIVEMQ (MISMO BROKER QUE WOKWI)
    // ---------------------------------------------------------
    private fun conectarMqtt() {
        tvEstadoMqtt.text = "Conectando a broker.hivemq.com..."

        mqttClient = MqttClient.builder()
            .useMqttVersion3()
            .identifier("AndroidApp_" + UUID.randomUUID().toString().substring(0, 8))
            .serverHost("broker.hivemq.com")
            .serverPort(1883)
            .buildAsync()

        mqttClient?.connect()?.whenComplete { _, throwable ->
            runOnUiThread {
                if (throwable != null) {
                    tvEstadoMqtt.text = "Error de conexión MQTT: ${throwable.message}"
                    tvEstadoMqtt.setTextColor(Color.RED)
                } else {
                    tvEstadoMqtt.text = "● Conectado en tiempo real (HiveMQ)"
                    tvEstadoMqtt.setTextColor(Color.parseColor("#16A34A"))
                    suscribirTopicos()
                }
            }
        }
    }

    private fun suscribirTopicos() {
        // 1. Escuchar Telemetría de MCU1
        mqttClient?.subscribeWith()
            ?.topicFilter("camara/1/telemetria")
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.callback { publish ->
                val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                procesarTelemetria(payload)
            }
            ?.send()

        // 2. Escuchar Alertas que recibe MCU2
        mqttClient?.subscribeWith()
            ?.topicFilter("camara/1/alerta")
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.callback { publish ->
                val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                procesarAlerta(payload)
            }
            ?.send()
    }

    private fun procesarTelemetria(jsonStr: String) {
        try {
            val json = gson.fromJson(jsonStr, JsonObject::class.java)
            val temp = json.get("temp").asFloat
            val puertaAbierta = json.get("puerta_abierta").asBoolean

            runOnUiThread {
                tvTemperatura.text = String.format("%.1f °C", temp)
                if (puertaAbierta) {
                    tvEstadoPuerta.text = "ABIERTA"
                    tvEstadoPuerta.setTextColor(Color.parseColor("#DC2626"))
                } else {
                    tvEstadoPuerta.text = "CERRADA"
                    tvEstadoPuerta.setTextColor(Color.parseColor("#16A34A"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun procesarAlerta(jsonStr: String) {
        try {
            val json = gson.fromJson(jsonStr, JsonObject::class.java)
            val activa = json.get("activa").asBoolean
            val motivo = json.get("motivo").asString

            runOnUiThread {
                if (activa) {
                    cardAlerta.setCardBackgroundColor(Color.parseColor("#FEE2E2"))
                    tvTituloAlerta.text = "🚨 ALERTA ACTIVA EN MCU2"
                    tvTituloAlerta.setTextColor(Color.parseColor("#991B1B"))

                    val descripcion = when (motivo) {
                        "PUERTA_Y_TEMPERATURA" -> "Puerta abierta y temperatura fuera de rango."
                        "PUERTA_ABIERTA" -> "La puerta de la cámara está abierta."
                        "TEMPERATURA" -> "Temperatura elevada detectada."
                        else -> "Anomalía detectada: $motivo"
                    }
                    tvDetalleAlerta.text = descripcion
                    tvDetalleAlerta.setTextColor(Color.parseColor("#B91C1C"))

                    if (estadoAlertaAnterior != true) {
                        lanzarNotificacion("🚨 Alerta Cámara Frigorífica", descripcion)
                    }
                } else {
                    cardAlerta.setCardBackgroundColor(Color.parseColor("#DCFCE7"))
                    tvTituloAlerta.text = "✅ ALERTA SOLUCIONADA"
                    tvTituloAlerta.setTextColor(Color.parseColor("#166534"))
                    tvDetalleAlerta.text = "MCU2 desactivó la alarma. Parámetros normales."
                    tvDetalleAlerta.setTextColor(Color.parseColor("#15803D"))

                    if (estadoAlertaAnterior == true) {
                        lanzarNotificacion("✅ Problema Solucionado", "La cámara frigorífica volvió a la normalidad.")
                    }
                }
                estadoAlertaAnterior = activa
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun desconectarMqtt() {
        mqttClient?.disconnect()
    }

    // ---------------------------------------------------------
    // NOTIFICACIONES PUSH LOCALES EN ANDROID
    // ---------------------------------------------------------
    private fun configurarNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alertas de Frío",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun lanzarNotificacion(titulo: String, mensaje: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        desconectarMqtt()
    }
}