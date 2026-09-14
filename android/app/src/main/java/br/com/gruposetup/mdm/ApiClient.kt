package br.com.gruposetup.mdm

import android.os.Build
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.TlsVersion
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy { build() }

    private fun build(): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
        if (Build.VERSION.SDK_INT in 21..22) {
            try {
                val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .build()
                b.connectionSpecs(listOf(spec, ConnectionSpec.CLEARTEXT))
                Tls12SocketFactory.enable(b)
            } catch (e: Exception) { /* segue com defaults */ }
        }
        return b.build()
    }

    /** Retorna true se o backend respondeu 2xx. */
    fun enviar(payload: JSONObject): Boolean = try {
        val body = payload.toString().toRequestBody(JSON)
        val url = BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/posicoes"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + BuildConfig.API_KEY)
            .post(body)
            .build()
        client.newCall(req).execute().use { resp -> resp.isSuccessful }
    } catch (e: Exception) {
        false
    }
}
