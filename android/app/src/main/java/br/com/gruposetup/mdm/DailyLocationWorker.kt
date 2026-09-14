package br.com.gruposetup.mdm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DailyLocationWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext

        if (!Permissions.temLocalizacaoBackground(ctx)) {
            Notifier.avisarLocalizacao(ctx, "Toque para conceder a permissao de localizacao")
            return Result.success() // sem permissao nao adianta reter
        }
        if (!Permissions.gpsLigado(ctx)) {
            Notifier.avisarLocalizacao(ctx, "Ative a localizacao do dispositivo")
            return Result.retry()
        }

        val fix = LocationRepository.obterLocalizacao(ctx) ?: return Result.retry()
        val payload = DeviceInfo.buildPayload(ctx, fix.location, fix.provider)
        val ok = ApiClient.enviar(payload)

        // aproveita o ciclo diario para checar atualizacao do app
        val info = UpdateChecker.checar()
        if (info != null) {
            Notifier.avisarAtualizacao(ctx, info.versionName)
        }

        return if (ok) Result.success() else Result.retry()
    }
}
