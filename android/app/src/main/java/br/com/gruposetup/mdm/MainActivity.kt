package br.com.gruposetup.mdm

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var statusGeral: TextView
    private lateinit var linhaLoc: TextView
    private lateinit var linhaBg: TextView
    private lateinit var linhaGps: TextView
    private lateinit var linhaNotif: TextView
    private lateinit var linhaBateria: TextView
    private lateinit var linhaGms: TextView
    private lateinit var txtAndroidId: TextView
    private lateinit var txtVersao: TextView
    private lateinit var btnCopiarId: Button
    private lateinit var btnAtualizar: Button
    private lateinit var btnCorrigir: Button
    private lateinit var btnEnviarAgora: Button

    private var updateInfo: UpdateChecker.Info? = null

    private val reqLoc = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pedirBackgroundSeNecessario(); atualizar()
    }
    private val reqBg = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pedirNotifSeNecessario(); atualizar()
    }
    private val reqNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pedirIsencaoBateria(); atualizar()
    }
    private val abrirConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        atualizar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusGeral = findViewById(R.id.statusGeral)
        linhaLoc = findViewById(R.id.linhaLoc)
        linhaBg = findViewById(R.id.linhaBg)
        linhaGps = findViewById(R.id.linhaGps)
        linhaNotif = findViewById(R.id.linhaNotif)
        linhaBateria = findViewById(R.id.linhaBateria)
        linhaGms = findViewById(R.id.linhaGms)
        txtAndroidId = findViewById(R.id.txtAndroidId)
        txtVersao = findViewById(R.id.txtVersao)
        btnCopiarId = findViewById(R.id.btnCopiarId)
        btnAtualizar = findViewById(R.id.btnAtualizar)
        btnCorrigir = findViewById(R.id.btnCorrigir)
        btnEnviarAgora = findViewById(R.id.btnEnviarAgora)

        val androidId = DeviceInfo.androidId(this)
        txtAndroidId.text = androidId
        txtVersao.text = "Versao " + BuildConfig.VERSION_NAME + " (build " + BuildConfig.VERSION_CODE + ")"

        btnCopiarId.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ANDROID_ID", androidId))
            Toast.makeText(this, "ANDROID_ID copiado", Toast.LENGTH_SHORT).show()
        }
        btnCorrigir.setOnClickListener { iniciarSetup() }
        btnEnviarAgora.setOnClickListener {
            Scheduler.executarAgora(this)
            statusGeral.text = "Envio disparado..."
        }
        btnAtualizar.setOnClickListener {
            btnAtualizar.isEnabled = false
            btnAtualizar.text = "Baixando..."
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { UpdateChecker.baixarEInstalar(this@MainActivity) }
                if (!ok) {
                    btnAtualizar.isEnabled = true
                    btnAtualizar.text = "Atualizar aplicativo"
                    Toast.makeText(this@MainActivity, "Falha ao baixar a atualizacao", Toast.LENGTH_LONG).show()
                }
            }
        }

        Scheduler.agendarDiario(this)
    }

    override fun onResume() {
        super.onResume()
        atualizar()
        verificarAtualizacao()
    }

    private fun verificarAtualizacao() {
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { UpdateChecker.checar() }
            updateInfo = info
            if (info != null) {
                btnAtualizar.visibility = android.view.View.VISIBLE
                btnAtualizar.text = "Atualizar para " + info.versionName
            } else {
                btnAtualizar.visibility = android.view.View.GONE
            }
        }
    }

    private fun iniciarSetup() {
        if (!Permissions.temLocalizacaoBasica(this)) {
            reqLoc.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            pedirBackgroundSeNecessario()
        }
    }

    private fun pedirBackgroundSeNecessario() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            Permissions.temLocalizacaoBasica(this) &&
            !Permissions.temLocalizacaoBackground(this)) {
            reqBg.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            pedirNotifSeNecessario()
        }
    }

    private fun pedirNotifSeNecessario() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            reqNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            pedirIsencaoBateria()
        }
    }

    @SuppressLint("BatteryLife")
    private fun pedirIsencaoBateria() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    i.data = Uri.parse("package:$packageName")
                    abrirConfig.launch(i)
                    return
                } catch (e: Exception) { }
            }
        }
        atualizar()
    }

    private fun bateriaOtimizada(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun temGms(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS

    private fun linha(view: TextView, ok: Boolean, okText: String, falhaText: String) {
        if (ok) {
            view.text = "OK  -  $okText"
            view.setTextColor(ContextCompat.getColor(this, R.color.ok))
        } else {
            view.text = "!  -  $falhaText"
            view.setTextColor(ContextCompat.getColor(this, R.color.error))
        }
    }

    private fun atualizar() {
        val loc = Permissions.temLocalizacaoBasica(this)
        val bg = Permissions.temLocalizacaoBackground(this)
        val gps = Permissions.gpsLigado(this)
        val notif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val bateriaOk = !bateriaOtimizada()

        linha(linhaLoc, loc, "Permissao de localizacao", "Permissao de localizacao necessaria")
        linha(linhaBg, bg, "Localizacao em segundo plano", "Falta 'Permitir o tempo todo'")
        linha(linhaGps, gps, "Localizacao (GPS) ligada", "Localizacao do aparelho desligada")
        linha(linhaNotif, notif, "Notificacoes permitidas", "Permita notificacoes")
        linha(linhaBateria, bateriaOk, "Bateria sem restricao", "Otimizacao de bateria ativa")

        if (temGms()) {
            linhaGms.text = "OK  -  Google Play Services disponivel"
            linhaGms.setTextColor(ContextCompat.getColor(this, R.color.ok))
        } else {
            linhaGms.text = "i  -  Usando modo alternativo de localizacao"
            linhaGms.setTextColor(ContextCompat.getColor(this, R.color.accent))
        }

        val tudoOk = loc && bg && gps
        if (tudoOk) {
            statusGeral.text = "Monitoramento ativo"
            statusGeral.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
            btnCorrigir.text = "Revisar permissoes"
        } else {
            statusGeral.text = "Configuracao incompleta"
            statusGeral.setBackgroundColor(ContextCompat.getColor(this, R.color.error))
            btnCorrigir.text = "Concluir configuracao"
        }
    }
}
