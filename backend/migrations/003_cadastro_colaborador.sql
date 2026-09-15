-- Cadastro de colaborador + patrimonio/IMEI por dispositivo.
-- colab_ativos: base de colaboradores (voce popula no Neon). IF NOT EXISTS nao mexe se ja existir.
CREATE TABLE IF NOT EXISTS mdm.colab_ativos (
    cadastro_id   BIGINT PRIMARY KEY,
    nome_completo TEXT,
    nome_cargo    TEXT
);

-- indice para busca por nome (autocomplete)
CREATE INDEX IF NOT EXISTS idx_colab_nome ON mdm.colab_ativos (nome_completo);

-- colunas de cadastro no device (patrimonio e imei ja existem)
ALTER TABLE mdm.devices ADD COLUMN IF NOT EXISTS colaborador_id    BIGINT;
ALTER TABLE mdm.devices ADD COLUMN IF NOT EXISTS colaborador_nome  TEXT;
ALTER TABLE mdm.devices ADD COLUMN IF NOT EXISTS colaborador_cargo TEXT;
ALTER TABLE mdm.devices ADD COLUMN IF NOT EXISTS cadastrado_em     TIMESTAMPTZ;
