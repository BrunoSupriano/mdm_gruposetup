"""Runner de migrations. Aplica os .sql pendentes de migrations/ em ordem,
registrando o que ja rodou em mdm.schema_migrations. Idempotente."""
import asyncio
import glob
import os
from urllib.parse import urlsplit, urlunsplit

import asyncpg

MIGRATIONS_DIR = os.path.join(os.path.dirname(__file__), "migrations")


def dsn_and_ssl(url: str):
    parts = urlsplit(url)
    host = parts.hostname or ""
    ssl = (
        "sslmode=require" in url
        or "channel_binding" in url
        or host.endswith("neon.tech")
        or os.getenv("DB_SSL", "").lower() in ("1", "true", "require")
    )
    dsn = urlunsplit((parts.scheme, parts.netloc, parts.path, "", ""))
    return dsn, (True if ssl else None)


async def apply_migrations(con) -> list:
    await con.execute(
        """
        CREATE SCHEMA IF NOT EXISTS mdm;
        CREATE TABLE IF NOT EXISTS mdm.schema_migrations (
            version TEXT PRIMARY KEY,
            applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        """
    )
    applied = {r["version"] for r in await con.fetch("SELECT version FROM mdm.schema_migrations")}
    aplicadas = []
    for f in sorted(glob.glob(os.path.join(MIGRATIONS_DIR, "*.sql"))):
        version = os.path.basename(f)
        if version in applied:
            continue
        sql = open(f, encoding="utf-8").read()
        async with con.transaction():
            await con.execute(sql)
            await con.execute("INSERT INTO mdm.schema_migrations(version) VALUES($1)", version)
        aplicadas.append(version)
    return aplicadas


async def run():
    url = os.environ.get("DATABASE_URL", "").strip()
    if not url:
        raise SystemExit(
            "ERRO: DATABASE_URL nao definido. Configure o secret DATABASE_URL no "
            "GitHub (Settings > Secrets and variables > Actions) com a connection "
            "string do Neon."
        )
    dsn, ssl = dsn_and_ssl(url)
    con = await asyncpg.connect(dsn=dsn, ssl=ssl)
    try:
        novos = await apply_migrations(con)
        print("Migrations aplicadas:", ", ".join(novos) if novos else "nenhuma (ja atualizado)")
    finally:
        await con.close()


if __name__ == "__main__":
    asyncio.run(run())
