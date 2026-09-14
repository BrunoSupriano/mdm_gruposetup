CREATE SCHEMA IF NOT EXISTS mdm;

CREATE TABLE IF NOT EXISTS mdm.devices (
    android_id      TEXT PRIMARY KEY,
    patrimonio      TEXT,
    fabricante      TEXT,
    modelo          TEXT,
    versao_android  TEXT,
    sdk_int         INTEGER,
    imei            TEXT,
    app_versao      TEXT,
    primeiro_visto  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_visto    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ativo           BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS mdm.posicoes (
    id            BIGSERIAL PRIMARY KEY,
    android_id    TEXT NOT NULL REFERENCES mdm.devices(android_id),
    lat           DOUBLE PRECISION NOT NULL,
    lon           DOUBLE PRECISION NOT NULL,
    precisao_m    REAL,
    provider      TEXT,
    bateria_pct   SMALLINT,
    operadora     TEXT,
    imei          TEXT,
    capturado_em  TIMESTAMPTZ NOT NULL,
    recebido_em   TIMESTAMPTZ NOT NULL DEFAULT now(),
    app_versao    TEXT,
    UNIQUE (android_id, capturado_em)
);

CREATE INDEX IF NOT EXISTS idx_posicoes_device_data
    ON mdm.posicoes (android_id, capturado_em DESC);

CREATE OR REPLACE VIEW mdm.ultima_posicao AS
SELECT DISTINCT ON (android_id) *
FROM mdm.posicoes
ORDER BY android_id, capturado_em DESC;
