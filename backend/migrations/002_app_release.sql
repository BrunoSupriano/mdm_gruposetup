-- Controle de versões do APK (auto-update). O APK fica guardado aqui (bytea),
-- então o app baixa a atualização direto da API, sem depender de repo público.
CREATE TABLE IF NOT EXISTS mdm.app_release (
    version_code   INTEGER PRIMARY KEY,
    version_name   TEXT NOT NULL,
    obrigatoria    BOOLEAN NOT NULL DEFAULT false,
    notas          TEXT,
    tamanho_bytes  BIGINT,
    apk_bytes      BYTEA NOT NULL,
    publicado_em   TIMESTAMPTZ NOT NULL DEFAULT now()
);
