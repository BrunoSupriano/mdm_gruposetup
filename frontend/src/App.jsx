import React, { useEffect, useState } from 'react'
import { HashRouter, Routes, Route, useNavigate, useParams, useLocation, Navigate } from 'react-router-dom'
import { MapContainer, TileLayer, Polyline, CircleMarker, Popup, useMap } from 'react-leaflet'
import * as api from './api.js'

function fmt(dt) {
  if (!dt) return '—'
  try { return new Date(dt).toLocaleString('pt-BR') } catch { return dt }
}

// aparelho "sumido" se não envia há mais de 2 dias
function statusAparelho(ultimo) {
  if (!ultimo) return { txt: 'sem envio', cls: 'st-off' }
  const dias = (Date.now() - new Date(ultimo).getTime()) / 86400000
  if (dias > 2) return { txt: 'inativo', cls: 'st-off' }
  if (dias > 1) return { txt: 'atenção', cls: 'st-warn' }
  return { txt: 'ativo', cls: 'st-on' }
}

function Login({ onOk }) {
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)
  useEffect(() => { api.prewarm() }, [])   // acorda o backend enquanto o T.I. digita
  async function entrar(e) {
    e.preventDefault()
    setErro(''); setCarregando(true)
    try { await api.login(senha); onOk() }
    catch { setErro('Senha inválida') }
    finally { setCarregando(false) }
  }
  return (
    <div className="login-wrap">
      <div className="brand-badge">SM</div>
      <h1>Setup MDM</h1>
      <p className="muted">Painel de monitoramento — acesso restrito</p>
      <form onSubmit={entrar} className="card login-card">
        <label className="field-label">Senha do painel</label>
        <input type="password" placeholder="••••••••" value={senha}
               onChange={e => setSenha(e.target.value)} autoFocus />
        <button className="primary" disabled={carregando}>
          {carregando ? 'Entrando…' : 'Entrar'}
        </button>
        {erro && <div className="err">{erro}</div>}
      </form>
      <p className="muted tiny">A primeira conexão pode levar alguns segundos (o servidor acorda).</p>
    </div>
  )
}

