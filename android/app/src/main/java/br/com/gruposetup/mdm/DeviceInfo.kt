package br.com.gruposetup.mdm

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceInfo {

    @SuppressLint("HardwareIds")
    fun androidId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "desconhecido"

    fun bateriaPct(context: Context): Int? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return level * 100 / scale
    }

    fun operadora(context: Context): String? = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        tm?.networkOperatorName?.takeIf { it.isNotBlank() }
    } catch (e: Exception) { null }

    @SuppressLint("HardwareIds", "MissingPermission")
    fun imeiOpcional(context: Context): String? {
        // Somente Android <= 9 (API 28) com READ_PHONE_STATE. No 10+ retorna null.
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return null
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
            val imei = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) tm.imei
                       else @Suppress("DEPRECATION") tm.deviceId
            imei?.takeIf { it.isNotBlank() }
        } catch (e: Exception) { null }
    }

    /** Contas Google visíveis para o app. No Android 8+ costuma vir vazio
     *  (o Google não expõe as contas para apps comuns). Requer GET_ACCOUNTS. */
    @SuppressLint("MissingPermission")
    fun contasGoogle(context: Context): List<String> = try {
        val am = android.accounts.AccountManager.get(context)
        am.getAccountsByType("com.google").map { it.name }
    } catch (e: Exception) { emptyList() }

    private fun agora(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date())

    fun buildPayload(context: Context, loc: Location, provider: String, origem: String = "agendado"): JSONObject {
        val json = JSONObject()
        json.put("android_id", androidId(context))
        json.put("fabricante", Build.MANUFACTURER)
        json.put("modelo", Build.MODEL)
        json.put("versao_android", Build.VERSION.RELEASE)
        json.put("sdk_int", Build.VERSION.SDK_INT)
        json.put("operadora", operadora(context) ?: JSONObject.NULL)
        json.put("bateria_pct", bateriaPct(context) ?: JSONObject.NULL)
        json.put("imei", imeiOpcional(context) ?: JSONObject.NULL)
        json.put("lat", loc.latitude)
        json.put("lon", loc.longitude)
        json.put("precisao_m", if (loc.hasAccuracy()) loc.accuracy else JSONObject.NULL)
        json.put("provider", provider)
        json.put("origem", origem)
        json.put("capturado_em", agora())
        json.put("app_versao", BuildConfig.VERSION_NAME)
        return json
    }
}
