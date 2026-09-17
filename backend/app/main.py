import base64
import hashlib
import hmac
import os
import sys
import time
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Optional

import asyncpg
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from pydantic import BaseModel

sys.path.append(os.path.join(os.path.dirname(__file__), ".."))
sys.path.append(os.path.dirname(__file__))
from migrate import apply_migrations, dsn_and_ssl  # noqa: E402
import fcm  # noqa: E402

API_KEY = os.environ.get("API_KEY", "")
ADMIN_KEY = os.environ.get("ADMIN_KEY", "")
PAINEL_SENHA = os.environ.get("PAINEL_SENHA", "")
SECRET_KEY = os.environ.get("SECRET_KEY", ADMIN_KEY or "dev-secret")
CORS_ORIGINS = os.environ.get("CORS_ORIGINS", "*")
DATABASE_URL = os.environ.get("DATABASE_URL", "")

pool: Optional[asyncpg.Pool] = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global pool
    if not DATABASE_URL:
        raise RuntimeError("DATABASE_URL nao definido")
    dsn, ssl = dsn_and_ssl(DATABASE_URL)
    pool = await asyncpg.create_pool(dsn=dsn, ssl=ssl, min_size=1, max_size=5)
    async with pool.acquire() as con:
        await apply_migrations(con)
    yield
    if pool:
        await pool.close()


