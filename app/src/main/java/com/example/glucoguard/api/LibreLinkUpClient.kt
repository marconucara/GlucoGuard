package com.example.glucoguard.api

import android.util.Log
import com.example.glucoguard.Config
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest

object LibreLinkUpClient {

    private const val TAG = "LibreLinkUpClient"
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

    // In-memory cache for steady-state polling
    private var cachedToken: String? = null
    private var cachedAccountIdHash: String? = null
    private var cachedPatientId: String? = null

    fun invalidateCache() {
        Log.i(TAG, "Cache invalidated")
        cachedToken = null
        cachedAccountIdHash = null
        cachedPatientId = null
    }

    /** Performs glucose fetch, using cached credentials if available, or logging in if needed. */
    fun fetchGlucose(email: String? = null, password: String? = null): GlucoseReading {
        // Try with current cache
        if (cachedToken != null && cachedAccountIdHash != null && cachedPatientId != null) {
            try {
                return fetchWithCredentials(cachedToken!!, cachedAccountIdHash!!, cachedPatientId!!)
            } catch (e: Exception) {
                Log.w(TAG, "Cached fetch failed, refreshing session: ${e.message}")
                // Fall through to refresh
            }
        }

        // Session refresh flow
        if (email == null || password == null) error("No credentials provided, use settings button to configure")
        
        val (token, accountIdHash) = login(email, password)
        cachedToken = token
        cachedAccountIdHash = accountIdHash

        val patientId = fetchPatientId(token, accountIdHash)
        cachedPatientId = patientId

        return fetchWithCredentials(token, accountIdHash, patientId)
    }

    private fun login(email: String, password: String): Pair<String, String> {
        Log.i(TAG, "Performing login...")
        val loginBody = """{"email":"$email","password":"$password"}"""
            .toRequestBody(json)
        val loginRequest = Request.Builder()
            .url("${Config.BASE_URL}/auth/login")
            .post(loginBody)
            .apply { baseHeaders.forEach { (k, v) -> header(k, v) } }
            .header("Content-Type", "application/json")
            .build()

        val response = client.newCall(loginRequest).execute()
        if (!response.isSuccessful) {
            error("Login failed: ${response.code} ${response.message}")
        }

        val bodyString = response.body?.string() ?: error("Empty login response")
        Log.d(TAG, "Login response received (length: ${bodyString.length})")
        
        val loginData = gson.fromJson(bodyString, LoginResponse::class.java)

        if (loginData?.data?.authTicket?.token == null) {
            Log.e(TAG, "Login successful but token missing. Response: $bodyString")
            error("Missing auth token in response")
        }

        val token = loginData.data.authTicket.token
        val accountId = loginData.data.user?.id ?: error("Missing account id")
        val accountIdHash = sha256(accountId)

        return Pair(token, accountIdHash)
    }

    private fun fetchPatientId(token: String, accountIdHash: String): String {
        Log.i(TAG, "Fetching patientId...")
        val request = Request.Builder()
            .url("${Config.BASE_URL}/llu/connections")
            .get()
            .apply { baseHeaders.forEach { (k, v) -> header(k, v) } }
            .header("Authorization", "Bearer $token")
            .header("Account-Id", accountIdHash)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            error("Get connections failed: ${response.code}")
        }

        val bodyString = response.body?.string() ?: error("Empty connections response")
        val connectionsData = gson.fromJson(bodyString, ConnectionsResponse::class.java)

        return connectionsData.data?.firstOrNull()?.patientId ?: error("No patients found")
    }

    private fun fetchWithCredentials(token: String, accountIdHash: String, patientId: String): GlucoseReading {
        val request = Request.Builder()
            .url("${Config.BASE_URL}/llu/connections/$patientId/graph")
            .get()
            .apply { baseHeaders.forEach { (k, v) -> header(k, v) } }
            .header("Authorization", "Bearer $token")
            .header("Account-Id", accountIdHash)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("API call failed with code ${response.code}")
        }

        val bodyString = response.body?.string() ?: error("Empty graph response")
        val graphData = gson.fromJson(bodyString, GraphResponse::class.java)

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
