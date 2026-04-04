package com.example.glucoguard.api

import com.example.glucoguard.Config
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest

object LibreLinkUpClient {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val json = "application/json".toMediaType()

    private val baseHeaders = mapOf(
        "User-Agent" to "LibreLinkUp/4.16.0 CFNetwork/1485 Darwin/23.1.0",
        "product" to "llu.ios",
        "version" to "4.16.0",
        "Accept" to "application/json",
        "Pragma" to "no-cache"
    )

    /** Performs login → connections → graph and returns the current glucose reading. */
    fun fetchGlucose(): GlucoseReading {
        // Step 1 - Login
        val loginBody = """{"email":"${Config.EMAIL}","password":"${Config.PASSWORD}"}"""
            .toRequestBody(json)
        val loginRequest = Request.Builder()
            .url("${Config.BASE_URL}/auth/login")
            .post(loginBody)
            .apply { baseHeaders.forEach { (k, v) -> header(k, v) } }
            .header("Content-Type", "application/json")
            .build()

        val loginResponse = client.newCall(loginRequest).execute()
        check(loginResponse.isSuccessful) { "Login failed: ${loginResponse.code}" }
        val loginData = gson.fromJson(loginResponse.body!!.string(), LoginResponse::class.java)

        val token = loginData.data?.authTicket?.token
            ?: error("Missing auth token")
        val accountId = loginData.data?.user?.id
            ?: error("Missing account id")
        val accountIdHash = sha256(accountId)

        // Step 2 - Get connections
        val connectionsRequest = Request.Builder()
            .url("${Config.BASE_URL}/llu/connections")
            .get()
            .apply { baseHeaders.forEach { (k, v) -> header(k, v) } }
            .header("Authorization", "Bearer $token")
            .header("Account-Id", accountIdHash)
            .build()

        val connectionsResponse = client.newCall(connectionsRequest).execute()
        check(connectionsResponse.isSuccessful) { "Get connections failed: ${connectionsResponse.code}" }
        val connectionsData = gson.fromJson(connectionsResponse.body!!.string(), ConnectionsResponse::class.java)

        val patientId = connectionsData.data?.firstOrNull()?.patientId
            ?: error("Missing patientId")

        // Step 3 - Get glucose
        val graphRequest = Request.Builder()
            .url("${Config.BASE_URL}/llu/connections/$patientId/graph")
            .get()
            .apply { baseHeaders.forEach { (k, v) -> header(k, v) } }
            .header("Authorization", "Bearer $token")
            .header("Account-Id", accountIdHash)
            .build()

        val graphResponse = client.newCall(graphRequest).execute()
        check(graphResponse.isSuccessful) { "Get graph failed: ${graphResponse.code}" }
        val graphData = gson.fromJson(graphResponse.body!!.string(), GraphResponse::class.java)

        val measurement = graphData.data?.connection?.glucoseMeasurement
            ?: error("Missing glucose measurement")
        val value = measurement.ValueInMgPerDl ?: error("Missing glucose value")
        val trend = measurement.TrendArrow ?: 0

        return GlucoseReading(value, trend)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
