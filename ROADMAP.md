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

## ➕ Extras pós-Fase 1 (entregues)

| Item | Onde | Status |
|---|---|---|
| **Reset de cadastro** — `DELETE /api/v1/dispositivos/{android_id}/cadastro` (protegido por `X-Admin-Key`). Destrava o device: limpa colaborador/patrimônio/IMEI/`cadastrado_em`; o app volta a mostrar o formulário no próximo abrir. Não apaga posições. Sem migration. | 🗄️ | ✅ |

> Decisão: **não guardar histórico de proprietários** — só interessa quem está com o aparelho agora. O item 12 abaixo fica só como "troca" via reset + novo cadastro (sem tabela de histórico).

## 🔜 Fase 2 — Features médias (a debater)

| # | Item | Esforço | Onde |
|---|---|---|---|
| 12 | **Troca de proprietário**: hoje resolvida via reset (T.I.) + novo cadastro. Melhoria futura opcional: fluxo no próprio app ("já está com fulano, substituir?") — sem histórico | 🟡 | 📱🗄️ |
| 13 | **Saber se excluiu o app** → heurística no painel: aparelho sem enviar há X dias = "possivelmente removido/desligado" | 🔴 | 🗄️📊 |
| 14 | **Aparelhos coletivos** (1 p/ equipe): perguntar se é coletivo → lista de equipes | 🟡 | 📱🗄️ |

## 🗓️ Fase 3 — Backlog / futuro

| # | Item | Esforço | Onde |
|---|---|---|---|
| 15 | Enviar +1x/dia sem histórico (só última posição) — modo alternativo | 🟡 | 📱🗄️ |
| 16 | Termo de responsabilidade + assinatura manual (desenhada) | 🔴 | 📱🗄️ |
| 17 | "Vigia": 4 pessoas p/ 1 dispositivo (variação de multi-usuário) | 🟡 | 📱🗄️ |

---

## Decisões pendentes antes da Fase 2

1. **"Saber se o usuário excluiu o app" não é em tempo real.** O Android não avisa quando o app é desinstalado (por segurança). O caminho real é o painel olhar o `ultimo_visto`: sem enviar há X dias → "sumiu" (desinstalado, sem bateria, sem sinal ou GPS off — não dá pra distinguir a causa). O aviso "não exclua" (item 5, já entregue) previne; a detecção é sempre inferência. Por isso o item 13 é 🔴.

2. **"Troca de proprietário" (12) x "trava após salvar".** Elas se completam: o cadastro trava pro usuário comum, e a transferência é a única forma de destravar (com dupla confirmação). Recomendo fazer 12 e 13 juntos — sem a transferência, um aparelho que troca de dono fica preso no nome errado.

3. **Coletivo/Vigia (14, 17) dependem do Apex.** O trabalho grande não é o app, é trazer a lista de equipes do Apex para uma fonte que o app consome (tabela no Neon sincronizada ou endpoint). Definir se o Apex expõe as equipes por API/REST ou se extrai para tabela — isso muda o esforço entre 🟡 e 🔴.

## Ordem sugerida

Fase 1 (✅ concluída) → **12 + 13 juntos** (dono + detecção de sumiço, o coração do controle) → **coletivo (14)** quando a fonte do Apex estiver definida → o resto (15–17) conforme a necessidade.
