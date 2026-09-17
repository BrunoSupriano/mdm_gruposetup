package br.com.gruposetup.mdm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val WORK_DIARIO = "mdm_localizacao_diaria"
    private const val WORK_RECONFIRM = "mdm_reconfirmar_cadastro"

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun agendarDiario(context: Context) {
        val req = PeriodicWorkRequestBuilder<DailyLocationWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_DIARIO, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Checagem de hora em hora para a reconfirmação mensal do cadastro. */
    fun agendarReconfirmacao(context: Context) {
        val req = PeriodicWorkRequestBuilder<ReconfirmWorker>(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_RECONFIRM, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun executarAgora(context: Context, origem: String = "manual") {
        val req = OneTimeWorkRequestBuilder<DailyLocationWorker>()
            .setConstraints(constraints())
            .setInputData(androidx.work.workDataOf("origem" to origem))
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }
}
