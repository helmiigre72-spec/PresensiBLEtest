package com.skripsi.presensi

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Nilai teks dari JSON, "-" jika kosong/null. */
fun JSONObject.str(k: String): String = if (isNull(k)) "-" else optString(k, "-")

/** Komunikasi ke backend. Panggil dari thread latar belakang. */
object Api {
    class ApiError(val code: Int, msg: String) : Exception(msg)

    private fun request(base: String, path: String, method: String, token: String?, body: JSONObject?): String {
        val c = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/json")
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try { JSONObject(text).optString("detail", "Error $code") } catch (e: Exception) { "Error $code" }
                throw ApiError(code, msg)
            }
            return text
        } finally {
            c.disconnect()
        }
    }

    fun ping(base: String) { request(base, "/api/ping", "GET", null, null) }

    fun login(base: String, code: String, password: String, deviceId: String): JSONObject =
        JSONObject(
            request(
                base, "/api/login", "POST", null,
                JSONObject().put("employee_code", code).put("password", password).put("device_id", deviceId)
            )
        )

    fun today(base: String, token: String): JSONObject =
        JSONObject(request(base, "/api/me/today", "GET", token, null))

    fun history(base: String, token: String): JSONArray =
        JSONArray(request(base, "/api/me/history?days=7", "GET", token, null))
}
