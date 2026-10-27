package com.edulock.app

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Narrow client for the child's latest location and device heartbeat.
 * It deliberately stores only the newest location, not a movement history.
 */
internal object ChildLocationSyncClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun uploadLatestLocation(
        context: Context,
        childId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?,
        recordedAtMillis: Long
    ) {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid longitude" }
        val now = Instant.now().toString()
        val body = JSONObject()
            .put("child_id", childId)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("recorded_at", Instant.ofEpochMilli(recordedAtMillis).toString())
            .put("updated_at", now)
        if (accuracyMeters != null && accuracyMeters.isFinite() && accuracyMeters >= 0f) {
            body.put("accuracy_meters", accuracyMeters.toDouble())
        } else {
            body.put("accuracy_meters", JSONObject.NULL)
        }
        authorizedRequest(
            context,
            "rest/v1/child_locations?on_conflict=child_id",
            "POST",
            body,
            profile = true,
            prefer = "resolution=merge-duplicates,return=minimal",
            expectedRole = "child"
        )
    }

    fun sendHeartbeat(
        context: Context,
        childId: String,
        sharingEnabled: Boolean,
        batteryPercent: Int?,
        deviceLocked: Boolean? = null,
        guardMode: String? = null,
        currentAppPackage: String? = null,
        screenOn: Boolean? = null,
        blockedAppEvents: Int? = null
    ) {
        val now = Instant.now().toString()
        val body = JSONObject()
            .put("child_id", childId)
            .put("last_seen_at", now)
            .put("sharing_enabled", sharingEnabled)
            .put("updated_at", now)
        if (batteryPercent != null && batteryPercent in 0..100) {
            body.put("battery_percent", batteryPercent)
        }
        if (deviceLocked != null) body.put("device_locked", deviceLocked)
        if (guardMode != null && guardMode in setOf("unknown", "kiosk", "accessibility", "off")) {
            body.put("guard_mode", guardMode)
        }
        if (currentAppPackage != null) body.put("current_app_package", currentAppPackage.take(255))
        if (screenOn != null) body.put("screen_on", screenOn)
        if (blockedAppEvents != null) body.put("blocked_app_events", blockedAppEvents.coerceAtLeast(0))

        try {
            authorizedRequest(
                context,
                "rest/v1/child_device_status?on_conflict=child_id",
                "POST",
                body,
                profile = true,
                prefer = "resolution=merge-duplicates,return=minimal",
                expectedRole = "child"
            )
        } catch (error: IOException) {
            // Backward compatibility: older installations without migration 0033 still
            // record an ordinary heartbeat instead of losing all presence reports.
            if (deviceLocked == null && guardMode == null && currentAppPackage == null &&
                screenOn == null && blockedAppEvents == null) throw error
            val legacyBody = JSONObject()
                .put("child_id", childId)
                .put("last_seen_at", now)
                .put("sharing_enabled", sharingEnabled)
                .put("updated_at", now)
            if (batteryPercent != null && batteryPercent in 0..100) legacyBody.put("battery_percent", batteryPercent)
            authorizedRequest(
                context,
                "rest/v1/child_device_status?on_conflict=child_id",
                "POST",
                legacyBody,
                profile = true,
                prefer = "resolution=merge-duplicates,return=minimal",
                expectedRole = "child"
            )
        }
    }

    fun loadParentDeviceStatuses(context: Context): JSONArray {
        try {
            return authorizedJsonRequest(
                context,
                "rest/v1/child_device_status?select=child_id,last_seen_at,sharing_enabled,battery_percent,device_locked,guard_mode,current_app_package,screen_on,blocked_app_events",
                expectedRole = "parent"
            )
        } catch (_: IOException) {
            // Keep the parent status screen usable before migration 0033 is applied.
            return authorizedJsonRequest(
                context,
                "rest/v1/child_device_status?select=child_id,last_seen_at,sharing_enabled,battery_percent",
                expectedRole = "parent"
            )
        }
    }

    fun loadParentChildren(context: Context): JSONArray =
        authorizedJsonRequest(
            context,
            "rest/v1/children?select=id,display_name&order=display_name.asc",
            expectedRole = "parent"
        )

    private fun authorizedJsonRequest(context: Context, path: String, expectedRole: String): JSONArray {
        val result = authorizedRequest(context, path, "GET", null, true, "return=representation", expectedRole)
        return try {
            if (result.body.trimStart().startsWith("[")) JSONArray(result.body) else JSONArray()
        } catch (_: JSONException) {
            throw IOException("Supabase returned an invalid response.")
        }
    }

    private fun authorizedRequest(
        context: Context,
        path: String,
        method: String,
        body: JSONObject?,
        profile: Boolean,
        prefer: String,
        expectedRole: String
    ): RawResponse {
        val store = SecureSessionStore(context.applicationContext)
        var session = store.read() ?: throw IOException("EduLock session expired. Open EduLock and sign in again.")
        if (session.accountRole != expectedRole) throw IOException("This EduLock action requires a $expectedRole session.")
        val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (!baseUrl.startsWith("https://") || anonKey.isBlank()) {
            throw IOException("EduLock Supabase configuration is missing.")
        }

        var result = perform(baseUrl, anonKey, path, method, body, session.accessToken, profile, prefer)
        if (result.code == 401 && session.refreshToken.isNotBlank()) {
            val refreshResult = perform(
                baseUrl, anonKey,
                "auth/v1/token?grant_type=refresh_token",
                "POST",
                JSONObject().put("refresh_token", session.refreshToken),
                null, false, "return=minimal"
            )
            if (refreshResult.code in 200..299) {
                val refreshed = try { JSONObject(refreshResult.body) } catch (_: Exception) { JSONObject() }
                val access = refreshed.optString("access_token")
                val refresh = refreshed.optString("refresh_token", session.refreshToken)
                if (access.isNotBlank()) {
                    session = AuthSession(
                        accessToken = access,
                        refreshToken = refresh.ifBlank { session.refreshToken },
                        userId = refreshed.optJSONObject("user")?.optString("id")?.takeIf { it.isNotBlank() } ?: session.userId,
                        accountRole = session.accountRole,
                        expiresAtMillis = System.currentTimeMillis() + refreshed.optLong("expires_in", 3600L).coerceIn(60L, 86400L) * 1000L
                    )
                    store.save(session)
                    result = perform(baseUrl, anonKey, path, method, body, session.accessToken, profile, prefer)
                }
            }
        }
        if (result.code !in 200..299) {
            val message = try {
                val obj = JSONObject(result.body)
                listOf("message", "msg", "error_description", "error", "hint")
                    .firstNotNullOfOrNull { key ->
                        obj.optString(key).takeIf { it.isNotBlank() && it != "null" }
                    }
            } catch (_: Exception) { null }
            throw IOException(message?.take(300) ?: "Location sync failed (HTTP ${result.code}).")
        }
        return result
    }

    private fun perform(
        baseUrl: String,
        anonKey: String,
        path: String,
        method: String,
        body: JSONObject?,
        accessToken: String?,
        profile: Boolean,
        prefer: String
    ): RawResponse {
        val builder = Request.Builder()
            .url("$baseUrl/$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${accessToken ?: anonKey}")
            .header("Accept", "application/json")
        if (profile) {
            if (method == "GET" || method == "DELETE") builder.header("Accept-Profile", "edulock")
            else builder.header("Content-Profile", "edulock")
        }
        builder.header("Prefer", prefer)
        if (method == "POST" || method == "PATCH" || method == "PUT") {
            builder.header("Content-Type", "application/json")
                .method(method, (body ?: JSONObject()).toString().toRequestBody(jsonMedia))
        } else if (method == "DELETE") {
            builder.delete()
        } else {
            builder.get()
        }
        return http.newCall(builder.build()).execute().use { response ->
            RawResponse(response.code, response.body?.string().orEmpty())
        }
    }

    private data class RawResponse(val code: Int, val body: String)
}
