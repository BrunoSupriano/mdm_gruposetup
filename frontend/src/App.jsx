import React, { useEffect, useState } from 'react'
import { MapContainer, TileLayer, Polyline, CircleMarker, Popup, useMap } from 'react-leaflet'
import * as api from './api.js'

function fmt(dt) {
  if (!dt) return '-'
  try { return new Date(dt).toLocaleString('pt-BR') } catch { return dt }
}

function Login({ onOk }) {
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)
  async function entrar(e) {
    e.preventDefault()
    setErro(''); setCarregando(true)
    try { await api.login(senha); onOk() }
    catch { setErro('Senha inválida') }
    finally { setCarregando(false) }
  }
  return (
    <div className="login-wrap">
      <h1>Setup MDM</h1>
      <p className="muted">Painel de monitoramento — acesso restrito</p>
      <form onSubmit={entrar} className="card">
        <input type="password" placeholder="Senha do painel" value={senha}
               onChange={e => setSenha(e.target.value)} autoFocus />
        <button className="primary" disabled={carregando}>
          {carregando ? 'Entrando...' : 'Entrar'}
        </button>
        {erro && <div className="err">{erro}</div>}
      </form>
    </div>
  )
}

function Dispositivos({ onAbrir }) {
  const [lista, setLista] = useState(null)
  const [erro, setErro] = useState('')
  useEffect(() => {
    api.listarDispositivos().then(setLista).catch(() => setErro('Falha ao carregar'))
  }, [])
  if (erro) return <div className="card err">{erro}</div>
  if (!lista) return <div className="card muted">Carregando...</div>
  if (lista.length === 0) return <div className="card muted">Nenhum aparelho registrou posição ainda.</div>
  return (
    <div className="card">
      <table>
        <thead>
          <tr><th>Aparelho</th><th>Patrimônio</th><th>Bateria</th><th>Último visto</th><th></th></tr>
        </thead>
        <tbody>
          {lista.map(d => (
            <tr key={d.android_id} className="click" onClick={() => onAbrir(d)}>
              <td><b>{d.fabricante || ''} {d.modelo || d.android_id}</b><br/>
                  <span className="muted">{d.android_id}</span></td>
              <td>{d.patrimonio || <span className="muted">—</span>}</td>
              <td>{d.bateria_pct != null ? d.bateria_pct + '%' : '—'}</td>
              <td>{fmt(d.ultimo_visto)}</td>
              <td><span className="badge">histórico</span></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function FitBounds({ pontos }) {
  const map = useMap()
  useEffect(() => {
    if (pontos.length) {
      const b = pontos.map(p => [p.lat, p.lon])
      map.fitBounds(b, { padding: [40, 40], maxZoom: 16 })
    }
  }, [pontos, map])
  return null
}

function Historico({ device, onVoltar }) {
  const [pts, setPts] = useState(null)
  const [erro, setErro] = useState('')
  useEffect(() => {
    api.historico(device.android_id).then(setPts).catch(() => setErro('Falha ao carregar histórico'))
  }, [device])
  if (erro) return <div className="card err">{erro}</div>
  if (!pts) return <div className="card muted">Carregando...</div>
  const centro = pts.length ? [pts[0].lat, pts[0].lon] : [-28.68, -49.37]
  const linha = pts.map(p => [p.lat, p.lon])
  return (
    <div>
      <button className="link" onClick={onVoltar}>&larr; voltar</button>
      <h2>{device.fabricante || ''} {device.modelo || device.android_id}</h2>
      <p className="muted">{device.android_id} · {pts.length} posições</p>
      {pts.length > 0 && (
        <div className="map">
          <MapContainer center={centro} zoom={13} style={{ height: '100%', width: '100%' }}>
            <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                       attribution="&copy; OpenStreetMap" />
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
      <div className="card">
        <table>
          <thead><tr><th>Data</th><th>Lat</th><th>Lon</th><th>Precisão</th><th>Bateria</th><th>Fonte</th></tr></thead>
          <tbody>
            {pts.map((p, i) => (
              <tr key={i}>
                <td>{fmt(p.capturado_em)}</td>
                <td>{p.lat.toFixed(5)}</td>
                <td>{p.lon.toFixed(5)}</td>
                <td>{p.precisao_m != null ? Math.round(p.precisao_m) + ' m' : '—'}</td>
                <td>{p.bateria_pct != null ? p.bateria_pct + '%' : '—'}</td>
                <td>{p.provider || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default function App() {
  const [logado, setLogado] = useState(!!api.getToken())
  const [device, setDevice] = useState(null)
  if (!logado) return <Login onOk={() => setLogado(true)} />
  return (
    <div>
      <div className="topbar">
        <h1>Setup MDM — Painel</h1>
        <button onClick={() => { api.logout(); setLogado(false); setDevice(null) }}>Sair</button>
      </div>
      <div className="container">
        {device
          ? <Historico device={device} onVoltar={() => setDevice(null)} />
          : <Dispositivos onAbrir={setDevice} />}
      </div>
    </div>
  )
}
