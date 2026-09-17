package br.com.gruposetup.mdm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Roda de hora em hora. Quando o cadastro passa de 30 dias sem reconfirmação,
 *  marca a pendência e dispara uma notificação — e continua avisando a cada hora
 *  até o usuário reconfirmar no app. */
class ReconfirmWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!Prefs.cadastrado(ctx)) return Result.success()

        if (Prefs.reconfirmacaoVencida(ctx)) {
            Prefs.setPrecisaReconfirmar(ctx, true)
        }
        if (Prefs.precisaReconfirmar(ctx)) {
            Notifier.avisarReconfirmar(ctx)
        }
        return Result.success()
    }
}
