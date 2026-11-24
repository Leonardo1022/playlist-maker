CREATE TABLE IF NOT EXISTS sessao_usuario (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    usuario_id TEXT,
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    expira_em TEXT NOT NULL,
    atualizado_em TEXT NOT NULL,
    estado TEXT NOT NULL CHECK (estado IN ('ATIVO', 'EXPIRADO', 'REVOGADO', 'DESCONECTADO'))
);

CREATE TABLE IF NOT EXISTS faixa_curtida (
    id TEXT PRIMARY KEY,
    titulo TEXT NOT NULL,
    artistas TEXT NOT NULL,
    album TEXT NOT NULL,
    duracao_ms INTEGER NOT NULL,
    curtida_em TEXT NOT NULL,
    sincronizada_em TEXT NOT NULL,
    fonte TEXT NOT NULL DEFAULT 'spotify'
);

CREATE INDEX IF NOT EXISTS idx_faixa_curtida_curtida_em ON faixa_curtida (curtida_em DESC);
CREATE INDEX IF NOT EXISTS idx_faixa_curtida_titulo ON faixa_curtida (titulo);

CREATE TABLE IF NOT EXISTS registro_sincronizacao (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    iniciado_em TEXT NOT NULL,
    finalizado_em TEXT,
    status TEXT NOT NULL CHECK (status IN ('EM_ANDAMENTO', 'CONCLUIDO', 'LIMITADO', 'ERRO')),
    total_curtidas INTEGER DEFAULT 0,
    novas_faixas INTEGER DEFAULT 0,
    removidas_faixas INTEGER DEFAULT 0,
    mensagem TEXT,
    tempo_espera_segundos INTEGER
);

CREATE INDEX IF NOT EXISTS idx_registro_sincronizacao_iniciado_em ON registro_sincronizacao (iniciado_em DESC);
