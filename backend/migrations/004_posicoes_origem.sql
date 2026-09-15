-- Fase 1: a posição não guarda mais IMEI (o IMEI vive no cadastro do device).
-- Passa a registrar a "origem" da captura: 'agendado' (job diário) ou 'manual' (botão).
-- A view usa SELECT *, então depende da coluna imei — precisa ser derrubada antes
-- do DROP COLUMN e recriada depois.

DROP VIEW IF EXISTS mdm.ultima_posicao;

ALTER TABLE mdm.posicoes DROP COLUMN IF EXISTS imei;
ALTER TABLE mdm.posicoes ADD COLUMN IF NOT EXISTS origem TEXT NOT NULL DEFAULT 'agendado';

CREATE OR REPLACE VIEW mdm.ultima_posicao AS
SELECT DISTINCT ON (android_id) *
FROM mdm.posicoes
ORDER BY android_id, capturado_em DESC;