app = FastAPI(title="Setup MDM API", version="1.2.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[o.strip() for o in CORS_ORIGINS.split(",")] if CORS_ORIGINS != "*" else ["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------------------- auth ----------------------
def auth_device(authorization: str = Header(default="")):
    if not API_KEY or authorization != f"Bearer {API_KEY}":
        raise HTTPException(status_code=401, detail="nao autorizado")


def auth_admin(x_admin_key: str = Header(default="")):
    if not ADMIN_KEY or x_admin_key != ADMIN_KEY:
        raise HTTPException(status_code=401, detail="admin nao autorizado")


def _sign(msg: bytes) -> str:
    return base64.urlsafe_b64encode(hmac.new(SECRET_KEY.encode(), msg, hashlib.sha256).digest()).decode()


def criar_token(horas: int = 12) -> str:
    exp = str(int(time.time()) + horas * 3600)
    payload = base64.urlsafe_b64encode(exp.encode()).decode()
    return f"{payload}.{_sign(payload.encode())}"


def token_valido(token: str) -> bool:
    try:
        payload, sig = token.split(".", 1)
        if not hmac.compare_digest(sig, _sign(payload.encode())):
            return False
        exp = int(base64.urlsafe_b64decode(payload).decode())
        return time.time() < exp
    except Exception:
        return False


def auth_painel(authorization: str = Header(default="")):
    if not authorization.startswith("Bearer ") or not token_valido(authorization[7:]):
        raise HTTPException(status_code=401, detail="painel nao autorizado")


def auth_painel_ou_admin(authorization: str = Header(default=""), x_admin_key: str = Header(default="")):
    """Aceita a sessao do painel (Bearer) OU a chave admin (X-Admin-Key).
    Usado pelo reset: dá pra chamar tanto pelo painel logado quanto por curl da T.I."""
    if ADMIN_KEY and hmac.compare_digest(x_admin_key, ADMIN_KEY):
        return
    if authorization.startswith("Bearer ") and token_valido(authorization[7:]):
        return
    raise HTTPException(status_code=401, detail="nao autorizado")


class Login(BaseModel):
    senha: str


@app.post("/api/v1/login")
async def login(body: Login):
    if not PAINEL_SENHA or not hmac.compare_digest(body.senha, PAINEL_SENHA):
        raise HTTPException(status_code=401, detail="senha invalida")
    return {"token": criar_token()}


# ---------------------- modelos ----------------------
class Posicao(BaseModel):
    android_id: str
    fabricante: Optional[str] = None
    modelo: Optional[str] = None
    versao_android: Optional[str] = None
    sdk_int: Optional[int] = None
    operadora: Optional[str] = None
    bateria_pct: Optional[int] = None
    imei: Optional[str] = None
    lat: float
    lon: float
    precisao_m: Optional[float] = None
    provider: Optional[str] = None
    origem: Optional[str] = None
    capturado_em: Optional[str] = None
    app_versao: Optional[str] = None


def _parse_dt(valor: Optional[str]) -> datetime:
    if not valor:
        return datetime.now(timezone.utc)
    for fmt in ("%Y-%m-%dT%H:%M:%S%z", "%Y-%m-%dT%H:%M:%S.%f%z"):
        try:
            return datetime.strptime(valor, fmt)
        except ValueError:
            continue
    try:
        return datetime.fromisoformat(valor)
    except ValueError:
        return datetime.now(timezone.utc)


@app.get("/health")
async def health():
    return {"status": "ok"}


# ---------------------- posições (aparelhos) ----------------------
@app.post("/api/v1/posicoes", dependencies=[Depends(auth_device)])
async def receber(p: Posicao):
    capturado = _parse_dt(p.capturado_em)
    origem = p.origem if p.origem in ("agendado", "manual") else "agendado"
    async with pool.acquire() as con:
        async with con.transaction():
            await con.execute(
                """
                INSERT INTO mdm.devices
                    (android_id, fabricante, modelo, versao_android, sdk_int, imei, app_versao)
                VALUES ($1,$2,$3,$4,$5,$6,$7)
                ON CONFLICT (android_id) DO UPDATE SET
                    fabricante     = COALESCE(EXCLUDED.fabricante, mdm.devices.fabricante),
                    modelo         = COALESCE(EXCLUDED.modelo, mdm.devices.modelo),
                    versao_android = COALESCE(EXCLUDED.versao_android, mdm.devices.versao_android),
                    sdk_int        = COALESCE(EXCLUDED.sdk_int, mdm.devices.sdk_int),
                    imei           = COALESCE(EXCLUDED.imei, mdm.devices.imei),
                    app_versao     = COALESCE(EXCLUDED.app_versao, mdm.devices.app_versao),
                    ultimo_visto   = now()
                """,
                p.android_id, p.fabricante, p.modelo, p.versao_android,
                p.sdk_int, p.imei, p.app_versao,
            )
            await con.execute(
                """
                INSERT INTO mdm.posicoes
                    (android_id, lat, lon, precisao_m, provider, bateria_pct,
                     operadora, origem, capturado_em, app_versao)
                VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
                ON CONFLICT (android_id, capturado_em) DO NOTHING
                """,
                p.android_id, p.lat, p.lon, p.precisao_m, p.provider,
                p.bateria_pct, p.operadora, origem, capturado, p.app_versao,
            )
    return {"ok": True}


# ---------------------- painel (admin) ----------------------
@app.get("/api/v1/dispositivos", dependencies=[Depends(auth_painel)])
async def listar_dispositivos():
    async with pool.acquire() as con:
        rows = await con.fetch(
            """
            SELECT d.android_id, d.patrimonio, d.fabricante, d.modelo,
                   d.versao_android, d.imei, d.app_versao, d.ultimo_visto,
                   d.tipo_uso, d.colaborador_nome, d.colaborador_cargo,
                   d.equipe_descricao, d.equipe_processo, d.cadastrado_em,
                   u.lat, u.lon, u.capturado_em AS ultima_posicao_em,
                   u.bateria_pct, u.operadora
            FROM mdm.devices d
            LEFT JOIN mdm.ultima_posicao u ON u.android_id = d.android_id
            ORDER BY d.ultimo_visto DESC
            """
        )
    return [dict(r) for r in rows]


@app.get("/api/v1/dispositivos/{android_id}/posicoes", dependencies=[Depends(auth_painel)])
async def historico(android_id: str, limite: int = 200):
    limite = max(1, min(limite, 2000))
    async with pool.acquire() as con:
        rows = await con.fetch(
            """
            SELECT lat, lon, precisao_m, provider, bateria_pct, operadora,
                   capturado_em, recebido_em
            FROM mdm.posicoes
            WHERE android_id = $1
            ORDER BY capturado_em DESC
            LIMIT $2
            """,
            android_id, limite,
        )
    return [dict(r) for r in rows]


# ---------------------- auto-update do app ----------------------
@app.get("/api/v1/app/versao", dependencies=[Depends(auth_device)])
async def versao_app():
    async with pool.acquire() as con:
        row = await con.fetchrow(
            """
            SELECT version_code, version_name, obrigatoria, notas, tamanho_bytes, publicado_em
            FROM mdm.app_release ORDER BY version_code DESC LIMIT 1
            """
        )
    if not row:
        return {"disponivel": False}
    d = dict(row)
    d["disponivel"] = True
    d["url"] = "/api/v1/app/apk"
    return d


@app.get("/api/v1/app/apk", dependencies=[Depends(auth_device)])
async def baixar_apk():
    async with pool.acquire() as con:
        row = await con.fetchrow(
            "SELECT version_code, apk_bytes FROM mdm.app_release ORDER BY version_code DESC LIMIT 1"
        )
    if not row:
        raise HTTPException(status_code=404, detail="nenhuma versao publicada")
    return Response(
        content=bytes(row["apk_bytes"]),
        media_type="application/vnd.android.package-archive",
        headers={"Content-Disposition": f'attachment; filename="setupmdm-{row["version_code"]}.apk"'},
    )


@app.post("/api/v1/app/release", dependencies=[Depends(auth_admin)])
async def publicar_release(
    request: Request,
    version_code: int,
    version_name: str,
    obrigatoria: bool = False,
    notas: str = "",
):
    apk = await request.body()
    if not apk:
        raise HTTPException(status_code=400, detail="corpo vazio (envie o APK)")
    async with pool.acquire() as con:
        await con.execute(
            """
            INSERT INTO mdm.app_release
                (version_code, version_name, obrigatoria, notas, tamanho_bytes, apk_bytes)
            VALUES ($1,$2,$3,$4,$5,$6)
            ON CONFLICT (version_code) DO UPDATE SET
                version_name = EXCLUDED.version_name,
                obrigatoria  = EXCLUDED.obrigatoria,
                notas        = EXCLUDED.notas,
                tamanho_bytes= EXCLUDED.tamanho_bytes,
                apk_bytes    = EXCLUDED.apk_bytes,
                publicado_em = now()
            """,
            version_code, version_name, obrigatoria, notas, len(apk), apk,
        )
    return {"ok": True, "version_code": version_code, "tamanho_bytes": len(apk)}


# ---------------------- cadastro (colaborador OU equipe + patrimonio/imei) ----------------------
class Cadastro(BaseModel):
    tipo_uso: str = "individual"          # 'individual' | 'equipe'
    colaborador_id: Optional[int] = None
    equipe_id: Optional[int] = None
    patrimonio: Optional[str] = None
    imei: Optional[str] = None


# Nome qualificado da tabela de equipes (descoberto em runtime — pode estar em
# qualquer schema, ex.: public.eqps_ativas). Cacheado após a 1ª descoberta.
_eqps_rel: Optional[str] = None


async def eqps_rel(con) -> str:
    global _eqps_rel
    if _eqps_rel:
        return _eqps_rel
    row = await con.fetchrow(
        """
        SELECT table_schema FROM information_schema.tables
        WHERE table_name = 'eqps_ativas'
        ORDER BY (table_schema = 'public') DESC, (table_schema = 'mdm') DESC
        LIMIT 1
        """
    )
    if not row:
        raise HTTPException(status_code=500, detail="tabela eqps_ativas nao encontrada no banco")
    _eqps_rel = f'"{row["table_schema"]}".eqps_ativas'
    return _eqps_rel


@app.get("/api/v1/colaboradores", dependencies=[Depends(auth_device)])
async def buscar_colaboradores(q: str = "", limite: int = 12):
    q = (q or "").strip()
    if len(q) < 2:
        return []
    limite = max(1, min(limite, 30))
    async with pool.acquire() as con:
        rows = await con.fetch(
            """
            SELECT cadastro_id, nome_completo, nome_cargo
            FROM mdm.colab_ativos
            WHERE nome_completo ILIKE '%' || $1 || '%'
               OR cadastro_id::text LIKE $1 || '%'
            ORDER BY nome_completo
            LIMIT $2
            """,
            q, limite,
        )
    return [dict(r) for r in rows]


@app.get("/api/v1/equipes", dependencies=[Depends(auth_device)])
async def buscar_equipes(q: str = "", limite: int = 12):
    q = (q or "").strip()
    if len(q) < 2:
        return []
    limite = max(1, min(limite, 30))
    async with pool.acquire() as con:
        rel = await eqps_rel(con)
        rows = await con.fetch(
            f"""
            SELECT id, descricao, processo
            FROM {rel}
            WHERE descricao ILIKE '%' || $1 || '%'
               OR processo ILIKE '%' || $1 || '%'
               OR id::text LIKE $1 || '%'
            ORDER BY descricao
            LIMIT $2
            """,
            q, limite,
        )
    return [dict(r) for r in rows]


@app.get("/api/v1/dispositivos/{android_id}/cadastro", dependencies=[Depends(auth_device)])
async def obter_cadastro(android_id: str):
    async with pool.acquire() as con:
        row = await con.fetchrow(
            """
            SELECT tipo_uso, colaborador_id, colaborador_nome, colaborador_cargo,
                   equipe_id, equipe_descricao, equipe_processo,
                   patrimonio, imei, cadastrado_em
            FROM mdm.devices WHERE android_id = $1
            """,
            android_id,
        )
    if not row or row["cadastrado_em"] is None:
        return {"cadastrado": False}
    d = dict(row)
    d["cadastrado"] = True
    return d


async def _gravar_cadastro(con, android_id: str, c: Cadastro, checar_trava: bool) -> dict:
    patrimonio = (c.patrimonio or "").strip()
    imei = (c.imei or "").strip()
    if bool(patrimonio) == bool(imei):
        raise HTTPException(status_code=400, detail="informe patrimonio OU imei (exatamente um)")
    if patrimonio and (not patrimonio.isdigit() or len(patrimonio) > 7):
        raise HTTPException(status_code=400, detail="patrimonio deve ser numerico com ate 7 digitos")
    tipo = (c.tipo_uso or "individual").strip().lower()
    if tipo not in ("individual", "equipe"):
        raise HTTPException(status_code=400, detail="tipo_uso invalido")

    if checar_trava:
        atual = await con.fetchrow("SELECT cadastrado_em FROM mdm.devices WHERE android_id = $1", android_id)
        if atual and atual["cadastrado_em"] is not None:
            raise HTTPException(status_code=409, detail="dispositivo ja cadastrado")

    colab = None
    equipe = None
    if tipo == "individual":
        if not c.colaborador_id:
            raise HTTPException(status_code=400, detail="colaborador_id obrigatorio")
        colab = await con.fetchrow(
            "SELECT cadastro_id, nome_completo, nome_cargo FROM mdm.colab_ativos WHERE cadastro_id = $1",
            c.colaborador_id,
        )
        if not colab:
            raise HTTPException(status_code=404, detail="colaborador nao encontrado")
    else:
        if not c.equipe_id:
            raise HTTPException(status_code=400, detail="equipe_id obrigatorio")
        rel = await eqps_rel(con)
        equipe = await con.fetchrow(f"SELECT id, descricao, processo FROM {rel} WHERE id = $1", c.equipe_id)
        if not equipe:
            raise HTTPException(status_code=404, detail="equipe nao encontrada")

    await con.execute(
        "INSERT INTO mdm.devices (android_id) VALUES ($1) ON CONFLICT (android_id) DO NOTHING",
        android_id,
    )
    await con.execute(
        """
        UPDATE mdm.devices SET
            tipo_uso          = $2,
            colaborador_id    = $3,
            colaborador_nome  = $4,
            colaborador_cargo = $5,
            equipe_id         = $6,
            equipe_descricao  = $7,
            equipe_processo   = $8,
            patrimonio        = $9,
            imei              = $10,
            cadastrado_em     = now()
        WHERE android_id = $1
        """,
        android_id, tipo,
        (colab["cadastro_id"] if colab else None),
        (colab["nome_completo"] if colab else None),
        (colab["nome_cargo"] if colab else None),
        (equipe["id"] if equipe else None),
        (equipe["descricao"] if equipe else None),
        (equipe["processo"] if equipe else None),
        (patrimonio or None), (imei or None),
    )
    return {
        "ok": True, "cadastrado": True, "tipo_uso": tipo,
        "colaborador_nome": (colab["nome_completo"] if colab else None),
        "colaborador_cargo": (colab["nome_cargo"] if colab else None),
        "equipe_descricao": (equipe["descricao"] if equipe else None),
        "equipe_processo": (equipe["processo"] if equipe else None),
        "patrimonio": patrimonio or None, "imei": imei or None,
    }


@app.post("/api/v1/dispositivos/{android_id}/cadastro", dependencies=[Depends(auth_device)])
async def salvar_cadastro(android_id: str, c: Cadastro):
    async with pool.acquire() as con:
        return await _gravar_cadastro(con, android_id, c, checar_trava=True)


@app.delete("/api/v1/dispositivos/{android_id}/cadastro", dependencies=[Depends(auth_painel_ou_admin)])
async def resetar_cadastro(android_id: str):
    """Destrava o cadastro de um device (uso T.I., pelo painel ou por curl com X-Admin-Key).
    O aparelho volta a mostrar o formulario em branco no proximo abrir do app. Nao apaga posicoes."""
    async with pool.acquire() as con:
        row = await con.fetchrow(
            "SELECT cadastrado_em FROM mdm.devices WHERE android_id = $1", android_id
        )
        if not row:
            raise HTTPException(status_code=404, detail="dispositivo nao encontrado")
        await con.execute(
            """
            UPDATE mdm.devices SET
                tipo_uso          = 'individual',
                colaborador_id    = NULL,
                colaborador_nome  = NULL,
                colaborador_cargo = NULL,
                equipe_id         = NULL,
                equipe_descricao  = NULL,
                equipe_processo   = NULL,
                patrimonio        = NULL,
                imei              = NULL,
                cadastrado_em     = NULL
            WHERE android_id = $1
            """,
            android_id,
        )
    return {"ok": True, "cadastrado": False, "resetado": True}


@app.post("/api/v1/dispositivos/{android_id}/cadastro/reconfirmar", dependencies=[Depends(auth_device)])
async def reconfirmar_cadastro(android_id: str, c: Cadastro):
    """Reconfirmacao periodica (mensal) feita pelo proprio aparelho: SOBRESCREVE o
    cadastro atual (mesmo que ja exista), permitindo confirmar o mesmo responsavel/equipe
    ou registrar outro. Diferente do POST /cadastro, aqui nao ha trava 409."""
    async with pool.acquire() as con:
        r = await _gravar_cadastro(con, android_id, c, checar_trava=False)
    r["reconfirmado"] = True
    return r


# ---------------------- comandos remotos (FCM) ----------------------
class FcmToken(BaseModel):
    token: str


@app.post("/api/v1/dispositivos/{android_id}/fcm-token", dependencies=[Depends(auth_device)])
async def salvar_fcm_token(android_id: str, body: FcmToken):
    token = (body.token or "").strip()
    if not token:
        raise HTTPException(status_code=400, detail="token vazio")
    async with pool.acquire() as con:
        await con.execute(
            "INSERT INTO mdm.devices (android_id) VALUES ($1) ON CONFLICT (android_id) DO NOTHING",
            android_id,
        )
        await con.execute(
            "UPDATE mdm.devices SET fcm_token = $2, fcm_atualizado_em = now() WHERE android_id = $1",
            android_id, token,
        )
    return {"ok": True}


class Comando(BaseModel):
    tipo: str            # "localizacao" | "reconfirmar" | "atualizar" | "recado"
    texto: Optional[str] = None


TIPOS_COMANDO = {"localizacao", "reconfirmar", "atualizar", "recado"}


@app.post("/api/v1/dispositivos/{android_id}/comando", dependencies=[Depends(auth_painel_ou_admin)])
async def enviar_comando(android_id: str, cmd: Comando):
    if cmd.tipo not in TIPOS_COMANDO:
        raise HTTPException(status_code=400, detail="tipo de comando invalido")
    if not fcm.configurado():
        raise HTTPException(status_code=503, detail="FCM nao configurado no servidor")
    async with pool.acquire() as con:
        row = await con.fetchrow("SELECT fcm_token FROM mdm.devices WHERE android_id = $1", android_id)
    if not row:
        raise HTTPException(status_code=404, detail="dispositivo nao encontrado")
    token = row["fcm_token"]
    if not token:
        raise HTTPException(status_code=409, detail="dispositivo ainda nao registrou push (abra o app uma vez)")
    status, corpo = await fcm.enviar_comando(token, {"tipo": cmd.tipo, "texto": cmd.texto or ""})
    if status < 200 or status >= 300:
        raise HTTPException(status_code=502, detail=f"falha no envio FCM ({status})")
    return {"ok": True, "enviado": cmd.tipo}
