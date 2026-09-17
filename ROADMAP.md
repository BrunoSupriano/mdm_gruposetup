# Roadmap — Setup MDM

Backlog priorizado por esforço e dependência.

**Legenda de esforço:** 🟢 pequeno · 🟡 médio · 🔴 grande.
**Onde vive:** 📱 app · 🗄️ backend/DB · 📊 painel.

---

## ✅ Fase 1 — Rápido e alto valor (v1.2.0) — CONCLUÍDA

Entregue numa leva só: 1 versão do app (`versionCode 3` / `1.2.0`) + 1 migration (`004_posicoes_origem.sql`).

| # | Item | Esforço | Onde | Status |
|---|---|---|---|---|
| 1 | Bug do botão "Permitir/Resolver" sem texto (trocado por `MaterialButton` com `backgroundTint` + `textColor`) | 🟢 | 📱 | ✅ |
| 2 | "Permitir o tempo todo" como condição obrigatória (pendência bloqueante no Android 10+) | 🟢 | 📱 | ✅ |
| 3 | Sem internet → tela cheia "Sem conexão" em vez de abrir o cadastro | 🟢 | 📱 | ✅ |
| 4 | Ao concluir o cadastro, disparar a localização na hora (origem `manual`) | 🟢 | 📱 | ✅ |
| 5 | Aviso "não exclua o app" na tela | 🟢 | 📱 | ✅ |
| 6 | "Copiar ANDROID_ID" movido para ícone de config (engrenagem) no topo direito, junto com versão e atualização | 🟢 | 📱 | ✅ |
| 7 | Campo patrimônio/IMEI: **pergunta primeiro** "Tem patrimônio?" → Sim mostra patrimônio / Não mostra IMEI | 🟢 | 📱 | ✅ |
| 8 | Botão de discador rápido `*#06#` ao lado do campo IMEI | 🟢 | 📱 | ✅ |
| 9 | Ajuste tablet/telas grandes (padding via `values-sw600dp/dimens.xml`) | 🟡 | 📱 | ✅ |
| 10 | Remover coluna `imei` de `posicoes` (o IMEI vive em `devices`) | 🟢 | 🗄️ | ✅ |
| 11 | Coluna `origem` em `posicoes` (`agendado` × `manual`) — o app manda a flag | 🟡 | 📱🗄️ | ✅ |

**Detalhes técnicos da Fase 1:**

- **Migration `004_posicoes_origem.sql`**: derruba a view `mdm.ultima_posicao` (ela usa `SELECT *`, então depende de `imei`), faz `DROP COLUMN imei`, adiciona `origem TEXT NOT NULL DEFAULT 'agendado'` e recria a view.
- **`main.py`**: `Posicao.origem` (opcional); o `INSERT` em `posicoes` grava `origem` (validado para `agendado`/`manual`, default `agendado`) e não grava mais `imei`. O `imei` continua no upsert de `devices`.
- **App**: `DeviceInfo.buildPayload(..., origem)` — o botão "Enviar agora" e o envio pós-cadastro mandam `manual`; o worker diário manda `agendado`.

---

## ➕ Entregue depois da Fase 1 (v1.2.x → v1.3.0)

| Item | Onde | Status |
|---|---|---|
| **Reset de cadastro** — `DELETE .../cadastro` (painel logado ou `X-Admin-Key`). Limpa colaborador/equipe/patrimônio/IMEI/`cadastrado_em`; o app reabre o form. Não apaga posições. | 🗄️📊 | ✅ |
| **Responsável na lista/detalhe do painel** — a lista mostra quem (ou qual equipe) está com cada aparelho | 📊 | ✅ |
| **Cadastro fica fixo no aparelho** (cache local) — abre já mostrando o cadastro, sem flash de "cadastrar de novo"; só reabre se a T.I. resetar | 📱 | ✅ |
| **Reconfirmação mensal** — a cada 30 dias o app pede confirmação (notificação de hora em hora); `POST .../cadastro/reconfirmar` sobrescreve | 📱🗄️ | ✅ |
| **Comandos remotos via FCM** — painel dispara push: `localizacao` (ver onde está agora), `reconfirmar`, `atualizar`, `recado`. Coluna `fcm_token` + `POST .../comando` + `POST .../fcm-token` | 📱🗄️📊 | ✅ |
| **Uso de equipe** (era o item 14) — cadastro em 2 etapas: individual (`colab_ativos`) OU equipe (`eqps_ativas`, autocomplete). Snapshot da equipe em `devices`. | 📱🗄️📊 | ✅ |
| **Cadastro em etapas** — pergunta 1 (individual/equipe) → responsável → pergunta 2 (patrimônio/IMEI), uma de cada vez | 📱 | ✅ |
| **Polimento visual** — botão "Enviar" na paleta (verde), avisos em vermelho vivo (glass), banner de atualização visível + "Verificar atualizações", diálogo de pendências rolável | 📱 | ✅ |

> Decisão: **não guardar histórico de proprietários** — só interessa quem está com o aparelho agora. Troca de mão = reset (T.I.) **ou** a reconfirmação mensal (o próprio usuário registra o novo dono/equipe).

## 🔜 Fase 2 — Em aberto

| # | Item | Esforço | Onde |
|---|---|---|---|
| 12 | **Refresh visual do painel** — modernizar UI (React + CSS), botão voltar do navegador (react-router) e keep-warm p/ o login lento (cold start do Render free) | 🟡 | 📊 |
| 13 | **Setup do Firebase** (seu lado) — criar projeto, `google-services.json`, `FCM_SERVICE_ACCOUNT` no Render — pra ligar os comandos push que já estão no código | 🟢 | 🗄️📱 |
| 14 | **Saber se excluiu o app** → agora com FCM dá pra melhorar: ping silencioso; sem resposta há X = "sumiu" (some com a heurística de `ultimo_visto`) | 🟡 | 🗄️📊 |
| 15 | **Filtro de equipe ativa** — se `eqps_ativas.status` tiver inativas, filtrar no `GET /equipes` | 🟢 | 🗄️ |

## 🗓️ Fase 3 — Backlog / futuro

| # | Item | Esforço | Onde |
|---|---|---|---|
| 16 | Enviar +1x/dia sem histórico (só última posição) — modo alternativo | 🟡 | 📱🗄️ |
| 17 | Termo de responsabilidade + assinatura manual (desenhada) | 🔴 | 📱🗄️ |
| 18 | Horário fixo do envio diário (ex.: sempre de manhã) via `initialDelay`/flex no WorkManager | 🟢 | 📱 |

---

## Notas

- **"Saber se o usuário excluiu o app" não é em tempo real.** O Android não avisa a desinstalação. Com FCM dá pra melhorar (ping silencioso → sem resposta = suspeita), mas ainda é inferência: sem enviar/sem responder há X dias pode ser desinstalado, sem bateria, sem sinal ou GPS off. O aviso "não desinstale" previne.
- **Comandos push dependem do Firebase configurado** (item 13). Sem isso o app funciona normal e os comandos respondem `503`; a reconfirmação mensal automática (de hora em hora) funciona sem FCM.

## Ordem sugerida

**13 (setup Firebase)** pra ligar os comandos que já estão prontos → **12 (refresh do painel)** → **14 (detecção de sumiço com FCM)** → o resto conforme a necessidade.
