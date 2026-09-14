package br.com.gruposetup.mdm

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object UpdateChecker {

    data class Info(val versionCode: Int, val versionName: String, val obrigatoria: Boolean)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun base() = BuildConfig.API_BASE_URL.trimEnd('/')

    /** Info se houver versao mais nova que a instalada; senao null. (roda fora da main thread) */
    fun checar(): Info? {
        return try {
            val req = Request.Builder()
                .url(base() + "/api/v1/app/versao")
                .addHeader("Authorization", "Bearer " + BuildConfig.API_KEY)
                .get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string() ?: return null)
                if (!json.optBoolean("disponivel", false)) return null
                val vc = json.optInt("version_code", 0)
                if (vc <= BuildConfig.VERSION_CODE) return null
                Info(vc, json.optString("version_name", ""), json.optBoolean("obrigatoria", false))
            }
        } catch (e: Exception) { null }
    }

    /** Baixa o APK novo e dispara o instalador do sistema. (roda fora da main thread) */
    fun baixarEInstalar(context: Context): Boolean {
        return try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apk = File(dir, "update.apk")
            val req = Request.Builder()
                .url(base() + "/api/v1/app/apk")
                .addHeader("Authorization", "Bearer " + BuildConfig.API_KEY)
                .get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val body = resp.body ?: return false
                body.byteStream().use { input -> apk.outputStream().use { out -> input.copyTo(out) } }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }
}
