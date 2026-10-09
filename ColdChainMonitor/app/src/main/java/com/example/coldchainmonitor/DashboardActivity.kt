package com.example.coldchainmonitor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.coldchainmonitor.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: DatabaseReference

    private lateinit var tvTemperature: TextView
    private lateinit var tvDoorStatus: TextView
    private lateinit var indicatorDoor: View
    private lateinit var tvSystemState: TextView
    private lateinit var cardTemperature: MaterialCardView
    private lateinit var cardDoor: MaterialCardView
    private lateinit var switchCooler: SwitchMaterial
    private lateinit var switchAlarm: SwitchMaterial
    private lateinit var btnLogout: Button

    private val UMBRAL_TEMP = 8.0
    private var isUpdatingUi = false // Bandera para evitar rebotes hacia Firebase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        auth = FirebaseAuth.getInstance()

        // 1. Instancia con la URL exacta de tu consola de Firebase
        val database = FirebaseDatabase.getInstance("https://coldchainmonitor-32ba5-default-rtdb.firebaseio.com")
        dbRef = database.getReference("camara_frio_1")

        initViews()
        setupListeners()
        setupFirebaseRealtimeObserver()
    }

    private fun initViews() {
        tvTemperature = findViewById(R.id.tvTemperature)
        tvDoorStatus = findViewById(R.id.tvDoorStatus)
        indicatorDoor = findViewById(R.id.indicatorDoor)
        tvSystemState = findViewById(R.id.tvSystemState)
        cardTemperature = findViewById(R.id.cardTemperature)
        cardDoor = findViewById(R.id.cardDoor)
        switchCooler = findViewById(R.id.switchCooler)
        switchAlarm = findViewById(R.id.switchAlarm)
        btnLogout = findViewById(R.id.btnLogout)
    }

    private fun setupListeners() {
        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Control manual de Enfriador -> Publica en Firebase
        switchCooler.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                dbRef.child("enfriadorActivo").setValue(isChecked)
            }
        }

        // Control manual de Alarma -> Publica en Firebase
        switchAlarm.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                dbRef.child("sirenaActiva").setValue(isChecked)
            }
        }
    }

    private fun setupFirebaseRealtimeObserver() {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                try {
                    // 2. Lectura tolerante a tipos (Double / Long / Int)
                    val rawTemp = snapshot.child("temperatura").value
                    val temp = when (rawTemp) {
                        is Number -> rawTemp.toDouble()
                        is String -> rawTemp.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }

                    val puerta = snapshot.child("puertaAbierta").getValue(Boolean::class.java) ?: false
                    val enfriador = snapshot.child("enfriadorActivo").getValue(Boolean::class.java) ?: false
                    val sirena = snapshot.child("sirenaActiva").getValue(Boolean::class.java) ?: false

                    val data = TelemetryData(temp, puerta, enfriador, sirena)
                    actualizarInterfaz(data)
                } catch (e: Exception) {
                    Toast.makeText(this@DashboardActivity, "Error de formato: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@DashboardActivity,
                    "Error de sincronización: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun actualizarInterfaz(data: TelemetryData) {
        isUpdatingUi = true

        // 1. Mostrar Temperatura
        tvTemperature.text = String.format("%.1f °C", data.temperatura)

        val hayAlertaTemp = data.temperatura > UMBRAL_TEMP
        val hayAlertaPuerta = data.puertaAbierta

        if (hayAlertaTemp) {
            tvTemperature.setTextColor(Color.parseColor("#D32F2F"))
            cardTemperature.strokeColor = Color.parseColor("#EF5350")
        } else {
            tvTemperature.setTextColor(Color.parseColor("#0D47A1"))
            cardTemperature.strokeColor = Color.parseColor("#CFD8DC")
        }

        // 2. Mostrar Estado de Puerta
        if (hayAlertaPuerta) {
            tvDoorStatus.text = "ABIERTA"
            tvDoorStatus.setTextColor(Color.parseColor("#D32F2F"))
            cardDoor.strokeColor = Color.parseColor("#EF5350")
            indicatorDoor.setBackgroundColor(Color.parseColor("#D32F2F"))
        } else {
            tvDoorStatus.text = "CERRADA"
            tvDoorStatus.setTextColor(Color.parseColor("#2E7D32"))
            cardDoor.strokeColor = Color.parseColor("#CFD8DC")
            indicatorDoor.setBackgroundColor(Color.parseColor("#2E7D32"))
        }

        // 3. Sincronizar Switches de Actuadores
        switchCooler.isChecked = data.enfriadorActivo
        switchAlarm.isChecked = data.sirenaActiva

        // 4. Barra de Alerta General
        if (hayAlertaTemp || hayAlertaPuerta) {
            tvSystemState.text = "CRÍTICO: LÍMITES SUPERADOS O COMPUERTA ABIERTA"
            tvSystemState.setBackgroundColor(Color.parseColor("#FFEBEE"))
            tvSystemState.setTextColor(Color.parseColor("#C62828"))
        } else {
            tvSystemState.text = "SISTEMA OPERANDO EN PARÁMETROS SEGUROS"
            tvSystemState.setBackgroundColor(Color.parseColor("#E8F5E9"))
            tvSystemState.setTextColor(Color.parseColor("#2E7D32"))
        }

        isUpdatingUi = false
    }
}