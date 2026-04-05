package com.glucoguard.app.api

// Step 1 - Login response
data class LoginResponse(val data: LoginData?)
data class LoginData(val authTicket: AuthTicket?, val user: User?)
data class AuthTicket(val token: String?)
data class User(val id: String?)

// Step 2 - Connections response
data class ConnectionsResponse(val data: List<Connection>?)
data class Connection(val patientId: String?)

// Step 3 - Graph response
data class GraphResponse(val data: GraphData?)
data class GraphData(val connection: ConnectionData?)
data class ConnectionData(val glucoseMeasurement: GlucoseMeasurement?)
data class GlucoseMeasurement(val ValueInMgPerDl: Int?, val TrendArrow: Int?)

// Result returned to the rest of the app
data class GlucoseReading(val value: Int, val trend: Int) {
    fun trendToArrow(): String = when (trend) {
        1 -> "↓"
        2 -> "↘"
        3 -> "→"
        4 -> "↗"
        5 -> "↑"
        else -> "→"
    }
}
