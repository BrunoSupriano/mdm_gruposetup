package br.com.gruposetup.mdm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

/** Recebe comandos do painel via push (FCM). Mensagens são "data-only", então
 *  onMessageReceived é chamado mesmo com o app fechado/segundo plano. */
class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        registrarToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val ctx = applicationContext
        val tipo = message.data["tipo"] ?: return
        val texto = message.data["texto"] ?: ""
        when (tipo) {
            "localizacao" -> Scheduler.executarAgora(ctx, "painel")
            "reconfirmar" -> {
                Prefs.setPrecisaReconfirmar(ctx, true)
                Notifier.avisarReconfirmar(ctx)
            }
            "atualizar" -> runBlocking {
                val info = UpdateChecker.checar()
                if (info != null) Notifier.avisarAtualizacao(ctx, info.versionName)
            }
            "recado" -> Notifier.avisarRecado(ctx, texto)
        }
    }

    companion object {
        /** Registra o token no backend numa thread (fora da main). */
        fun registrarToken(ctx: android.content.Context, token: String) {
            Thread {
                try { ApiClient.registrarFcmToken(DeviceInfo.androidId(ctx), token) } catch (e: Exception) { }
            }.start()
        }
    }
}
