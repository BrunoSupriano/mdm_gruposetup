# Setup MDM — Monorepo

Sistema interno do **Grupo Setup** para saber **onde estão os celulares corporativos**.
A empresa distribui aparelhos Android para os colaboradores; este projeto instala em cada
um deles um app próprio que, **1x por dia**, envia a localização do aparelho para um banco
de dados central. A T.I. acompanha tudo por um painel web — útil principalmente em caso de
**perda ou furto** do aparelho.

### O que ele faz

- O app roda em segundo plano e manda **uma posição por dia** (mais o modelo, fabricante,
  versão do Android, bateria e operadora). Também dá pra forçar um envio na hora pelo próprio app.
- No **primeiro uso**, o colaborador se cadastra no app (nome/matrícula + patrimônio **ou** IMEI).
  Depois disso o cadastro trava; só a T.I. destrava (reset) pelo painel.
- O **painel** lista todos os aparelhos, mostra **quem está com cada um** e o **histórico de
  localização no mapa**.
- O app **se atualiza sozinho**: uma nova versão publicada no GitHub chega nos aparelhos no
  ciclo seguinte, com 1 toque.

### O que ele NÃO faz

- **Não bloqueia, não trava e não apaga (wipe)** o aparelho — é só rastreamento de localização.
- **Não captura IMEI automaticamente** no Android 10+ (o sistema não permite a apps comuns);
  por isso o IMEI, quando usado, é **digitado** pelo colaborador no cadastro.
- Não é rastreamento em tempo real: a posição é **diária** (o intervalo é configurável).

### Stack

- **App Android** — Kotlin nativo, `minSdk 21` (Android 5+), WorkManager para o envio diário,
  FusedLocationProvider (com fallback), auto-update via APK assinado.
- **Backend** — FastAPI + asyncpg + **Postgres (Neon)**, migrations versionadas, hospedado no Render.
- **Painel** — React + Vite + Leaflet (mapa), hospedado separado (Render Static / Vercel).
- **CI/CD** — GitHub Actions: push no `backend/` roda migrations e redeploya; tag `vX` compila,
  assina e publica o APK.

```
mdm-setup/
├── backend/            API FastAPI + migrations (Postgres/Neon)
├── android/            App Android (Kotlin) com auto-update
├── frontend/           Painel web (React/Vite) - consulta, histórico e reset de cadastro
└── .github/workflows/  backend.yml + android.yml
```

## Como funciona (o fluxo)

```
commit em backend/   → Actions roda migrations no Neon → redeploy da API no Render
git tag vX + push    → Actions compila+assina o APK → publica Release → registra no backend
                       → no próximo ciclo o app avisa "atualizar" e instala com 1 toque
```

## 1. Criar o repositório e dar o primeiro push

```bash
cd mdm-setup
git init && git add . && git commit -m "projeto mdm"
git branch -M main
git remote add origin https://github.com/BrunoSupriano/mdm-setup.git
git push -u origin main
```

> O `.gitignore` já bloqueia keystore, senhas e builds. **Confira que `keystore.properties`, `*.jks` e `local.properties` NÃO foram commitados** (`git status` antes do push).

## 2. Secrets do GitHub (Settings → Secrets and variables → Actions)

| Secret | Valor |
|---|---|
| `DATABASE_URL` | connection string do Neon (com `?sslmode=require`) |
| `API_KEY` | chave que o app usa (a mesma do backend) |
| `ADMIN_KEY` | chave separada p/ publicar releases (defina no Render também) |
| `API_BASE_URL` | `https://mdm-gruposetup-backend.onrender.com` |
| `RENDER_DEPLOY_HOOK` | (opcional) URL do deploy hook do Render |
| `KEYSTORE_BASE64` | o keystore em base64 (veja abaixo) |
| `KEYSTORE_PASSWORD` | senha do keystore |
| `KEY_ALIAS` | `setupmdm` |
| `KEY_PASSWORD` | senha da chave |

O `KEYSTORE_BASE64` já está pronto no arquivo `keystore.base64.txt` que te entreguei — cole o conteúdo inteiro nesse secret.

## 3. Render (API)

- Serviço já existe. Em **Environment**, garanta: `DATABASE_URL`, `API_KEY` e agora também `ADMIN_KEY`.
- (Opcional) Settings → **Deploy Hook**: copie a URL e ponha no secret `RENDER_DEPLOY_HOOK` — aí cada push no backend redeploya sozinho. Se preferir, deixe o auto-deploy do Render ligado e não use o hook.

## 4. Rodar/testar local (Docker) — stack completa

Na **raiz** do repo (sobe banco + API + painel de uma vez):

```bash
docker compose up --build
#  Painel:  http://localhost:5173
#  API:     http://localhost:8000   (/health)
#  Banco:   localhost:5432          (user/senha/db = mdm)
```

Login do painel em dev: senha `painel-dev` (definida no compose; ajuste em `.env` se quiser — veja `.env.example`).

- Só a API + banco: `cd backend && docker compose up --build`
- Compilar o APK via Docker (o android **não** é serviço do compose):

  ```bash
  docker build -t mdm-apk android/
  docker run --rm -v "$PWD/android":/proj -w /proj mdm-apk ./gradlew :app:assembleDebug
  ```

## 5. Lançar uma nova versão do app

1. Edite `android/app/build.gradle.kts`: aumente `versionCode` (ex.: 1 → 2) e ajuste `versionName`.
2. `git commit` e crie a tag:
   ```bash
   git tag v1.1.0 && git push --tags
   ```
3. O Actions compila, assina, publica a Release e registra no backend.
4. No próximo ciclo diário (ou ao abrir o app), o aparelho recebe o aviso e instala com 1 toque.

## Migrations

Crie `backend/migrations/00X_descricao.sql` e commit. O workflow aplica as pendentes no Neon
(registra em `mdm.schema_migrations`). A API também aplica no start (idempotente).

## ⚠️ Keystore

O arquivo `setupmdm-release.jks` e as senhas te entreguei à parte. **Guarde em lugar seguro.**
Se perder, não dá mais para atualizar o app por cima (o Android exige a mesma chave).


## Painel web (frontend/)

Painel React para o T.I. ver os aparelhos e o histórico no mapa. Login por senha de admin.
Deploy separado (Render Static Site ou Vercel) — veja `frontend/README.md`. Em produção,
defina no backend `PAINEL_SENHA` (senha do painel) e `CORS_ORIGINS` (URL do painel).
