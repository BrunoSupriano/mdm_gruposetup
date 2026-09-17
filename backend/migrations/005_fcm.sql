-- FCM: cada device guarda seu token de push (para o painel disparar comandos).
ALTER TABLE mdm.devices ADD COLUMN IF NOT EXISTS fcm_token TEXT;
ALTER TABLE mdm.devices ADD COLUMN IF NOT EXISTS fcm_atualizado_em TIMESTAMPTZ;
