const BASE = (import.meta.env.VITE_API_BASE_URL || 'https://mdm-gruposetup-backend.onrender.com').replace(/\/$/, '')

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
