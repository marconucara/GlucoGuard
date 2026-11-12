package com.glucoguard.app.api

// Step 1 - Login response
data class LoginResponse(val data: LoginData?)
data class LoginData(val authTicket: AuthTicket?, val user: User?, val step: LoginStep?)
data class AuthTicket(val token: String?)
data class User(val id: String?)
// Present when the account must complete an action in the official app (e.g. type "tou" = accept Terms of Use)
data class LoginStep(val type: String?)

/** Login was accepted but LibreLinkUp requires a pending step to be completed in the official app. */
class LoginActionRequiredException(val stepType: String?) :
    Exception("Login requires action in LibreLinkUp app: $stepType")

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
