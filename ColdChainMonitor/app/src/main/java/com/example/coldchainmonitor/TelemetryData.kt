package com.example.coldchainmonitor

data class TelemetryData(
    val temperatura: Double = 0.0,
    val puertaAbierta: Boolean = false,
    val enfriadorActivo: Boolean = false,
    val sirenaActiva: Boolean = false,
    val timestamp: Long = 0L
)