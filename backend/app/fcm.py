"""Envio de comandos push via FCM HTTP v1.

Usa uma service account do Firebase (JSON) fornecida na env var FCM_SERVICE_ACCOUNT.
Mensagens são "data-only" com prioridade alta, para o app reagir mesmo em segundo
plano (o FirebaseMessagingService recebe e decide o que fazer)."""
import asyncio
import json
import os

FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"

_creds = None
_project_id = None


def configurado() -> bool:
    return bool(os.environ.get("FCM_SERVICE_ACCOUNT", "").strip())


def _init():
    global _creds, _project_id
    if _creds is not None:
        return
    raw = os.environ.get("FCM_SERVICE_ACCOUNT", "").strip()
    if not raw:
        raise RuntimeError("FCM_SERVICE_ACCOUNT nao configurado")
    info = json.loads(raw)
    from google.oauth2 import service_account
    _creds = service_account.Credentials.from_service_account_info(info, scopes=[FCM_SCOPE])
    _project_id = info["project_id"]


def _enviar_sync(token: str, data: dict) -> tuple:
    _init()
    from google.auth.transport.requests import Request as GRequest
    import requests

    _creds.refresh(GRequest())
    url = f"https://fcm.googleapis.com/v1/projects/{_project_id}/messages:send"
    msg = {
        "message": {
            "token": token,
            "android": {"priority": "high"},
            "data": {k: str(v) for k, v in data.items() if v is not None},
        }
    }
    r = requests.post(
        url,
        headers={"Authorization": f"Bearer {_creds.token}", "Content-Type": "application/json"},
        json=msg,
        timeout=15,
    )
    return r.status_code, r.text


async def enviar_comando(token: str, data: dict) -> tuple:
    """Retorna (status_http, corpo). Roda o request bloqueante numa thread."""
    return await asyncio.to_thread(_enviar_sync, token, data)
