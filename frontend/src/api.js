const BASE = (import.meta.env.VITE_API_BASE_URL || 'https://mdm-gruposetup-backend.onrender.com').replace(/\/$/, '')

// "Acorda" o backend do Render free (cold start) — chamado ao abrir o painel e em intervalo.
export function prewarm() {
  try { fetch(BASE + '/health', { cache: 'no-store' }).catch(() => {}) } catch { /* ignore */ }
}

export function getToken() { return localStorage.getItem('mdm_token') || '' }
export function setToken(t) { localStorage.setItem('mdm_token', t) }
export function logout() { localStorage.removeItem('mdm_token') }

async function req(path, opts = {}) {
  const r = await fetch(BASE + path, {
    ...opts,
    headers: { ...(opts.headers || {}), Authorization: 'Bearer ' + getToken() },
  })
  if (r.status === 401) { logout(); throw new Error('sessao expirada') }
  if (!r.ok) throw new Error('erro ' + r.status)
  return r.json()
}

export async function login(senha) {
  const r = await fetch(BASE + '/api/v1/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ senha }),
  })
  if (!r.ok) throw new Error('senha invalida')
  const d = await r.json()
  setToken(d.token)
  return d.token
}

export const listarDispositivos = () => req('/api/v1/dispositivos')
export const historico = (id, limite = 300) =>
  req('/api/v1/dispositivos/' + encodeURIComponent(id) + '/posicoes?limite=' + limite)

// Destrava o cadastro do aparelho (usa a sessão do painel; o backend também aceita X-Admin-Key)
export const resetarCadastro = (id) =>
  req('/api/v1/dispositivos/' + encodeURIComponent(id) + '/cadastro', { method: 'DELETE' })

// Envia um comando push (FCM) para o aparelho. tipo: localizacao | reconfirmar | atualizar | recado
export async function enviarComando(id, tipo, texto) {
  const r = await fetch(BASE + '/api/v1/dispositivos/' + encodeURIComponent(id) + '/comando', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + getToken() },
    body: JSON.stringify({ tipo, texto }),
  })
  if (r.status === 401) { logout(); throw new Error('sessao expirada') }
  if (r.ok) return r.json()
  let detail = ''
  try { detail = (await r.json()).detail || '' } catch { /* ignore */ }
  const err = new Error(detail || ('erro ' + r.status))
  err.status = r.status
  throw err
}
