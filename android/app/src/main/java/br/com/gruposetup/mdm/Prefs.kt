package br.com.gruposetup.mdm

import android.content.Context

object Prefs {
    private const val NAME = "mdm_prefs"
    // 30 dias em ms — janela de reconfirmação do cadastro
    const val INTERVALO_RECONFIRMAR_MS = 30L * 24 * 60 * 60 * 1000

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---------------- última localização enviada ----------------
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

    // ---------------- cadastro (cache local) ----------------
    /** Confirmação/cadastro feito pelo usuário: renova o relógio de reconfirmação. */
    fun registrarConfirmacao(ctx: Context, nome: String, cargo: String, doc: String) {
        sp(ctx).edit()
            .putBoolean("cad", true)
            .putString("cadNome", nome)
            .putString("cadCargo", cargo)
            .putString("cadDoc", doc)
            .putLong("cadConfirmadoEm", System.currentTimeMillis())
            .putBoolean("precisaReconf", false)
            .apply()
    }

    /** Estado vindo do servidor (ao abrir o app): atualiza os dados, mas NÃO reinicia
     *  o relógio de reconfirmação (só uma confirmação do usuário faz isso). */
    fun marcarDoServidor(ctx: Context, nome: String, cargo: String, doc: String) {
        val e = sp(ctx).edit()
            .putBoolean("cad", true)
            .putString("cadNome", nome)
            .putString("cadCargo", cargo)
            .putString("cadDoc", doc)
        if (sp(ctx).getLong("cadConfirmadoEm", 0L) == 0L) {
            e.putLong("cadConfirmadoEm", System.currentTimeMillis())
        }
        e.apply()
    }

    fun limparCadastro(ctx: Context) {
        sp(ctx).edit()
            .remove("cad").remove("cadNome").remove("cadCargo").remove("cadDoc")
            .remove("cadConfirmadoEm").remove("precisaReconf")
            .apply()
    }

    fun cadastrado(ctx: Context) = sp(ctx).getBoolean("cad", false)
    fun cadNome(ctx: Context) = sp(ctx).getString("cadNome", "") ?: ""
    fun cadCargo(ctx: Context) = sp(ctx).getString("cadCargo", "") ?: ""
    fun cadDoc(ctx: Context) = sp(ctx).getString("cadDoc", "") ?: ""
    fun confirmadoEm(ctx: Context) = sp(ctx).getLong("cadConfirmadoEm", 0L)

    fun precisaReconfirmar(ctx: Context) = sp(ctx).getBoolean("precisaReconf", false)
    fun setPrecisaReconfirmar(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean("precisaReconf", v).apply()

    /** true se o cadastro está ativo e já passou da janela de 30 dias. */
    fun reconfirmacaoVencida(ctx: Context): Boolean {
        if (!cadastrado(ctx)) return false
        val conf = confirmadoEm(ctx)
        if (conf == 0L) return false
        return System.currentTimeMillis() - conf >= INTERVALO_RECONFIRMAR_MS
    }
}
