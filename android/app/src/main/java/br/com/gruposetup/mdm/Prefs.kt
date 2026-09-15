package br.com.gruposetup.mdm

import android.content.Context

object Prefs {
    private const val NAME = "mdm_prefs"
    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun salvarUltima(ctx: Context, lat: Double, lon: Double, quandoMs: Long) {
        sp(ctx).edit()
            .putString("lat", lat.toString())
            .putString("lon", lon.toString())
            .putLong("quando", quandoMs)
            .apply()
    }

    fun temEnvio(ctx: Context) = sp(ctx).contains("quando")
    fun lat(ctx: Context) = sp(ctx).getString("lat", null)
    fun lon(ctx: Context) = sp(ctx).getString("lon", null)
    fun quando(ctx: Context) = sp(ctx).getLong("quando", 0L)
}
