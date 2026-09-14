# Setup MDM — Frontend (painel web)

Painel React (Vite) para o T.I. consultar os aparelhos e o histórico de localização.
Login por senha única de admin. Lê a API do backend (não usa a chave dos aparelhos).

## Rodar local
```bash
cd frontend
npm install
echo "VITE_API_BASE_URL=http://localhost:8000" > .env   # aponta pro backend local
npm run dev            # abre em http://localhost:5173
```

## Build
```bash
npm run build          # gera dist/ (estático)
```

## Deploy (escolha um)

### Render (Static Site)
1. New + → **Static Site** → conecte o repo.
2. Root Directory: `frontend`
3. Build Command: `npm install && npm run build`
4. Publish Directory: `dist`
5. Environment: `VITE_API_BASE_URL` = URL da API (ex.: https://mdm-gruposetup-backend.onrender.com)

### Vercel
1. Import do repo → Root Directory: `frontend`
2. Framework: Vite (detecta sozinho)
3. Env: `VITE_API_BASE_URL` = URL da API

> Deploys de site estático **atualizam sozinhos** a cada push (não precisa de GitHub Action).

## IMPORTANTE (produção)
- No **backend** (Render), defina:
  - `PAINEL_SENHA` = a senha de acesso ao painel (forte).
  - `CORS_ORIGINS` = a URL exata do painel (ex.: `https://painel-mdm.onrender.com`) — **troque o `*`** por isso em produção.
- A senha do painel nunca fica no frontend; o login troca senha por um token temporário (12h).
