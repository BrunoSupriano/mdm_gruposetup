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

    fun executarAgora(context: Context) {
        val req = OneTimeWorkRequestBuilder<DailyLocationWorker>()
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }
}