function Dispositivos() {
  const nav = useNavigate()
  const [lista, setLista] = useState(null)
  const [erro, setErro] = useState('')
  const [busca, setBusca] = useState('')
  useEffect(() => {
    api.listarDispositivos().then(setLista).catch(() => setErro('Falha ao carregar'))
  }, [])
  if (erro) return <div className="card err">{erro}</div>
  if (!lista) return <div className="card skeleton">Carregando aparelhos…</div>
  if (lista.length === 0) return <div className="card empty">Nenhum aparelho registrou posição ainda.</div>

  const q = busca.trim().toLowerCase()
  const filtrada = !q ? lista : lista.filter(d =>
    [d.modelo, d.fabricante, d.android_id, d.colaborador_nome, d.equipe_descricao, d.patrimonio]
      .some(v => (v || '').toString().toLowerCase().includes(q)))

  const ativos = lista.filter(d => statusAparelho(d.ultimo_visto).cls === 'st-on').length

  return (
    <div>
      <div className="stat-row">
        <div className="stat"><div className="stat-num">{lista.length}</div><div className="stat-lbl">aparelhos</div></div>
        <div className="stat"><div className="stat-num">{ativos}</div><div className="stat-lbl">ativos hoje</div></div>
        <div className="stat"><div className="stat-num">{lista.length - ativos}</div><div className="stat-lbl">inativos</div></div>
      </div>

      <div className="card">
        <input className="search" placeholder="Buscar por modelo, responsável, patrimônio…"
               value={busca} onChange={e => setBusca(e.target.value)} />
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Aparelho</th><th>Responsável</th><th>Patrimônio</th><th>Bateria</th><th>Último visto</th><th>Status</th></tr>
            </thead>
            <tbody>
              {filtrada.map(d => {
                const st = statusAparelho(d.ultimo_visto)
                return (
                  <tr key={d.android_id} className="click"
                      onClick={() => nav('/dispositivo/' + encodeURIComponent(d.android_id), { state: { device: d } })}>
                    <td><b>{d.fabricante || ''} {d.modelo || d.android_id}</b><br/>
                        <span className="muted mono">{d.android_id}</span></td>
                    <td>
                      {d.tipo_uso === 'equipe' && d.equipe_descricao
                        ? <><b>{d.equipe_descricao}</b><br/><span className="muted">Equipe{d.equipe_processo ? ' · ' + d.equipe_processo : ''}</span></>
                        : d.colaborador_nome
                          ? <><b>{d.colaborador_nome}</b>{d.colaborador_cargo && <><br/><span className="muted">{d.colaborador_cargo}</span></>}</>
                          : <span className="muted">não cadastrado</span>}
                    </td>
                    <td>{d.patrimonio || <span className="muted">—</span>}</td>
                    <td>{d.bateria_pct != null ? d.bateria_pct + '%' : '—'}</td>
                    <td className="nowrap">{fmt(d.ultimo_visto)}</td>
                    <td><span className={'status ' + st.cls}>{st.txt}</span></td>
                  </tr>
                )
              })}
              {filtrada.length === 0 && (
                <tr><td colSpan="6" className="muted" style={{ textAlign: 'center', padding: '24px' }}>Nada encontrado para “{busca}”.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

function FitBounds({ pontos }) {
  const map = useMap()
  useEffect(() => {
    if (pontos.length) map.fitBounds(pontos.map(p => [p.lat, p.lon]), { padding: [40, 40], maxZoom: 16 })
  }, [pontos, map])
  return null
}

function Historico() {
  const nav = useNavigate()
  const { id } = useParams()
  const loc = useLocation()
  const [device, setDevice] = useState(loc.state?.device || null)
  const [pts, setPts] = useState(null)
  const [erro, setErro] = useState('')
  const [confirmando, setConfirmando] = useState(false)
  const [resetando, setResetando] = useState(false)
  const [resetMsg, setResetMsg] = useState('')
  const [cmdMsg, setCmdMsg] = useState('')
  const [cmdOk, setCmdOk] = useState(false)
  const [cmdBusy, setCmdBusy] = useState('')

  // se veio por refresh (sem state), busca o device na lista
  useEffect(() => {
    if (!device) api.listarDispositivos().then(l => setDevice((l || []).find(d => d.android_id === id) || null)).catch(() => {})
  }, [id])

  useEffect(() => {
    api.historico(id).then(setPts).catch(() => setErro('Falha ao carregar histórico'))
  }, [id])

  async function resetar() {
    setResetando(true); setResetMsg('')
    try {
      await api.resetarCadastro(id)
      setResetMsg('Cadastro limpo. O aparelho volta a pedir o cadastro (na hora, se o push estiver ligado).')
      setConfirmando(false)
    } catch { setResetMsg('Falha ao limpar o cadastro. Tente novamente.') }
    finally { setResetando(false) }
  }

  async function comando(tipo) {
    setCmdBusy(tipo); setCmdMsg(''); setCmdOk(false)
    try {
      await api.enviarComando(id, tipo, '')
      setCmdOk(true)
      setCmdMsg(tipo === 'localizacao'
        ? 'Pedido enviado. A localização deve chegar em instantes — recarregue o histórico.'
        : 'Pedido de reconfirmação enviado ao aparelho.')
    } catch (e) {
      const s = e.status
      setCmdMsg(
        s === 503 ? 'Push (FCM) ainda não está configurado no servidor.'
        : s === 409 ? 'Este aparelho ainda não registrou o push (precisa abrir o app uma vez com a nova versão).'
        : s === 404 ? 'Aparelho não encontrado.'
        : 'Falha ao enviar o comando.')
    } finally { setCmdBusy('') }
  }

  const d = device || { android_id: id }
  const jaResetado = resetMsg.startsWith('Cadastro limpo')
  const centro = pts && pts.length ? [pts[0].lat, pts[0].lon] : [-28.68, -49.37]
  const linha = pts ? pts.map(p => [p.lat, p.lon]) : []

  return (
    <div>
      <button className="link back" onClick={() => nav(-1)}>&larr; voltar</button>
      <h2>{d.fabricante || ''} {d.modelo || d.android_id}</h2>
      <p className="muted mono">{d.android_id}{pts ? ' · ' + pts.length + ' posições' : ''}</p>

      <div className="card">
        <div className="row-between">
          <div>
            <div className="label">{d.tipo_uso === 'equipe' ? 'Equipe responsável' : 'Responsável atual'}</div>
            {d.tipo_uso === 'equipe' && d.equipe_descricao
              ? <div><b>{d.equipe_descricao}</b>{d.equipe_processo ? ' · ' + d.equipe_processo : ''}
                  {d.patrimonio ? <span className="muted"> · Patrimônio {d.patrimonio}</span>
                    : d.imei ? <span className="muted"> · IMEI {d.imei}</span> : null}</div>
              : d.colaborador_nome
                ? <div><b>{d.colaborador_nome}</b>{d.colaborador_cargo ? ' · ' + d.colaborador_cargo : ''}
                    {d.patrimonio ? <span className="muted"> · Patrimônio {d.patrimonio}</span>
                      : d.imei ? <span className="muted"> · IMEI {d.imei}</span> : null}</div>
                : d.patrimonio
                  ? <div><span className="muted">Patrimônio {d.patrimonio}</span></div>
                  : <div className="muted">Sem cadastro ativo</div>}
          </div>
          {!jaResetado && (
            confirmando
              ? <div className="reset-confirm">
                  <span>Limpar o cadastro deste aparelho?</span>
                  <button className="danger" disabled={resetando} onClick={resetar}>
                    {resetando ? 'Limpando…' : 'Sim, limpar'}
                  </button>
                  <button className="link" disabled={resetando} onClick={() => setConfirmando(false)}>cancelar</button>
                </div>
              : <button className="danger-outline" onClick={() => setConfirmando(true)}>Limpar cadastro</button>
          )}
        </div>
        {resetMsg && <div className={jaResetado ? 'ok-msg' : 'err'}>{resetMsg}</div>}

        <div className="cmd-row">
          <button className="primary-outline" disabled={!!cmdBusy} onClick={() => comando('localizacao')}>
            {cmdBusy === 'localizacao' ? 'Enviando…' : '📍 Solicitar localização agora'}
          </button>
          <button className="ghost-btn" disabled={!!cmdBusy} onClick={() => comando('reconfirmar')}>
            {cmdBusy === 'reconfirmar' ? 'Enviando…' : 'Pedir reconfirmação'}
          </button>
        </div>
        {cmdMsg && <div className={cmdOk ? 'ok-msg' : 'err'}>{cmdMsg}</div>}
      </div>

      {erro && <div className="card err">{erro}</div>}
      {!pts && !erro && <div className="card skeleton">Carregando histórico…</div>}

      {pts && pts.length > 0 && (
        <div className="map">
          <MapContainer center={centro} zoom={13} style={{ height: '100%', width: '100%' }}>
            <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" attribution="&copy; OpenStreetMap" />
            <Polyline positions={linha} color="#006D58" />
            {pts.map((p, i) => (
              <CircleMarker key={i} center={[p.lat, p.lon]} radius={i === 0 ? 8 : 5}
                            color={i === 0 ? '#008CBA' : '#5bb98e'} fillOpacity={0.9}>
                <Popup>{fmt(p.capturado_em)}<br/>{p.lat.toFixed(5)}, {p.lon.toFixed(5)}</Popup>
              </CircleMarker>
            ))}
            <FitBounds pontos={pts} />
          </MapContainer>
        </div>
      )}

      {pts && pts.length > 0 && (
        <div className="card">
          <div className="table-wrap">
            <table>
              <thead><tr><th>Data</th><th>Lat</th><th>Lon</th><th>Precisão</th><th>Bateria</th><th>Fonte</th></tr></thead>
              <tbody>
                {pts.map((p, i) => (
                  <tr key={i}>
                    <td className="nowrap">{fmt(p.capturado_em)}</td>
                    <td className="mono">{p.lat.toFixed(5)}</td>
                    <td className="mono">{p.lon.toFixed(5)}</td>
                    <td>{p.precisao_m != null ? Math.round(p.precisao_m) + ' m' : '—'}</td>
                    <td>{p.bateria_pct != null ? p.bateria_pct + '%' : '—'}</td>
                    <td>{p.provider || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {pts && pts.length === 0 && <div className="card empty">Este aparelho ainda não enviou nenhuma posição.</div>}
    </div>
  )
}

function Shell({ onSair, children }) {
  return (
    <div>
      <div className="topbar">
        <div className="topbar-brand"><span className="brand-badge sm">SM</span> Setup MDM — Painel</div>
        <button className="btn-sair" onClick={onSair}>Sair</button>
      </div>
      <div className="container">{children}</div>
    </div>
  )
}

export default function App() {
  const [logado, setLogado] = useState(!!api.getToken())

  // keep-warm: cutuca o /health a cada 10 min enquanto o painel está aberto
  useEffect(() => {
    if (!logado) return
    api.prewarm()
    const t = setInterval(api.prewarm, 10 * 60 * 1000)
    return () => clearInterval(t)
  }, [logado])

  if (!logado) return <Login onOk={() => setLogado(true)} />

  const sair = () => { api.logout(); setLogado(false) }
  return (
    <HashRouter>
      <Shell onSair={sair}>
        <Routes>
          <Route path="/" element={<Dispositivos />} />
          <Route path="/dispositivo/:id" element={<Historico />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Shell>
    </HashRouter>
  )
}
