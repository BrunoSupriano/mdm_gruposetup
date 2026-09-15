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
from migrate import apply_migrations, dsn_and_ssl  # noqa: E402

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
                   d.colaborador_nome, d.colaborador_cargo, d.cadastrado_em,
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


# ---------------------- cadastro (colaborador + patrimonio/imei) ----------------------
class Cadastro(BaseModel):
    colaborador_id: int
    patrimonio: Optional[str] = None
    imei: Optional[str] = None


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


@app.get("/api/v1/dispositivos/{android_id}/cadastro", dependencies=[Depends(auth_device)])
async def obter_cadastro(android_id: str):
    async with pool.acquire() as con:
        row = await con.fetchrow(
            """
            SELECT colaborador_id, colaborador_nome, colaborador_cargo,
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


@app.post("/api/v1/dispositivos/{android_id}/cadastro", dependencies=[Depends(auth_device)])
async def salvar_cadastro(android_id: str, c: Cadastro):
    patrimonio = (c.patrimonio or "").strip()
    imei = (c.imei or "").strip()
    tem_pat = bool(patrimonio)
    tem_imei = bool(imei)
    if tem_pat == tem_imei:
        raise HTTPException(status_code=400, detail="informe patrimonio OU imei (exatamente um)")
    if tem_pat and (not patrimonio.isdigit() or len(patrimonio) > 7):
        raise HTTPException(status_code=400, detail="patrimonio deve ser numerico com ate 7 digitos")
    async with pool.acquire() as con:
        atual = await con.fetchrow(
            "SELECT cadastrado_em FROM mdm.devices WHERE android_id = $1", android_id
        )
        if atual and atual["cadastrado_em"] is not None:
            raise HTTPException(status_code=409, detail="dispositivo ja cadastrado")
        colab = await con.fetchrow(
            "SELECT cadastro_id, nome_completo, nome_cargo FROM mdm.colab_ativos WHERE cadastro_id = $1",
            c.colaborador_id,
        )
        if not colab:
            raise HTTPException(status_code=404, detail="colaborador nao encontrado")
        await con.execute(
            "INSERT INTO mdm.devices (android_id) VALUES ($1) ON CONFLICT (android_id) DO NOTHING",
            android_id,
        )
        await con.execute(
            """
            UPDATE mdm.devices SET
                colaborador_id    = $2,
                colaborador_nome  = $3,
                colaborador_cargo = $4,
                patrimonio        = $5,
                imei              = COALESCE($6, imei),
                cadastrado_em     = now()
            WHERE android_id = $1
            """,
            android_id, colab["cadastro_id"], colab["nome_completo"], colab["nome_cargo"],
            (patrimonio or None), (imei or None),
        )
    return {
        "ok": True, "cadastrado": True,
        "colaborador_nome": colab["nome_completo"], "colaborador_cargo": colab["nome_cargo"],
        "patrimonio": patrimonio or None, "imei": imei or None,
    }


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
                colaborador_id    = NULL,
                colaborador_nome  = NULL,
                colaborador_cargo = NULL,
                patrimonio        = NULL,
                imei              = NULL,
                cadastrado_em     = NULL
            WHERE android_id = $1
            """,
            android_id,
        )
    return {"ok": True, "cadastrado": False, "resetado": True}
