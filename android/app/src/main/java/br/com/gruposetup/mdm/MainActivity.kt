package br.com.gruposetup.mdm

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var androidId: String

    private lateinit var rootScroll: View
    private lateinit var offlineOverlay: View
    private lateinit var btnTentarNovamente: Button
    private lateinit var btnConfig: ImageButton
    private lateinit var updateBanner: LinearLayout
    private lateinit var btnAtualizarMain: Button
    private lateinit var statusPill: TextView
    private lateinit var txtLastLoc: TextView
    private lateinit var txtLastTime: TextView
    private lateinit var txtEnvioStatus: TextView
    private lateinit var btnEnviarAgora: Button
    private lateinit var formCadastro: LinearLayout
    private lateinit var autoColaborador: AutoCompleteTextView
    private lateinit var btnTemPatSim: MaterialButton
    private lateinit var btnTemPatNao: MaterialButton
    private lateinit var boxPatrimonio: LinearLayout
    private lateinit var edtPatrimonio: EditText
    private lateinit var boxImei: LinearLayout
    private lateinit var edtImei: EditText
    private lateinit var btnDiscarImei: Button
    private lateinit var btnSalvarCadastro: Button
    private lateinit var txtCadastroErro: TextView
    private lateinit var cadastroTravado: LinearLayout
    private lateinit var txtColabNome: TextView
    private lateinit var txtColabCargo: TextView
    private lateinit var txtDoc: TextView

    private var colabSelecionado: ColabItem? = null
    // null = ainda não respondeu; true = tem patrimônio; false = usar IMEI
    private var temPatrimonio: Boolean? = null
    private var cadastrado = false

    private var dialogPend: AlertDialog? = null
    private var containerPend: LinearLayout? = null
    private var dialogConfig: AlertDialog? = null
    private var atualizacaoInfo: UpdateChecker.Info? = null

    private data class Pend(val texto: String, val acao: () -> Unit)

    private val reqLoc = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pedirBackgroundSeNecessario(); sincronizar()
    }
    private val reqBg = registerForActivityResult(ActivityResultContracts.RequestPermission()) { sincronizar() }
    private val reqNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { sincronizar() }
    private val abrirConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { sincronizar() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        androidId = DeviceInfo.androidId(this)

        rootScroll = findViewById(R.id.rootScroll)
        offlineOverlay = findViewById(R.id.offlineOverlay)
        btnTentarNovamente = findViewById(R.id.btnTentarNovamente)
        btnConfig = findViewById(R.id.btnConfig)
        updateBanner = findViewById(R.id.updateBanner)
        btnAtualizarMain = findViewById(R.id.btnAtualizarMain)
        statusPill = findViewById(R.id.statusPill)
        txtLastLoc = findViewById(R.id.txtLastLoc)
        txtLastTime = findViewById(R.id.txtLastTime)
        txtEnvioStatus = findViewById(R.id.txtEnvioStatus)
        btnEnviarAgora = findViewById(R.id.btnEnviarAgora)
        formCadastro = findViewById(R.id.formCadastro)
        autoColaborador = findViewById(R.id.autoColaborador)
        btnTemPatSim = findViewById(R.id.btnTemPatSim)
        btnTemPatNao = findViewById(R.id.btnTemPatNao)
        boxPatrimonio = findViewById(R.id.boxPatrimonio)
        edtPatrimonio = findViewById(R.id.edtPatrimonio)
        boxImei = findViewById(R.id.boxImei)
        edtImei = findViewById(R.id.edtImei)
        btnDiscarImei = findViewById(R.id.btnDiscarImei)
        btnSalvarCadastro = findViewById(R.id.btnSalvarCadastro)
        txtCadastroErro = findViewById(R.id.txtCadastroErro)
        cadastroTravado = findViewById(R.id.cadastroTravado)
        txtColabNome = findViewById(R.id.txtColabNome)
        txtColabCargo = findViewById(R.id.txtColabCargo)
        txtDoc = findViewById(R.id.txtDoc)

        autoColaborador.setAdapter(ColaboradorAdapter(this))
        autoColaborador.setOnItemClickListener { parent, _, position, _ ->
            colabSelecionado = parent.getItemAtPosition(position) as? ColabItem
        }
        autoColaborador.doAfterTextChanged {
            val sel = colabSelecionado
            if (sel != null && it?.toString() != sel.nome) colabSelecionado = null
        }

        btnTemPatSim.setOnClickListener { escolherPatrimonio(true) }
        btnTemPatNao.setOnClickListener { escolherPatrimonio(false) }
        btnDiscarImei.setOnClickListener { discarCodigoImei() }

        btnSalvarCadastro.setOnClickListener { salvarCadastro() }
        btnEnviarAgora.setOnClickListener { enviarAgora() }
        btnConfig.setOnClickListener { mostrarConfig() }
        btnTentarNovamente.setOnClickListener { verificarConexao() }
        btnAtualizarMain.setOnClickListener { baixarAtualizacao(btnAtualizarMain) }

        Scheduler.agendarDiario(this)
        mostrarUltima()
    }

    override fun onResume() {
        super.onResume()
        if (!verificarConexao()) return
        sincronizar()
        verificarAtualizacao()
        mostrarUltima()
        if (!cadastrado) carregarCadastro()
    }

    override fun onDestroy() {
        dialogPend?.dismiss(); dialogPend = null
        dialogConfig?.dismiss(); dialogConfig = null
        super.onDestroy()
    }

    // ---------------- conexão ----------------
    /** Mostra/esconde overlay de offline. Retorna true se online. */
    private fun verificarConexao(): Boolean {
        val online = Permissions.temInternet(this)
        offlineOverlay.visibility = if (online) View.GONE else View.VISIBLE
        if (online && offlineOverlay.tag == "estava_offline") {
            offlineOverlay.tag = null
            // reconectou: reprocessa a tela
            sincronizar(); verificarAtualizacao(); mostrarUltima()
            if (!cadastrado) carregarCadastro()
        } else if (!online) {
            offlineOverlay.tag = "estava_offline"
        }
        return online
    }

    // ---------------- última localização ----------------
    private fun mostrarUltima() {
        if (Prefs.temEnvio(this)) {
            val lat = Prefs.lat(this); val lon = Prefs.lon(this)
            txtLastLoc.text = "$lat, $lon"
            val fmt = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
            txtLastTime.text = "Enviado em " + fmt.format(Date(Prefs.quando(this)))
        } else {
            txtLastLoc.text = "Aguardando primeiro envio"
            txtLastTime.text = "—"
        }
    }

    private fun statusEnvio(msg: String, erro: Boolean) {
        txtEnvioStatus.visibility = View.VISIBLE
        txtEnvioStatus.text = msg
        txtEnvioStatus.setTextColor(ContextCompat.getColor(this, if (erro) R.color.error else R.color.ok))
    }

    private fun enviarAgora() = enviarLocalizacao("manual")

    @SuppressLint("MissingPermission")
    private fun enviarLocalizacao(origem: String) {
        if (!Permissions.temLocalizacaoBasica(this)) {
            reqLoc.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        if (!Permissions.gpsLigado(this)) { statusEnvio("Ligue a localização (GPS) do aparelho", true); return }
        statusEnvio("Enviando...", false); btnEnviarAgora.isEnabled = false
        lifecycleScope.launch {
            val fix = withContext(Dispatchers.IO) { LocationRepository.obterLocalizacao(this@MainActivity) }
            if (fix == null) { statusEnvio("Não consegui obter a localização agora", true); btnEnviarAgora.isEnabled = true; return@launch }
            val payload = DeviceInfo.buildPayload(this@MainActivity, fix.location, fix.provider, origem)
            val code = withContext(Dispatchers.IO) { ApiClient.enviarComCodigo(payload) }
            btnEnviarAgora.isEnabled = true
            when {
                code in 200..299 -> {
                    Prefs.salvarUltima(this@MainActivity, fix.location.latitude, fix.location.longitude, System.currentTimeMillis())
                    statusEnvio("Enviado com sucesso!", false); mostrarUltima()
                }
                code == 401 -> statusEnvio("Falha: chave inválida (401)", true)
                code == 0 -> statusEnvio("Falha: sem conexão", true)
                else -> statusEnvio("Falha no envio (HTTP $code)", true)
            }
        }
    }

    // ---------------- cadastro ----------------
    private fun escolherPatrimonio(sim: Boolean) {
        temPatrimonio = sim
        txtCadastroErro.visibility = View.GONE
        boxPatrimonio.visibility = if (sim) View.VISIBLE else View.GONE
        boxImei.visibility = if (sim) View.GONE else View.VISIBLE
        if (sim) edtImei.text?.clear() else edtPatrimonio.text?.clear()
        estilizarToggle(btnTemPatSim, sim)
        estilizarToggle(btnTemPatNao, !sim)
    }

    private fun estilizarToggle(btn: MaterialButton, selecionado: Boolean) {
        val cor = if (selecionado) R.color.primary else R.color.glassFill
        btn.setBackgroundColor(ContextCompat.getColor(this, cor))
        // MaterialButton usa backgroundTintList; garante a cor:
        btn.backgroundTintList = ContextCompat.getColorStateList(this, cor)
    }

    private fun discarCodigoImei() {
        try {
            // "*#06#" precisa do '#' codificado (%23) para o discador interpretar o MMI
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode("*#06#")))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir o discador", Toast.LENGTH_SHORT).show()
        }
    }

    private fun carregarCadastro() {
        lifecycleScope.launch {
            val cad = withContext(Dispatchers.IO) { ApiClient.getCadastro(androidId) }
            if (cad != null && cad.optBoolean("cadastrado", false)) {
                val nome = cad.optString("colaborador_nome", "")
                val cargo = cad.optString("colaborador_cargo", "")
                val patr = cad.optString("patrimonio", "")
                val imei = cad.optString("imei", "")
                val doc = if (patr.isNotEmpty() && patr != "null") "Patrimônio: $patr"
                          else if (imei.isNotEmpty() && imei != "null") "IMEI: $imei" else ""
                mostrarTravado(nome, cargo, doc)
            }
        }
    }

    private fun mostrarTravado(nome: String, cargo: String, doc: String) {
        cadastrado = true
        formCadastro.visibility = View.GONE
        cadastroTravado.visibility = View.VISIBLE
        txtColabNome.text = nome
        txtColabCargo.text = cargo
        txtDoc.text = doc
    }

    private fun erroCadastro(msg: String) {
        txtCadastroErro.visibility = View.VISIBLE
        txtCadastroErro.text = msg
    }

    private fun salvarCadastro() {
        txtCadastroErro.visibility = View.GONE
        val colab = colabSelecionado
        if (colab == null || autoColaborador.text.toString() != colab.nome) {
            erroCadastro("Escolha seu nome na lista de sugestões."); return
        }
        val tem = temPatrimonio
        if (tem == null) { erroCadastro("Responda se o aparelho tem patrimônio."); return }
        val patr = edtPatrimonio.text.toString().trim()
        val imei = edtImei.text.toString().trim()
        if (tem) {
            if (patr.isEmpty() || !patr.all { it.isDigit() } || patr.length > 7) {
                erroCadastro("Patrimônio: só números, até 7 dígitos."); return
            }
        } else {
            if (imei.length < 14) { erroCadastro("Informe um IMEI válido (14-17 dígitos)."); return }
        }
        btnSalvarCadastro.isEnabled = false; btnSalvarCadastro.text = "Salvando..."
        lifecycleScope.launch {
            val (ok, msg) = withContext(Dispatchers.IO) {
                ApiClient.salvarCadastro(androidId, colab.id, if (tem) patr else null, if (tem) null else imei)
            }
            if (ok) {
                val doc = if (tem) "Patrimônio: $patr" else "IMEI: $imei"
                mostrarTravado(colab.nome, colab.cargo, doc)
                Toast.makeText(this@MainActivity, "Cadastro salvo!", Toast.LENGTH_SHORT).show()
                // Dispara a primeira localização imediatamente após o cadastro
                enviarLocalizacao("manual")
            } else {
                btnSalvarCadastro.isEnabled = true; btnSalvarCadastro.text = "Salvar cadastro"
                erroCadastro(traduzErro(msg))
            }
        }
    }

    private fun traduzErro(msg: String): String = when {
        msg.contains("ja cadastrado") -> "Este aparelho já está cadastrado."
        msg.contains("patrimonio OU imei") -> "Informe patrimônio OU IMEI."
        msg.contains("colaborador") -> "Colaborador não encontrado."
        msg.contains("patrimonio") -> "Patrimônio inválido (só números, até 7 dígitos)."
        msg.contains("conexao") -> "Sem conexão. Tente de novo."
        else -> "Não foi possível salvar. Tente novamente."
    }

    // ---------------- configurações (engrenagem) ----------------
    private fun mostrarConfig() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_config, null)
        view.findViewById<TextView>(R.id.txtAndroidId).text = androidId
        view.findViewById<TextView>(R.id.txtVersao).text =
            "Versão " + BuildConfig.VERSION_NAME + " (build " + BuildConfig.VERSION_CODE + ")"

        view.findViewById<Button>(R.id.btnCopiarId).setOnClickListener {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("ANDROID_ID", androidId))
            Toast.makeText(this, "ANDROID_ID copiado", Toast.LENGTH_SHORT).show()
        }

        val btnUpd = view.findViewById<Button>(R.id.btnAtualizar)
        val info = atualizacaoInfo
        if (info != null) {
            btnUpd.visibility = View.VISIBLE
            btnUpd.text = "Atualizar para " + info.versionName
            btnUpd.setOnClickListener { baixarAtualizacao(btnUpd) }
        } else btnUpd.visibility = View.GONE

        val btnVerificar = view.findViewById<Button>(R.id.btnVerificarUpd)
        btnVerificar.setOnClickListener {
            btnVerificar.isEnabled = false; btnVerificar.text = "Verificando..."
            lifecycleScope.launch {
                val nova = withContext(Dispatchers.IO) { UpdateChecker.checar() }
                atualizacaoInfo = nova
                aplicarEstadoAtualizacao()
                btnVerificar.isEnabled = true; btnVerificar.text = "Verificar atualizações"
                if (nova != null) {
                    btnUpd.visibility = View.VISIBLE
                    btnUpd.text = "Atualizar para " + nova.versionName
                    btnUpd.setOnClickListener { baixarAtualizacao(btnUpd) }
                    Toast.makeText(this@MainActivity, "Atualização disponível: " + nova.versionName, Toast.LENGTH_LONG).show()
                } else {
                    btnUpd.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Você já está na versão mais recente", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialogConfig = AlertDialog.Builder(this, R.style.Theme_SetupMDM_Dialog)
            .setView(view).create()
        view.findViewById<Button>(R.id.btnFecharConfig).setOnClickListener { dialogConfig?.dismiss() }
        dialogConfig?.show()
    }

    // ---------------- atualização ----------------
    private fun verificarAtualizacao() {
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { UpdateChecker.checar() }
            atualizacaoInfo = info
            aplicarEstadoAtualizacao()
        }
    }

    /** Reflete o estado da atualização na engrenagem e no banner da tela. */
    private fun aplicarEstadoAtualizacao() {
        val info = atualizacaoInfo
        if (info != null) {
            btnConfig.setColorFilter(ContextCompat.getColor(this, R.color.ok))
            updateBanner.visibility = View.VISIBLE
            findViewById<TextView>(R.id.txtUpdateTitulo).text = "Atualização disponível — " + info.versionName
        } else {
            btnConfig.clearColorFilter()
            updateBanner.visibility = View.GONE
        }
    }

    private fun baixarAtualizacao(botao: Button) {
        botao.isEnabled = false; botao.text = "Baixando..."
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { UpdateChecker.baixarEInstalar(this@MainActivity) }
            if (!ok) {
                botao.isEnabled = true; botao.text = "Atualizar agora"
                Toast.makeText(this@MainActivity, "Falha ao baixar a atualização", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------------- pendências (popup bloqueante) ----------------
    private fun sincronizar() {
        val pend = calcularPendencias()
        if (pend.isEmpty()) {
            statusPill.text = "●  Monitoramento ativo"
            esconderPendencias()
        } else {
            statusPill.text = "●  Configuração pendente"
            mostrarPendencias(pend)
        }
    }

    private fun bateriaOtimizada(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun calcularPendencias(): List<Pend> {
        val lista = mutableListOf<Pend>()
        if (!Permissions.temLocalizacaoBasica(this)) {
            lista.add(Pend("Permitir acesso à localização") {
                reqLoc.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            })
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Permissions.temLocalizacaoBackground(this)) {
            lista.add(Pend("Definir localização como \"Permitir o tempo todo\"") {
                reqBg.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            })
        }
        if (!Permissions.gpsLigado(this)) {
            lista.add(Pend("Ativar a localização (GPS) do aparelho") {
                abrirConfig.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            })
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            lista.add(Pend("Permitir notificações") {
                reqNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
        }
        if (bateriaOtimizada()) {
            lista.add(Pend("Desativar otimização de bateria") { pedirIsencaoBateria() })
        }
        return lista
    }

    private fun mostrarPendencias(pend: List<Pend>) {
        if (dialogPend == null) {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_pendencias, null)
            containerPend = view.findViewById(R.id.listaPendencias)
            dialogPend = AlertDialog.Builder(this, R.style.Theme_SetupMDM_Dialog)
                .setView(view).setCancelable(false).create()
        }
        val cont = containerPend ?: return
        cont.removeAllViews()
        for (p in pend) {
            val row = LayoutInflater.from(this).inflate(R.layout.row_pendencia, cont, false)
            row.findViewById<TextView>(R.id.txtPend).text = p.texto
            row.findViewById<Button>(R.id.btnPend).setOnClickListener { p.acao() }
            cont.addView(row)
        }
        if (dialogPend?.isShowing != true) {
            aplicarBlur(true)
            dialogPend?.show()
        }
    }

    private fun esconderPendencias() {
        if (dialogPend?.isShowing == true) dialogPend?.dismiss()
        aplicarBlur(false)
    }

    private fun aplicarBlur(ativar: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rootScroll.setRenderEffect(
                if (ativar) RenderEffect.createBlurEffect(26f, 26f, Shader.TileMode.CLAMP) else null
            )
        }
    }

    @SuppressLint("BatteryLife")
    private fun pedirIsencaoBateria() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                i.data = Uri.parse("package:$packageName")
                abrirConfig.launch(i)
            } catch (e: Exception) { }
        }
    }

    private fun pedirBackgroundSeNecessario() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            Permissions.temLocalizacaoBasica(this) && !Permissions.temLocalizacaoBackground(this)) {
            reqBg.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
}
