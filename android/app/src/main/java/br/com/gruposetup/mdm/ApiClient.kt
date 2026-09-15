package br.com.gruposetup.mdm

import android.os.Build
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.TlsVersion
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ColabItem(val id: Long, val nome: String, val cargo: String) {
    override fun toString(): String = nome
}

object ApiClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val client: OkHttpClient by lazy { build() }

    private fun base() = BuildConfig.API_BASE_URL.trimEnd('/')
    private fun bearer() = "Bearer " + BuildConfig.API_KEY

    private fun build(): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
        if (Build.VERSION.SDK_INT in 21..22) {
            try {
                val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2).build()
                b.connectionSpecs(listOf(spec, ConnectionSpec.CLEARTEXT))
                Tls12SocketFactory.enable(b)
            } catch (e: Exception) { }
        }
        return b.build()
    }

    /** Envia posicao. Retorna codigo HTTP (0 = erro de rede). */
    fun enviarComCodigo(payload: JSONObject): Int = try {
        val req = Request.Builder()
            .url(base() + "/api/v1/posicoes")
            .addHeader("Authorization", bearer())
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { it.code }
    } catch (e: Exception) { 0 }

    fun enviar(payload: JSONObject): Boolean = enviarComCodigo(payload) in 200..299

    /** Busca colaboradores (chamar fora da main thread). */
    fun buscarColaboradores(q: String): List<ColabItem> = try {
        val url = base() + "/api/v1/colaboradores?q=" + URLEncoder.encode(q, "UTF-8")
        val req = Request.Builder().url(url).addHeader("Authorization", bearer()).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) emptyList()
            else {
                val arr = JSONArray(resp.body?.string() ?: "[]")
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    ColabItem(o.getLong("cadastro_id"), o.optString("nome_completo"), o.optString("nome_cargo"))
                }
            }
        }
    } catch (e: Exception) { emptyList() }

    /** Cadastro atual do device (ou null em erro). Tem campo "cadastrado". */
    fun getCadastro(androidId: String): JSONObject? = try {
        val url = base() + "/api/v1/dispositivos/" + URLEncoder.encode(androidId, "UTF-8") + "/cadastro"
        val req = Request.Builder().url(url).addHeader("Authorization", bearer()).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else JSONObject(resp.body?.string() ?: "{}")
        }
    } catch (e: Exception) { null }

    /** Salva cadastro. Retorna (ok, mensagem). */
    fun salvarCadastro(androidId: String, colaboradorId: Long, patrimonio: String?, imei: String?): Pair<Boolean, String> {
        return try {
            val json = JSONObject()
            json.put("colaborador_id", colaboradorId)
            if (!patrimonio.isNullOrBlank()) json.put("patrimonio", patrimonio)
            if (!imei.isNullOrBlank()) json.put("imei", imei)
            val url = base() + "/api/v1/dispositivos/" + URLEncoder.encode(androidId, "UTF-8") + "/cadastro"
            val req = Request.Builder().url(url).addHeader("Authorization", bearer())
                .post(json.toString().toRequestBody(JSON)).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Pair(true, "ok")
                else {
                    val msg = try { JSONObject(resp.body?.string() ?: "{}").optString("detail", "erro") }
                              catch (e: Exception) { "erro " + resp.code }
                    Pair(false, msg)
                }
            }
        } catch (e: Exception) { Pair(false, "sem conexao") }
    }
}
