-- Uso de equipe: o device pode ser cadastrado como individual (colaborador)
-- ou de equipe (eqps_ativas). Guardamos um snapshot da equipe, como no colaborador.
ALTER TABLE mdm.devices ADD COLUMN IF NOT EXISTS tipo_uso TEXT NOT NULL DEFAULT 'individual';
ALTER TABLE mdm.devices ADD COLUMN IF NOT EXISTS equipe_id INTEGER;
ALTER TABLE mdm.devices ADD COLUMN IF NOT EXISTS equipe_descricao TEXT;
ALTER TABLE mdm.devices ADD COLUMN IF NOT EXISTS equipe_processo TEXT;
